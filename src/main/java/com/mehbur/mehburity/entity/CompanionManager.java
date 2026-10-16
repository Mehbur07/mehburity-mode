package com.mehbur.mehburity.entity;

import com.mehbur.mehburity.MehburityMod;
import com.mehbur.mehburity.ai.GameEvents;
import com.mehbur.mehburity.config.MehburityConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Keeps every player's Mehburity next to them: hands out the first box, brings Mehburity along across
 * logouts and dimensions, and re-creates it if it got lost. All state lives in {@link CompanionRegistry}.
 */
public final class CompanionManager {
	private static final int CHECK_INTERVAL = 20;
	private static final double BUILD_LEASH = 96.0;

	private CompanionManager() {
	}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onLeave(handler.player));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK_INTERVAL == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					keepNear(player, false);
				}
			}
		});
	}

	// ---------------------------------------------------------------- lookup

	public static @Nullable MehburityEntity find(MinecraftServer server, UUID owner) {
		CompanionRegistry.Companion companion = CompanionRegistry.companion(server, owner);
		return companion == null ? null : findEntity(server, companion.entityId());
	}

	private static @Nullable MehburityEntity findEntity(MinecraftServer server, UUID entityId) {
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getEntity(entityId) instanceof MehburityEntity mehburity && !mehburity.isRemoved()) {
				return mehburity;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- lifecycle

	private static void onJoin(ServerPlayer player) {
		// The first-join box is handed out by the Verity box feature.
		keepNear(player, true);
	}

	/** A following Mehburity leaves with its owner and comes back when they rejoin; a waiting one stays put. */
	private static void onLeave(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		CompanionRegistry.Companion companion = CompanionRegistry.companion(server, player.getUUID());
		if (companion == null || companion.mode() != CompanionMode.FOLLOW) {
			return;
		}
		MehburityEntity mehburity = findEntity(server, companion.entityId());
		if (mehburity != null) {
			mehburity.discard();
		}
		CompanionRegistry.putCompanion(server, player.getUUID(), companion.withStowed(true));
	}

	/**
	 * Makes sure a following Mehburity is near its owner: teleports it across dimensions or long distances,
	 * and re-creates it when it is not loaded anywhere (stowed at logout, or stuck in an unloaded chunk).
	 */
	private static void keepNear(ServerPlayer player, boolean joining) {
		MinecraftServer server = player.level().getServer();
		CompanionRegistry.Companion companion = CompanionRegistry.companion(server, player.getUUID());
		if (companion == null || player.isSpectator()) {
			return;
		}

		MehburityEntity mehburity = findEntity(server, companion.entityId());
		if (mehburity != null) {
			if (companion.mode() == CompanionMode.FOLLOW && mehburity.isTooFarFrom(player) && !isBusyBuilding(mehburity, player)) {
				mehburity.teleportToOwner(player);
			}
			return;
		}

		if (companion.mode() == CompanionMode.FOLLOW || companion.stowed()) {
			MehburityEntity created = spawnCompanion(player, companion.mode());
			if (created != null && joining) {
				MehburitySpeech.speak(player, MehburitySpeech.text("welcome_back", "Welcome back, %s!", player.getDisplayName()));
			}
		}
	}

	/** A house under construction keeps Mehburity at the site, unless the owner went really far or elsewhere. */
	private static boolean isBusyBuilding(MehburityEntity mehburity, ServerPlayer owner) {
		return mehburity.getBuildJob() != null && owner.level() == mehburity.level() && mehburity.distanceToSqr(owner) < BUILD_LEASH * BUILD_LEASH;
	}

	// ---------------------------------------------------------------- box

	/** Places a fresh box in front of the player. Any older box of theirs becomes invalid and disappears. */
	public static MehburityBoxEntity spawnBox(ServerPlayer player) {
		ServerLevel level = player.level();
		MehburityBoxEntity box = MehburityEntities.BOX.create(level, EntitySpawnReason.TRIGGERED);
		if (box == null) {
			throw new IllegalStateException("Could not create the Mehburity box");
		}
		Vec3 spot = boxSpot(player);
		box.snapTo(spot.x, spot.y, spot.z, player.getYRot() + 180.0F, 0.0F);
		box.setOwnerId(player.getUUID());
		CompanionRegistry.putBox(level.getServer(), player.getUUID(), box.getUUID());
		level.addFreshEntity(box);
		level.sendParticles(ParticleTypes.END_ROD, spot.x, spot.y + 0.5, spot.z, 15, 0.3, 0.3, 0.3, 0.02);

		MehburitySpeech.say(player, MehburitySpeech.text("box.appeared",
				"A box appeared next to you... Hit it (left click) to open it."));
		MehburityMod.LOGGER.info("Gave a Mehburity box to {}", player.getGameProfile().name());
		return box;
	}

	/** Two blocks in front of the player, on the first free spot found scanning down from head height. */
	private static Vec3 boxSpot(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 forward = Vec3.directionFromRotation(0.0F, player.getYRot()).scale(2.0);
		BlockPos column = BlockPos.containing(player.getX() + forward.x, player.getY() + 1.0, player.getZ() + forward.z);
		for (int dy = 0; dy < 6; dy++) {
			BlockPos pos = column.below(dy);
			if (level.getBlockState(pos).isAir() && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) {
				return Vec3.atBottomCenterOf(pos);
			}
		}
		return Vec3.atBottomCenterOf(player.blockPosition()); // no room in front: at the player's feet
	}

	/** Called by the box when its opening animation ends. */
	static void emergeFromBox(MehburityBoxEntity box) {
		ServerLevel level = (ServerLevel) box.level();
		MinecraftServer server = level.getServer();
		UUID ownerId = box.getOwnerId();
		ServerPlayer owner = ownerId == null ? null : server.getPlayerList().getPlayer(ownerId);
		box.poof(level);
		if (ownerId == null) {
			return;
		}

		if (CompanionRegistry.companion(server, ownerId) != null) {
			if (owner != null) {
				MehburitySpeech.say(owner, MehburitySpeech.text("already_have", "You already have me!"));
			}
			return;
		}

		Vec3 start = box.position().add(0.0, 0.1, 0.0);
		MehburityEntity mehburity = spawnCompanion(level, ownerId, start, CompanionMode.FOLLOW);
		if (mehburity == null) {
			return;
		}
		mehburity.startEmerging();
		level.sendParticles(new DustParticleOptions(MehburityBoxEntity.NEON_CYAN, 1.5F),
				start.x, start.y + 0.6, start.z, 40, 0.4, 0.4, 0.4, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, start.x, start.y + 0.6, start.z, 25, 0.2, 0.4, 0.2, 0.08);
		level.playSound(null, start.x, start.y, start.z, MehburitySounds.EMERGE, SoundSource.NEUTRAL, 1.0F, 1.0F);
		if (owner != null) {
			// Verity's line, word for word; spoken with Mehburity's voice. Editable in the config (ai.greeting).
			MehburitySpeech.speak(owner, Component.literal(MehburityConfig.get().ai.greeting));
			GameEvents.record(owner, "Mehburity came out of its box and met the player for the first time");
		}
	}

	// ---------------------------------------------------------------- spawning

	/** Re-creates Mehburity beside its owner (after a logout, or when it got lost). */
	private static @Nullable MehburityEntity spawnCompanion(ServerPlayer owner, CompanionMode mode) {
		return spawnCompanion(owner.level(), owner.getUUID(), mehburity -> mehburity.findSpotNear(owner), mode);
	}

	private static @Nullable MehburityEntity spawnCompanion(ServerLevel level, UUID ownerId, Vec3 pos, CompanionMode mode) {
		return spawnCompanion(level, ownerId, mehburity -> pos, mode);
	}

	private static @Nullable MehburityEntity spawnCompanion(ServerLevel level, UUID ownerId,
			Function<MehburityEntity, Vec3> placement, CompanionMode mode) {
		MehburityEntity mehburity = MehburityEntities.MEHBURITY.create(level, EntitySpawnReason.TRIGGERED);
		if (mehburity == null) {
			return null;
		}
		Vec3 pos = placement.apply(mehburity);
		mehburity.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
		mehburity.setOwnerId(ownerId);
		mehburity.setMode(mode);
		// Register first: the entity validates itself against the registry and must find itself there.
		// A re-created Mehburity keeps the owner's choices (combat on/off).
		CompanionRegistry.Companion previous = CompanionRegistry.companion(level.getServer(), ownerId);
		CompanionRegistry.putCompanion(level.getServer(), ownerId, previous == null
				? new CompanionRegistry.Companion(mehburity.getUUID(), mode)
				: previous.withEntity(mehburity.getUUID()).withMode(mode).withStowed(false));
		level.addFreshEntity(mehburity);
		return mehburity;
	}

	// ---------------------------------------------------------------- commands

	public enum SummonResult { TELEPORTED, RESPAWNED, BOX_GIVEN }

	/** {@code /mehburity cagir}: brings Mehburity to the player, or a new box if they never opened theirs. */
	public static SummonResult summon(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		CompanionRegistry.Companion companion = CompanionRegistry.companion(server, player.getUUID());
		if (companion == null) {
			spawnBox(player);
			return SummonResult.BOX_GIVEN;
		}

		MehburityEntity mehburity = findEntity(server, companion.entityId());
		if (mehburity != null) {
			MehburityEntity moved = mehburity.teleportToOwner(player);
			setMode(player, moved, CompanionMode.FOLLOW);
			return SummonResult.TELEPORTED;
		}
		spawnCompanion(player, CompanionMode.FOLLOW);
		return SummonResult.RESPAWNED;
	}

	/** Sets the mode on the loaded entity and in the registry. Returns false when Mehburity is not loaded. */
	public static boolean setMode(ServerPlayer player, CompanionMode mode) {
		MehburityEntity mehburity = find(player.level().getServer(), player.getUUID());
		if (mehburity == null) {
			return false;
		}
		setMode(player, mehburity, mode);
		return true;
	}

	private static void setMode(ServerPlayer player, MehburityEntity mehburity, CompanionMode mode) {
		mehburity.setMode(mode);
		update(player, companion -> companion.withEntity(mehburity.getUUID()).withMode(mode).withStowed(false));
	}

	/** {@code /mehburity savas}: whether Mehburity fights for the player. Returns false without a Mehburity. */
	public static boolean setCombat(ServerPlayer player, boolean combat) {
		return update(player, companion -> companion.withCombat(combat));
	}

	public static boolean isCombatEnabled(MinecraftServer server, UUID owner) {
		CompanionRegistry.Companion companion = CompanionRegistry.companion(server, owner);
		return companion != null && companion.combat();
	}

	private static boolean update(ServerPlayer player, UnaryOperator<CompanionRegistry.Companion> change) {
		MinecraftServer server = player.level().getServer();
		CompanionRegistry.Companion companion = CompanionRegistry.companion(server, player.getUUID());
		if (companion == null) {
			return false;
		}
		CompanionRegistry.putCompanion(server, player.getUUID(), change.apply(companion));
		return true;
	}
}
