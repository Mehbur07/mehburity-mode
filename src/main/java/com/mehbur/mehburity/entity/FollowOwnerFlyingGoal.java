package com.mehbur.mehburity.entity;

import com.mehbur.mehburity.config.MehburityConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/** Flies after the owner while in {@link CompanionMode#FOLLOW}, teleporting when left too far behind. */
final class FollowOwnerFlyingGoal extends Goal {
	private final MehburityEntity mehburity;
	private @Nullable ServerPlayer owner;
	private int timeToRecalcPath;

	FollowOwnerFlyingGoal(MehburityEntity mehburity) {
		this.mehburity = mehburity;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (mehburity.getMode() != CompanionMode.FOLLOW || mehburity.isEmerging()) {
			return false;
		}
		ServerPlayer candidate = mehburity.getOwnerPlayer();
		if (candidate == null || candidate.isSpectator()) {
			return false;
		}
		double start = MehburityConfig.get().companion.followStartDistance;
		if (!mehburity.isTooFarFrom(candidate) && mehburity.distanceToSqr(candidate) < start * start) {
			return false;
		}
		owner = candidate;
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		if (owner == null || owner.isRemoved() || mehburity.getMode() != CompanionMode.FOLLOW) {
			return false;
		}
		double stop = MehburityConfig.get().companion.followStopDistance;
		return mehburity.distanceToSqr(owner) > stop * stop;
	}

	@Override
	public void start() {
		timeToRecalcPath = 0;
	}

	@Override
	public void stop() {
		owner = null;
		mehburity.getNavigation().stop();
	}

	@Override
	public void tick() {
		if (owner == null) {
			return;
		}
		mehburity.getLookControl().setLookAt(owner, 10.0F, mehburity.getMaxHeadXRot());
		if (--timeToRecalcPath > 0) {
			return;
		}
		timeToRecalcPath = adjustedTickDelay(10);
		// Different dimensions are handled by CompanionManager, which can move entities between levels.
		if (owner.level() == mehburity.level() && mehburity.isTooFarFrom(owner)) {
			mehburity.teleportToOwner(owner);
		} else {
			mehburity.getNavigation().moveTo(owner.getX(), owner.getY() + 1.2, owner.getZ(),
					MehburityConfig.get().companion.followSpeed);
		}
	}
}
