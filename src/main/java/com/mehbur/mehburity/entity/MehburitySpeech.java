package com.mehbur.mehburity.entity;

import com.mehbur.mehburity.MehburityMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/** Formats and sends what Mehburity says, so every line in chat looks the same. */
public final class MehburitySpeech {
	private static final String KEY = MehburityMod.MOD_ID + ".speech.";

	private MehburitySpeech() {
	}

	public static Component line(Component message) {
		return Component.empty()
				.append(Component.literal("[Mehburity] ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
				.append(message.copy().withStyle(ChatFormatting.WHITE));
	}

	public static void say(ServerPlayer player, Component message) {
		player.sendSystemMessage(line(message));
	}

	/** A translated line; {@code fallback} is the English text shown when the key is missing. */
	public static MutableComponent text(String key, String fallback, Object... args) {
		return Component.translatableWithFallback(KEY + key, fallback, args);
	}
}
