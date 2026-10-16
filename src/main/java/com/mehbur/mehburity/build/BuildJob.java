package com.mehbur.mehburity.build;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A house being built: the plan, how far along it is, and what every changed block was before, so the house can be
 * taken down again. Lives on the entity; the undo record is handed to {@link Building} as the work goes on.
 */
public final class BuildJob {
	private final HousePlan plan;
	/** The first state seen at each changed position, in the order they were changed. */
	private final Map<BlockPos, BlockState> before = new LinkedHashMap<>();
	private int next;
	private double budget;
	private int placed;

	BuildJob(HousePlan plan) {
		this.plan = plan;
	}

	public HousePlan plan() {
		return plan;
	}

	public int placed() {
		return placed;
	}

	/** Steps done so far out of all steps (clearing air counts too). */
	public int progress() {
		return next;
	}

	public int total() {
		return plan.steps().size();
	}

	boolean isDone() {
		return next >= plan.steps().size();
	}

	HousePlan.Step current() {
		return plan.steps().get(next);
	}

	void advance() {
		next++;
	}

	void addBudget(double blocks) {
		budget += blocks;
	}

	boolean spend() {
		if (budget < 1.0) {
			return false;
		}
		budget -= 1.0;
		return true;
	}

	void resetBudget() {
		budget = 0.0;
	}

	void remember(BlockPos pos, BlockState original) {
		before.putIfAbsent(pos.immutable(), original);
		placed++;
	}

	Map<BlockPos, BlockState> before() {
		return before;
	}
}
