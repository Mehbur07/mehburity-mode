package com.mehbur.mehburity.combat;

import com.mehbur.mehburity.entity.MehburityEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/**
 * Chooses what Mehburity fights ({@link CombatTargets#pick}) and lets go when the target dies, wanders off,
 * hides for too long, or combat gets switched off. A more urgent target (someone hurting the owner) replaces a
 * less urgent one.
 */
public final class DefendOwnerTargetGoal extends Goal {
	private static final int SEARCH_INTERVAL = 5;
	private static final int RETHINK_INTERVAL = 10;
	/** Gives up on a target it cannot see for this long. */
	private static final int UNSEEN_MEMORY_TICKS = 60;

	private final MehburityEntity mehburity;
	private CombatTargets.@Nullable Choice choice;
	private int searchDelay;
	private int rethinkDelay;
	private int unseenTicks;

	public DefendOwnerTargetGoal(MehburityEntity mehburity) {
		this.mehburity = mehburity;
		this.setFlags(EnumSet.of(Goal.Flag.TARGET));
	}

	@Override
	public boolean canUse() {
		if (--searchDelay > 0) {
			return false;
		}
		searchDelay = reducedTickDelay(SEARCH_INTERVAL);
		ServerPlayer owner = mehburity.getOwnerPlayer();
		if (!CombatTargets.isActive(mehburity, owner)) {
			return false;
		}
		choice = CombatTargets.pick(mehburity, owner);
		return choice != null;
	}

	@Override
	public void start() {
		mehburity.setTarget(choice == null ? null : choice.target());
		unseenTicks = 0;
		rethinkDelay = reducedTickDelay(RETHINK_INTERVAL);
	}

	@Override
	public boolean canContinueToUse() {
		LivingEntity target = mehburity.getTarget();
		ServerPlayer owner = mehburity.getOwnerPlayer();
		if (choice == null || target != choice.target() || !CombatTargets.isActive(mehburity, owner)
				|| !CombatTargets.isAllowed(mehburity, owner, target) || !CombatTargets.isInReach(mehburity, owner, target)) {
			return false;
		}
		if (mehburity.getSensing().hasLineOfSight(target)) {
			unseenTicks = 0;
		} else if (++unseenTicks > reducedTickDelay(UNSEEN_MEMORY_TICKS)) {
			return false;
		}
		return true;
	}

	@Override
	public void tick() {
		if (--rethinkDelay > 0 || choice == null) {
			return;
		}
		rethinkDelay = reducedTickDelay(RETHINK_INTERVAL);
		ServerPlayer owner = mehburity.getOwnerPlayer();
		if (owner == null) {
			return;
		}
		CombatTargets.Choice better = CombatTargets.pick(mehburity, owner);
		if (better != null && better.target() != choice.target() && better.reason().ordinal() < choice.reason().ordinal()) {
			choice = better;
			mehburity.setTarget(better.target());
			unseenTicks = 0;
		}
	}

	@Override
	public void stop() {
		mehburity.setTarget(null);
		choice = null;
	}

	/** Why the current target was chosen, or null when Mehburity is not fighting. */
	public CombatTargets.@Nullable Reason reason() {
		return choice == null ? null : choice.reason();
	}
}
