package com.mehbur.mehburity.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Minimal client for the Gemini Interactions API ({@code POST /v1beta/interactions}), used for chat and for
 * text-to-speech. Plain Java (java.net.http + Gson) so it can be tested against a fake server.
 *
 * <p>Retries follow the usual exponential backoff with jitter: rate limits (429), Google-side errors (5xx)
 * and network failures are retried up to {@link #MAX_RETRIES} times, honouring {@code Retry-After}.
 * Conversation state is sent with every request ({@code store: false}), so nothing is kept on Google's side.
 */
public final class GeminiClient {
	public static final URI DEFAULT_BASE_URI = URI.create("https://generativelanguage.googleapis.com/");
	public static final int MAX_RETRIES = 2;
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
	private static final long BASE_BACKOFF_MS = 600;
	private static final long MAX_RETRY_AFTER_MS = 5000;
	/** Gemini TTS returns 24 kHz, 16-bit, mono PCM when asked for raw audio. */
	private static final int TTS_SAMPLE_RATE = 24000;
	private static final Gson GSON = new Gson();

	private final HttpClient http;
	private final URI baseUri;
	private final Supplier<String> apiKey;

	public GeminiClient(URI baseUri, Supplier<String> apiKey) {
		this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
		this.baseUri = baseUri;
		this.apiKey = apiKey;
	}

	// ---------------------------------------------------------------- public API

	/** Asks {@code model} for Mehburity's next line. Completes with plain text. */
	public CompletableFuture<String> chat(String model, String systemInstruction, List<ChatTurn> history, String message,
			int maxOutputTokens) {
		JsonArray input = new JsonArray();
		for (ChatTurn turn : history) {
			input.add(step(turn.fromPlayer() ? "user_input" : "model_output", textBlock(turn.text())));
		}
		input.add(step("user_input", textBlock(message)));

		JsonObject body = new JsonObject();
		body.addProperty("model", model);
		body.addProperty("system_instruction", systemInstruction);
		body.add("input", input);
		body.addProperty("store", false);
		JsonObject generation = new JsonObject();
		generation.addProperty("max_output_tokens", maxOutputTokens);
		generation.addProperty("thinking_level", "minimal");
		body.add("generation_config", generation);

		return post("v1beta/interactions", body, 0).thenCompose(response -> {
			// Some models do not accept every thinking level: retry once without it.
			if (response.isThinkingRejection()) {
				generation.remove("thinking_level");
				return post("v1beta/interactions", body, 0);
			}
			return CompletableFuture.completedFuture(response);
		}).thenApply(response -> extractText(response.requireSuccess()));
	}

	/** Text-to-speech. Completes with a playable WAV file (24 kHz mono PCM). */
	public CompletableFuture<byte[]> speak(String model, String voice, String style, String text) {
		JsonObject block = textBlock(text);
		if (style != null && !style.isBlank()) {
			JsonObject annotation = new JsonObject();
			annotation.addProperty("type", "speech_metadata");
			annotation.addProperty("style", style);
			JsonArray annotations = new JsonArray();
			annotations.add(annotation);
			block.add("annotations", annotations);
		}
		JsonArray input = new JsonArray();
		input.add(step("user_input", block));

		JsonObject body = new JsonObject();
		body.addProperty("model", model);
		body.add("input", input);
		body.addProperty("store", false);
		JsonObject format = new JsonObject();
		format.addProperty("type", "audio");
		format.addProperty("mime_type", "audio/wav");
		body.add("response_format", format);
		JsonObject speaker = new JsonObject();
		speaker.addProperty("voice", voice);
		JsonArray speech = new JsonArray();
		speech.add(speaker);
		JsonObject generation = new JsonObject();
		generation.add("speech_config", speech);
		body.add("generation_config", generation);

		return post("v1beta/interactions", body, 0).thenApply(response -> extractAudio(response.requireSuccess()));
	}

	/** Cheap call that only checks that the key works (lists one model). */
	public CompletableFuture<Void> checkKey() {
		String key = key();
		if (key == null) {
			return CompletableFuture.failedFuture(new GeminiException(GeminiException.Kind.NO_KEY, "No API key"));
		}
		HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("v1beta/models?pageSize=1"))
				.timeout(REQUEST_TIMEOUT)
				.header("x-goog-api-key", key)
				.GET()
				.build();
		return send(request, 0).thenApply(response -> {
			response.requireSuccess();
			return null;
		});
	}

	// ---------------------------------------------------------------- HTTP

	private record Response(int status, String body) {
		boolean isThinkingRejection() {
			return status == 400 && body.toLowerCase(Locale.ROOT).contains("thinking");
		}

		JsonObject requireSuccess() {
			if (status / 100 == 2) {
				try {
					return JsonParser.parseString(body).getAsJsonObject();
				} catch (JsonParseException | IllegalStateException e) {
					throw new GeminiException(GeminiException.Kind.BAD_RESPONSE, "Response is not JSON", e);
				}
			}
			throw toException(status, body);
		}
	}

	private String key() {
		String key = apiKey.get();
		return key == null || key.isBlank() ? null : key.strip();
	}

	private CompletableFuture<Response> post(String path, JsonObject body, int attempt) {
		String key = key();
		if (key == null) {
			return CompletableFuture.failedFuture(new GeminiException(GeminiException.Kind.NO_KEY, "No API key"));
		}
		HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
				.timeout(REQUEST_TIMEOUT)
				.header("Content-Type", "application/json")
				.header("x-goog-api-key", key)
				.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
				.build();
		return send(request, attempt);
	}

	private CompletableFuture<Response> send(HttpRequest request, int attempt) {
		return http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
				.handle((response, error) -> {
					if (error != null) {
						if (attempt < MAX_RETRIES) {
							return retryLater(request, attempt, backoff(attempt));
						}
						return CompletableFuture.<Response>failedFuture(
								new GeminiException(GeminiException.Kind.NETWORK, "Could not reach Gemini: " + rootMessage(error), error));
					}
					int status = response.statusCode();
					boolean retryable = status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
					if (retryable && attempt < MAX_RETRIES) {
						long delay = response.headers().firstValue("Retry-After")
								.map(GeminiClient::parseRetryAfter)
								.orElse(backoff(attempt));
						return retryLater(request, attempt, delay);
					}
					return CompletableFuture.completedFuture(new Response(status, response.body()));
				})
				.thenCompose(future -> future);
	}

	private CompletableFuture<Response> retryLater(HttpRequest request, int attempt, long delayMs) {
		return CompletableFuture.supplyAsync(() -> null, CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS))
				.thenCompose(ignored -> send(request, attempt + 1));
	}

	/** 600 ms, 1200 ms, ... plus up to 300 ms of random jitter so many clients do not retry in lockstep. */
	private static long backoff(int attempt) {
		return BASE_BACKOFF_MS * (1L << attempt) + ThreadLocalRandom.current().nextLong(300);
	}

	private static long parseRetryAfter(String value) {
		try {
			return Math.min(MAX_RETRY_AFTER_MS, Math.max(0, (long) (Double.parseDouble(value.strip()) * 1000)));
		} catch (NumberFormatException e) {
			return BASE_BACKOFF_MS;
		}
	}

	private static String rootMessage(Throwable error) {
		Throwable root = error;
		while (root.getCause() != null) {
			root = root.getCause();
		}
		return root instanceof IOException && root.getMessage() == null ? root.getClass().getSimpleName() : String.valueOf(root.getMessage());
	}

	/** Maps an HTTP error to a kind. Google wraps errors as {@code {"error": {"code", "message", "status"}}}. */
	static GeminiException toException(int status, String body) {
		String message = body;
		try {
			JsonObject error = JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("error");
			if (error != null && error.has("message")) {
				message = error.get("message").getAsString();
			}
		} catch (RuntimeException ignored) {
			// Not JSON: keep the raw body.
		}
		String lower = message.toLowerCase(Locale.ROOT);
		GeminiException.Kind kind = switch (status) {
			case 400 -> lower.contains("api key") || lower.contains("api_key") ? GeminiException.Kind.INVALID_KEY : GeminiException.Kind.BAD_REQUEST;
			case 401 -> GeminiException.Kind.INVALID_KEY;
			case 403 -> lower.contains("location") || lower.contains("region") ? GeminiException.Kind.REGION : GeminiException.Kind.INVALID_KEY;
			case 404 -> GeminiException.Kind.MODEL_NOT_FOUND;
			case 429 -> GeminiException.Kind.QUOTA;
			default -> status >= 500 ? GeminiException.Kind.SERVER : GeminiException.Kind.BAD_REQUEST;
		};
		return new GeminiException(kind, "HTTP " + status + ": " + message);
	}

	// ---------------------------------------------------------------- JSON

	private static JsonObject textBlock(String text) {
		JsonObject block = new JsonObject();
		block.addProperty("type", "text");
		block.addProperty("text", text);
		return block;
	}

	private static JsonObject step(String type, JsonObject content) {
		JsonObject step = new JsonObject();
		step.addProperty("type", type);
		JsonArray blocks = new JsonArray();
		blocks.add(content);
		step.add("content", blocks);
		return step;
	}

	/** Text of the last {@code model_output} step. */
	static String extractText(JsonObject interaction) {
		JsonObject output = lastModelOutput(interaction);
		StringBuilder text = new StringBuilder();
		for (JsonElement element : output.getAsJsonArray("content")) {
			JsonObject block = element.getAsJsonObject();
			if ("text".equals(stringOrNull(block, "type")) && block.has("text")) {
				text.append(block.get("text").getAsString());
			}
		}
		if (text.isEmpty()) {
			throw new GeminiException(GeminiException.Kind.BAD_RESPONSE, "Gemini returned no text");
		}
		return text.toString();
	}

	/** Audio of the last {@code model_output} step as a WAV file; raw PCM gets a WAV header. */
	static byte[] extractAudio(JsonObject interaction) {
		JsonObject output = lastModelOutput(interaction);
		for (JsonElement element : output.getAsJsonArray("content")) {
			JsonObject block = element.getAsJsonObject();
			if ("audio".equals(stringOrNull(block, "type")) && block.has("data")) {
				byte[] data = Base64.getDecoder().decode(block.get("data").getAsString());
				String mime = String.valueOf(stringOrNull(block, "mime_type")).toLowerCase(Locale.ROOT);
				if (mime.startsWith("audio/l16") || mime.contains("pcm")) {
					int rate = block.has("sample_rate") ? block.get("sample_rate").getAsInt() : TTS_SAMPLE_RATE;
					int channels = block.has("channels") ? block.get("channels").getAsInt() : 1;
					return wavFromPcm(data, rate, channels);
				}
				return data;
			}
		}
		throw new GeminiException(GeminiException.Kind.BAD_RESPONSE, "Gemini returned no audio");
	}

	private static JsonObject lastModelOutput(JsonObject interaction) {
		String status = stringOrNull(interaction, "status");
		JsonArray steps = interaction.has("steps") ? interaction.getAsJsonArray("steps") : new JsonArray();
		for (int i = steps.size() - 1; i >= 0; i--) {
			JsonObject step = steps.get(i).getAsJsonObject();
			if ("model_output".equals(stringOrNull(step, "type"))) {
				if (step.has("error") && step.get("error").isJsonObject()) {
					JsonObject error = step.getAsJsonObject("error");
					throw new GeminiException(GeminiException.Kind.BAD_RESPONSE, "Gemini error: " + stringOrNull(error, "message"));
				}
				if (step.has("content")) {
					return step;
				}
			}
		}
		throw new GeminiException(GeminiException.Kind.BAD_RESPONSE, "No model output (status " + status + ")");
	}

	private static String stringOrNull(JsonObject object, String field) {
		JsonElement value = object.get(field);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}

	/** Wraps little-endian 16-bit PCM in a 44-byte RIFF/WAVE header. */
	public static byte[] wavFromPcm(byte[] pcm, int sampleRate, int channels) {
		int byteRate = sampleRate * channels * 2;
		ByteBuffer wav = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
		wav.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + pcm.length).put("WAVE".getBytes(StandardCharsets.US_ASCII));
		wav.put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1).putShort((short) channels)
				.putInt(sampleRate).putInt(byteRate).putShort((short) (channels * 2)).putShort((short) 16);
		wav.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(pcm.length).put(pcm);
		return wav.array();
	}
}
