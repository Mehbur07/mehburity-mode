package com.mehbur.mehburity.ai;

import java.util.List;
import java.util.Locale;

/**
 * Turns what Gemini heard on push-to-talk into a chat message for Mehburity. The message goes through normal chat,
 * so chat orders ("maden kaz"), apologies and questions all work exactly as if they had been typed.
 */
public final class VoiceTranscript {
	/** Minecraft's chat message limit. */
	public static final int MAX_CHAT_LENGTH = 256;
	/** Used when the server has no chat prefixes at all. */
	private static final String DEFAULT_NAME = "Mehburity";

	private VoiceTranscript() {
	}

	/**
	 * Cleans Gemini's transcript: surrounding quotes and whitespace go, line breaks become spaces, and the markers
	 * models use for "nothing was said" ({@code [silence]}, {@code (no speech)}, ...) become an empty string.
	 */
	public static String clean(String transcript) {
		String text = transcript == null ? "" : transcript.replaceAll("\\s+", " ").strip();
		while (text.length() >= 2 && isQuote(text.charAt(0)) && isQuote(text.charAt(text.length() - 1))) {
			text = text.substring(1, text.length() - 1).strip();
		}
		String marker = text.toLowerCase(Locale.ROOT).replaceAll("[\\[\\]().*]", "").strip();
		if (marker.isEmpty() || marker.equals("silence") || marker.equals("no speech") || marker.equals("inaudible")
				|| marker.equals("empty") || marker.equals("sessizlik")) {
			return "";
		}
		return text;
	}

	/**
	 * The chat line to send: addressed to Mehburity with the first chat prefix ("Mehburity, ..."), unless the player
	 * already said its name, and short enough for chat.
	 */
	public static String toChat(String transcript, List<String> prefixes) {
		String text = clean(transcript);
		if (text.isEmpty()) {
			return "";
		}
		String name = prefixes.isEmpty() || prefixes.getFirst().isBlank() ? DEFAULT_NAME : prefixes.getFirst().strip();
		name = name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
		String message = ChatAddress.extract(text, prefixes) != null ? text : name + ", " + text;
		return message.length() > MAX_CHAT_LENGTH ? message.substring(0, MAX_CHAT_LENGTH).strip() : message;
	}

	private static boolean isQuote(char c) {
		return c == '"' || c == '\'' || c == '“' || c == '”' || c == '«' || c == '»';
	}
}
