package com.mehbur.mehburity.ai;

import com.mehbur.mehburity.MehburityMod;
import com.mehbur.mehburity.config.MehburityConfig;
import com.mehbur.mehburity.entity.CompanionRegistry;
import com.mehbur.mehburity.entity.MehburitySpeech;
import com.mehbur.mehburity.network.AiReplyPayload;
import com.mehbur.mehburity.network.AiRequestPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of talking to Mehburity. A chat message addressed to Mehburity (or an important event) becomes an
 * {@link AiRequestPayload} for the owner's client, which holds the Gemini key. The answer comes back as an
 * {@link AiReplyPayload} and is shown to everyone nearby. If the client cannot answer (no mod, timeout), the server
 * replies with a built-in line, so Mehburity always says something. Runs on the server thread only.
 */
public final class AiRouter {
	private record Pending(UUID owner, long createdAt, boolean event, String message) {
	}

	private static final Map<Integer, Pending> PENDING = new HashMap<>();
	private static final Map<UUID, Long> LAST_QUESTION = new HashMap<>();
	private static final Map<UUID, Long> LAST_EVENT_COMMENT = new HashMap<>();
	private static final List<Runnable> ORDERS = new ArrayList<>();
	private static int nextId;

	private AiRouter() {
	}

	public static void register() {
		ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> onPlayerChat(sender, message.signedContent()));
		ServerPlayNetworking.registerGlobalReceiver(AiReplyPayload.TYPE, (payload, context) -> onReply(context.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(AiRouter::expireStale);
		ServerTickEvents.END_SERVER_TICK.register(server -> runOrders());
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUUID();
			PENDING.values().removeIf(pending -> pending.owner().equals(id));
			LAST_QUESTION.remove(id);
			LAST_EVENT_COMMENT.remove(id);
		});
	}

	// ---------------------------------------------------------------- incoming

	/** Handles a chat line; returns true when it was a question for Mehburity. */
	public static boolean onPlayerChat(ServerPlayer player, String text) {
		MehburityConfig.Ai config = MehburityConfig.get().ai;
		if (!config.enabled) {
			return false;
		}
		String question = ChatAddress.extract(text, config.chatPrefixes);
		if (question == null || CompanionRegistry.companion(player.level().getServer(), player.getUUID()) == null) {
			return false;
		}
		if (question.length() > config.maxMessageLength) {
			question = question.substring(0, config.maxMessageLength);
		}
		Runnable order = ChatCommandCallback.EVENT.invoker().handle(player, question);
		if (order != null) {
			// An order ("maden kaz"), carried out without asking Gemini once the owner's own line is in the chat.
			later(order);
			return true;
		}

		long now = System.currentTimeMillis();
		Long last = LAST_QUESTION.get(player.getUUID());
		if (last != null && now - last < config.cooldownSeconds * 1000) {
			return true; // spam: ignore silently
		}
		if (hasPending(player.getUUID(), false)) {
			MehburitySpeech.say(player, MehburitySpeech.text("still_thinking", "Hold on, I'm still thinking about your last question!"));
			return true;
		}
		LAST_QUESTION.put(player.getUUID(), now);
		send(player, false, question);
		return true;
	}

	/** Lets Mehburity comment on an event (rate-limited). */
	public static void react(ServerPlayer player, String event) {
		MehburityConfig.Ai config = MehburityConfig.get().ai;
		if (!config.enabled || !config.commentOnEvents || hasPending(player.getUUID(), true)) {
			return;
		}
		long now = System.currentTimeMillis();
		Long last = LAST_EVENT_COMMENT.get(player.getUUID());
		if (last != null && now - last < config.eventCommentCooldownSeconds * 1000L) {
			return;
		}
		LAST_EVENT_COMMENT.put(player.getUUID(), now);
		send(player, true, event);
	}

	private static void send(ServerPlayer player, boolean event, String message) {
		if (!ServerPlayNetworking.canSend(player, AiRequestPayload.TYPE)) {
			// Client without the mod (should not happen) or a test player: answer right here.
			answerWithBuiltin(player, event, message);
			return;
		}
		int id = ++nextId;
		PENDING.put(id, new Pending(player.getUUID(), System.currentTimeMillis(), event, message));
		ServerPlayNetworking.send(player, new AiRequestPayload(id, event, message, GameContext.describe(player)));
		if (!event) {
			player.sendSystemMessage(MehburitySpeech.text("thinking", "Mehburity is thinking...").withStyle(ChatFormatting.AQUA), true);
		}
	}

	private static void onReply(ServerPlayer player, AiReplyPayload payload) {
		Pending pending = PENDING.get(payload.id());
		if (pending == null || !pending.owner().equals(player.getUUID())) {
			return; // unknown, expired, or someone answering for another player
		}
		PENDING.remove(payload.id());
		String text = sanitize(payload.text(), MehburityConfig.get().ai.maxReplyLength);
		if (text.isEmpty()) {
			answerWithBuiltin(player, pending.event(), pending.message());
			return;
		}
		deliver(player, text, payload.fromGemini(), false);
	}

	/** Runs {@code action} at the end of this tick, after the chat line that caused it has been shown. */
	public static void later(Runnable action) {
		ORDERS.add(action);
	}

	/** Runs the chat orders received this tick. Public so tests can flush them without waiting a tick. */
	public static void runOrders() {
		if (ORDERS.isEmpty()) {
			return;
		}
		List<Runnable> orders = List.copyOf(ORDERS);
		ORDERS.clear();
		orders.forEach(Runnable::run);
	}

	private static void expireStale(MinecraftServer server) {
		if (PENDING.isEmpty() || server.getTickCount() % 20 != 0) {
			return;
		}
		long cutoff = System.currentTimeMillis() - MehburityConfig.get().ai.requestTimeoutSeconds * 1000L;
		Iterator<Pending> it = PENDING.values().iterator();
		while (it.hasNext()) {
			Pending pending = it.next();
			if (pending.createdAt() < cutoff) {
				it.remove();
				ServerPlayer player = server.getPlayerList().getPlayer(pending.owner());
				if (player != null) {
					MehburityMod.LOGGER.debug("AI request for {} timed out", player.getGameProfile().name());
					answerWithBuiltin(player, pending.event(), pending.message());
				}
			}
		}
	}

	private static boolean hasPending(UUID owner, boolean event) {
		for (Pending pending : PENDING.values()) {
			if (pending.owner().equals(owner) && pending.event() == event) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- outgoing

	private static void answerWithBuiltin(ServerPlayer player, boolean event, String message) {
		String language = player.clientInformation().language();
		String text = event ? BuiltinReplies.reactTo(message, language) : BuiltinReplies.reply(message, language);
		// The client did not answer, so it will not speak this itself: ask it to.
		deliver(player, text, false, true);
	}

	/** Shows the answer to the owner and everyone within hearing range. */
	private static void deliver(ServerPlayer owner, String text, boolean fromGemini, boolean speakForOwner) {
		Component line = MehburitySpeech.line(Component.literal(text));
		double radius = MehburityConfig.get().ai.hearingRadius;
		for (ServerPlayer listener : owner.level().players()) {
			if (listener == owner || listener.distanceToSqr(owner) <= radius * radius) {
				listener.sendSystemMessage(line);
			}
		}
		MehburitySpeech.talk(owner, text);
		if (speakForOwner) {
			MehburitySpeech.sendVoice(owner, Component.literal(text));
		}
		GameEvents.record(owner, "Mehburity said: " + text);
		MehburityReplyCallback.EVENT.invoker().onReply(owner, text, fromGemini);
	}

	/** One line, no formatting codes or control characters, at most {@code max} characters. */
	public static String sanitize(String text, int max) {
		String unformatted = ChatFormatting.stripFormatting(text.replace('\n', ' ').replace('\r', ' '));
		String clean = StringUtil.filterText(unformatted == null ? "" : unformatted).replace("§", "").strip();
		clean = clean.replaceAll("\\s{2,}", " ");
		if (clean.length() > max) {
			int cut = clean.lastIndexOf(' ', max - 1);
			clean = clean.substring(0, cut > max / 2 ? cut : max - 1) + "…";
		}
		return clean;
	}
}
