package com.mehbur.mehburity.client.ai;

import com.mehbur.mehburity.MehburityMod;
import com.mehbur.mehburity.ai.BuiltinReplies;
import com.mehbur.mehburity.ai.ChatTurn;
import com.mehbur.mehburity.ai.GeminiClient;
import com.mehbur.mehburity.ai.GeminiException;
import com.mehbur.mehburity.ai.MehburityPersona;
import com.mehbur.mehburity.client.config.MehburityClientConfig;
import com.mehbur.mehburity.network.AiReplyPayload;
import com.mehbur.mehburity.network.AiRequestPayload;
import com.mehbur.mehburity.network.SpeakPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Client side of talking to Mehburity: answers the server's {@link AiRequestPayload}s with Gemini (using this
 * player's key, which never leaves the client), falls back to built-in replies, speaks the answers, and tells
 * the player when Gemini is not connected.
 */
public final class ClientAi {
	private static final Deque<ChatTurn> HISTORY = new ArrayDeque<>();
	private static @Nullable GeminiClient gemini;
	private static String geminiFor = "";
	/** Last connection problem shown in chat, so the same warning is not repeated every message. */
	private static GeminiException.@Nullable Kind lastProblem;

	private ClientAi() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(AiRequestPayload.TYPE, (payload, context) -> handle(payload));
		ClientPlayNetworking.registerGlobalReceiver(SpeakPayload.TYPE, (payload, context) -> VoicePlayer.speak(payload.text().getString(),
				new VoicePlayer.Speaker(payload.speakerId(), payload.from().orElse(null))));
		ClientTickEvents.END_CLIENT_TICK.register(VoicePlayer::tick);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			HISTORY.clear();
			lastProblem = null;
			announceConnection();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> HISTORY.clear());
	}

	/** The Gemini client for the current base URL; rebuilt when the settings change it. */
	public static synchronized GeminiClient gemini() {
		MehburityClientConfig config = MehburityClientConfig.get();
		if (gemini == null || !geminiFor.equals(config.apiBaseUrl)) {
			gemini = new GeminiClient(config.baseUri(), () -> MehburityClientConfig.get().geminiApiKey);
			geminiFor = config.apiBaseUrl;
		}
		return gemini;
	}

	private static void handle(AiRequestPayload request) {
		MehburityClientConfig config = MehburityClientConfig.get();
		String language = Minecraft.getInstance().options.languageCode;
		if (config.geminiApiKey.isBlank()) {
			reply(request, builtin(request, language), false);
			return;
		}

		String system = MehburityPersona.systemInstruction(request.context());
		String prompt = request.event() ? MehburityPersona.eventPrompt(request.message()) : request.message();
		List<ChatTurn> history = request.event() ? List.of() : List.copyOf(HISTORY);
		gemini().chat(config.chatModel, system, history, prompt, MehburityPersona.MAX_OUTPUT_TOKENS)
				.whenComplete((text, error) -> Minecraft.getInstance().execute(() -> {
					if (error != null) {
						GeminiException problem = GeminiException.of(error);
						MehburityMod.LOGGER.warn("Gemini request failed: {}", problem.getMessage());
						warn(problem.kind());
						reply(request, builtin(request, language), false);
						return;
					}
					lastProblem = null;
					if (!request.event()) {
						remember(new ChatTurn(true, request.message()), config.historyMessages);
						remember(new ChatTurn(false, text), config.historyMessages);
					}
					reply(request, text, true);
				}));
	}

	private static String builtin(AiRequestPayload request, String language) {
		return request.event() ? BuiltinReplies.reactTo(request.message(), language) : BuiltinReplies.reply(request.message(), language);
	}

	private static void reply(AiRequestPayload request, String text, boolean fromGemini) {
		ClientPlayNetworking.send(new AiReplyPayload(request.id(), text, fromGemini));
		VoicePlayer.speak(text);
	}

	private static void remember(ChatTurn turn, int max) {
		HISTORY.addLast(turn);
		while (HISTORY.size() > max) {
			HISTORY.removeFirst();
		}
	}

	// ---------------------------------------------------------------- status messages

	/** On join: says whether Gemini is connected, so the player knows what to expect. */
	private static void announceConnection() {
		if (MehburityClientConfig.get().geminiApiKey.isBlank()) {
			warn(GeminiException.Kind.NO_KEY);
			return;
		}
		CompletableFuture<Void> check = gemini().checkKey();
		check.whenComplete((ok, error) -> Minecraft.getInstance().execute(() -> {
			if (error == null) {
				lastProblem = null;
				status(Component.translatableWithFallback("mehburity.ai.connected", "Gemini connected ✔").withStyle(ChatFormatting.GREEN));
			} else {
				warn(GeminiException.of(error).kind());
			}
		}));
	}

	private static void warn(GeminiException.Kind kind) {
		if (kind == lastProblem) {
			return;
		}
		lastProblem = kind;
		status(problemText(kind).withStyle(ChatFormatting.YELLOW));
	}

	public static MutableComponent problemText(GeminiException.Kind kind) {
		String key = "mehburity.ai.problem." + kind.name().toLowerCase(Locale.ROOT);
		String fallback = switch (kind) {
			case NO_KEY -> "Gemini is not connected (no API key). I'll use simple built-in replies. Add a key in Mods → Mehburity → Configure.";
			case INVALID_KEY -> "Gemini rejected the API key. Check it in Mods → Mehburity → Configure.";
			case QUOTA -> "Gemini quota or rate limit reached. I'll use built-in replies for now.";
			case MODEL_NOT_FOUND -> "The selected Gemini model is not available. Pick another one in the settings.";
			case REGION -> "Gemini is not available in your region.";
			case NETWORK -> "Can't reach Gemini (no internet?). I'll use built-in replies.";
			case SERVER -> "Gemini is having problems right now. I'll use built-in replies.";
			case BAD_REQUEST, BAD_RESPONSE -> "Gemini couldn't answer that. I'll use built-in replies.";
		};
		return Component.translatableWithFallback(key, fallback);
	}

	private static void status(Component message) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null) {
			minecraft.player.sendSystemMessage(Component.empty()
					.append(Component.literal("[Mehburity] ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
					.append(message));
		}
	}
}
