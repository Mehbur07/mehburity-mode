package com.mehbur.mehburity.build;

import com.mehbur.mehburity.config.MehburityConfig;
import com.mehbur.mehburity.entity.MehburityBoxEntity;
import com.mehbur.mehburity.entity.MehburityEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Builds Mehburity's {@link BuildJob}: hovers over the part being built and places blocks at
 * {@code build.blocksPerSecond}, with particles and the block's own sound. Fights (a higher priority goal) pause it.
 */
public final class BuildGoal extends Goal {
	private static final int REPATH_INTERVAL = 20;
	/** Flies (well, blinks) straight to the work when navigation could not get there in this many ticks. */
	private static final int BLINK_AFTER_TICKS = 60;
	/** Upper bound on steps looked at per tick, so long runs of already-empty air do not stall the server. */
	private static final int MAX_CHECKS_PER_TICK = 2000;
	private static final double HOVER_ABOVE = 2.5;

	private final MehburityEntity mehburity;
	private int repathDelay;
	private int outOfReachTicks;
	private int placedThisGoal;

	public BuildGoal(MehburityEntity mehburity) {
		this.mehburity = mehburity;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return mehburity.getBuildJob() != null && !mehburity.isEmerging() && !mehburity.isRaging()
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
		repathDelay = 0;
		outOfReachTicks = 0;
		BuildJob job = mehburity.getBuildJob();
		if (job != null) {
			job.resetBudget();
		}
	}

	@Override
	public void stop() {
		mehburity.getNavigation().stop();
	}

	@Override
	public void tick() {
		BuildJob job = mehburity.getBuildJob();
		ServerPlayer owner = mehburity.getOwnerPlayer();
		if (job == null || owner == null) {
			return;
		}
		ServerLevel level = (ServerLevel) mehburity.level();
		MehburityConfig.Build config = MehburityConfig.get().build;
		job.addBudget(config.blocksPerSecond / 20.0);

		int checks = 0;
		while (!job.isDone() && checks++ < MAX_CHECKS_PER_TICK) {
			HousePlan.Step step = job.current();
			if (!needsChange(level, step)) {
				job.advance(); // already right (most of the clearing is air): free
				continue;
			}
			if (!inReach(step.pos(), config.reach)) {
				approach(step.pos());
				return;
			}
			outOfReachTicks = 0;
			if (!job.spend()) {
				break;
			}
			place(level, owner, job, step);
			job.advance();
		}
		if (job.isDone()) {
			Building.finish(mehburity, owner, Building.End.DONE);
			return;
		}
		BlockPos pos = job.current().pos();
		mehburity.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
	}

	private static boolean needsChange(ServerLevel level, HousePlan.Step step) {
		BlockState current = level.getBlockState(step.pos());
		if (step.state().isAir()) {
			return !current.isAir();
		}
		return current != step.state();
	}

	private boolean inReach(BlockPos pos, double reach) {
		return mehburity.distanceToSqr(Vec3.atCenterOf(pos)) <= reach * reach;
	}

	private void approach(BlockPos pos) {
		Vec3 hover = Vec3.atCenterOf(pos).add(0.0, HOVER_ABOVE, 0.0);
		if (++outOfReachTicks > adjustedTickDelay(BLINK_AFTER_TICKS)) {
			// Stuck behind its own walls: blink over, it is a magic sphere after all.
			ServerLevel level = (ServerLevel) mehburity.level();
			level.sendParticles(ParticleTypes.PORTAL, mehburity.getX(), mehburity.getY() + 0.4, mehburity.getZ(), 20, 0.3, 0.3, 0.3, 0.2);
			mehburity.teleportTo(hover.x, hover.y, hover.z);
			mehburity.getNavigation().stop();
			outOfReachTicks = 0;
			return;
		}
		if (--repathDelay <= 0) {
			repathDelay = adjustedTickDelay(REPATH_INTERVAL);
			mehburity.getNavigation().moveTo(hover.x, hover.y, hover.z, MehburityConfig.get().companion.followSpeed);
		}
	}

	private void place(ServerLevel level, ServerPlayer owner, BuildJob job, HousePlan.Step step) {
		BlockPos pos = step.pos();
		BlockState current = level.getBlockState(pos);
		if (!Building.mayChange(level, owner, pos, current)) {
			return; // became protected, or someone put a chest there meanwhile: leave it alone
		}
		BlockState state = step.raw() ? step.state() : Block.updateFromNeighbourShapes(step.state(), level, pos);
		job.remember(pos, current);
		level.setBlock(pos, state, Block.UPDATE_ALL);
		if (state.isAir()) {
			return;
		}
		if (placedThisGoal++ % 2 == 0) {
			level.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.6F, 1.0F);
		}
		Vec3 center = Vec3.atCenterOf(pos);
		level.sendParticles(new DustParticleOptions(MehburityBoxEntity.NEON_CYAN, 0.9F), center.x, center.y, center.z,
				3, 0.3, 0.3, 0.3, 0.0);
	}
}
