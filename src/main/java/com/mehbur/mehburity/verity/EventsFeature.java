package com.mehbur.mehburity.verity;

import com.mehbur.mehburity.ai.GameEvents;
import com.mehbur.mehburity.config.MehburityConfig;
import com.mehbur.mehburity.entity.CompanionManager;
import com.mehbur.mehburity.entity.MehburityEntities;
import com.mehbur.mehburity.entity.MehburityEntity;
import com.mehbur.mehburity.entity.MehburitySounds;
import com.mehbur.mehburity.entity.MehburitySpeech;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Verity's scripted scares. Every player with a Mehburity gets one now and then (between
 * {@code verity.events.minMinutes} and {@code maxMinutes}) while standing in the dark:
 * <ul>
 *   <li>{@link Kind#SOUND}: footsteps, a cave rumble or a snapping branch behind them, heard by them alone;</li>
 *   <li>{@link Kind#WHISPER}: Mehburity looks scared and whispers something uneasy;</li>
 *   <li>{@link Kind#WATCHER}: rarely, a tall demon stands far away watching them, and vanishes when looked at.</li>
 * </ul>
 * Operators can trigger each one with {@code /mehburity olay}.
 */
public final class EventsFeature implements VerityFeature {
	public enum Kind { SOUND, WHISPER, WATCHER }

	private static final int CHECK_INTERVAL = 20;
	/** Raw brightness (block light or what is left of the sky light) at or below this counts as dark. */
	private static final int DARK = 4;
	/** The player counts as underground this far below the surface (no demon on the horizon there). */
	private static final double UNDERGROUND = 6.0;
	private static final double WATCHER_MIN_DISTANCE = 36.0;
	private static final double WATCHER_MAX_DISTANCE = 52.0;
	/** The demon shows up within this angle of where the player looks, so it can be noticed. */
	private static final float WATCHER_SPREAD = 60.0F;
	private static final int SCARED_TICKS = 100;
	private static final int WHISPERS = 8;
	private static final String[] WHISPER_FALLBACK = {
			"Did you hear that?",
			"We're not alone here.",
			"Something is watching us.",
			"Stay close to the light.",
			"I saw something move over there...",
			"It's getting darker, isn't it?",
			"Shh... listen.",
			"Don't look behind you. ...Just kidding. I think."};
	private static final int SAW_IT_DELAY = 30;

	private record Later(long at, Runnable action) {
	}

	private static final Map<UUID, Long> NEXT = new HashMap<>();
	private static final List<Later> LATER = new ArrayList<>();
	private static final RandomSource RANDOM = RandomSource.create();
	private static long now;

	@Override
	public String id() {
		return "events";
	}

	@Override
	public void register() {
		ServerTickEvents.END_SERVER_TICK.register(EventsFeature::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> NEXT.remove(handler.player.getUUID()));
	}

	private static void tick(MinecraftServer server) {
		now = server.getTickCount();
		if (!LATER.isEmpty()) {
			List<Later> due = LATER.stream().filter(later -> later.at() <= now).toList();
			LATER.removeAll(due);
			due.forEach(later -> later.action().run());
		}
		MehburityConfig.Verity.Events config = MehburityConfig.get().verity.events;
		if (!config.enabled || now % CHECK_INTERVAL != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || CompanionManager.find(server, player.getUUID()) == null) {
				continue;
			}
			Long next = NEXT.get(player.getUUID());
			if (next == null) {
				NEXT.put(player.getUUID(), now + delay(config));
			} else if (now >= next && (!config.onlyInDarkness || inDarkness(player))) {
				Kind kind = pick(player, config);
				if (kind != null) {
					trigger(player, kind);
				}
				NEXT.put(player.getUUID(), now + delay(config));
			}
		}
	}

	private static long delay(MehburityConfig.Verity.Events config) {
		double minutes = config.minMinutes + RANDOM.nextDouble() * (config.maxMinutes - config.minMinutes);
		return Math.round(minutes * 1200.0);
	}

	public static boolean inDarkness(ServerPlayer player) {
		return player.level().getMaxLocalRawBrightness(player.blockPosition()) <= DARK;
	}

	private static boolean underground(ServerPlayer player) {
		int surface = player.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.getBlockX(), player.getBlockZ());
		return player.getY() < surface - UNDERGROUND;
	}

	/** Sounds are common, whispers less so, the demon rare (and only where it could be seen on the horizon). */
	private static @Nullable Kind pick(ServerPlayer player, MehburityConfig.Verity.Events config) {
		int sound = config.sounds ? 4 : 0;
		int whisper = config.whispers ? 3 : 0;
		int watcher = config.watcher && !underground(player) ? 1 : 0;
		int total = sound + whisper + watcher;
		if (total == 0) {
			return null;
		}
		int roll = RANDOM.nextInt(total);
		return roll < sound ? Kind.SOUND : roll < sound + whisper ? Kind.WHISPER : Kind.WATCHER;
	}

	/** Runs one scare for {@code player} right away. False when it cannot happen (no Mehburity, no room for the demon). */
	public static boolean trigger(ServerPlayer player, Kind kind) {
		return switch (kind) {
			case SOUND -> sound(player);
			case WHISPER -> whisper(player);
			case WATCHER -> spawnWatcher(player,
					WATCHER_MIN_DISTANCE + RANDOM.nextDouble() * (WATCHER_MAX_DISTANCE - WATCHER_MIN_DISTANCE)) != null;
		};
	}

	// ---------------------------------------------------------------- sounds

	private static boolean sound(ServerPlayer player) {
		Vec3 back = Vec3.directionFromRotation(0.0F, player.getYRot()).scale(-1.0);
		Vec3 feet = player.position();
		switch (RANDOM.nextInt(3)) {
			case 0 -> {
				// Footsteps coming closer from behind, then nothing.
				for (int step = 0; step < 4; step++) {
					double distance = 9.0 - step * 1.4;
					Vec3 at = feet.add(back.scale(distance));
					LATER.add(new Later(now + step * 9L, () -> playTo(player, MehburitySounds.EVENT_FOOTSTEP, at, 0.9F, 0.8F)));
				}
			}
			case 1 -> playTo(player, MehburitySounds.EVENT_AMBIENCE, feet.add(back.scale(10.0)), 1.0F, 0.9F);
			default -> playTo(player, MehburitySounds.EVENT_SNAP, feet.add(back.scale(6.0)).add(back.yRot(Mth.HALF_PI).scale(3.0)),
					0.8F, 1.3F);
		}
		GameEvents.record(player, "the player heard something behind them in the dark");
		return true;
	}

	/** A sound only this player hears, as if it came from {@code at}. */
	private static void playTo(ServerPlayer player, SoundEvent sound, Vec3 at, float volume, float pitch) {
		if (!player.hasDisconnected()) {
			player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.AMBIENT,
					at.x, at.y, at.z, volume, pitch, RANDOM.nextLong()));
		}
	}

	// ---------------------------------------------------------------- whispers

	private static boolean whisper(ServerPlayer player) {
		MehburityEntity mehburity = CompanionManager.find(player.level().getServer(), player.getUUID());
		if (mehburity == null || mehburity.isRaging()) {
			return false;
		}
		mehburity.setScared(SCARED_TICKS);
		int line = RANDOM.nextInt(WHISPERS);
		MehburitySpeech.speak(player, MehburitySpeech.text("verity.whisper." + (line + 1), WHISPER_FALLBACK[line]));
		GameEvents.record(player, "Mehburity got scared in the dark and whispered: " + WHISPER_FALLBACK[line]);
		return true;
	}

	// ---------------------------------------------------------------- the demon

	/**
	 * Puts the demon {@code distance} blocks away, roughly where the player looks, on the surface (or at the player's
	 * height when they are underground). Null when that spot is not loaded.
	 */
	public static @Nullable WatcherEntity spawnWatcher(ServerPlayer player, double distance) {
		ServerLevel level = player.level();
		float yaw = player.getYRot() + (RANDOM.nextFloat() * 2.0F - 1.0F) * WATCHER_SPREAD;
		Vec3 dir = Vec3.directionFromRotation(0.0F, yaw);
		double x = player.getX() + dir.x * distance;
		double z = player.getZ() + dir.z * distance;
		if (!level.hasChunkAt(BlockPos.containing(x, player.getY(), z))) {
			return null;
		}
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
		double y = surface <= player.getY() + UNDERGROUND ? surface : player.getY();
		WatcherEntity watcher = MehburityEntities.WATCHER.create(level, EntitySpawnReason.EVENT);
		if (watcher == null) {
			return null;
		}
		watcher.snapTo(x, y, z, yaw + 180.0F, 0.0F);
		watcher.watch(player);
		level.addFreshEntity(watcher);
		GameEvents.record(player, "a tall demon with a dark smiling face stood far away in the dark, watching the player");
		return watcher;
	}

	/** The player looked at the demon (or walked up to it): it is gone, and Mehburity saw it too. */
	static void watcherSeen(WatcherEntity watcher, ServerPlayer player) {
		playTo(player, MehburitySounds.WATCHER_VANISH, watcher.getEyePosition(), 1.5F, 0.6F);
		player.level().sendParticles(ParticleTypes.LARGE_SMOKE, watcher.getX(), watcher.getEyeY(), watcher.getZ(), 20, 0.6, 1.5, 0.6, 0.02);
		GameEvents.record(player, "the demon vanished the moment the player looked at it");
		MehburityEntity mehburity = CompanionManager.find(player.level().getServer(), player.getUUID());
		if (mehburity != null && !mehburity.isRaging()) {
			mehburity.setScared(SCARED_TICKS);
			LATER.add(new Later(now + SAW_IT_DELAY, () -> MehburitySpeech.speak(player,
					MehburitySpeech.text("verity.watcher_seen", "...You saw it too, didn't you?"))));
		}
	}
}
