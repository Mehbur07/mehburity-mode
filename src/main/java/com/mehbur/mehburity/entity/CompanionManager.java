package com.mehbur.mehburity.entity;

import com.mehbur.mehburity.MehburityMod;
import com.mehbur.mehburity.ai.GameEvents;
import com.mehbur.mehburity.config.MehburityConfig;
import com.mehbur.mehburity.verity.FlashlightItem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
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
	/** Carried Mehburities that landed in the world as items this tick (Q, dragged out, dropped on death). */
	private static final List<ItemEntity> DROPPED = new ArrayList<>();

	private CompanionManager() {
	}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onLeave(handler.player));
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity item && item.getItem().is(MehburityItem.ITEM)) {
				DROPPED.add(item); // turned back into the ball at the end of the tick, not while the level adds it
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!DROPPED.isEmpty()) {
				List<ItemEntity> dropped = new ArrayList<>(DROPPED);
				DROPPED.clear();
				dropped.forEach(CompanionManager::unpackDropped);
			}
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
		float anger = companion.anger();
		if (mehburity != null) {
			anger = mehburity.getAnger(); // a rage ends here, the temper is remembered
			mehburity.discard();
		}
		CompanionRegistry.putCompanion(server, player.getUUID(), companion.withStowed(true).withAnger(anger));
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
			if (companion.mode() == CompanionMode.FOLLOW && mehburity.isTooFarFrom(player) && !isBusyBuilding(mehburity, player)
					&& !mehburity.isRaging()) { // a rage goes after the culprit, wherever they run
				mehburity.teleportToOwner(player);
			}
			return;
		}

		if (companion.mode() == CompanionMode.FOLLOW || companion.stowed()) {
			MehburityEntity created = spawnCompanion(player, companion.mode());
			if (created != null && joining) {
				MehburitySpeech.say(player, MehburitySpeech.text("welcome_back", "Welcome back, %s!", player.getDisplayName()));
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
				"A box appeared next to you... Right click it to open it."));
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
			MehburitySpeech.say(owner, Component.literal(MehburityConfig.get().ai.greeting));
			GameEvents.record(owner, "Mehburity came out of its box and met the player for the first time");
			FlashlightItem.gift(owner);
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
		CompanionRegistry.Companion stored = CompanionRegistry.companion(level.getServer(), ownerId);
		if (stored != null) {
			mehburity.setAnger(stored.anger());
		}
		// Register first: the entity validates itself against the registry and must find itself there.
		// A re-created Mehburity keeps the owner's choices (combat on/off).
		CompanionRegistry.Companion previous = CompanionRegistry.companion(level.getServer(), ownerId);
		CompanionRegistry.putCompanion(level.getServer(), ownerId, previous == null
				? new CompanionRegistry.Companion(mehburity.getUUID(), mode)
				: previous.withEntity(mehburity.getUUID()).withMode(mode).withStowed(false));
		level.addFreshEntity(mehburity);
		return mehburity;
	}

	// ---------------------------------------------------------------- revenge

	/** Mehburity leaves the world, to come back for {@code culprit} in {@code ticks} of in-game time. */
	public static void vanish(MehburityEntity mehburity, UUID culprit, long ticks) {
		UUID ownerId = mehburity.getOwnerId();
		if (ownerId == null || !(mehburity.level() instanceof ServerLevel level)) {
			return;
		}
		CompanionRegistry.Companion companion = CompanionRegistry.companion(level.getServer(), ownerId);
		if (companion != null) {
			CompanionRegistry.putCompanion(level.getServer(), ownerId,
					companion.withAnger(mehburity.getAnger()).withRevenge(culprit, ticks));
		}
		mehburity.setMiningJob(null);
		mehburity.setBuildJob(null);
		mehburity.discard();
	}

	/** A vanished Mehburity appears at {@code spot}, following its owner again (the revenge is over). */
	public static @Nullable MehburityEntity comeBack(ServerLevel level, UUID ownerId, Vec3 spot) {
		MehburityEntity mehburity = spawnCompanion(level, ownerId, spot, CompanionMode.FOLLOW);
		CompanionRegistry.Companion companion = CompanionRegistry.companion(level.getServer(), ownerId);
		if (mehburity != null && companion != null) {
			CompanionRegistry.putCompanion(level.getServer(), ownerId, companion.withoutRevenge());
		}
		return mehburity;
	}

	/** Calls off a pending revenge: Mehburity comes back calm, beside its owner (next check). */
	public static boolean forgive(MinecraftServer server, UUID ownerId) {
		CompanionRegistry.Companion companion = CompanionRegistry.companion(server, ownerId);
		if (companion == null || companion.mode() != CompanionMode.GONE) {
			return false;
		}
		CompanionRegistry.putCompanion(server, ownerId, companion.withoutRevenge().withMode(CompanionMode.FOLLOW).withAnger(0.0F));
		return true;
	}

	// ---------------------------------------------------------------- carrying

	/** The owner's right click: Mehburity goes into their inventory as an item and leaves the world. */
	public static void carry(ServerPlayer owner, MehburityEntity mehburity) {
		ServerLevel level = (ServerLevel) mehburity.level();
		update(owner, companion -> companion.withMode(CompanionMode.CARRIED).withStowed(false).withAnger(mehburity.getAnger()));
		level.sendParticles(new DustParticleOptions(MehburityBoxEntity.NEON_CYAN, 1.0F),
				mehburity.getX(), mehburity.getY() + 0.4, mehburity.getZ(), 15, 0.25, 0.25, 0.25, 0.0);
		level.playSound(null, mehburity.getX(), mehburity.getY(), mehburity.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 1.4F);
		mehburity.discard();
		ItemStack item = MehburityItem.of(owner.getUUID());
		if (!owner.getInventory().add(item)) {
			owner.drop(item, false);
		}
		MehburitySpeech.say(owner, MehburitySpeech.text("carry.picked_up", "Wheee! Right click to let me out again."));
	}

	/**
	 * Lets a carried Mehburity out at {@code spot} (beside the owner when null). False when it is not carried, e.g.
	 * it was called back with {@code /mehburity cagir} in the meantime.
	 */
	public static boolean release(ServerPlayer owner, @Nullable Vec3 spot) {
		CompanionRegistry.Companion companion = CompanionRegistry.companion(owner.level().getServer(), owner.getUUID());
		if (companion == null || companion.mode() != CompanionMode.CARRIED) {
			return false;
		}
		MehburityEntity mehburity = spot == null ? spawnCompanion(owner, CompanionMode.FOLLOW)
				: spawnCompanion(owner.level(), owner.getUUID(), spot, CompanionMode.FOLLOW);
		if (mehburity == null) {
			return false;
		}
		owner.level().sendParticles(new DustParticleOptions(MehburityBoxEntity.NEON_CYAN, 1.2F),
				mehburity.getX(), mehburity.getY() + 0.4, mehburity.getZ(), 20, 0.3, 0.3, 0.3, 0.0);
		owner.level().playSound(null, mehburity.getX(), mehburity.getY(), mehburity.getZ(), MehburitySounds.EMERGE,
				SoundSource.NEUTRAL, 0.8F, 1.3F);
		MehburitySpeech.say(owner, MehburitySpeech.text("carry.released", "Phew, it was dark in there!"));
		return true;
	}

	/**
	 * A carried Mehburity thrown out of the inventory (Q) does not lie there as an item: it comes out as the ball
	 * where it landed and keeps the throw, so it rolls on. Waits as an item (that never despawns) while its owner
	 * is offline; a stale copy just goes away.
	 */
	private static void unpackDropped(ItemEntity item) {
		if (item.isRemoved() || !(item.level() instanceof ServerLevel level)) {
			return;
		}
		UUID ownerId = item.getItem().get(MehburityItem.OWNER);
		ServerPlayer owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
		if (owner == null) {
			item.setUnlimitedLifetime();
			return;
		}
		Vec3 velocity = item.getDeltaMovement();
		// Thrown in another dimension than its owner: it comes out beside them instead.
		Vec3 spot = owner.level() == level ? item.position() : null;
		item.discard();
		if (release(owner, spot)) {
			MehburityEntity mehburity = find(level.getServer(), ownerId);
			if (mehburity != null && spot != null) {
				mehburity.setDeltaMovement(velocity);
			}
		}
	}

	// ---------------------------------------------------------------- commands

	public enum SummonResult { TELEPORTED, RESPAWNED, BOX_GIVEN, GONE }

	/** {@code /mehburity cagir}: brings Mehburity to the player, or a new box if they never opened theirs. */
	public static SummonResult summon(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		CompanionRegistry.Companion companion = CompanionRegistry.companion(server, player.getUUID());
		if (companion == null) {
			spawnBox(player);
			return SummonResult.BOX_GIVEN;
		}
		if (companion.mode() == CompanionMode.GONE) {
			return SummonResult.GONE; // it comes back when it wants to
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
