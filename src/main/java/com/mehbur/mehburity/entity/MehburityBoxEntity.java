package com.mehbur.mehburity.entity;

import com.mehbur.mehburity.config.MehburityConfig;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * The cardboard box Mehburity arrives in. Only its owner can open it, with a right click. Opening plays an animation
 * for {@code verity.box.openTicks} ticks, then Mehburity rises out and the box vanishes.
 */
public class MehburityBoxEntity extends Entity {
	/** -1 while closed, otherwise ticks since opening started. Synced for the lid animation. */
	private static final EntityDataAccessor<Integer> DATA_OPEN_TICKS =
			SynchedEntityData.defineId(MehburityBoxEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_OPEN_DURATION =
			SynchedEntityData.defineId(MehburityBoxEntity.class, EntityDataSerializers.INT);
	public static final int NEON_CYAN = 0x00F0FF;
	private static final int VALIDATE_INTERVAL = 20;

	private @Nullable UUID ownerId;

	public MehburityBoxEntity(EntityType<? extends MehburityBoxEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		entityData.define(DATA_OPEN_TICKS, -1);
		entityData.define(DATA_OPEN_DURATION, 40);
	}

	public @Nullable UUID getOwnerId() {
		return ownerId;
	}

	public void setOwnerId(@Nullable UUID ownerId) {
		this.ownerId = ownerId;
	}

	public boolean isOpening() {
		return this.entityData.get(DATA_OPEN_TICKS) >= 0;
	}

	/** 0 = closed, 1 = fully open. */
	public float getOpenProgress(float partialTicks) {
		int ticks = this.entityData.get(DATA_OPEN_TICKS);
		if (ticks < 0) {
			return 0.0F;
		}
		return Math.min(1.0F, (ticks + partialTicks) / this.entityData.get(DATA_OPEN_DURATION));
	}

	/** Starts the opening animation if {@code player} owns the box. Returns false for anyone else. */
	public boolean tryOpen(ServerPlayer player) {
		if (isOpening()) {
			return true;
		}
		if (ownerId == null || !ownerId.equals(player.getUUID())) {
			return false;
		}
		this.entityData.set(DATA_OPEN_DURATION, MehburityConfig.get().verity.box.openTicks);
		this.entityData.set(DATA_OPEN_TICKS, 0);
		this.level().playSound(null, this.getX(), this.getY(), this.getZ(), MehburitySounds.BOX_SHAKE,
				SoundSource.NEUTRAL, 1.0F, 1.0F);
		return true;
	}

	/** Right click opens the box, for its owner only. */
	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			if (!isOwner(serverPlayer)) {
				notYours(serverPlayer);
			} else {
				tryOpen(serverPlayer);
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** Hitting it does nothing but remind how it opens; the box takes no damage and gives no knockback. */
	@Override
	public boolean skipAttackInteraction(Entity source) {
		if (source instanceof ServerPlayer player && !isOpening()) {
			if (isOwner(player)) {
				MehburitySpeech.say(player, MehburitySpeech.text("box.how_to_open", "Right click the box to open it."));
			} else {
				notYours(player);
			}
		}
		return true;
	}

	private boolean isOwner(ServerPlayer player) {
		return ownerId != null && ownerId.equals(player.getUUID());
	}

	private void notYours(ServerPlayer player) {
		ServerPlayer owner = ownerId == null ? null : player.level().getServer().getPlayerList().getPlayer(ownerId);
		MehburitySpeech.say(player, owner != null
				? MehburitySpeech.text("box.not_yours", "This box belongs to %s.", owner.getDisplayName())
				: MehburitySpeech.text("box.not_yours_unknown", "This box is not yours."));
	}


	@Override
	public void tick() {
		super.tick();

		if (!this.isNoGravity()) {
			this.applyGravity();
		}
		this.move(MoverType.SELF, this.getDeltaMovement());
		this.setDeltaMovement(this.getDeltaMovement().multiply(0.6, 0.98, 0.6));
		if (this.onGround()) {
			this.setDeltaMovement(this.getDeltaMovement().multiply(1.0, -0.5, 1.0));
		}

		int ticks = this.entityData.get(DATA_OPEN_TICKS);
		if (this.level().isClientSide()) {
			if (ticks >= 0) {
				spawnOpeningParticles(ticks);
			}
			return;
		}

		ServerLevel level = (ServerLevel) this.level();
		if (this.tickCount % VALIDATE_INTERVAL == 0
				&& (ownerId == null || !CompanionRegistry.isCurrentBox(level, ownerId, this.getUUID()))) {
			// Replaced by a newer box, or the owner already has Mehburity.
			poof(level);
			return;
		}

		if (ticks >= 0) {
			int duration = this.entityData.get(DATA_OPEN_DURATION);
			if (ticks == duration / 2) {
				level.playSound(null, this.getX(), this.getY(), this.getZ(), MehburitySounds.BOX_OPEN,
						SoundSource.NEUTRAL, 1.0F, 1.0F);
			}
			if (ticks >= duration) {
				CompanionManager.emergeFromBox(this);
				return;
			}
			this.entityData.set(DATA_OPEN_TICKS, ticks + 1);
		}
	}

	private void spawnOpeningParticles(int ticks) {
		Level level = this.level();
		double top = this.getY() + this.getBbHeight();
		if (ticks % 2 == 0) {
			level.addParticle(new DustParticleOptions(NEON_CYAN, 1.0F),
					this.getX() + (this.random.nextDouble() - 0.5) * this.getBbWidth(), top,
					this.getZ() + (this.random.nextDouble() - 0.5) * this.getBbWidth(), 0.0, 0.05, 0.0);
		}
		if (ticks % 5 == 0) {
			level.addParticle(ParticleTypes.END_ROD, this.getX(), top, this.getZ(),
					(this.random.nextDouble() - 0.5) * 0.05, 0.08, (this.random.nextDouble() - 0.5) * 0.05);
		}
	}

	/** Removes the box with a puff of smoke. */
	void poof(ServerLevel level) {
		level.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + 0.4, this.getZ(), 20, 0.3, 0.3, 0.3, 0.02);
		this.discard();
	}

	// ---------------------------------------------------------------- an indestructible prop

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return !this.isRemoved();
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeHitByProjectile() {
		return false;
	}

	@Override
	public PushReaction getPistonPushReaction() {
		return PushReaction.IGNORE;
	}

	@Override
	protected double getDefaultGravity() {
		return 0.04;
	}

	@Override
	public boolean shouldBeSaved() {
		return super.shouldBeSaved() && ownerId != null;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.storeNullable("Owner", UUIDUtil.CODEC, ownerId);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		ownerId = input.read("Owner", UUIDUtil.CODEC).orElse(null);
		// An opening that was interrupted by a save restarts closed; the owner just right clicks it again.
		this.entityData.set(DATA_OPEN_TICKS, -1);
	}
}
