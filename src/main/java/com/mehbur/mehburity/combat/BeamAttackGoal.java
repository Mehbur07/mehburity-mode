package com.mehbur.mehburity.combat;

import com.mehbur.mehburity.ai.GameEvents;
import com.mehbur.mehburity.config.MehburityConfig;
import com.mehbur.mehburity.entity.CompanionMode;
import com.mehbur.mehburity.entity.MehburityBoxEntity;
import com.mehbur.mehburity.entity.MehburityEntity;
import com.mehbur.mehburity.entity.MehburitySounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Fights the current target with a neon beam: flies into range (unless it is guarding a spot in STAY mode),
 * charges while the target stays in sight, then hits. The beam is drawn by the client from the synced beam target.
 */
public final class BeamAttackGoal extends Goal {
	/** Flies closer when the target is beyond this share of the beam range. */
	private static final double APPROACH_FRACTION = 0.75;
	/** Hovers this far above the target's head while closing in, so it sees over small obstacles. */
	private static final double HOVER_ABOVE = 1.5;
	private static final int REPATH_INTERVAL = 10;
	/** Remembered for the owner's recent events (Gemini context). */
	private static final String KILL_EVENT = "Mehburity zapped and killed %s (%s)";

	private final MehburityEntity mehburity;
	private final DefendOwnerTargetGoal targeting;
	private int charge;
	private int cooldown;
	private int flash;
	private int repathDelay;

	public BeamAttackGoal(MehburityEntity mehburity, DefendOwnerTargetGoal targeting) {
		this.mehburity = mehburity;
		this.targeting = targeting;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = mehburity.getTarget();
		return target != null && target.isAlive() && !mehburity.isEmerging();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		charge = 0;
		repathDelay = 0;
	}

	@Override
	public void stop() {
		charge = 0;
		flash = 0;
		mehburity.setBeamTarget(null);
		mehburity.getNavigation().stop();
	}

	@Override
	public void tick() {
		LivingEntity target = mehburity.getTarget();
		if (target == null) {
			return;
		}
		MehburityConfig.Combat config = MehburityConfig.get().combat;
		mehburity.getLookControl().setLookAt(target, 30.0F, 30.0F);
		double distance = mehburity.distanceTo(target);
		boolean sees = mehburity.getSensing().hasLineOfSight(target);

		if (mehburity.getMode() == CompanionMode.FOLLOW) {
			approach(target, distance, sees, config);
		}
		if (cooldown > 0) {
			cooldown--;
		}
		if (flash > 0 && --flash == 0 && charge == 0) {
			mehburity.setBeamTarget(null);
		}

		if (!sees || distance > config.beamRange) {
			if (charge > 0) {
				charge = 0;
				mehburity.setBeamTarget(null);
			}
			return;
		}
		if (cooldown > 0) {
			return;
		}
		if (charge == 0) {
			mehburity.setBeamTarget(target);
			mehburity.playSound(MehburitySounds.BEAM_CHARGE, 0.6F, 1.6F);
		}
		if (++charge >= config.chargeTicks) {
			fire(target, config);
			charge = 0;
			cooldown = config.cooldownTicks;
			flash = MehburityEntity.BEAM_FLASH_TICKS; // keeps the beam visible while it flashes
		}
	}

	private void approach(LivingEntity target, double distance, boolean sees, MehburityConfig.Combat config) {
		if (distance <= config.beamRange * APPROACH_FRACTION && sees) {
			mehburity.getNavigation().stop();
			return;
		}
		if (--repathDelay > 0) {
			return;
		}
		repathDelay = adjustedTickDelay(REPATH_INTERVAL);
		mehburity.getNavigation().moveTo(target.getX(), target.getY() + target.getBbHeight() + HOVER_ABOVE, target.getZ(),
				MehburityConfig.get().companion.followSpeed);
	}

	private void fire(LivingEntity target, MehburityConfig.Combat config) {
		ServerLevel level = (ServerLevel) mehburity.level();
		ServerPlayer owner = mehburity.getOwnerPlayer();
		if (owner != null && config.ownerGetsDrops && !(target instanceof Player)) {
			// Counts as the owner's kill: experience and player-only loot drop.
			target.setLastHurtByPlayer(owner, 100);
		}
		target.hurtServer(level, MehburityDamage.beam(level, mehburity), (float) config.damage);
		level.broadcastEntityEvent(mehburity, MehburityEntity.EVENT_BEAM_FIRED);
		mehburity.playSound(MehburitySounds.BEAM_FIRE, 1.0F, 1.2F + mehburity.getRandom().nextFloat() * 0.2F);

		Vec3 hit = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
		level.sendParticles(new DustParticleOptions(MehburityBoxEntity.NEON_CYAN, 1.2F), hit.x, hit.y, hit.z,
				12, 0.25, 0.25, 0.25, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, hit.x, hit.y, hit.z, 5, 0.1, 0.1, 0.1, 0.06);

		if (!target.isAlive() && owner != null) {
			CombatTargets.Reason reason = targeting.reason();
			String why = switch (reason == null ? CombatTargets.Reason.GUARDING : reason) {
				case DEFENDING -> "it had attacked the player";
				case HELPING -> "the player was fighting it";
				case GUARDING -> "it was a threat";
			};
			GameEvents.record(owner, KILL_EVENT.formatted(target.getType().getDescription().getString(), why));
		}
	}
}
