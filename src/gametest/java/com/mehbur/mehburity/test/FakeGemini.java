package com.mehbur.mehburity.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mehbur.mehburity.ai.GeminiClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A local stand-in for the Gemini API, so tests never touch the network or need a real key. Each request is
 * recorded; answers are scripted per test (status + body), defaulting to a successful text or audio interaction.
 */
public final class FakeGemini implements AutoCloseable {
	public record Request(String method, String path, String apiKey, JsonObject body) {
		public boolean isSpeech() {
			return body != null && body.has("response_format");
		}
	}

	private record Scripted(int status, String body) {
	}

	private final HttpServer server;
	private final List<Request> requests = new CopyOnWriteArrayList<>();
	private final Deque<Scripted> script = new ArrayDeque<>();
	private volatile String chatAnswer = "Neon cyan, of course!";

	public FakeGemini() {
		try {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		server.createContext("/", this::handle);
		server.start();
	}

	public URI baseUri() {
		return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
	}

	public List<Request> requests() {
		return requests;
	}

	public void chatAnswer(String answer) {
		chatAnswer = answer;
	}

	/** The next request gets exactly this answer (then defaults resume). */
	public synchronized void enqueue(int status, String body) {
		script.addLast(new Scripted(status, body));
	}

	private void handle(HttpExchange exchange) throws IOException {
		String raw = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		JsonObject body = raw.isBlank() ? null : JsonParser.parseString(raw).getAsJsonObject();
		Request request = new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
				exchange.getRequestHeaders().getFirst("x-goog-api-key"), body);
		requests.add(request);

		Scripted scripted;
		synchronized (this) {
			scripted = script.pollFirst();
		}
		if (scripted == null) {
			scripted = new Scripted(200, request.path().startsWith("/v1beta/models")
					? "{\"models\": [{\"name\": \"models/gemini-3.5-flash-lite\"}]}"
					: request.isSpeech() ? audioInteraction() : textInteraction(chatAnswer));
		}
		byte[] bytes = scripted.body().getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(scripted.status(), bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}

	public static String textInteraction(String text) {
		JsonObject block = new JsonObject();
		block.addProperty("type", "text");
		block.addProperty("text", text);
		return interaction(block);
	}

	/** 0.2 s of silence as raw 24 kHz PCM, the way Gemini returns {@code audio/l16}. */
	public static String audioInteraction() {
		JsonObject block = new JsonObject();
		block.addProperty("type", "audio");
		block.addProperty("mime_type", "audio/l16");
		block.addProperty("data", Base64.getEncoder().encodeToString(new byte[24000 * 2 / 5]));
		return interaction(block);
	}

	private static String interaction(JsonObject block) {
		return "{\"id\": \"v1_test\", \"status\": \"completed\", \"steps\": [{\"type\": \"model_output\", \"content\": ["
				+ block + "]}]}";
	}

	public GeminiClient client(String key) {
		return new GeminiClient(baseUri(), () -> key);
	}

	@Override
	public void close() {
		server.stop(0);
	}
}
