package com.mehbur.mehburity.ai;

/** A Gemini call that failed, sorted into causes the player can act on. Never contains the API key. */
public final class GeminiException extends RuntimeException {
	public enum Kind {
		/** No API key entered. */
		NO_KEY,
		/** The key was rejected. */
		INVALID_KEY,
		/** Free-tier or rate limit reached. */
		QUOTA,
		/** The selected model does not exist (anymore). */
		MODEL_NOT_FOUND,
		/** Gemini is not offered in the player's region. */
		REGION,
		/** No internet, DNS failure or timeout. */
		NETWORK,
		/** Google-side error (5xx). */
		SERVER,
		/** Request rejected for another reason (400). */
		BAD_REQUEST,
		/** The answer had no usable text or audio (blocked or empty). */
		BAD_RESPONSE
	}

	private final Kind kind;

	public GeminiException(Kind kind, String message) {
		super(message);
		this.kind = kind;
	}

	public GeminiException(Kind kind, String message, Throwable cause) {
		super(message, cause);
		this.kind = kind;
	}

	public Kind kind() {
		return kind;
	}

	/** Unwraps {@code CompletionException}s so callers can switch on the kind. */
	public static GeminiException of(Throwable error) {
		Throwable current = error;
		while (current != null) {
			if (current instanceof GeminiException gemini) {
				return gemini;
			}
			current = current.getCause();
		}
		return new GeminiException(Kind.NETWORK, String.valueOf(error), error);
	}
}
