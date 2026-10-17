package com.mehbur.mehburity.client.ai;

import com.mehbur.mehburity.MehburityMod;
import com.mehbur.mehburity.ai.GeminiClient;
import com.mehbur.mehburity.ai.GeminiException;
import com.mehbur.mehburity.ai.VoiceTranscript;
import com.mehbur.mehburity.client.config.MehburityClientConfig;
import com.mehbur.mehburity.config.MehburityConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Push-to-talk. While the "Talk to Mehburity" key is held, the microphone records; on release the recording goes to
 * Gemini as audio, and what it heard is sent to chat as "Mehburity, ...", so chat orders and questions work the same
 * as typed ones. The key is unbound by default: set it in Mehburity's settings screen or in Controls.
 */
public final class VoiceInput {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(MehburityMod.id("mehburity"));
	public static final KeyMapping PUSH_TO_TALK = new KeyMapping("key.mehburity.push_to_talk", InputConstants.Type.KEYSYM,
			InputConstants.UNKNOWN.getValue(), CATEGORY);
	/** Shorter presses are taken as accidental. */
	private static final double MIN_SECONDS = 0.3;
	private static final String KEY = "mehburity.voice.";

	private static @Nullable MicRecorder recorder;
	/** A transcription is on its way; a new press waits for it. */
	private static boolean transcribing;

	private VoiceInput() {
	}

	public static void register() {
		KeyMappingHelper.registerKeyMapping(PUSH_TO_TALK);
		ClientTickEvents.END_CLIENT_TICK.register(VoiceInput::tick);
	}

	private static void tick(Minecraft minecraft) {
		boolean held = PUSH_TO_TALK.isDown() && minecraft.player != null && minecraft.gui.screen() == null;
		if (held && recorder == null && !transcribing) {
			start(minecraft);
		} else if (recorder != null && (!held || !recorder.isRecording())) {
			finish(minecraft);
		} else if (recorder != null && minecraft.player != null) {
			minecraft.player.sendOverlayMessage(Component.translatableWithFallback(KEY + "listening",
					"🎙 Listening... %s s (release to send)", String.format("%.1f", recorder.seconds())).withStyle(ChatFormatting.AQUA));
		}
	}

	private static void start(Minecraft minecraft) {
		// Consume the press, so holding the key does not restart right after a refusal.
		while (PUSH_TO_TALK.consumeClick()) {
			// drained
		}
		if (MehburityClientConfig.get().geminiApiKey.isBlank()) {
			overlay(minecraft, Component.translatableWithFallback(KEY + "no_key",
					"Talking needs a Gemini API key: Mods → Mehburity → Configure.").withStyle(ChatFormatting.YELLOW));
			pause(minecraft);
			return;
		}
		try {
			recorder = MicRecorder.start(MehburityClientConfig.get().pushToTalkMaxSeconds);
		} catch (Exception e) {
			MehburityMod.LOGGER.warn("No microphone for push-to-talk: {}", e.toString());
			overlay(minecraft, Component.translatableWithFallback(KEY + "no_mic",
					"No microphone found. Check Windows sound settings.").withStyle(ChatFormatting.RED));
			pause(minecraft);
			return;
		}
		VoicePlayer.hush(); // so the microphone does not hear Mehburity
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.6F, 0.4F));
	}

	private static void finish(Minecraft minecraft) {
		MicRecorder done = recorder;
		recorder = null;
		byte[] pcm = done.stop();
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.2F, 0.4F));
		if (pcm.length < MIN_SECONDS * MicRecorder.SAMPLE_RATE * 2) {
			overlay(minecraft, Component.translatableWithFallback(KEY + "too_short",
					"Hold the key while you talk.").withStyle(ChatFormatting.GRAY));
			return;
		}
		submit(pcm);
	}

	/**
	 * Sends a finished recording (16 kHz mono PCM) to Gemini and the transcript to chat. Also used by the client
	 * game test, which has no microphone. Completes with the chat line sent, or an empty string.
	 */
	public static CompletableFuture<String> submit(byte[] pcm) {
		Minecraft minecraft = Minecraft.getInstance();
		MehburityClientConfig config = MehburityClientConfig.get();
		transcribing = true;
		overlay(minecraft, Component.translatableWithFallback(KEY + "thinking", "🎙 ...").withStyle(ChatFormatting.AQUA));
		byte[] wav = GeminiClient.wavFromPcm(pcm, MicRecorder.SAMPLE_RATE, 1);
		CompletableFuture<String> sent = new CompletableFuture<>();
		ClientAi.gemini().transcribe(config.chatModel, wav, minecraft.options.languageCode)
				.whenComplete((transcript, error) -> minecraft.execute(() -> {
					transcribing = false;
					if (error != null) {
						GeminiException problem = GeminiException.of(error);
						MehburityMod.LOGGER.warn("Push-to-talk transcription failed: {}", problem.getMessage());
						overlay(minecraft, ClientAi.problemText(problem.kind()).withStyle(ChatFormatting.YELLOW));
						sent.complete("");
						return;
					}
					String message = VoiceTranscript.toChat(transcript, MehburityConfig.get().ai.chatPrefixes);
					if (message.isEmpty() || minecraft.player == null) {
						overlay(minecraft, Component.translatableWithFallback(KEY + "not_understood",
								"I didn't catch that. Try again?").withStyle(ChatFormatting.GRAY));
						sent.complete("");
						return;
					}
					overlay(minecraft, Component.empty());
					minecraft.player.connection.sendChat(message);
					sent.complete(message);
				}));
		return sent;
	}

	/** After a refusal, holding the key does not retry (and repeat the message) every tick: wait a second. */
	private static void pause(Minecraft minecraft) {
		transcribing = true;
		CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS).execute(() -> minecraft.execute(() -> transcribing = false));
	}

	public static boolean isRecording() {
		return recorder != null;
	}

	/** Recording, or waiting for Gemini's transcript. */
	public static boolean isBusy() {
		return recorder != null || transcribing;
	}

	private static void overlay(Minecraft minecraft, Component message) {
		if (minecraft.player != null) {
			minecraft.player.sendOverlayMessage(message);
		}
	}
}
