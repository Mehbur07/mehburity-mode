package com.mehbur.mehburity.combat;

import com.mehbur.mehburity.config.MehburityConfig;
import com.mehbur.mehburity.entity.CompanionManager;
import com.mehbur.mehburity.entity.CompanionMode;
import com.mehbur.mehburity.entity.MehburityEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;

/**
 * Who Mehburity may fight and whom it picks. It never hurts its owner, the owner's pets, villagers or other
 * Mehburitys, and players only when the server allows it ({@code combat.fightPlayers}).
 */
public final class CombatTargets {
	/** What the owner did (got hit, hit something) counts for this long, in ticks. */
	static final int OWNER_MEMORY_TICKS = 100;
	/** Allowed beyond the beam range before a guard post (STAY) lets a target go. */
	private static final double STAY_SLACK = 2.0;

	/** Why a target was picked; earlier constants win over later ones. */
	public enum Reason { DEFENDING, HELPING, GUARDING }

	public record Choice(LivingEntity target, Reason reason) {
	}

	private CombatTargets() {
	}

	/** Combat is switched on for the server and for this owner, and the owner is around to be protected. */
	public static boolean isActive(MehburityEntity mehburity, @Nullable ServerPlayer owner) {
		return owner != null
				&& !owner.isSpectator()
				&& !mehburity.isEmerging()
				&& MehburityConfig.get().combat.enabled
				&& CompanionManager.isCombatEnabled(owner.level().getServer(), owner.getUUID());
	}

	/** True for anything Mehburity is ever allowed to hit, whatever the reason. */
	public static boolean isAllowed(MehburityEntity mehburity, ServerPlayer owner, @Nullable LivingEntity target) {
		if (target == null || !target.isAlive() || target.isRemoved() || target.level() != mehburity.level()) {
			return false;
		}
		if (target == owner || target instanceof MehburityEntity || target instanceof ArmorStand || target instanceof Npc) {
			return false;
		}
		if (target instanceof OwnableEntity pet && isOwnedBy(pet, owner)) {
			return false;
		}
		if (target instanceof Player player
				&& (!MehburityConfig.get().combat.fightPlayers || player.isCreative() || player.isSpectator())) {
			return false;
		}
		return mehburity.canAttack(target);
	}

	private static boolean isOwnedBy(OwnableEntity pet, ServerPlayer owner) {
		EntityReference<LivingEntity> reference = pet.getOwnerReference();
		return reference != null && owner.getUUID().equals(reference.getUUID());
	}

	/** A monster that is a threat right now: hostile, and not a neutral mob that has been left alone. */
	static boolean isHostile(LivingEntity entity) {
		if (!(entity instanceof Enemy)) {
			return false;
		}
		return !(entity instanceof NeutralMob neutral) || neutral.isAngry();
	}

	private static boolean recent(ServerPlayer owner, int timestamp) {
		return owner.tickCount - timestamp <= OWNER_MEMORY_TICKS;
	}

	/** Whether {@code target} is still worth fighting: close to the owner, or within reach of a guard post. */
	public static boolean isInReach(MehburityEntity mehburity, ServerPlayer owner, LivingEntity target) {
		MehburityConfig.Combat config = MehburityConfig.get().combat;
		if (mehburity.getMode() == CompanionMode.STAY) {
			double reach = config.beamRange + STAY_SLACK;
			return mehburity.distanceToSqr(target) <= reach * reach;
		}
		return target.level() == owner.level() && target.distanceToSqr(owner) <= config.chaseRadius * config.chaseRadius;
	}

	/**
	 * The best target right now, in this order: whoever just hurt the owner, whatever the owner just hit,
	 * then the closest visible hostile mob that is after the owner (any hostile near a guard post in STAY mode).
	 */
	public static @Nullable Choice pick(MehburityEntity mehburity, ServerPlayer owner) {
		MehburityConfig.Combat config = MehburityConfig.get().combat;

		LivingEntity attacker = owner.getLastHurtByMob();
		if (config.defendOwner && valid(mehburity, owner, attacker) && recent(owner, owner.getLastHurtByMobTimestamp())) {
			return new Choice(attacker, Reason.DEFENDING);
		}
		LivingEntity victim = owner.getLastHurtMob();
		if (config.helpOwnerAttack && valid(mehburity, owner, victim) && recent(owner, owner.getLastHurtMobTimestamp())) {
			return new Choice(victim, Reason.HELPING);
		}

		boolean guardPost = mehburity.getMode() == CompanionMode.STAY;
		if (!guardPost && !config.guardAgainstHostiles && !config.attackAllHostiles) {
			return null;
		}
		boolean anyHostile = guardPost || config.attackAllHostiles;
		LivingEntity center = guardPost ? mehburity : owner;
		double radius = guardPost ? config.beamRange : config.guardRadius;
		AABB area = center.getBoundingBox().inflate(radius);
		ServerLevel level = (ServerLevel) mehburity.level();
		return level.getEntitiesOfClass(Mob.class, area, mob -> isHostile(mob)
						&& (anyHostile || mob.getTarget() == owner)
						&& mob.distanceToSqr(center) <= radius * radius
						&& valid(mehburity, owner, mob)
						&& mehburity.getSensing().hasLineOfSight(mob))
				.stream()
				.min(Comparator.comparingDouble(mob -> mob.distanceToSqr(center)))
				.map(mob -> new Choice(mob, Reason.GUARDING))
				.orElse(null);
	}

	private static boolean valid(MehburityEntity mehburity, ServerPlayer owner, @Nullable LivingEntity target) {
		return isAllowed(mehburity, owner, target) && isInReach(mehburity, owner, target);
	}
}
