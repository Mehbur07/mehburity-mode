package com.mehbur.mehburity.mining;

import com.mehbur.mehburity.config.MehburityConfig;
import com.mehbur.mehburity.entity.CompanionMode;
import com.mehbur.mehburity.entity.MehburityEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * Works through Mehburity's {@link MiningJob}: picks the nearest exposed target, flies next to it, breaks it over
 * time with the vanilla crack animation, and puts the drops in the owner's {@link MiningBag}. Fighting (a higher
 * priority goal) pauses it; the job continues afterwards.
 */
public final class MineGoal extends Goal {
	private static final int SEARCH_INTERVAL = 10;
	private static final int REPATH_INTERVAL = 10;
	/** Gives up on a block it could not start breaking within this many ticks. */
	private static final int GIVE_UP_TICKS = 200;
	private static final int HIT_SOUND_INTERVAL = 4;

	private final MehburityEntity mehburity;
	private @Nullable BlockPos target;
	private int progress;
	private int breakTicks;
	private int ticksOnTarget;
	private int searchDelay;
	private int repathDelay;
	private int lastStage = -1;

	public MineGoal(MehburityEntity mehburity) {
		this.mehburity = mehburity;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return mehburity.getMiningJob() != null && !mehburity.isEmerging() && !mehburity.isRaging()
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
		searchDelay = 0;
		repathDelay = 0;
	}

	/** Paused (by a fight) or finished: forget the half-broken block, keep the job. */
	@Override
	public void stop() {
		clearTarget();
		mehburity.getNavigation().stop();
	}

	@Override
	public void tick() {
		MiningJob job = mehburity.getMiningJob();
		ServerPlayer owner = mehburity.getOwnerPlayer();
		if (job == null || owner == null) {
			return;
		}
		ServerLevel level = (ServerLevel) mehburity.level();
		if (target == null) {
			if (--searchDelay > 0) {
				return;
			}
			searchDelay = adjustedTickDelay(SEARCH_INTERVAL);
			target = nextTarget(level, owner, job);
			if (target == null) {
				Mining.finish(mehburity, owner, job.kind() == MiningJob.Kind.SINGLE ? MiningJob.End.UNREACHABLE : MiningJob.End.NOTHING_LEFT);
				return;
			}
			breakTicks = MiningRules.breakTicks(level, target, level.getBlockState(target));
		}

		BlockState state = level.getBlockState(target);
		if (!stillWanted(level, job, target, state)) {
			clearTarget();
			return;
		}
		if (++ticksOnTarget > adjustedTickDelay(GIVE_UP_TICKS) && progress == 0) {
			giveUp(job);
			return;
		}

		Vec3 center = Vec3.atCenterOf(target);
		mehburity.getLookControl().setLookAt(center.x, center.y, center.z);
		if (!canReach(level, target)) {
			resetProgress(level);
			if (--repathDelay <= 0) {
				repathDelay = adjustedTickDelay(REPATH_INTERVAL);
				BlockPos side = MiningRules.openSide(level, target, mehburity.position());
				if (side == null) {
					giveUp(job);
					return;
				}
				mehburity.getNavigation().moveTo(side.getX() + 0.5, side.getY() + 0.1, side.getZ() + 0.5,
						MehburityConfig.get().companion.followSpeed);
			}
			return;
		}

		mehburity.getNavigation().stop();
		progress++;
		int stage = Math.min(9, progress * 10 / breakTicks);
		if (stage != lastStage) {
			level.destroyBlockProgress(mehburity.getId(), target, stage);
			lastStage = stage;
		}
		if (progress % HIT_SOUND_INTERVAL == 1) {
			level.playSound(null, target, state.getSoundType().getHitSound(), SoundSource.NEUTRAL, 0.5F, 0.8F);
		}
		if (progress >= breakTicks) {
			BlockPos mined = target;
			clearTarget();
			breakBlock(level, owner, job, mined, state);
		}
	}

	/** Nearest wanted block around the owner (around Mehburity when it guards a spot in STAY mode). */
	private @Nullable BlockPos nextTarget(ServerLevel level, ServerPlayer owner, MiningJob job) {
		if (job.kind() == MiningJob.Kind.SINGLE) {
			BlockPos pos = job.single();
			return pos != null && !job.isSkipped(pos) && stillWanted(level, job, pos, level.getBlockState(pos)) ? pos : null;
		}
		MehburityConfig.Mining config = MehburityConfig.get().mining;
		Entity center = mehburity.getMode() == CompanionMode.STAY || owner.level() != level ? mehburity : owner;
		BlockPos origin = center.blockPosition();
		int r = config.searchRadius;
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -r, -r), origin.offset(r, r, r))) {
			if (pos.getY() < level.getMinY() || pos.getY() > level.getMaxY()) {
				continue;
			}
			double distance = mehburity.distanceToSqr(Vec3.atCenterOf(pos));
			if (distance >= bestDistance || job.isSkipped(pos)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (stillWanted(level, job, pos, state)) {
				best = pos.immutable();
				bestDistance = distance;
			}
		}
		return best;
	}

	private static boolean stillWanted(ServerLevel level, MiningJob job, BlockPos pos, BlockState state) {
		if (!MiningRules.isBreakable(level, pos, state)) {
			return false;
		}
		if (job.kind() == MiningJob.Kind.SINGLE) {
			return true;
		}
		return MiningRules.isTarget(state) && (!MehburityConfig.get().mining.exposedOnly || MiningRules.isExposed(level, pos));
	}

	/** Within reach and nothing in between. */
	private boolean canReach(ServerLevel level, BlockPos pos) {
		double reach = MehburityConfig.get().mining.reach;
		Vec3 eye = mehburity.getEyePosition();
		Vec3 center = Vec3.atCenterOf(pos);
		if (eye.distanceToSqr(center) > reach * reach) {
			return false;
		}
		BlockHitResult hit = level.clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mehburity));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
	}

	private void breakBlock(ServerLevel level, ServerPlayer owner, MiningJob job, BlockPos pos, BlockState state) {
		if (!MiningRules.ownerMayBreak(level, owner, pos, state)) {
			job.skip(pos); // protected here; try the next one
			if (job.kind() == MiningJob.Kind.SINGLE) {
				Mining.finish(mehburity, owner, MiningJob.End.UNREACHABLE);
			}
			return;
		}
		ItemStack tool = MiningRules.tool();
		List<ItemStack> drops = Block.getDrops(state, level, pos, null, mehburity, tool);
		state.spawnAfterBreak(level, pos, tool, true); // experience orbs, like a player mining it
		level.destroyBlock(pos, false, mehburity);
		job.countMined();

		boolean full = false;
		for (ItemStack drop : drops) {
			ItemStack rest = MiningBag.add(owner, drop);
			if (!rest.isEmpty()) {
				Block.popResource(level, pos, rest); // nothing is lost: what does not fit drops on the ground
				full = true;
			}
		}
		if (!drops.isEmpty()) {
			mehburity.playSound(SoundEvents.ITEM_PICKUP, 0.4F, 1.4F + mehburity.getRandom().nextFloat() * 0.4F);
		}

		if (job.kind() == MiningJob.Kind.SINGLE) {
			Mining.finish(mehburity, owner, MiningJob.End.DONE);
		} else if (full || MiningBag.isFull(owner)) {
			Mining.finish(mehburity, owner, MiningJob.End.BAG_FULL);
		} else if (job.mined() >= MehburityConfig.get().mining.maxBlocksPerTrip) {
			Mining.finish(mehburity, owner, MiningJob.End.TRIP_LIMIT);
		}
	}

	private void giveUp(MiningJob job) {
		if (target != null) {
			job.skip(target);
		}
		clearTarget();
	}

	private void clearTarget() {
		if (target != null) {
			resetProgress((ServerLevel) mehburity.level());
		}
		target = null;
		ticksOnTarget = 0;
	}

	private void resetProgress(ServerLevel level) {
		if (lastStage >= 0 && target != null) {
			level.destroyBlockProgress(mehburity.getId(), target, -1);
		}
		progress = 0;
		lastStage = -1;
	}
}
