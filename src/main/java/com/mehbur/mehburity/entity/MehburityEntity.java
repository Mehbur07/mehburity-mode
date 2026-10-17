package com.mehbur.mehburity.entity;

import com.mehbur.mehburity.anger.Anger;
import com.mehbur.mehburity.anger.MonsterGoal;
import com.mehbur.mehburity.anger.RageTargetGoal;
import com.mehbur.mehburity.build.BuildGoal;
import com.mehbur.mehburity.build.BuildJob;
import com.mehbur.mehburity.combat.BeamAttackGoal;
import com.mehbur.mehburity.combat.DefendOwnerTargetGoal;
import com.mehbur.mehburity.config.MehburityConfig;
import com.mehbur.mehburity.mining.MineGoal;
import com.mehbur.mehburity.mining.MiningJob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
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
	/** Entity id of what the beam is aimed at (charging or just fired), 0 when idle. Synced for the beam renderer. */
	private static final EntityDataAccessor<Integer> DATA_BEAM_TARGET =
			SynchedEntityData.defineId(MehburityEntity.class, EntityDataSerializers.INT);
	/** Temper, 0-100. Synced so the client can show it (angry sparks, the red rage face). */
	private static final EntityDataAccessor<Float> DATA_ANGER =
			SynchedEntityData.defineId(MehburityEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Boolean> DATA_RAGING =
			SynchedEntityData.defineId(MehburityEntity.class, EntityDataSerializers.BOOLEAN);
	/** Height of the rage form (the demon) in blocks, 0 while it is a sphere. The hitbox and the model follow it. */
	private static final EntityDataAccessor<Float> DATA_MONSTER_HEIGHT =
			SynchedEntityData.defineId(MehburityEntity.class, EntityDataSerializers.FLOAT);
	/** Ticks left of turning into the demon, and how long the whole transformation takes. Synced for the animation. */
	private static final EntityDataAccessor<Integer> DATA_TRANSFORM_TICKS =
			SynchedEntityData.defineId(MehburityEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_TRANSFORM_DURATION =
			SynchedEntityData.defineId(MehburityEntity.class, EntityDataSerializers.INT);
	/** Ticks left of looking scared (wide eyes, small mouth), e.g. after a scripted scare. Synced for the face. */
	private static final EntityDataAccessor<Integer> DATA_SCARED_TICKS =
			SynchedEntityData.defineId(MehburityEntity.class, EntityDataSerializers.INT);
	public static final int EMERGE_DURATION = 30;
	private static final int VALIDATE_INTERVAL = 20;
	/** Entity event sent when a beam shot lands; the client flashes the beam. */
	public static final byte EVENT_BEAM_FIRED = 100;
	public static final int BEAM_FLASH_TICKS = 5;
	/** Entity event sent when the demon swipes with its claws; the client swings the arm. */
	public static final byte EVENT_CLAW_SWIPE = 101;
	public static final int SWIPE_TICKS = 12;
	/** How long the client takes to draw the beam at full charge (just looks; the real charge is server-side). */
	private static final float BEAM_CHARGE_VISUAL_TICKS = 12.0F;

	private @Nullable UUID ownerId;
	private CompanionMode mode = CompanionMode.FOLLOW;
	/** What the owner asked it to mine; not saved (a new Mehburity after login starts idle). */
	private @Nullable MiningJob miningJob;
	/** The house it is building; not saved either. */
	private @Nullable BuildJob buildJob;
	/** Whom it is raging at; not saved (a rage ends with the session). */
	private @Nullable LivingEntity rageCulprit;
	private int rageTicks;
	private int clientBeamTicks;
	private int clientFlashTicks;
	private int clientSwipeTicks;

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
		entityData.define(DATA_BEAM_TARGET, 0);
		entityData.define(DATA_ANGER, 0.0F);
		entityData.define(DATA_RAGING, false);
		entityData.define(DATA_MONSTER_HEIGHT, 0.0F);
		entityData.define(DATA_TRANSFORM_TICKS, 0);
		entityData.define(DATA_TRANSFORM_DURATION, 0);
		entityData.define(DATA_SCARED_TICKS, 0);
	}

	@Override
	protected void registerGoals() {
		DefendOwnerTargetGoal targeting = new DefendOwnerTargetGoal(this);
		// A rage overrides everything: the only target is the culprit.
		this.targetSelector.addGoal(0, new RageTargetGoal(this));
		this.targetSelector.addGoal(1, targeting);
		// The rage form walks and claws instead of flying and shooting.
		this.goalSelector.addGoal(0, new MonsterGoal(this));
		// Fighting comes before following; the companion manager still teleports it back if it gets too far.
		this.goalSelector.addGoal(1, new BeamAttackGoal(this, targeting));
		// Jobs pause for fights and keep following from pulling it away from the work. Only one job runs at a time.
		this.goalSelector.addGoal(2, new BuildGoal(this));
		this.goalSelector.addGoal(2, new MineGoal(this));
		this.goalSelector.addGoal(3, new FollowOwnerFlyingGoal(this));
		this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
	}

	/** Like any mob, minus the vanilla rule that mobs never attack ghasts. */
	@Override
	public boolean canAttack(LivingEntity target) {
		if (target instanceof Player && this.level().getDifficulty() == Difficulty.PEACEFUL) {
			return false;
		}
		return target.canBeSeenAsEnemy();
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

	public @Nullable MiningJob getMiningJob() {
		return miningJob;
	}

	public void setMiningJob(@Nullable MiningJob job) {
		this.miningJob = job;
	}

	// ---------------------------------------------------------------- anger

	public float getAnger() {
		return this.entityData.get(DATA_ANGER);
	}

	/** Use {@link Anger} to change it: that also handles warnings, the rage and saving. */
	public void setAnger(float anger) {
		this.entityData.set(DATA_ANGER, Math.max(0.0F, Math.min(100.0F, anger)));
	}

	public boolean isRaging() {
		return this.entityData.get(DATA_RAGING);
	}

	public @Nullable LivingEntity getRageCulprit() {
		return rageCulprit;
	}

	public int getRageTicks() {
		return rageTicks;
	}

	/** Starts (culprit) or ends (null) the rage. Called by {@link Anger}. Ending it also ends the rage form. */
	public void setRageCulprit(@Nullable LivingEntity culprit) {
		this.rageCulprit = culprit;
		this.rageTicks = 0;
		this.entityData.set(DATA_RAGING, culprit != null);
		if (culprit == null) {
			this.setTarget(null);
			setMonster(0.0F, 0);
		}
	}

	// ---------------------------------------------------------------- expressions

	/** Shows the scared face for {@code ticks} (the anger faces still win while it is angry). */
	public void setScared(int ticks) {
		this.entityData.set(DATA_SCARED_TICKS, Math.max(0, ticks));
	}

	public boolean isScared() {
		return this.entityData.get(DATA_SCARED_TICKS) > 0;
	}

	// ---------------------------------------------------------------- rage form

	public boolean isMonster() {
		return getMonsterHeight() > 0.0F;
	}

	public float getMonsterHeight() {
		// The entity type's size is looked up once before the synced data exists.
		return this.entityData == null ? 0.0F : this.entityData.get(DATA_MONSTER_HEIGHT);
	}

	/** Turns into the demon ({@code height} blocks, growing for {@code transformTicks}), or back into a sphere (0). */
	public void setMonster(float height, int transformTicks) {
		this.entityData.set(DATA_TRANSFORM_DURATION, height > 0.0F ? transformTicks : 0);
		this.entityData.set(DATA_TRANSFORM_TICKS, height > 0.0F ? transformTicks : 0);
		this.entityData.set(DATA_MONSTER_HEIGHT, Math.max(0.0F, height));
		this.getNavigation().stop();
		this.setDeltaMovement(Vec3.ZERO);
	}

	public boolean isTransforming() {
		return this.entityData.get(DATA_TRANSFORM_TICKS) > 0;
	}

	/** 0 when the transformation just started, 1 when the demon is fully grown (also when it is not a monster). */
	public float getTransformProgress(float partialTicks) {
		int left = this.entityData.get(DATA_TRANSFORM_TICKS);
		int duration = this.entityData.get(DATA_TRANSFORM_DURATION);
		if (left <= 0 || duration <= 0) {
			return 1.0F;
		}
		return Math.min(1.0F, (duration - left + partialTicks) / duration);
	}

	/** Client: 0 → the claw swipe just started, 1 → done (or no swipe). */
	public float getSwipeProgress(float partialTicks) {
		return clientSwipeTicks <= 0 ? 1.0F : Math.min(1.0F, (SWIPE_TICKS - clientSwipeTicks + partialTicks) / SWIPE_TICKS);
	}

	@Override
	protected EntityDimensions getDefaultDimensions(Pose pose) {
		float height = getMonsterHeight();
		// Skinny: 2 blocks wide at 18 tall. It walks through blocks, so the size never gets it stuck.
		return height > 0.0F ? EntityDimensions.scalable(height / 9.0F, height).withEyeHeight(height * 0.9F)
				: super.getDefaultDimensions(pose);
	}

	/**
	 * Right click: with a cake it calms down; otherwise its owner picks it up and carries it in the inventory (not
	 * while it is raging, or still rising out of its box).
	 */
	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (player.getItemInHand(hand).is(net.minecraft.world.item.Items.CAKE)) {
			if (this.level() instanceof ServerLevel && player instanceof ServerPlayer serverPlayer) {
				Anger.feedCake(this, serverPlayer, hand);
			}
			return InteractionResult.SUCCESS;
		}
		if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer owner)) {
			return this.level().isClientSide() && hand == InteractionHand.MAIN_HAND ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		if (!owner.getUUID().equals(ownerId)) {
			return InteractionResult.PASS;
		}
		if (isRaging() || isMonster() || isEmerging()) {
			MehburitySpeech.say(owner, MehburitySpeech.text("carry.too_angry", "Don't touch me right now!"));
		} else {
			CompanionManager.carry(owner, this);
		}
		return InteractionResult.SUCCESS;
	}

	public @Nullable BuildJob getBuildJob() {
		return buildJob;
	}

	public void setBuildJob(@Nullable BuildJob job) {
		this.buildJob = job;
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

	// ---------------------------------------------------------------- beam

	public void setBeamTarget(@Nullable LivingEntity target) {
		this.entityData.set(DATA_BEAM_TARGET, target == null ? 0 : target.getId());
	}

	public @Nullable LivingEntity getBeamTarget() {
		int id = this.entityData.get(DATA_BEAM_TARGET);
		return id != 0 && this.level().getEntity(id) instanceof LivingEntity target ? target : null;
	}

	/** Client: 0 → the beam just started charging, 1 → fully charged. */
	public float getBeamCharge(float partialTicks) {
		return Math.min(1.0F, (clientBeamTicks + partialTicks) / BEAM_CHARGE_VISUAL_TICKS);
	}

	/** Client: 1 right after a shot landed, fading to 0. */
	public float getBeamFlash(float partialTicks) {
		return clientFlashTicks <= 0 ? 0.0F : Math.max(0.0F, (clientFlashTicks - partialTicks) / BEAM_FLASH_TICKS);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_BEAM_TARGET.equals(accessor)) {
			clientBeamTicks = 0;
		} else if (DATA_MONSTER_HEIGHT.equals(accessor)) {
			// The demon walks through walls (an 18-block body would never fit through a cave).
			this.noPhysics = isMonster();
			this.refreshDimensions();
		}
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == EVENT_BEAM_FIRED) {
			clientFlashTicks = BEAM_FLASH_TICKS;
		} else if (id == EVENT_CLAW_SWIPE) {
			clientSwipeTicks = SWIPE_TICKS;
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			if (this.entityData.get(DATA_BEAM_TARGET) != 0) {
				clientBeamTicks++;
			}
			if (clientFlashTicks > 0) {
				clientFlashTicks--;
			}
			if (clientSwipeTicks > 0) {
				clientSwipeTicks--;
			}
			return;
		}

		if (rageCulprit != null) {
			rageTicks++;
		}
		Anger.tick(this);

		int scared = this.entityData.get(DATA_SCARED_TICKS);
		if (scared > 0) {
			this.entityData.set(DATA_SCARED_TICKS, scared - 1);
			// Scared, it keeps looking at its owner.
			ServerPlayer owner = getOwnerPlayer();
			if (owner != null && owner.level() == level && !isMonster()) {
				this.getLookControl().setLookAt(owner, 30.0F, 30.0F);
			}
		}

		int transform = this.entityData.get(DATA_TRANSFORM_TICKS);
		if (transform > 0) {
			this.entityData.set(DATA_TRANSFORM_TICKS, transform - 1);
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

	/** Never hurt, but it remembers who tried. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		Anger.onAttacked(this, source);
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
		output.putFloat("Anger", getAnger());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		ownerId = input.read("Owner", UUIDUtil.CODEC).orElse(null);
		mode = input.read("Mode", CompanionMode.CODEC).orElse(CompanionMode.FOLLOW);
		setAnger(input.getFloatOr("Anger", 0.0F));
	}
}
