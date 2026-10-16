package com.mehbur.mehburity.entity;

import com.mehbur.mehburity.config.MehburityConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * The floating companion. It cannot be hurt or killed: damage is ignored and falling into the void brings it
 * back to its owner. Ownership and the follow/stay mode are mirrored in {@link CompanionRegistry}.
 */
public class MehburityEntity extends PathfinderMob {
	/** Ticks left of the "rising out of the box" animation; 0 when done. Synced for the renderer. */
	private static final EntityDataAccessor<Integer> DATA_EMERGE_TICKS =
			SynchedEntityData.defineId(MehburityEntity.class, EntityDataSerializers.INT);
	public static final int EMERGE_DURATION = 30;
	private static final int VALIDATE_INTERVAL = 20;

	private @Nullable UUID ownerId;
	private CompanionMode mode = CompanionMode.FOLLOW;

	public MehburityEntity(EntityType<? extends MehburityEntity> type, Level level) {
		super(type, level);
		this.moveControl = new FlyingMoveControl<>(this, 20, true);
		this.setNoGravity(true);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.FLYING_SPEED, 0.12F)
				.add(Attributes.MOVEMENT_SPEED, 0.12F)
				.add(Attributes.FOLLOW_RANGE, 48.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(DATA_EMERGE_TICKS, 0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new FollowOwnerFlyingGoal(this));
		this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
		navigation.setCanOpenDoors(false);
		navigation.setCanFloat(true);
		return navigation;
	}

	@Override
	public void travel(Vec3 input) {
		this.travelFlying(input, this.getSpeed());
	}

	// ---------------------------------------------------------------- owner and mode

	public @Nullable UUID getOwnerId() {
		return ownerId;
	}

	public void setOwnerId(@Nullable UUID ownerId) {
		this.ownerId = ownerId;
	}

	public @Nullable ServerPlayer getOwnerPlayer() {
		if (ownerId == null || !(this.level() instanceof ServerLevel level)) {
			return null;
		}
		return level.getServer().getPlayerList().getPlayer(ownerId);
	}

	public CompanionMode getMode() {
		return mode;
	}

	public void setMode(CompanionMode mode) {
		this.mode = mode;
		if (mode == CompanionMode.STAY) {
			this.getNavigation().stop();
			this.setDeltaMovement(Vec3.ZERO);
		}
	}

	// ---------------------------------------------------------------- emerge animation

	public void startEmerging() {
		this.entityData.set(DATA_EMERGE_TICKS, EMERGE_DURATION);
	}

	public boolean isEmerging() {
		return this.entityData.get(DATA_EMERGE_TICKS) > 0;
	}

	/** 0 when it just started rising out of the box, 1 when fully out. */
	public float getEmergeProgress(float partialTicks) {
		int left = this.entityData.get(DATA_EMERGE_TICKS);
		if (left <= 0) {
			return 1.0F;
		}
		return Math.min(1.0F, (EMERGE_DURATION - left + partialTicks) / EMERGE_DURATION);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}

		int emerge = this.entityData.get(DATA_EMERGE_TICKS);
		if (emerge > 0) {
			this.entityData.set(DATA_EMERGE_TICKS, emerge - 1);
			this.setDeltaMovement(0.0, 0.025, 0.0);
		}

		// A copy left in an unloaded chunk (crash, re-summon) is no longer the registered one: remove it.
		if (this.tickCount % VALIDATE_INTERVAL == 0
				&& (ownerId == null || !CompanionRegistry.isCurrentCompanion(level, ownerId, this.getUUID()))) {
			this.discard();
		}
	}

	// ---------------------------------------------------------------- teleporting

	public boolean isTooFarFrom(Player owner) {
		double max = MehburityConfig.get().companion.teleportDistance;
		return owner.level() != this.level() || this.distanceToSqr(owner) > max * max;
	}

	/** Moves next to the owner, also across dimensions. Returns the entity that now represents Mehburity. */
	public MehburityEntity teleportToOwner(ServerPlayer owner) {
		Vec3 target = findSpotNear(owner);
		var moved = this.teleport(new TeleportTransition(owner.level(), target, Vec3.ZERO,
				this.getYRot(), this.getXRot(), TeleportTransition.DO_NOTHING));
		this.getNavigation().stop();
		return moved instanceof MehburityEntity companion ? companion : this;
	}

	/**
	 * A free spot beside the owner's shoulders, 1.5-2.5 blocks away so it never overlaps the camera,
	 * falling back to just above their head.
	 */
	Vec3 findSpotNear(ServerPlayer owner) {
		ServerLevel level = owner.level();
		for (int attempt = 0; attempt < 12; attempt++) {
			double angle = this.random.nextDouble() * Math.PI * 2.0;
			double distance = 1.5 + this.random.nextDouble();
			Vec3 candidate = new Vec3(owner.getX() + Math.cos(angle) * distance, owner.getY() + 1.2,
					owner.getZ() + Math.sin(angle) * distance);
			if (level.noCollision(this, this.getDimensions(this.getPose()).makeBoundingBox(candidate))
					&& level.getFluidState(BlockPos.containing(candidate)).isEmpty()) {
				return candidate;
			}
		}
		return owner.position().add(0.0, owner.getBbHeight() + 0.3, 0.0);
	}

	// ---------------------------------------------------------------- immortality

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public void kill(ServerLevel level) {
		// Immortal: /kill and similar instant deaths are ignored.
	}

	@Override
	protected void onBelowWorld() {
		ServerPlayer owner = getOwnerPlayer();
		if (owner != null) {
			teleportToOwner(owner);
		} else if (this.level() instanceof ServerLevel level) {
			BlockPos spawn = level.getRespawnData().pos();
			this.teleportTo(spawn.getX() + 0.5, level.getMaxY(), spawn.getZ() + 0.5);
		}
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	protected void checkFallDamage(double ya, boolean onGround, net.minecraft.world.level.block.state.BlockState onState, BlockPos pos) {
		// Flying; never takes fall damage.
	}

	// ---------------------------------------------------------------- saving

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.storeNullable("Owner", UUIDUtil.CODEC, ownerId);
		output.store("Mode", CompanionMode.CODEC, mode);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		ownerId = input.read("Owner", UUIDUtil.CODEC).orElse(null);
		mode = input.read("Mode", CompanionMode.CODEC).orElse(CompanionMode.FOLLOW);
	}
}
