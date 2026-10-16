package com.mehbur.mehburity.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mehbur.mehburity.ai.AiRouter;
import com.mehbur.mehburity.ai.BuiltinReplies;
import com.mehbur.mehburity.ai.ChatAddress;
import com.mehbur.mehburity.ai.ChatTurn;
import com.mehbur.mehburity.ai.GeminiClient;
import com.mehbur.mehburity.ai.GeminiException;
import com.mehbur.mehburity.ai.MehburityReplyCallback;
import com.mehbur.mehburity.entity.CompanionMode;
import com.mehbur.mehburity.entity.CompanionRegistry;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/** Talking to Mehburity: chat parsing, built-in replies, the Gemini client (against a fake server) and routing. */
public final class MehburityAiGameTests {
	private static final List<String> PREFIXES = List.of("mehburity", "mehbur");
	/** Replies delivered by AiRouter, per owner, captured through the public callback. */
	static final Map<UUID, List<String>> REPLIES = new ConcurrentHashMap<>();

	static {
		MehburityReplyCallback.EVENT.register((owner, text, fromGemini) ->
				REPLIES.computeIfAbsent(owner.getUUID(), id -> new CopyOnWriteArrayList<>()).add(text));
	}

	private static <T> T await(java.util.concurrent.CompletableFuture<T> future) {
		try {
			return future.get(10, TimeUnit.SECONDS);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static GeminiException failure(java.util.concurrent.CompletableFuture<?> future) {
		try {
			future.get(10, TimeUnit.SECONDS);
		} catch (Exception e) {
			return GeminiException.of(e);
		}
		throw new AssertionError("expected the call to fail");
	}

	// ---------------------------------------------------------------- chat parsing

	@GameTest
	public void chat_address_finds_questions_for_mehburity(GameTestHelper helper) {
		helper.assertValueEqual("nasılsın?", ChatAddress.extract("Mehburity, nasılsın?", PREFIXES), "comma");
		helper.assertValueEqual("selam", ChatAddress.extract("MEHBURİTY selam", PREFIXES), "Turkish capital İ");
		helper.assertValueEqual("naber", ChatAddress.extract("@mehburity: naber", PREFIXES), "@ and colon");
		helper.assertValueEqual("what time is it", ChatAddress.extract("mehbur what time is it", PREFIXES), "short prefix");
		helper.assertValueEqual("", ChatAddress.extract("Mehburity!", PREFIXES), "just the name");
		helper.assertTrue(ChatAddress.extract("mehburityyy hi", PREFIXES) == null, "longer word is not the name");
		helper.assertTrue(ChatAddress.extract("hey mehburity", PREFIXES) == null, "name must come first");
		helper.assertTrue(ChatAddress.extract("hello world", PREFIXES) == null, "unrelated chat");
		helper.succeed();
	}

	@GameTest
	public void builtin_replies_follow_the_game_language(GameTestHelper helper) {
		helper.assertValueEqual("Merhaba! Buradayım.", BuiltinReplies.reply("selam", "tr_tr"), "Turkish greeting");
		helper.assertValueEqual("Hi! I'm right here.", BuiltinReplies.reply("hello there", "en_us"), "English greeting");
		helper.assertTrue(BuiltinReplies.reply("Nasılsın?", "tr_tr").startsWith("Harikayım"), "accents are ignored");
		helper.assertTrue(BuiltinReplies.reply("explain quantum physics", "en_us").contains("API key"), "unknown → hint about the key");
		helper.assertTrue(BuiltinReplies.reactTo("the player died (zombie)", "tr_tr").startsWith("Hayır"), "death reaction");
		helper.succeed();
	}

	@GameTest
	public void replies_are_cleaned_and_shortened(GameTestHelper helper) {
		helper.assertValueEqual("Hi there!", AiRouter.sanitize("  Hi\n\nthere!  ", 100), "newlines collapse");
		helper.assertValueEqual("red text", AiRouter.sanitize("§cred text", 100), "formatting codes removed");
		String shortened = AiRouter.sanitize("one two three four five six", 12);
		helper.assertTrue(shortened.length() <= 12 && shortened.endsWith("…"), "long replies are cut: " + shortened);
		helper.succeed();
	}

	// ---------------------------------------------------------------- Gemini client

	@GameTest
	public void gemini_chat_sends_interactions_request(GameTestHelper helper) throws Exception {
		try (FakeGemini fake = new FakeGemini()) {
			fake.chatAnswer("I know everything!");
			String answer = await(fake.client("test-key").chat("gemini-3.5-flash-lite", "You are Mehburity.",
					List.of(new ChatTurn(true, "hi"), new ChatTurn(false, "Hello!")), "who are you?", 300));
			helper.assertValueEqual("I know everything!", answer, "answer text");

			FakeGemini.Request request = fake.requests().getFirst();
			helper.assertValueEqual("/v1beta/interactions", request.path(), "endpoint");
			helper.assertValueEqual("test-key", request.apiKey(), "key goes in the x-goog-api-key header");
			JsonObject body = request.body();
			helper.assertValueEqual("gemini-3.5-flash-lite", body.get("model").getAsString(), "model");
			helper.assertValueEqual("You are Mehburity.", body.get("system_instruction").getAsString(), "system instruction");
			helper.assertFalse(body.get("store").getAsBoolean(), "nothing is stored on Google's side");
			JsonArray input = body.getAsJsonArray("input");
			helper.assertValueEqual(3, input.size(), "history + new message");
			helper.assertValueEqual("user_input", input.get(0).getAsJsonObject().get("type").getAsString(), "player turn");
			helper.assertValueEqual("model_output", input.get(1).getAsJsonObject().get("type").getAsString(), "Mehburity turn");
		}
		helper.succeed();
	}

	@GameTest
	public void gemini_errors_are_classified(GameTestHelper helper) throws Exception {
		try (FakeGemini fake = new FakeGemini()) {
			fake.enqueue(400, "{\"error\": {\"code\": 400, \"message\": \"API key not valid. Please pass a valid API key.\", \"status\": \"INVALID_ARGUMENT\"}}");
			helper.assertValueEqual(GeminiException.Kind.INVALID_KEY,
					failure(fake.client("bad").chat("m", "s", List.of(), "hi", 50)).kind(), "bad key");

			fake.enqueue(404, "{\"error\": {\"code\": 404, \"message\": \"models/x is not found\"}}");
			helper.assertValueEqual(GeminiException.Kind.MODEL_NOT_FOUND,
					failure(fake.client("k").chat("x", "s", List.of(), "hi", 50)).kind(), "unknown model");

			helper.assertValueEqual(GeminiException.Kind.NO_KEY,
					failure(fake.client("").chat("m", "s", List.of(), "hi", 50)).kind(), "no key");
		}
		helper.succeed();
	}

	@GameTest
	public void gemini_retries_temporary_failures(GameTestHelper helper) throws Exception {
		try (FakeGemini fake = new FakeGemini()) {
			fake.enqueue(503, "{\"error\": {\"code\": 503, \"message\": \"overloaded\"}}");
			fake.chatAnswer("Back online!");
			helper.assertValueEqual("Back online!", await(fake.client("k").chat("m", "s", List.of(), "hi", 50)), "answer after retry");
			helper.assertValueEqual(2, fake.requests().size(), "one retry");

			for (int i = 0; i <= GeminiClient.MAX_RETRIES; i++) {
				fake.enqueue(429, "{\"error\": {\"code\": 429, \"message\": \"quota\"}}");
			}
			helper.assertValueEqual(GeminiException.Kind.QUOTA,
					failure(fake.client("k").chat("m", "s", List.of(), "hi", 50)).kind(), "gives up after retries");
		}
		helper.succeed();
	}

	@GameTest
	public void gemini_thinking_level_falls_back(GameTestHelper helper) throws Exception {
		try (FakeGemini fake = new FakeGemini()) {
			fake.enqueue(400, "{\"error\": {\"code\": 400, \"message\": \"thinking_level is not supported for this model\"}}");
			helper.assertValueEqual("Neon cyan, of course!", await(fake.client("k").chat("m", "s", List.of(), "hi", 50)), "answer");
			helper.assertTrue(fake.requests().get(0).body().getAsJsonObject("generation_config").has("thinking_level"), "first try asks for minimal thinking");
			helper.assertFalse(fake.requests().get(1).body().getAsJsonObject("generation_config").has("thinking_level"), "second try drops it");
		}
		helper.succeed();
	}

	@GameTest
	public void gemini_speech_returns_playable_wav(GameTestHelper helper) throws Exception {
		try (FakeGemini fake = new FakeGemini()) {
			byte[] wav = await(fake.client("k").speak("gemini-3.8-flash-tts", "Puck", "energetic", "Hello! I'm Mehburity."));
			try (AudioInputStream audio = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav))) {
				helper.assertValueEqual(24000.0F, audio.getFormat().getSampleRate(), "24 kHz");
				helper.assertValueEqual(1, audio.getFormat().getChannels(), "mono");
				helper.assertValueEqual(16, audio.getFormat().getSampleSizeInBits(), "16-bit");
			}
			JsonObject body = fake.requests().getFirst().body();
			helper.assertValueEqual("audio", body.getAsJsonObject("response_format").get("type").getAsString(), "audio output");
			helper.assertValueEqual("Puck", body.getAsJsonObject("generation_config").getAsJsonArray("speech_config")
					.get(0).getAsJsonObject().get("voice").getAsString(), "voice");
			JsonObject text = body.getAsJsonArray("input").get(0).getAsJsonObject().getAsJsonArray("content").get(0).getAsJsonObject();
			helper.assertValueEqual("energetic", text.getAsJsonArray("annotations").get(0).getAsJsonObject().get("style").getAsString(), "style");
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- routing

	@GameTest
	public void question_is_answered_even_without_gemini(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		CompanionRegistry.putCompanion(helper.getLevel().getServer(), player.getUUID(),
				new CompanionRegistry.Companion(UUID.randomUUID(), CompanionMode.STAY, false));

		helper.assertFalse(AiRouter.onPlayerChat(player, "hello everyone"), "normal chat is not for Mehburity");
		helper.assertTrue(AiRouter.onPlayerChat(player, "Mehburity, hello!"), "addressed chat is handled");
		helper.assertValueEqual(List.of("Hi! I'm right here."), REPLIES.get(player.getUUID()), "built-in reply delivered");
		helper.succeed();
	}

	@GameTest
	public void players_without_mehburity_are_ignored(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertFalse(AiRouter.onPlayerChat(player, "Mehburity, hello!"), "no Mehburity, no answer");
		helper.assertTrue(REPLIES.get(player.getUUID()) == null, "nothing delivered");
		helper.succeed();
	}
}
