package com.mehbur.mehburity.anger;

import com.mehbur.mehburity.combat.MehburityDamage;
import com.mehbur.mehburity.config.MehburityConfig;
import com.mehbur.mehburity.entity.MehburityEntity;
import com.mehbur.mehburity.entity.MehburitySounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * The rage form's hunt. While the demon grows it sinks to the ground; then it walks after the culprit, straight
 * through blocks, with its feet on the surface (or down at the culprit's level when they hide underground), and tears
 * at them with its claws once they are in reach. Nothing is broken on the way: the body just passes through.
 */
public final class MonsterGoal extends Goal {
	/** Ticks between two footsteps. */
	private static final int STEP_INTERVAL = 12;
	/** The feet move at most this far up or down per tick. */
	private static final double MAX_CLIMB = 0.8;
	/** The culprit counts as hiding underground when this far below the surface. */
	private static final double UNDERGROUND = 4.0;
	/** Stops walking at this share of the claw reach. */
	private static final double STOP_FRACTION = 0.6;
	/** First swipe after reaching the culprit. */
	private static final int FIRST_SWIPE_DELAY = 10;

	private final MehburityEntity mehburity;
	private int cooldown;
	private int stepTimer;

	public MonsterGoal(MehburityEntity mehburity) {
		this.mehburity = mehburity;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return mehburity.isMonster() && mehburity.getRageCulprit() != null;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		cooldown = FIRST_SWIPE_DELAY;
		stepTimer = 0;
		mehburity.getNavigation().stop();
	}

	@Override
	public void tick() {
		LivingEntity culprit = mehburity.getRageCulprit();
		if (culprit == null || !(mehburity.level() instanceof ServerLevel level)) {
			return;
		}
		MehburityConfig.Monster config = MehburityConfig.get().monster;
		mehburity.setDeltaMovement(Vec3.ZERO);
		mehburity.getLookControl().setLookAt(culprit.getX(), culprit.getEyeY(), culprit.getZ(), 10.0F, 30.0F);

		double dx = culprit.getX() - mehburity.getX();
		double dz = culprit.getZ() - mehburity.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		if (distance > 0.01) {
			float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
			mehburity.setYRot(yaw);
			mehburity.setYBodyRot(yaw);
		}

		if (mehburity.isTransforming()) {
			// Grows where it stands and sinks onto the ground.
			mehburity.setPos(mehburity.getX(), towards(mehburity.getY(), feetY(level, mehburity.getX(), mehburity.getZ(), culprit)),
					mehburity.getZ());
			if (mehburity.tickCount % 4 == 0) {
				float grown = mehburity.getTransformProgress(0.0F) * mehburity.getMonsterHeight();
				level.sendParticles(ParticleTypes.LARGE_SMOKE, mehburity.getX(), mehburity.getY() + grown * 0.5, mehburity.getZ(),
						8, 0.6, grown * 0.3 + 0.2, 0.6, 0.02);
			}
			return;
		}

		double x = mehburity.getX();
		double z = mehburity.getZ();
		double stopAt = config.reach * STOP_FRACTION;
		if (distance > stopAt) {
			double step = Math.min(config.blocksPerSecond / 20.0, distance - stopAt);
			x += dx / distance * step;
			z += dz / distance * step;
			if (step > 0.01 && ++stepTimer >= STEP_INTERVAL) {
				stepTimer = 0;
				mehburity.playSound(MehburitySounds.MONSTER_STEP, 2.5F, 0.5F + mehburity.getRandom().nextFloat() * 0.1F);
				level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, mehburity.getY() + 0.1, z, 2, 0.4, 0.0, 0.4, 0.01);
			}
		}
		mehburity.setPos(x, towards(mehburity.getY(), feetY(level, x, z, culprit)), z);

		if (cooldown > 0) {
			cooldown--;
		} else if (inReach(culprit, distance, config)) {
			swipe(level, culprit, config);
			cooldown = config.attackCooldownTicks;
		}
	}

	/** On the surface, unless the culprit hides underground: then it follows them down through the rock. */
	private static double feetY(ServerLevel level, double x, double z, LivingEntity culprit) {
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
		return culprit.getY() < surface - UNDERGROUND ? culprit.getY() : surface;
	}

	private static double towards(double y, double target) {
		return y + Mth.clamp(target - y, -MAX_CLIMB, MAX_CLIMB);
	}

	/** Close on the ground, and anywhere from its feet up to its head (a pillar is no escape). */
	private boolean inReach(LivingEntity culprit, double distance, MehburityConfig.Monster config) {
		double bottom = mehburity.getY() - 2.0;
		double top = mehburity.getY() + mehburity.getMonsterHeight();
		return distance <= config.reach && culprit.getY() >= bottom && culprit.getY() <= top;
	}

	private void swipe(ServerLevel level, LivingEntity culprit, MehburityConfig.Monster config) {
		level.broadcastEntityEvent(mehburity, MehburityEntity.EVENT_CLAW_SWIPE);
		mehburity.playSound(MehburitySounds.MONSTER_SWIPE, 2.0F, 0.7F);
		ServerPlayer owner = mehburity.getOwnerPlayer();
		if (owner != null && MehburityConfig.get().combat.ownerGetsDrops && !(culprit instanceof Player)) {
			culprit.setLastHurtByPlayer(owner, 100);
		}
		if (culprit.hurtServer(level, MehburityDamage.claw(level, mehburity), (float) config.damage) && config.knockback > 0.0) {
			Vec3 away = new Vec3(culprit.getX() - mehburity.getX(), 0.0, culprit.getZ() - mehburity.getZ());
			away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize();
			culprit.push(away.x * config.knockback, 0.35 * config.knockback, away.z * config.knockback);
			culprit.hurtMarked = true; // sends the new velocity to a player's client
		}
		Vec3 hit = culprit.position().add(0.0, culprit.getBbHeight() * 0.5, 0.0);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, hit.x, hit.y, hit.z, 3, 0.4, 0.3, 0.4, 0.0);
		level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, hit.x, hit.y, hit.z, 6, 0.3, 0.3, 0.3, 0.1);
	}
}
