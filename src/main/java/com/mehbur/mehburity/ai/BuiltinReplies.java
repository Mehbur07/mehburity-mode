package com.mehbur.mehburity.ai;

import java.util.List;

/**
 * What Mehburity says without Gemini (no key, no internet, quota...). Simple keyword rules in Turkish and English;
 * the language follows the player's game language.
 */
public final class BuiltinReplies {
	private record Rule(List<String> keywords, String turkish, String english) {
	}

	private static final List<Rule> RULES = List.of(
			new Rule(List.of("kimsin", "adin ne", "ismin", "who are you", "your name"),
					"Ben Mehburity! Kişisel yardımcı arkadaşın.",
					"I'm Mehburity! Your personal helper friend."),
			new Rule(List.of("nasilsin", "naber", "ne haber", "how are you"),
					"Harikayım! Sen nasılsın?",
					"I'm great! How are you?"),
			new Rule(List.of("yardim", "komut", "ne yapabilirsin", "help", "command", "what can you do"),
					"Şimdilik /mehburity takip, /mehburity otur ve /mehburity cagir komutlarını biliyorum.",
					"For now I know /mehburity takip (follow), /mehburity otur (stay) and /mehburity cagir (come)."),
			new Rule(List.of("tesekkur", "sagol", "eyvallah", "thank"),
					"Rica ederim!",
					"You're welcome!"),
			new Rule(List.of("merhaba", "selam", "hey", "hello", "hi", "sa"),
					"Merhaba! Buradayım.",
					"Hi! I'm right here."));

	private BuiltinReplies() {
	}

	public static boolean isTurkish(String languageCode) {
		return languageCode != null && languageCode.toLowerCase().startsWith("tr");
	}

	/** Reply to something the player said. */
	public static String reply(String message, String languageCode) {
		boolean turkish = isTurkish(languageCode);
		String folded = ChatAddress.fold(message);
		for (Rule rule : RULES) {
			for (String keyword : rule.keywords()) {
				if (containsWord(folded, keyword)) {
					return turkish ? rule.turkish() : rule.english();
				}
			}
		}
		if (message.isBlank()) {
			return turkish ? "Efendim? Buradayım!" : "Yes? I'm here!";
		}
		return turkish
				? "Şu an Gemini'ye bağlı değilim, sadece basit şeyler söyleyebiliyorum. Modlar → Mehburity → Yapılandır'dan API anahtarı girebilirsin."
				: "I'm not connected to Gemini right now, so I can only say simple things. Add an API key in Mods → Mehburity → Configure.";
	}

	/** Reaction to a game event without Gemini. */
	public static String reactTo(String event, String languageCode) {
		boolean turkish = isTurkish(languageCode);
		if (event.contains("died")) {
			return turkish ? "Hayır! Hemen geri gel, seni bekliyorum!" : "Oh no! Come back quickly, I'm waiting!";
		}
		return turkish ? "Dikkat et!" : "Watch out!";
	}

	private static boolean containsWord(String text, String keyword) {
		int from = 0;
		while (true) {
			int index = text.indexOf(keyword, from);
			if (index < 0) {
				return false;
			}
			int end = index + keyword.length();
			boolean startOk = index == 0 || !Character.isLetterOrDigit(text.charAt(index - 1));
			boolean endOk = end == text.length() || !Character.isLetterOrDigit(text.charAt(end));
			if (startOk && endOk) {
				return true;
			}
			from = index + 1;
		}
	}
}
