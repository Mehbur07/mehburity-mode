package com.mehbur.mehburity.build;

import com.mehbur.mehburity.config.MehburityConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A small house as an ordered list of block placements: clear the space, lay a foundation and floor, raise the
 * walls with corner pillars, a door and windows, put a stepped roof on top, then torches and furniture.
 * <p>
 * Local coordinates: {@code x} runs to the owner's right (-3..3 for the walls), {@code z} away from them
 * (0 = front wall with the door, 6 = back wall), {@code y} up from the floor.
 */
public final class HousePlan {
	/** Half the outer width: walls at x = ±3. */
	static final int HALF = 3;
	static final int DEPTH = 6;
	static final int WALL_HEIGHT = 4;
	/** Roof layers (9x9, 7x7, 5x5, 3x3, 1x1), the first one hangs over the walls by one block. */
	static final int ROOF_LAYERS = 5;
	/** The front wall starts this many blocks in front of the owner. */
	static final int GAP = 3;

	/** One placement; {@code raw} states are placed as they are (both halves of doors and beds). */
	public record Step(BlockPos pos, BlockState state, boolean raw) {
	}

	private final BlockPos origin;
	private final Direction facing;
	private final List<Step> steps;
	/** Every position the house may change (to check it is all allowed before starting). */
	private final Set<BlockPos> area;

	private HousePlan(BlockPos origin, Direction facing, List<Step> steps, Set<BlockPos> area) {
		this.origin = origin;
		this.facing = facing;
		this.steps = steps;
		this.area = area;
	}

	public List<Step> steps() {
		return steps;
	}

	public Set<BlockPos> area() {
		return area;
	}

	/** Floor level in the middle of the front wall. */
	public BlockPos origin() {
		return origin;
	}

	public Direction facing() {
		return facing;
	}

	/** Centre of the house at floor level, for Mehburity to hover over. */
	public BlockPos center() {
		return at(0, 0, DEPTH / 2);
	}

	/** A house in front of {@code feet}, the door facing back towards them. */
	public static HousePlan inFrontOf(ServerLevel level, BlockPos feet, Direction facing, HouseStyle.Palette palette) {
		BlockPos front = feet.relative(facing, GAP);
		BlockPos middle = front.relative(facing, DEPTH / 2);
		// The floor replaces the top block of the ground in the middle of the house.
		int floorY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, middle.getX(), middle.getZ()) - 1;
		return new Builder(new BlockPos(front.getX(), floorY, front.getZ()), facing, palette, level).build();
	}

	BlockPos at(int x, int y, int z) {
		return origin.relative(facing.getClockWise(), x).relative(facing, z).above(y);
	}

	private static final class Builder {
		private final BlockPos origin;
		private final Direction facing;
		private final Direction right;
		private final HouseStyle.Palette palette;
		private final ServerLevel level;
		private final List<Step> steps = new ArrayList<>();
		private final Set<BlockPos> area = new LinkedHashSet<>();

		Builder(BlockPos origin, Direction facing, HouseStyle.Palette palette, ServerLevel level) {
			this.origin = origin;
			this.facing = facing;
			this.right = facing.getClockWise();
			this.palette = palette;
			this.level = level;
		}

		BlockPos at(int x, int y, int z) {
			return origin.relative(right, x).relative(facing, z).above(y);
		}

		void put(int x, int y, int z, BlockState state) {
			put(at(x, y, z), state, false);
		}

		void put(BlockPos pos, BlockState state, boolean raw) {
			steps.add(new Step(pos, state, raw));
			area.add(pos);
		}

		HousePlan build() {
			int top = WALL_HEIGHT + ROOF_LAYERS;
			int overhang = HALF + 1;
			// 1. Clear the space (top down, so nothing is left floating on what gets removed).
			for (int y = top; y >= 1; y--) {
				for (int x = -overhang; x <= overhang; x++) {
					for (int z = -1; z <= DEPTH + 1; z++) {
						put(x, y, z, Blocks.AIR.defaultBlockState());
					}
				}
			}
			// 2. Foundation under the floor where the ground is missing.
			int depth = MehburityConfig.get().build.maxFoundationDepth;
			for (int x = -HALF; x <= HALF; x++) {
				for (int z = 0; z <= DEPTH; z++) {
					List<BlockPos> column = new ArrayList<>();
					for (int y = -1; y >= -depth; y--) {
						BlockPos pos = at(x, y, z);
						if (!level.getBlockState(pos).canBeReplaced()) {
							break;
						}
						column.add(pos);
					}
					for (int i = column.size() - 1; i >= 0; i--) {
						put(column.get(i), palette.foundation(), false);
					}
				}
			}
			// 3. Floor.
			for (int x = -HALF; x <= HALF; x++) {
				for (int z = 0; z <= DEPTH; z++) {
					put(x, 0, z, isEdge(x, z) ? palette.foundation() : palette.floor());
				}
			}
			// 4. Walls with corner pillars, leaving holes for the door and the windows.
			for (int y = 1; y <= WALL_HEIGHT; y++) {
				for (int x = -HALF; x <= HALF; x++) {
					for (int z = 0; z <= DEPTH; z++) {
						if (!isEdge(x, z) || isDoor(x, y, z)) {
							continue;
						}
						if (isWindow(x, y, z)) {
							continue;
						}
						boolean corner = Math.abs(x) == HALF && (z == 0 || z == DEPTH);
						put(x, y, z, corner ? palette.corners() : palette.walls());
					}
				}
			}
			// 5. Windows (after the walls, so the panes connect to them).
			for (int y = 1; y <= WALL_HEIGHT; y++) {
				for (int x = -HALF; x <= HALF; x++) {
					for (int z = 0; z <= DEPTH; z++) {
						if (isEdge(x, z) && isWindow(x, y, z)) {
							put(x, y, z, Blocks.GLASS_PANE.defaultBlockState());
						}
					}
				}
			}
			// 6. Roof: a stepped pyramid, the first layer is also the ceiling.
			for (int layer = 0; layer < ROOF_LAYERS; layer++) {
				int half = HALF + 1 - layer;
				for (int x = -half; x <= half; x++) {
					for (int z = DEPTH / 2 - half; z <= DEPTH / 2 + half; z++) {
						put(x, WALL_HEIGHT + 1 + layer, z, palette.roof());
					}
				}
			}
			// 7. Door, facing the owner.
			BlockState door = palette.door().defaultBlockState()
					.setValue(DoorBlock.FACING, facing)
					.setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
			put(at(0, 1, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), true);
			put(at(0, 2, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), true);
			// 8. Light: the nights are pitch black with Verity darkness.
			Direction back = facing.getOpposite();
			put(at(-1, 3, -1), torch(back), true);
			put(at(1, 3, -1), torch(back), true);
			put(at(0, 3, DEPTH - 1), torch(back), true);
			put(at(-HALF + 1, 3, 2), torch(right), true);
			put(at(HALF - 1, 3, 2), torch(right.getOpposite()), true);
			// 9. Furniture.
			if (MehburityConfig.get().build.furnish) {
				BlockState bed = Blocks.BED.cyan().defaultBlockState().setValue(BedBlock.FACING, facing);
				put(at(-2, 1, 4), bed.setValue(BedBlock.PART, BedPart.FOOT), true);
				put(at(-2, 1, 5), bed.setValue(BedBlock.PART, BedPart.HEAD), true);
				put(2, 1, 5, Blocks.CRAFTING_TABLE.defaultBlockState());
				put(at(2, 1, 4), Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, right.getOpposite()), true);
				put(at(2, 1, 3), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, right.getOpposite()), true);
			}
			return new HousePlan(origin, facing, List.copyOf(steps), Set.copyOf(area));
		}

		private static boolean isEdge(int x, int z) {
			return Math.abs(x) == HALF || z == 0 || z == DEPTH;
		}

		private static boolean isDoor(int x, int y, int z) {
			return x == 0 && z == 0 && (y == 1 || y == 2);
		}

		/** Two windows on each side wall, two at the back, one on each side of the door. */
		private static boolean isWindow(int x, int y, int z) {
			if (y != 2) {
				return false;
			}
			if (Math.abs(x) == HALF) {
				return z == 2 || z == 4;
			}
			if (z == DEPTH) {
				return x == -1 || x == 1;
			}
			return z == 0 && Math.abs(x) == 2;
		}

		private static BlockState torch(Direction pointing) {
			return Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, pointing);
		}
	}
}
