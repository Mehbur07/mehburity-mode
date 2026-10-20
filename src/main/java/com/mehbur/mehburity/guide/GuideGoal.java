package com.mehbur.mehburity.guide;

import com.mehbur.mehburity.config.MehburityConfig;
import com.mehbur.mehburity.entity.MehburityBoxEntity;
import com.mehbur.mehburity.entity.MehburityEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Works through Mehburity's {@link GuideJob}: plans a route a player can walk ({@link RoutePlanner}, a segment at a
 * time, the next one worked out before the current one runs out), rolls along exactly that route, and shows the way
 * ahead to the owner as a dotted neon line on the ground. It stops and turns round when the owner falls behind
 * (calling them now and then), never cuts through blocks, and gives up only when there is no way on foot. Done once the
 * owner is there. Fighting (a higher priority goal) pauses it.
 */
public final class GuideGoal extends Goal {
	/** Waits when the owner is farther than this (it is pulled back to them at 20). */
	static final double WAIT_DISTANCE = 12.0;
	/** Goes on again once the owner is this close. */
	private static final double RESUME_DISTANCE = 7.0;
	/** Rolls at most this many steps of the route in one go. */
	private static final int LEAD_STEPS = 16;
	/** Plans the next segment when fewer steps than this are left. */
	private static final int PLAN_AHEAD_STEPS = 24;
	private static final int REPATH_INTERVAL = 20;
	/** Calls the owner after waiting this long, then again every {@link #CALL_INTERVAL}. */
	private static final int FIRST_CALL = 40;
	private static final int CALL_INTERVAL = 300;
	/** Replans after moving less than a block in this many ticks. */
	private static final int STUCK_TICKS = 60;
	/** Gives up after this many segments in a row found no way on. */
	private static final int MAX_FAILED_PLANS = 3;
	/** The route shown on the ground: this many steps ahead of Mehburity, every this many ticks. */
	private static final int SHOWN_STEPS = 32;
	private static final int SHOW_INTERVAL = 15;
	private static final DustParticleOptions ROUTE_DOT = new DustParticleOptions(MehburityBoxEntity.NEON_CYAN, 1.6F);

	private final MehburityEntity mehburity;
	private @Nullable GuideJob routeFor;
	private final List<BlockPos> route = new ArrayList<>();
	private boolean routeComplete;
	/** Index of the route step Mehburity is at. */
	private int index;
	private @Nullable RoutePlanner planner;
	/** The planner works on a fresh route (true) or the segment after the current one (false). */
	private boolean replacing;
	private int failedPlans;
	private boolean waiting;
	private int repathDelay;
	private int stuckTimer;
	private Vec3 stuckCheck = Vec3.ZERO;

	public GuideGoal(MehburityEntity mehburity) {
		this.mehburity = mehburity;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return mehburity.getGuideJob() != null && !mehburity.isEmerging() && !mehburity.isRaging() && !mehburity.isMonster()
				&& mehburity.getOwnerPlayer() != null;
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
	public void start() {
		waiting = false;
		repathDelay = 0;
		stuckTimer = 0;
		stuckCheck = mehburity.position();
	}

	@Override
	public void stop() {
		mehburity.getNavigation().stop();
	}

	@Override
	public void tick() {
		GuideJob job = mehburity.getGuideJob();
		ServerPlayer owner = mehburity.getOwnerPlayer();
		if (job == null || owner == null) {
			return;
		}
		if (job != routeFor) {
			resetRoute(job);
		}
		if (owner.level().dimension() != job.dimension()) {
			Guiding.lost(mehburity, owner);
			return;
		}
		if (Guiding.horizontalDistance(owner, job.target()) < Guiding.ARRIVE_DISTANCE) {
			Guiding.arrive(mehburity, owner, job);
			return;
		}
		ServerLevel level = (ServerLevel) mehburity.level();
		if (!plan(level, job, owner)) {
			return;
		}
		if (route.isEmpty()) {
			return; // the first segment is still being worked out
		}
		index = nearestStep();
		if (mehburity.tickCount % SHOW_INTERVAL == 0) {
			showRoute(level, owner);
		}

		double gap = mehburity.distanceTo(owner);
		if (gap > WAIT_DISTANCE || (waiting && gap > RESUME_DISTANCE)) {
			if (!waiting) {
				waiting = true;
				mehburity.getNavigation().stop();
			}
			job.waitTicks++;
			mehburity.getLookControl().setLookAt(owner, 30.0F, 30.0F);
			if (job.waitTicks == FIRST_CALL || (job.waitTicks > FIRST_CALL && (job.waitTicks - FIRST_CALL) % CALL_INTERVAL == 0)) {
				Guiding.callOwner(owner);
			}
			return;
		}
		if (waiting) {
			waiting = false;
			job.waitTicks = 0;
			repathDelay = 0;
			stuckTimer = 0;
			stuckCheck = mehburity.position();
		}

		if (index >= route.size() - 1) {
			mehburity.getNavigation().stop();
			BlockPos target = job.target();
			mehburity.getLookControl().setLookAt(target.getX() + 0.5, mehburity.getEyeY(), target.getZ() + 0.5);
			return; // at the end of what is planned: waits for the owner, or for the next segment
		}
		if (++stuckTimer >= STUCK_TICKS) {
			stuckTimer = 0;
			if (mehburity.position().distanceToSqr(stuckCheck) < 1.0) {
				// Its own body got caught on the way (the way itself is fine for a player): plan again from here.
				startPlanning(level, job, true);
			}
			stuckCheck = mehburity.position();
		}
		if (--repathDelay <= 0 || mehburity.getNavigation().isDone()) {
			repathDelay = adjustedTickDelay(REPATH_INTERVAL);
			mehburity.getNavigation().moveTo(path(), MehburityConfig.get().companion.followSpeed);
		}
	}

	private void resetRoute(GuideJob job) {
		routeFor = job;
		route.clear();
		routeComplete = false;
		index = 0;
		planner = null;
		failedPlans = 0;
	}

	/**
	 * Keeps the route long enough: a fresh one when there is none, the next segment when the current one runs out.
	 * False when there is no way on at all (the guiding ended).
	 */
	private boolean plan(ServerLevel level, GuideJob job, ServerPlayer owner) {
		if (planner == null) {
			if (route.isEmpty()) {
				startPlanning(level, job, true);
			} else if (!routeComplete && route.size() - index < PLAN_AHEAD_STEPS) {
				startPlanning(level, job, false);
			}
		}
		if (planner == null || !planner.step()) {
			return true;
		}
		RoutePlanner.Route found = planner.result();
		planner = null;
		if (found == null) {
			if (++failedPlans >= MAX_FAILED_PLANS || route.isEmpty()) {
				Guiding.noWay(mehburity, owner);
				return false;
			}
			startPlanning(level, job, true); // try again from where it is now
			return true;
		}
		failedPlans = 0;
		if (replacing) {
			route.clear();
			route.addAll(found.steps());
			index = 0;
		} else {
			route.addAll(found.steps().subList(1, found.steps().size()));
		}
		routeComplete = found.complete();
		repathDelay = 0;
		return true;
	}

	private void startPlanning(ServerLevel level, GuideJob job, boolean fresh) {
		BlockPos from = fresh || route.isEmpty() ? RoutePlanner.walkableNear(level, mehburity.blockPosition()) : route.getLast();
		if (from == null) {
			ServerPlayer owner = mehburity.getOwnerPlayer();
			from = owner == null ? null : RoutePlanner.walkableNear(level, owner.blockPosition());
		}
		if (from == null) {
			return;
		}
		replacing = fresh || route.isEmpty();
		planner = new RoutePlanner(level, from, job.target());
	}

	/**
	 * The route step closest to Mehburity, looking a little behind and ahead of the last one; the whole route when it
	 * is not near any of those (it was pulled back to its owner).
	 */
	private int nearestStep() {
		int near = nearestStep(Math.max(0, index - 4), Math.min(route.size() - 1, index + 24));
		BlockPos step = route.get(near);
		boolean onRoute = mehburity.distanceToSqr(step.getX() + 0.5, step.getY(), step.getZ() + 0.5) < 16.0;
		return onRoute ? near : nearestStep(0, route.size() - 1);
	}

	private int nearestStep(int from, int to) {
		int bestIndex = from;
		double bestDistance = Double.MAX_VALUE;
		for (int i = from; i <= to; i++) {
			BlockPos step = route.get(i);
			double distance = mehburity.distanceToSqr(step.getX() + 0.5, step.getY(), step.getZ() + 0.5);
			if (distance < bestDistance) {
				bestDistance = distance;
				bestIndex = i;
			}
		}
		return bestIndex;
	}

	/** The next stretch of the route as a vanilla path, so the navigation follows it step by step. */
	private Path path() {
		int end = Math.min(route.size() - 1, index + LEAD_STEPS);
		List<Node> nodes = new ArrayList<>();
		for (int i = index + 1; i <= end; i++) {
			BlockPos step = route.get(i);
			Node node = new Node(step.getX(), step.getY(), step.getZ());
			node.type = PathType.WALKABLE;
			nodes.add(node);
		}
		return new Path(nodes, route.get(end), end == route.size() - 1 && routeComplete);
	}

	/**
	 * Dots on the ground along the way, like a route on a map: from where the owner is, past Mehburity and on ahead.
	 * Only the owner sees them.
	 */
	private void showRoute(ServerLevel level, ServerPlayer owner) {
		int from = index;
		double closest = Double.MAX_VALUE;
		for (int i = Math.max(0, index - SHOWN_STEPS); i <= index; i++) {
			BlockPos step = route.get(i);
			double distance = owner.distanceToSqr(step.getX() + 0.5, step.getY(), step.getZ() + 0.5);
			if (distance < closest) {
				closest = distance;
				from = i;
			}
		}
		int end = Math.min(route.size() - 1, index + SHOWN_STEPS);
		for (int i = from; i <= end; i++) {
			BlockPos step = route.get(i);
			level.sendParticles(owner, ROUTE_DOT, true, false, step.getX() + 0.5, step.getY() + 0.15, step.getZ() + 0.5,
					1, 0.0, 0.0, 0.0, 0.0);
		}
	}
}
