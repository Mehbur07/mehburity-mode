package com.mehbur.mehburity.client.ai;

import com.mehbur.mehburity.MehburityMod;
import com.mehbur.mehburity.ai.GeminiException;
import com.mehbur.mehburity.client.config.MehburityClientConfig;
import com.mehbur.mehburity.entity.MehburitySounds;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundSource;

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
import java.util.stream.Stream;

/**
 * Mehburity's voice: Gemini text-to-speech, played in order on a background thread so lines never overlap.
 * Short lines are cached on disk (the greeting is fetched once). Without a key, or when TTS fails, Mehburity
 * chirps instead.
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

	private VoicePlayer() {
	}

	/** Says {@code text} with the configured voice. Call from the render thread. */
	public static void speak(String text) {
		MehburityClientConfig config = MehburityClientConfig.get();
		if (!config.voiceEnabled || text.isBlank()) {
			return;
		}
		speak(text, config.voiceName, config.voiceStyle);
	}

	/** Used by the settings screen's "Listen" button with an unsaved voice. */
	public static void speak(String text, String voice, String style) {
		MehburityClientConfig config = MehburityClientConfig.get();
		if (config.geminiApiKey.isBlank()) {
			chirp();
			return;
		}
		int asked = generation;
		CompletableFuture<byte[]> audio = fetch(config.voiceModel, voice, style, text);
		queue = queue.thenCombineAsync(audio.handle((wav, error) -> {
			if (error != null) {
				MehburityMod.LOGGER.warn("Mehburity voice failed: {}", GeminiException.of(error).getMessage());
				Minecraft.getInstance().execute(VoicePlayer::chirp);
				return null;
			}
			return wav;
		}), (ignored, wav) -> {
			if (wav != null && asked == generation) {
				play(wav, asked);
			}
			return null;
		}, AUDIO);
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

	/** Blocking playback on the audio thread, scaled by our volume × Minecraft's voice/master volume. */
	private static void play(byte[] wav, int asked) {
		try (AudioInputStream stream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav))) {
			AudioFormat format = stream.getFormat();
			try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
				line.open(format);
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
					line.write(buffer, 0, read);
				}
				line.drain();
			}
		} catch (Exception e) {
			// No audio device, unsupported format...: the chat line is still there.
			MehburityMod.LOGGER.warn("Could not play Mehburity's voice: {}", e.toString());
		} finally {
			playing = false;
		}
	}

	private static void applyVolume(SourceDataLine line) {
		float volume = (float) MehburityClientConfig.get().voiceVolume
				* Minecraft.getInstance().options.getFinalSoundSourceVolume(SoundSource.VOICE);
		if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
			FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
			float db = volume <= 0.0001F ? gain.getMinimum() : (float) (20.0 * Math.log10(volume));
			gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
		}
	}

	/** Short "speaking" sound when there is no voice. */
	public static void chirp() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(MehburitySounds.SPEAK, 1.0F));
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
