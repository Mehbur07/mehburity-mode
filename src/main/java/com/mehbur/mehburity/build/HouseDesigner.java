package com.mehbur.mehburity.build;

import com.mehbur.mehburity.entity.MehburitySpeech;
import com.mehbur.mehburity.network.HouseDesignReplyPayload;
import com.mehbur.mehburity.network.HouseDesignRequestPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Turns a described house into a {@link HouseSpec}: the keyword reading right away, then (when the owner's client has
 * a Gemini key) Gemini's design on top of it. Without an answer in {@link #TIMEOUT_MILLIS} the keyword reading is
 * built. Runs on the server thread only.
 */
public final class HouseDesigner {
	static final long TIMEOUT_MILLIS = 25_000L;

	private record Pending(UUID owner, String description, HouseSpec.Draft draft, long createdAt) {
	}

	private static final Map<Integer, Pending> PENDING = new HashMap<>();
	private static int nextId;

	private HouseDesigner() {
	}

	static void register() {
		ServerPlayNetworking.registerGlobalReceiver(HouseDesignReplyPayload.TYPE, (payload, context) -> onReply(context.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(HouseDesigner::expire);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				PENDING.values().removeIf(pending -> pending.owner().equals(handler.player.getUUID())));
	}

	/** Designs and builds the house {@code description} asks for, starting from {@code base}'s materials. */
	static boolean design(ServerPlayer owner, String description, HouseStyle.Palette base) {
		HouseSpec.Draft draft = HouseDescriptions.read(description, base);
		if (!ServerPlayNetworking.canSend(owner, HouseDesignRequestPayload.TYPE)) {
			return Building.begin(owner, draft.build(), description); // no client to ask (or a test player)
		}
		if (isDesigning(owner.getUUID())) {
			MehburitySpeech.say(owner, MehburitySpeech.text("build.still_designing", "Hold on, I'm still planning your house!"));
			return true;
		}
		int id = ++nextId;
		PENDING.put(id, new Pending(owner.getUUID(), description, draft, System.currentTimeMillis()));
		ServerPlayNetworking.send(owner, new HouseDesignRequestPayload(id, description));
		MehburitySpeech.say(owner, MehburitySpeech.text("build.designing", "Hmm, let me think about how to build that..."));
		return true;
	}

	private static boolean isDesigning(UUID owner) {
		return PENDING.values().stream().anyMatch(pending -> pending.owner().equals(owner));
	}

	private static void onReply(ServerPlayer player, HouseDesignReplyPayload payload) {
		Pending pending = PENDING.get(payload.id());
		if (pending == null || !pending.owner().equals(player.getUUID())) {
			return;
		}
		PENDING.remove(payload.id());
		HouseDesignJson.apply(payload.design(), pending.draft()); // nothing usable: the keyword reading stays
		Building.begin(player, pending.draft().build(), pending.description());
	}

	private static void expire(MinecraftServer server) {
		if (PENDING.isEmpty() || server.getTickCount() % 20 != 0) {
			return;
		}
		long cutoff = System.currentTimeMillis() - TIMEOUT_MILLIS;
		Iterator<Pending> it = PENDING.values().iterator();
		while (it.hasNext()) {
			Pending pending = it.next();
			if (pending.createdAt() < cutoff) {
				it.remove();
				ServerPlayer owner = server.getPlayerList().getPlayer(pending.owner());
				if (owner != null) {
					Building.begin(owner, pending.draft().build(), pending.description());
				}
			}
		}
	}
}
