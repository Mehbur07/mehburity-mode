package com.mehbur.mehburity.mining;

import com.mehbur.mehburity.MehburityMod;
import com.mehbur.mehburity.config.MehburityConfig;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Which blocks Mehburity mines, how long a block takes, and what it may never touch. The targets come from
 * {@code mining.targets} and are parsed again whenever the list changes (config reload).
 */
public final class MiningRules {
	private static @Nullable List<String> parsedFrom;
	private static List<TagKey<Block>> targetTags = List.of();
	private static Set<Block> targetBlocks = Set.of();

	private MiningRules() {
	}

	/** True for blocks Mehburity looks for when it goes mining (ores by default). */
	public static boolean isTarget(BlockState state) {
		refreshTargets();
		if (targetBlocks.contains(state.getBlock())) {
			return true;
		}
		for (TagKey<Block> tag : targetTags) {
			if (state.is(tag)) {
				return true;
			}
		}
		return false;
	}

	private static void refreshTargets() {
		List<String> configured = MehburityConfig.get().mining.targets;
		if (configured.equals(parsedFrom)) {
			return;
		}
		List<TagKey<Block>> tags = new ArrayList<>();
		Set<Block> blocks = new HashSet<>();
		for (String entry : configured) {
			boolean tag = entry.startsWith("#");
			Identifier id = Identifier.tryParse(tag ? entry.substring(1) : entry);
			if (id == null) {
				MehburityMod.LOGGER.warn("mining.targets: '{}' is not a valid id, skipped", entry);
			} else if (tag) {
				tags.add(TagKey.create(Registries.BLOCK, id));
			} else {
				BuiltInRegistries.BLOCK.getOptional(id).ifPresentOrElse(blocks::add,
						() -> MehburityMod.LOGGER.warn("mining.targets: unknown block '{}', skipped", entry));
			}
		}
		targetTags = List.copyOf(tags);
		targetBlocks = Set.copyOf(blocks);
		parsedFrom = List.copyOf(configured);
	}

	/**
	 * Mehburity never breaks air, liquids, unbreakable blocks (bedrock, barriers...) or blocks that hold items or
	 * data (chests, furnaces, spawners...): breaking those could throw away the owner's things.
	 */
	public static boolean isBreakable(ServerLevel level, BlockPos pos, BlockState state) {
		return !state.isAir()
				&& !(state.getBlock() instanceof LiquidBlock)
				&& !state.hasBlockEntity()
				&& state.getDestroySpeed(level, pos) >= 0.0F;
	}

	/** The owner could break it here: spawn protection, adventure mode and protection mods are respected. */
	public static boolean ownerMayBreak(ServerLevel level, ServerPlayer owner, BlockPos pos, BlockState state) {
		return level.mayInteract(owner, pos)
				&& !owner.blockActionRestricted(level, pos, owner.gameMode())
				&& PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, owner, pos, state, null);
	}

	/** Has a side Mehburity can reach it from (air or water, never lava). */
	public static boolean isExposed(ServerLevel level, BlockPos pos) {
		return openSide(level, pos, null) != null;
	}

	/** The open neighbour of {@code pos} closest to {@code from}, or null when the block is buried. */
	public static @Nullable BlockPos openSide(ServerLevel level, BlockPos pos, @Nullable Vec3 from) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Direction direction : Direction.values()) {
			BlockPos side = pos.relative(direction);
			BlockState state = level.getBlockState(side);
			if (!state.getCollisionShape(level, side).isEmpty() || state.getFluidState().is(FluidTags.LAVA)) {
				continue;
			}
			if (from == null) {
				return side;
			}
			double distance = from.distanceToSqr(Vec3.atCenterOf(side));
			if (distance < bestDistance) {
				bestDistance = distance;
				best = side;
			}
		}
		return best;
	}

	/** Ticks to break: harder blocks take longer (iron ore ~1.2 s, deepslate ores ~1.8 s by default). */
	public static int breakTicks(ServerLevel level, BlockPos pos, BlockState state) {
		MehburityConfig.Mining config = MehburityConfig.get().mining;
		int ticks = Mth.ceil(state.getDestroySpeed(level, pos) * config.ticksPerHardness);
		return Mth.clamp(ticks, config.minBreakTicks, config.maxBreakTicks);
	}

	/** The pretend tool used to work out drops ({@code mining.tool}); a diamond pickaxe if the id is unknown. */
	public static ItemStack tool() {
		Identifier id = Identifier.tryParse(MehburityConfig.get().mining.tool);
		return id == null ? new ItemStack(Items.DIAMOND_PICKAXE)
				: BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElseGet(() -> new ItemStack(Items.DIAMOND_PICKAXE));
	}
}
