package com.mehbur.mehburity.ai;

import com.mehbur.mehburity.entity.CompanionRegistry;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Short memory of what happened to each player recently ("hit by Zombie", "died"...). It is sent to Gemini as
 * context, and the important events make Mehburity comment on its own. Later phases record more events here.
 */
public final class GameEvents {
	private static final int MAX_EVENTS = 8;
	/** Damage at or above this, or ending below {@link #LOW_HEALTH}, is worth a comment. */
	private static final float BIG_HIT = 4.0F;
	private static final float LOW_HEALTH = 6.0F;

	private record Event(long gameTime, String text) {
	}

	private static final Map<UUID, Deque<Event>> EVENTS = new HashMap<>();

	private GameEvents() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && damageTaken > 0.0F && hasCompanion(player)) {
				String event = "the player was hurt by " + describe(source) + " (-" + Math.round(damageTaken) + " HP, now "
						+ Math.round(player.getHealth()) + "/" + Math.round(player.getMaxHealth()) + ")";
				record(player, event);
				if (source.getEntity() != null && (damageTaken >= BIG_HIT || player.getHealth() < LOW_HEALTH)) {
					AiRouter.react(player, event);
				}
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player && hasCompanion(player)) {
				String event = "the player died (" + describe(source) + ")";
				record(player, event);
				AiRouter.react(player, event);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> EVENTS.remove(handler.player.getUUID()));
	}

	public static void record(ServerPlayer player, String text) {
		Deque<Event> events = EVENTS.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
		events.addLast(new Event(player.level().getGameTime(), text));
		while (events.size() > MAX_EVENTS) {
			events.removeFirst();
		}
	}

	/** "- 12 s ago: ..." lines, oldest first; empty when nothing happened. */
	public static String recent(ServerPlayer player) {
		Deque<Event> events = EVENTS.get(player.getUUID());
		if (events == null || events.isEmpty()) {
			return "";
		}
		long now = player.level().getGameTime();
		StringBuilder out = new StringBuilder();
		for (Event event : events) {
			out.append("- ").append((now - event.gameTime()) / 20).append(" s ago: ").append(event.text()).append('\n');
		}
		return out.toString();
	}

	private static boolean hasCompanion(ServerPlayer player) {
		return CompanionRegistry.companion(player.level().getServer(), player.getUUID()) != null;
	}

	private static String describe(DamageSource source) {
		Entity attacker = source.getEntity();
		if (attacker != null) {
			return attacker.getType().getDescription().getString();
		}
		return source.getMsgId();
	}
}
