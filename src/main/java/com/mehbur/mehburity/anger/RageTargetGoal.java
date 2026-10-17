package com.mehbur.mehburity.anger;

import com.mehbur.mehburity.entity.MehburityEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** During a rage the culprit is the only target; {@link Anger} decides when the rage ends. */
public final class RageTargetGoal extends Goal {
	private final MehburityEntity mehburity;

	public RageTargetGoal(MehburityEntity mehburity) {
		this.mehburity = mehburity;
		this.setFlags(EnumSet.of(Goal.Flag.TARGET));
	}

	@Override
	public boolean canUse() {
		return mehburity.isRaging() && mehburity.getRageCulprit() != null;
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity culprit = mehburity.getRageCulprit();
		if (culprit != null && mehburity.getTarget() != culprit) {
			// Creative players cannot be targeted; the rage then simply runs out.
			mehburity.setTarget(culprit);
		}
	}

	@Override
	public void stop() {
		mehburity.setTarget(null);
	}
}
