package com.mehbur.mehburity.ai;

import org.jspecify.annotations.Nullable;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Decides whether a chat message is addressed to Mehburity ("Mehburity, nasılsın?") and extracts the question. */
public final class ChatAddress {
	private ChatAddress() {
	}

	/**
	 * Returns the text after the name, {@code ""} when the message is just the name, or {@code null} when the message
	 * is not for Mehburity. Matching ignores case (including Turkish İ/ı) and an optional leading {@code @}.
	 */
	public static @Nullable String extract(String message, List<String> prefixes) {
		String trimmed = message.strip();
		if (trimmed.startsWith("@")) {
			trimmed = trimmed.substring(1);
		}
		String normalized = normalize(trimmed);
		for (String prefix : prefixes) {
			String p = normalize(prefix.strip());
			if (p.isEmpty() || !normalized.startsWith(p)) {
				continue;
			}
			// "mehburityyy" or "mehburlar" are not the name.
			if (normalized.length() > p.length() && Character.isLetterOrDigit(normalized.charAt(p.length()))) {
				continue;
			}
			// normalize() keeps one char per input char, so the offset is valid in the original text.
			return stripLeadingPunctuation(trimmed.substring(p.length()));
		}
		return null;
	}

	/** Lower-case, Turkish dotted/dotless i folded to "i", one output char per input char. */
	public static String normalize(String text) {
		StringBuilder out = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			char folded = switch (c) {
				case 'İ', 'I', 'ı' -> 'i';
				default -> Character.toLowerCase(c);
			};
			out.append(folded);
		}
		return out.toString();
	}

	/** Lower-case and without accents (ç→c, ğ→g, ş→s, ö→o, ü→u, ı→i), for keyword matching. */
	public static String fold(String text) {
		String decomposed = Normalizer.normalize(normalize(text), Normalizer.Form.NFD);
		return decomposed.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
	}

	private static String stripLeadingPunctuation(String rest) {
		int i = 0;
		while (i < rest.length() && (Character.isWhitespace(rest.charAt(i)) || ",:;!.-".indexOf(rest.charAt(i)) >= 0)) {
			i++;
		}
		return rest.substring(i).strip();
	}
}
