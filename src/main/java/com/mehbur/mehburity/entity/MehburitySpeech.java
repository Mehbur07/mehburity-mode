package com.mehbur.mehburity.entity;

import com.mehbur.mehburity.MehburityMod;
import com.mehbur.mehburity.network.SpeakPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Formats and sends what Mehburity says, so every line looks the same in chat, moves its mouth and is spoken out
 * loud; the voice gets quieter the farther the player is from the Mehburity that talks.
 */
public final class MehburitySpeech {
	private static final String KEY = MehburityMod.MOD_ID + ".speech.";

	private MehburitySpeech() {
	}

	public static Component line(Component message) {
		return Component.empty()
				.append(Component.literal("[Mehburity] ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
				.append(message.copy().withStyle(ChatFormatting.WHITE));
	}

	/** {@code player}'s own Mehburity says {@code message} to them. */
	public static void say(ServerPlayer player, Component message) {
		say(player, message, companionOf(player));
	}

	/** {@code speaker} (null: not out in the world) says {@code message} to {@code player}: chat, mouth and voice. */
	public static void say(ServerPlayer player, Component message, @Nullable MehburityEntity speaker) {
		player.sendSystemMessage(line(message));
		if (speaker != null) {
			speaker.talk(message.getString());
		}
		sendVoice(player, message, speaker, false);
	}

	/**
	 * Like {@link #say}, but in a deep, slow, demonic voice (and dark red in chat): its revenge. The voice is heard at
	 * full volume however far away {@code speaker} is, like a voice in the player's head.
	 */
	public static void sayDeep(ServerPlayer player, Component message, MehburityEntity speaker) {
		player.sendSystemMessage(Component.empty()
				.append(Component.literal("[Mehburity] ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
				.append(message.copy().withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC)));
		speaker.talk(message.getString());
		sendVoice(player, message, null, true);
	}

	/** Moves the mouth of {@code player}'s Mehburity while it says {@code text}, if it is out in the world. */
	public static void talk(ServerPlayer player, String text) {
		MehburityEntity mehburity = companionOf(player);
		if (mehburity != null) {
			mehburity.talk(text);
		}
	}

	/** Asks the player's client to say {@code message} out loud in their own Mehburity's voice. */
	public static void sendVoice(ServerPlayer player, Component message) {
		sendVoice(player, message, companionOf(player), false);
	}

	/** Gemini TTS (or a chirp without a key) on the player's client, coming from {@code speaker}. */
	public static void sendVoice(ServerPlayer player, Component message, @Nullable MehburityEntity speaker, boolean deep) {
		if (!ServerPlayNetworking.canSend(player, SpeakPayload.TYPE)) {
			return;
		}
		SpeakPayload payload = speaker != null && speaker.level() == player.level()
				? new SpeakPayload(message, speaker.getId(), Optional.of(speaker.position()), deep)
				: new SpeakPayload(message, SpeakPayload.NO_SPEAKER, Optional.empty(), deep);
		ServerPlayNetworking.send(player, payload);
	}

	/** A translated line; {@code fallback} is the English text shown when the key is missing. */
	public static MutableComponent text(String key, String fallback, Object... args) {
		return Component.translatableWithFallback(KEY + key, fallback, args);
	}

	private static @Nullable MehburityEntity companionOf(ServerPlayer player) {
		return CompanionManager.find(player.level().getServer(), player.getUUID());
	}
}
