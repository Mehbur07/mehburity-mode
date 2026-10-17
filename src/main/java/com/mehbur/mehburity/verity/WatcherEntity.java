package com.mehbur.mehburity.verity;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * The demon of the scripted scares. It stands far away in the dark, turned towards one player, and never moves.
 * It vanishes once that player looks straight at it for a moment or comes close, or after a while unseen.
 * It cannot be touched (not pickable, takes no damage) and is never saved.
 */
public final class WatcherEntity extends Entity {
	/** Gone after this long, seen or not. */
	private static final int MAX_LIFE = 20 * 20;
	/** Vanishes when the player comes this close (horizontally). */
	private static final double VANISH_DISTANCE = 20.0;
	/** Looking straight at it (within ~14 degrees) for this long makes it vanish. */
	private static final int LOOK_TICKS = 15;
	private static final double LOOK_DOT = 0.97;

	private @Nullable UUID watched;
	private int life;
	private int lookedAt;

	public WatcherEntity(EntityType<? extends WatcherEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	/** The player it stares at; only they can make it vanish. */
	void watch(ServerPlayer player) {
		this.watched = player.getUUID();
		facePlayer(player);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer player = watched == null ? null : level.getServer().getPlayerList().getPlayer(watched);
		if (player == null || player.level() != level || player.isDeadOrDying() || ++life > MAX_LIFE) {
			this.discard(); // unseen: it was simply there
			return;
		}
		facePlayer(player);
		double distance = Math.sqrt(Mth.square(player.getX() - getX()) + Mth.square(player.getZ() - getZ()));
		Vec3 toHead = new Vec3(getX(), getEyeY(), getZ()).subtract(player.getEyePosition()).normalize();
		boolean looking = player.getViewVector(1.0F).dot(toHead) > LOOK_DOT && player.hasLineOfSight(this);
		lookedAt = looking ? lookedAt + 1 : 0;
		if (distance < VANISH_DISTANCE || lookedAt >= LOOK_TICKS) {
			EventsFeature.watcherSeen(this, player);
			this.discard();
		}
	}

	private void facePlayer(ServerPlayer player) {
		float yaw = (float) (Mth.atan2(player.getZ() - getZ(), player.getX() - getX()) * Mth.RAD_TO_DEG) - 90.0F;
		this.setYRot(yaw);
		this.setYHeadRot(yaw);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
