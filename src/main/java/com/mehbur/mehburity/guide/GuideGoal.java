package com.mehbur.mehburity.guide;

import com.mehbur.mehburity.entity.MehburityEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Works through Mehburity's {@link GuideJob}: rolls ahead towards the structure a short stretch at a time, stops and
 * turns round when the owner falls behind (and calls them now and then), goes round what blocks the way, and hops
 * ahead if nothing works. Done once the owner is there. Fighting (a higher priority goal) pauses it.
 */
public final class GuideGoal extends Goal {
	/** Rolls this far ahead in one go. */
	private static final double LEG = 10.0;
	/** Waits when the owner is farther than this (it is pulled back to them at 20). */
	static final double WAIT_DISTANCE = 12.0;
	/** Goes on again once the owner is this close. */
	private static final double RESUME_DISTANCE = 7.0;
	private static final double SPEED = 1.25;
	private static final int REPATH_INTERVAL = 20;
	/** Calls the owner after waiting this long, then again every {@link #CALL_INTERVAL}. */
	private static final int FIRST_CALL = 40;
	private static final int CALL_INTERVAL = 300;
	/** Looks for another way after moving less than a block in this many ticks. */
	private static final int STUCK_TICKS = 40;
	/** Ways round an obstacle, in degrees off the straight line; after the last one it hops ahead. */
	private static final float[] DETOURS = {0.0F, 35.0F, -35.0F, 70.0F, -70.0F};

	private final MehburityEntity mehburity;
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
		if (owner.level().dimension() != job.dimension()) {
			Guiding.lost(mehburity, owner);
			return;
		}
		if (Guiding.horizontalDistance(owner, job.target()) < Guiding.ARRIVE_DISTANCE) {
			Guiding.arrive(mehburity, owner, job);
			return;
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

		BlockPos target = job.target();
		double toTarget = Guiding.horizontalDistance(mehburity, target);
		mehburity.getLookControl().setLookAt(target.getX() + 0.5, mehburity.getEyeY(), target.getZ() + 0.5);
		if (toTarget < 4.0) {
			mehburity.getNavigation().stop();
			return; // there: waits on the spot for the owner
		}
		if (++stuckTimer >= STUCK_TICKS) {
			stuckTimer = 0;
			boolean moved = mehburity.position().distanceToSqr(stuckCheck) > 1.0;
			stuckCheck = mehburity.position();
			if (!moved) {
				job.detour++;
				repathDelay = 0;
				if (job.detour >= DETOURS.length) {
					job.detour = 0;
					hopAhead(target, toTarget);
					return;
				}
			} else if (job.detour > 0 && mehburity.getNavigation().isDone()) {
				job.detour = 0; // round it: straight on again
			}
		}
		if (--repathDelay <= 0 || mehburity.getNavigation().isDone()) {
			repathDelay = adjustedTickDelay(REPATH_INTERVAL);
			Vec3 step = waypoint(target, Math.min(LEG, toTarget), DETOURS[job.detour]);
			mehburity.getNavigation().moveTo(step.x, step.y, step.z, SPEED);
		}
	}

	/** {@code distance} towards the target, turned {@code degrees} off the straight line, on the ground there. */
	private Vec3 waypoint(BlockPos target, double distance, float degrees) {
		double dx = target.getX() + 0.5 - mehburity.getX();
		double dz = target.getZ() + 0.5 - mehburity.getZ();
		double length = Math.max(1.0E-4, Math.sqrt(dx * dx + dz * dz));
		double angle = Math.toRadians(degrees);
		double x = (dx * Math.cos(angle) - dz * Math.sin(angle)) / length;
		double z = (dx * Math.sin(angle) + dz * Math.cos(angle)) / length;
		double wx = mehburity.getX() + x * distance;
		double wz = mehburity.getZ() + z * distance;
		BlockPos column = BlockPos.containing(wx, mehburity.getY(), wz);
		ServerLevel level = (ServerLevel) mehburity.level();
		int ground = level.isLoaded(column)
				? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ())
				: column.getY();
		return new Vec3(wx, ground, wz);
	}

	/** Nothing gets it past: a puff, and it is a stretch further on (still within the owner's reach). */
	private void hopAhead(BlockPos target, double toTarget) {
		ServerLevel level = (ServerLevel) mehburity.level();
		Vec3 to = waypoint(target, Math.min(6.0, toTarget), 0.0F);
		if (!level.isLoaded(BlockPos.containing(to))) {
			return;
		}
		level.sendParticles(ParticleTypes.END_ROD, mehburity.getX(), mehburity.getY() + 0.4, mehburity.getZ(), 10, 0.2, 0.3, 0.2, 0.05);
		mehburity.getNavigation().stop();
		mehburity.teleportTo(to.x, to.y, to.z);
		level.sendParticles(ParticleTypes.END_ROD, to.x, to.y + 0.4, to.z, 10, 0.2, 0.3, 0.2, 0.05);
		stuckCheck = mehburity.position();
	}
}
