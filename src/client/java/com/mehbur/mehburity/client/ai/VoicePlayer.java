package com.mehbur.mehburity.client.ai;

import com.mehbur.mehburity.MehburityMod;
import com.mehbur.mehburity.ai.GeminiException;
import com.mehbur.mehburity.client.config.MehburityClientConfig;
import com.mehbur.mehburity.entity.MehburityEntity;
import com.mehbur.mehburity.entity.MehburitySounds;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Mehburity's voice: Gemini text-to-speech, played in order on a background thread so lines never overlap.
 * Short lines are cached on disk (the greeting is fetched once). Without a key, or when TTS fails, Mehburity
 * chirps instead. Both come from where Mehburity is: full volume up close, fading out with distance.
 */
public final class VoicePlayer {
	private static final int CACHE_MAX_CHARS = 300;
	private static final int CACHE_MAX_FILES = 100;
	private static final ExecutorService AUDIO = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Mehburity Voice");
		thread.setDaemon(true);
		return thread;
	});
	/** Each line waits for the previous one, so they play in the order they were asked for. */
	private static CompletableFuture<Void> queue = CompletableFuture.completedFuture(null);
	/** Bumped by {@link #hush()}: lines asked for before it are cut off or skipped. */
	private static volatile int generation;
	/** True while a line is coming out of the speakers; Mehburity moves its mouth meanwhile. */
	private static volatile boolean playing;
	/** Full volume this close to Mehburity, silent this far away (blocks); linear in between. */
	static final double FULL_VOLUME_DISTANCE = 3.0;
	static final double SILENT_DISTANCE = 32.0;
	/** Who is saying the current line, and how loud that is from here (updated every client tick). */
	private static volatile Speaker current = Speaker.NONE;
	private static volatile float distanceVolume = 1.0F;
	/** About this many seconds of audio are buffered, so walking away turns the voice down without a lag. */
	private static final float LINE_BUFFER_SECONDS = 0.25F;
	/** Its revenge line: a gravelly Gemini voice told to sound like a demon, then played slower and lower. */
	private static final String DEEP_VOICE = "Algenib";
	private static final String DEEP_STYLE = "a very deep, slow, menacing, demonic voice, like a growl coming out of the dark";
	private static final float DEEP_PITCH = 0.78F;
	/** Part of every line's style, so the cached lines said before it (with a wrong name) are fetched again. */
	private static final String NAME_HINT = "Say the name Mehburity exactly as meh-BUR-ih-tee, rhyming with Verity; "
			+ "there is no s sound in it";

	/**
	 * The Mehburity that talks: its entity id (or {@link #NO_ENTITY}) and where it stood, used until this client
	 * knows the entity. {@link #NONE}: not out in the world (carried, still in its box), heard at full volume.
	 */
	public record Speaker(int entityId, @Nullable Vec3 from) {
		public static final int NO_ENTITY = -1;
		public static final Speaker NONE = new Speaker(NO_ENTITY, null);

		/** The local player's own Mehburity, if it is out in the world. */
		public static Speaker own() {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.level == null || minecraft.player == null) {
				return NONE;
			}
			for (Entity entity : minecraft.level.entitiesForRendering()) {
				if (entity instanceof MehburityEntity mehburity && mehburity.isOwnedBy(minecraft.player.getUUID())) {
					return new Speaker(mehburity.getId(), mehburity.position());
				}
			}
			return NONE;
		}

		/** Where the voice comes from now (the middle of the ball), or null when it is heard at full volume. */
		@Nullable Vec3 position() {
			Minecraft minecraft = Minecraft.getInstance();
			Entity entity = minecraft.level == null || entityId == NO_ENTITY ? null : minecraft.level.getEntity(entityId);
			Vec3 at = entity != null ? entity.position() : from;
			return at == null ? null : at.add(0.0, 0.4, 0.0);
		}
	}

	private VoicePlayer() {
	}

	/** The local player's own Mehburity says {@code text} with the configured voice. Call from the render thread. */
	public static void speak(String text) {
		speak(text, Speaker.own());
	}

	/** {@code speaker} says {@code text}; quieter the farther it is. Call from the render thread. */
	public static void speak(String text, Speaker speaker) {
		speak(text, speaker, false);
	}

	/** Same, in the deep demonic voice of its revenge when {@code deep}. */
	public static void speak(String text, Speaker speaker, boolean deep) {
		MehburityClientConfig config = MehburityClientConfig.get();
		if (!config.voiceEnabled || text.isBlank()) {
			return;
		}
		if (deep) {
			speak(text, DEEP_VOICE, DEEP_STYLE, speaker, true);
		} else {
			speak(text, config.voiceName, config.voiceStyle, speaker, false);
		}
	}

	/** Used by the settings screen's "Listen" button with an unsaved voice (at full volume). */
	public static void speak(String text, String voice, String style) {
		speak(text, voice, style, Speaker.NONE, false);
	}

	private static void speak(String text, String voice, String style, Speaker speaker, boolean deep) {
		MehburityClientConfig config = MehburityClientConfig.get();
		if (config.geminiApiKey.isBlank()) {
			chirp(speaker, deep);
			return;
		}
		int asked = generation;
		CompletableFuture<byte[]> audio = fetch(config.voiceModel, voice, withNameHint(style), text);
		queue = queue.thenCombineAsync(audio.handle((wav, error) -> {
			if (error != null) {
				MehburityMod.LOGGER.warn("Mehburity voice failed: {}", GeminiException.of(error).getMessage());
				Minecraft.getInstance().execute(() -> chirp(speaker, deep));
				return null;
			}
			return wav;
		}), (ignored, wav) -> {
			if (wav != null && asked == generation) {
				play(wav, asked, speaker, deep);
			}
			return null;
		}, AUDIO);
	}

	/**
	 * Tells the voice how to say the name, on top of the configured style. Text-to-speech does not know the made-up
	 * name and guesses things like "Mehburisty"; respelling it in the text made it worse than this hint.
	 */
	static String withNameHint(String style) {
		return style == null || style.isBlank() ? NAME_HINT : style + ". " + NAME_HINT;
	}

	private static CompletableFuture<byte[]> fetch(String model, String voice, String style, String text) {
		Path cached = text.length() <= CACHE_MAX_CHARS ? cacheFile(model, voice, style, text) : null;
		if (cached != null && Files.isRegularFile(cached)) {
			try {
				return CompletableFuture.completedFuture(Files.readAllBytes(cached));
			} catch (IOException e) {
				MehburityMod.LOGGER.debug("Unreadable voice cache file {}", cached, e);
			}
		}
		return ClientAi.gemini().speak(model, voice, style, text).thenApply(wav -> {
			if (cached != null) {
				store(cached, wav);
			}
			return wav;
		});
	}

	/** Stops Mehburity mid-sentence and drops queued lines (push-to-talk starts, so the microphone hears only you). */
	public static void hush() {
		generation++;
	}

	public static boolean isPlaying() {
		return playing;
	}

	/** Whether the line coming out of the speakers right now is said by this entity (its mouth moves). */
	public static boolean isVoiceOf(int entityId) {
		return playing && current.entityId() == entityId;
	}

	/** Every client tick: how loud the current line is from where the player stands now. */
	public static void tick(Minecraft minecraft) {
		distanceVolume = minecraft.player == null ? 1.0F : volumeAt(minecraft.player.getEyePosition(), current.position());
	}

	/** 1 up to {@link #FULL_VOLUME_DISTANCE}, fading linearly to 0 at {@link #SILENT_DISTANCE}; 1 without a position. */
	public static float volumeAt(Vec3 listener, @Nullable Vec3 source) {
		if (source == null) {
			return 1.0F;
		}
		double distance = listener.distanceTo(source);
		double fade = (distance - FULL_VOLUME_DISTANCE) / (SILENT_DISTANCE - FULL_VOLUME_DISTANCE);
		return (float) Math.max(0.0, Math.min(1.0, 1.0 - fade));
	}

	/**
	 * Blocking playback on the audio thread, scaled by our volume × Minecraft's voice/master volume × how far
	 * away Mehburity is (re-applied between chunks, so it follows the player walking off mid-sentence). The deep
	 * voice plays the same samples at a lower rate: slower and lower, like a recording slowed down.
	 */
	private static void play(byte[] wav, int asked, Speaker speaker, boolean deep) {
		try (AudioInputStream stream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav))) {
			AudioFormat format = deep ? slowed(stream.getFormat(), DEEP_PITCH) : stream.getFormat();
			try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
				int frame = Math.max(1, format.getFrameSize());
				int bufferBytes = Math.max(frame, (int) (format.getFrameRate() * LINE_BUFFER_SECONDS) * frame);
				line.open(format, bufferBytes);
				current = speaker;
				distanceVolume = startVolume(speaker);
				applyVolume(line);
				line.start();
				playing = true;
				byte[] buffer = new byte[4096];
				int read;
				while ((read = stream.read(buffer)) > 0) {
					if (asked != generation) {
						line.flush(); // hushed
						return;
					}
					applyVolume(line);
					line.write(buffer, 0, read);
				}
				line.drain();
			}
		} catch (Exception e) {
			// No audio device, unsupported format...: the chat line is still there.
			MehburityMod.LOGGER.warn("Could not play Mehburity's voice: {}", e.toString());
		} finally {
			playing = false;
			current = Speaker.NONE;
		}
	}

	private static AudioFormat slowed(AudioFormat format, float factor) {
		return new AudioFormat(format.getEncoding(), format.getSampleRate() * factor, format.getSampleSizeInBits(),
				format.getChannels(), format.getFrameSize(), format.getFrameRate() * factor, format.isBigEndian());
	}

	/** How loud the line starts, worked out on the render thread (the client tick keeps it up to date afterwards). */
	private static float startVolume(Speaker speaker) {
		try {
			return Minecraft.getInstance().submit(() -> {
				var player = Minecraft.getInstance().player;
				return player == null ? 1.0F : volumeAt(player.getEyePosition(), speaker.position());
			}).get(1, TimeUnit.SECONDS);
		} catch (Exception e) {
			return distanceVolume; // the game is busy: the next client tick corrects it
		}
	}

	private static void applyVolume(SourceDataLine line) {
		float volume = (float) MehburityClientConfig.get().voiceVolume
				* Minecraft.getInstance().options.getFinalSoundSourceVolume(SoundSource.VOICE) * distanceVolume;
		if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
			FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
			float db = volume <= 0.0001F ? gain.getMinimum() : (float) (20.0 * Math.log10(volume));
			gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
		}
	}

	/**
	 * Short "speaking" sound when there is no voice, from where Mehburity is (Minecraft fades it out over
	 * {@link #SILENT_DISTANCE} blocks); at full volume when it is not out in the world. Deep: a low growl.
	 */
	public static void chirp(Speaker speaker, boolean deep) {
		Minecraft minecraft = Minecraft.getInstance();
		Vec3 at = speaker.position();
		float pitch = deep ? 0.5F : 1.0F;
		if (at == null || minecraft.level == null) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(MehburitySounds.SPEAK, pitch));
			return;
		}
		// Sounds are heard over 16 blocks per point of volume.
		minecraft.level.playLocalSound(at.x, at.y, at.z, MehburitySounds.SPEAK, SoundSource.NEUTRAL,
				(float) (SILENT_DISTANCE / 16.0), pitch, false);
	}

	// ---------------------------------------------------------------- disk cache

	private static Path cacheDir() {
		return FabricLoader.getInstance().getConfigDir().resolve(MehburityMod.MOD_ID).resolve("voice-cache");
	}

	private static Path cacheFile(String model, String voice, String style, String text) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest((model + '\n' + voice + '\n' + style + '\n' + text).getBytes(StandardCharsets.UTF_8));
			return cacheDir().resolve(HexFormat.of().formatHex(hash, 0, 16) + ".wav");
		} catch (NoSuchAlgorithmException e) {
			return null;
		}
	}

	private static void store(Path file, byte[] wav) {
		try {
			Files.createDirectories(file.getParent());
			Files.write(file, wav);
			try (Stream<Path> files = Files.list(file.getParent())) {
				var all = files.sorted(Comparator.comparingLong(VoicePlayer::modified)).toList();
				for (int i = 0; i < all.size() - CACHE_MAX_FILES; i++) {
					Files.deleteIfExists(all.get(i));
				}
			}
		} catch (IOException e) {
			MehburityMod.LOGGER.debug("Could not cache Mehburity's voice", e);
		}
	}

	private static long modified(Path path) {
		try {
			return Files.getLastModifiedTime(path).toMillis();
		} catch (IOException e) {
			return 0L;
		}
	}
}
