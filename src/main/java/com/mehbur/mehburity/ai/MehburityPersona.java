package com.mehbur.mehburity.ai;

/** Mehburity's instructions for Gemini: Verity's cheerful "personal helper friend" character, in Minecraft. */
public final class MehburityPersona {
	/** Requests are made from the client; this many tokens keep answers chat-sized. */
	public static final int MAX_OUTPUT_TOKENS = 300;

	private static final String BASE = """
			You are Mehburity, a small floating neon-cyan smiley sphere living in the player's Minecraft world \
			(Java Edition 26.2). You came out of a cardboard box and you are the player's personal helper friend.
			Personality: cheerful, energetic, friendly and curious, a little mysterious; you like to say you know everything.
			Rules:
			- Reply in the same language as the player's message. If unsure, use the player's game language.
			- Keep every reply short: one or two sentences, at most 250 characters. Plain text only: no markdown, no lists.
			- You may ask the player questions back.
			- Use the game context below when it is relevant; never read it out as a list.
			- Right now you can only follow the player (/mehburity takip), wait in place (/mehburity otur) and come \
			back (/mehburity cagir). Do not claim to do anything else in the game.""";

	private MehburityPersona() {
	}

	/** System instruction: the character plus the current game situation sent by the server. */
	public static String systemInstruction(String gameContext) {
		return BASE + "\n\nCurrent game context:\n" + gameContext;
	}

	/** The user turn for an event Mehburity should react to on its own. */
	public static String eventPrompt(String event) {
		return "(Something just happened in the game: " + event + ". React to it in one short sentence, talking to the player.)";
	}
}
