package com.mehbur.mehburity.anger;

import com.mehbur.mehburity.ai.GameEvents;
import com.mehbur.mehburity.config.MehburityConfig;
import com.mehbur.mehburity.entity.CompanionManager;
import com.mehbur.mehburity.entity.CompanionMode;
import com.mehbur.mehburity.entity.CompanionRegistry;
import com.mehbur.mehburity.entity.MehburityEntity;
import com.mehbur.mehburity.entity.MehburitySounds;
import com.mehbur.mehburity.entity.MehburitySpeech;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;

/**
 * A rage at a player does not break out at once: Mehburity vanishes without a word, stays away for
 * {@code anger.revengeDays} in-game days, and some time on the day after that it suddenly appears far behind the
 * culprit ({@code anger.revengeMinChunks} to {@code revengeMaxChunks} chunks), says in a deep voice that it came for
 * its revenge, turns into the demon and walks up to them. The countdown lives in the companion
 * registry, so it survives logouts and restarts; it counts in-game time (sleeping and {@code /time add} skip ahead).
 */
public final class Revenge {
	public static final long DAY_TICKS = 24000L;
	private static final int CHECK_INTERVAL = 20;
	/** Right behind the culprit (revenge distance 0, or nothing farther is loaded): this many blocks. */
	private static final double BEHIND = 6.0;
	private static final int DARKNESS_TICKS = 80;
	private static final RandomSource RANDOM = RandomSource.create();
	/** The overworld's day time at the last check, to see how much in-game time passed (-1: not started). */
	private static long lastDayTime = -1L;

	private Revenge() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK_INTERVAL == 0) {
				advance(server, elapsed(server));
			}
		});
	}

	/** In-game time since the last check: more after sleeping or {@code /time add}, never less than real time. */
	private static long elapsed(MinecraftServer server) {
		long now = server.overworld().getOverworldClockTime();
		long passed = lastDayTime < 0L ? CHECK_INTERVAL : now - lastDayTime;
		lastDayTime = now;
		return Math.max(CHECK_INTERVAL, passed); // set back in time, or the daylight cycle is off
	}

	/** Whether a rage at {@code culprit} waits for revenge instead of breaking out now. */
	static boolean waitsForRevenge(MehburityEntity mehburity, net.minecraft.world.entity.LivingEntity culprit) {
		return culprit instanceof ServerPlayer && MehburityConfig.get().anger.revengeDays > 0 && mehburity.getOwnerId() != null;
	}

	/** Leaves the world in a puff of smoke; comes back for {@code culprit} some time on the day after the wait. */
	static void vanish(MehburityEntity mehburity, ServerPlayer culprit) {
		ServerLevel level = (ServerLevel) mehburity.level();
		level.sendParticles(ParticleTypes.LARGE_SMOKE, mehburity.getX(), mehburity.getY() + 0.4, mehburity.getZ(), 30, 0.3, 0.4, 0.3, 0.02);
		level.sendParticles(ParticleTypes.SQUID_INK, mehburity.getX(), mehburity.getY() + 0.4, mehburity.getZ(), 15, 0.2, 0.3, 0.2, 0.05);
		level.playSound(null, mehburity.getX(), mehburity.getY(), mehburity.getZ(), MehburitySounds.MONSTER_SHRINK,
				SoundSource.HOSTILE, 1.0F, 0.5F);
		long wait = MehburityConfig.get().anger.revengeDays * DAY_TICKS + RANDOM.nextInt((int) DAY_TICKS);
		ServerPlayer owner = mehburity.getOwnerPlayer();
		if (owner != null) {
			GameEvents.record(owner, "Mehburity was driven into a rage by " + culprit.getName().getString()
					+ " and vanished without a word");
		}
		CompanionManager.vanish(mehburity, culprit.getUUID(), wait);
	}

	/** Counts the vanished Mehburities down by {@code ticks} of in-game time; brings back those whose day has come. */
	public static void advance(MinecraftServer server, long ticks) {
		for (Map.Entry<UUID, CompanionRegistry.Companion> entry : CompanionRegistry.companions(server).entrySet()) {
			CompanionRegistry.Companion companion = entry.getValue();
			if (companion.mode() != CompanionMode.GONE || companion.revengeOn().isEmpty()) {
				continue;
			}
			long left = companion.revengeIn() - ticks;
			if (left > 0L) {
				CompanionRegistry.putCompanion(server, entry.getKey(), companion.withRevengeIn(left));
				continue;
			}
			if (companion.revengeIn() > 0L) {
				CompanionRegistry.putCompanion(server, entry.getKey(), companion.withRevengeIn(0L));
			}
			// Its day has come: as soon as both its owner and the culprit are around.
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
			ServerPlayer culprit = server.getPlayerList().getPlayer(companion.revengeOn().get());
			if (owner != null && culprit != null && culprit.isAlive() && !culprit.isSpectator()) {
				comeBack(owner, culprit);
			}
		}
	}

	/**
	 * Where it appears: a random distance between the configured chunks behind the culprit, on the ground. Closer
	 * (a chunk at a time) where that is not loaded and ticking; right behind them if nothing is.
	 */
	private static Vec3 spotFarBehind(ServerLevel level, ServerPlayer culprit) {
		MehburityConfig.Anger config = MehburityConfig.get().anger;
		double min = config.revengeMinChunks * 16.0;
		double max = config.revengeMaxChunks * 16.0;
		Vec3 backwards = backwards(culprit);
		for (double distance = min + RANDOM.nextDouble() * (max - min); distance >= 16.0; distance -= 16.0) {
			Vec3 at = culprit.position().add(backwards.scale(distance));
			BlockPos column = BlockPos.containing(at);
			if (level.isPositionEntityTicking(column)) {
				int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
				return new Vec3(at.x, ground, at.z);
			}
		}
		return spotBehind(level, culprit);
	}

	private static Vec3 backwards(ServerPlayer culprit) {
		Vec3 backwards = culprit.getLookAngle().multiply(-1.0, 0.0, -1.0);
		return backwards.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : backwards.normalize();
	}

	/**
	 * A free spot {@link #BEHIND} blocks behind the culprit at their height (a little higher on a slope, closer when
	 * a wall is in the way); right at their feet if there is no room at all, e.g. in a tunnel.
	 */
	private static Vec3 spotBehind(ServerLevel level, ServerPlayer culprit) {
		Vec3 backwards = backwards(culprit);
		for (double distance = BEHIND; distance >= 1.0; distance -= 1.0) {
			for (int up = 0; up <= 3; up++) {
				Vec3 spot = culprit.position().add(backwards.scale(distance)).add(0.0, up, 0.0);
				if (level.noCollision(AABB.ofSize(spot.add(0.0, 0.4, 0.0), 0.8, 0.8, 0.8))) {
					return spot;
				}
			}
		}
		return culprit.position();
	}

	/** Out of nowhere, far behind the culprit: darkness, thunder, a deep voice, and the demon coming for them. */
	private static void comeBack(ServerPlayer owner, ServerPlayer culprit) {
		ServerLevel level = culprit.level();
		Vec3 spot = spotFarBehind(level, culprit);
		MehburityEntity mehburity = CompanionManager.comeBack(level, owner.getUUID(), spot);
		if (mehburity == null) {
			return;
		}
		level.sendParticles(ParticleTypes.LARGE_SMOKE, spot.x, spot.y + 0.5, spot.z, 40, 0.5, 0.6, 0.5, 0.03);
		// The thunder rolls where the culprit is; the roar comes from far behind them.
		level.playSound(null, culprit.getX(), culprit.getY(), culprit.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER,
				SoundSource.HOSTILE, 4.0F, 0.6F);
		level.playSound(null, spot.x, spot.y, spot.z, MehburitySounds.RAGE, SoundSource.HOSTILE, 8.0F, 0.5F);
		culprit.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DARKNESS_TICKS, 0, false, false));
		mehburity.getLookControl().setLookAt(culprit);
		var line = MehburitySpeech.text("anger.revenge", "I have come for my revenge.");
		MehburitySpeech.sayDeep(culprit, line, mehburity);
		if (owner != culprit) {
			MehburitySpeech.sayDeep(owner, line, mehburity);
		}
		GameEvents.record(owner, "Mehburity came back for its revenge on " + culprit.getName().getString());
		Anger.startRage(mehburity, culprit, null);
		mehburity.setApproaching(true);
	}
}
