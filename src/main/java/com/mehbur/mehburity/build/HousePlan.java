package com.mehbur.mehburity.build;

import com.mehbur.mehburity.config.MehburityConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.SmokerBlock;
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
 * A house as an ordered list of block placements, built from a {@link HouseSpec}: clear the space, lay foundations
 * and level the plot, raise each storey's walls (corner pillars, door, windows) with a floor between storeys, put the
 * roof on, add the extras (tower, chimney, porch, balcony, pool, garden), then ladders, lights and furniture.
 * <p>
 * Local coordinates: {@code x} runs to the owner's right (walls at ±half), {@code z} away from them (0 = front wall
 * with the door, depth - 1 = back wall), {@code y} up from the ground floor.
 */
public final class HousePlan {
	/** The plot stays at least this far in front of the owner's feet, so they are never walled in. */
	private static final int CLEARANCE = 2;
	/** Porch and balcony stick out this far in front of the house. */
	static final int PORCH_DEPTH = 2;
	/** A garden adds this much ground (inside a fence) around the house. */
	static final int GARDEN_MARGIN = 3;
	static final int POOL_WIDTH = 4;
	static final int POOL_MAX_LENGTH = 8;
	/** The plot around the house (garden, pool) is cleared this high above the ground. */
	private static final int PLOT_CLEAR_HEIGHT = 8;
	private static final Block[] FLOWERS = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.AZURE_BLUET,
			Blocks.OXEYE_DAISY, Blocks.ALLIUM};

	/** One placement; {@code raw} states are placed as they are (both halves of doors and beds). */
	public record Step(BlockPos pos, BlockState state, boolean raw) {
	}

	private final BlockPos origin;
	private final Direction facing;
	private final HouseSpec spec;
	private final List<Step> steps;
	/** Every position the house may change (to check it is all allowed before starting). */
	private final Set<BlockPos> area;

	private HousePlan(BlockPos origin, Direction facing, HouseSpec spec, List<Step> steps, Set<BlockPos> area) {
		this.origin = origin;
		this.facing = facing;
		this.spec = spec;
		this.steps = steps;
		this.area = area;
	}

	public List<Step> steps() {
		return steps;
	}

	public Set<BlockPos> area() {
		return area;
	}

	public HouseSpec spec() {
		return spec;
	}

	/** Floor level in the middle of the front wall. */
	public BlockPos origin() {
		return origin;
	}

	public Direction facing() {
		return facing;
	}

	/** Centre of the house at floor level. */
	public BlockPos center() {
		return at(0, 0, (spec.depth() - 1) / 2);
	}

	/** The classic house in front of {@code feet}, the door facing back towards them. */
	public static HousePlan inFrontOf(ServerLevel level, BlockPos feet, Direction facing, HouseStyle.Palette palette) {
		return inFrontOf(level, feet, facing, HouseSpec.standard(palette));
	}

	/** {@code spec} in front of {@code feet}, the door facing back towards them. */
	public static HousePlan inFrontOf(ServerLevel level, BlockPos feet, Direction facing, HouseSpec spec) {
		Bounds bounds = Bounds.of(spec);
		BlockPos front = feet.relative(facing, Math.max(3, -bounds.plotZ0() + CLEARANCE));
		BlockPos middle = front.relative(facing, (spec.depth() - 1) / 2);
		// The floor replaces the top block of the ground in the middle of the house.
		int floorY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, middle.getX(), middle.getZ()) - 1;
		return new Builder(new BlockPos(front.getX(), floorY, front.getZ()), facing, spec, bounds, level).build();
	}

	BlockPos at(int x, int y, int z) {
		return origin.relative(facing.getClockWise(), x).relative(facing, z).above(y);
	}

	/**
	 * Where things go, in local coordinates: the house with its roof overhang, porch and tower ({@code house*}), and
	 * the whole plot including garden and pool ({@code plot*}, the same as the house without them).
	 */
	record Bounds(int half, int back, int porch, int houseX0, int houseX1, int houseZ0, int houseZ1,
			int plotX0, int plotX1, int plotZ0, int plotZ1, boolean levelled) {
		static Bounds of(HouseSpec spec) {
			int half = spec.half();
			int back = spec.depth() - 1;
			int porch = spec.porch() || spec.balcony() ? PORCH_DEPTH : 0;
			int houseX0 = -half - 1 - (spec.tower() ? 1 : 0);
			int houseX1 = half + 1;
			int houseZ0 = -1 - Math.max(porch, spec.tower() ? 1 : 0);
			int houseZ1 = back + 1;
			boolean levelled = spec.garden() || spec.pool();
			int margin = spec.garden() ? GARDEN_MARGIN : levelled ? 1 : 0;
			int poolSide = spec.pool() ? POOL_WIDTH + 2 : 0;
			return new Bounds(half, back, porch, houseX0, houseX1, houseZ0, houseZ1,
					houseX0 - margin, houseX1 + poolSide + margin, houseZ0 - margin, houseZ1 + margin, levelled);
		}

		boolean inHouse(int x, int z) {
			return x >= houseX0 && x <= houseX1 && z >= houseZ0 && z <= houseZ1;
		}

		boolean inPlot(int x, int z) {
			return x >= plotX0 && x <= plotX1 && z >= plotZ0 && z <= plotZ1;
		}

		/** The porch's half width: never into the tower or past the side walls. */
		int porchHalf() {
			return Math.min(2, half - 1);
		}

		int poolX0() {
			return half + 3;
		}

		int poolX1() {
			return half + 2 + POOL_WIDTH;
		}

		int poolZ1() {
			return Math.min(back - 1, POOL_MAX_LENGTH);
		}

		/** The pool with its rim. */
		boolean inPool(int x, int z) {
			return x >= poolX0() - 1 && x <= poolX1() + 1 && z >= 0 && z <= poolZ1() + 1;
		}
	}

	private static final class Builder {
		private final BlockPos origin;
		private final Direction facing;
		private final Direction right;
		private final HouseSpec spec;
		private final HouseStyle.Palette palette;
		private final Bounds b;
		private final ServerLevel level;
		private final int half;
		private final int back;
		private final int rows;
		/** Ground floor up to the top of the roof (and of the tower, and the chimney's fire). */
		private final int roofY;
		private final int roofTop;
		private final int top;
		private final List<Step> steps = new ArrayList<>();
		private final Set<BlockPos> area = new LinkedHashSet<>();

		Builder(BlockPos origin, Direction facing, HouseSpec spec, Bounds bounds, ServerLevel level) {
			this.origin = origin;
			this.facing = facing;
			this.right = facing.getClockWise();
			this.spec = spec;
			this.palette = spec.palette();
			this.b = bounds;
			this.level = level;
			this.half = spec.half();
			this.back = spec.depth() - 1;
			this.rows = spec.wallRows();
			this.roofY = spec.floors() * (rows + 1);
			this.roofTop = switch (spec.roof()) {
				case PYRAMID -> roofY + Math.min(half + 1, back / 2 + 1);
				case GABLE -> roofY + half + 1;
				case FLAT -> roofY + 1;
			};
			this.top = Math.max(roofTop + (spec.tower() ? 4 : 0), spec.chimney() ? roofTop + 3 : 0);
		}

		BlockPos at(int x, int y, int z) {
			return origin.relative(right, x).relative(facing, z).above(y);
		}

		void put(int x, int y, int z, BlockState state) {
			put(at(x, y, z), state, false);
		}

		void raw(int x, int y, int z, BlockState state) {
			put(at(x, y, z), state, true);
		}

		void put(BlockPos pos, BlockState state, boolean raw) {
			steps.add(new Step(pos, state, raw));
			area.add(pos);
		}

		/** Bottom of storey {@code floor}: its floor blocks are at this height. */
		int base(int floor) {
			return floor * (rows + 1);
		}

		HousePlan build() {
			clear();
			foundations();
			groundFloor();
			for (int floor = 0; floor < spec.floors(); floor++) {
				walls(floor);
			}
			for (int floor = 0; floor < spec.floors(); floor++) {
				windows(floor);
			}
			roof();
			if (spec.tower()) {
				tower();
			}
			if (spec.chimney()) {
				chimney();
			}
			if (b.porch() > 0) {
				porchAndBalcony();
			}
			doors();
			ladders();
			if (spec.pool()) {
				pool();
			}
			if (spec.garden()) {
				garden();
			}
			lights();
			if (MehburityConfig.get().build.furnish) {
				furniture();
			}
			return new HousePlan(origin, facing, spec, List.copyOf(steps), Set.copyOf(area));
		}

		// ------------------------------------------------------------ ground

		/** Top down, so nothing is left floating on what gets removed. */
		private void clear() {
			for (int y = top; y >= 1; y--) {
				for (int x = b.plotX0(); x <= b.plotX1(); x++) {
					for (int z = b.plotZ0(); z <= b.plotZ1(); z++) {
						if (b.inHouse(x, z) || (b.levelled() && y <= PLOT_CLEAR_HEIGHT)) {
							put(x, y, z, Blocks.AIR.defaultBlockState());
						}
					}
				}
			}
		}

		private boolean inFootprint(int x, int z) {
			return (Math.abs(x) <= half && z >= 0 && z <= back) || inPorch(x, z) || inTower(x, z) || inChimney(x, z);
		}

		/** Grass under the chimney would die: it stands on bricks. */
		private boolean inChimney(int x, int z) {
			return spec.chimney() && x == half - 1 && z == back + 1;
		}

		private boolean inPorch(int x, int z) {
			return b.porch() > 0 && Math.abs(x) <= b.porchHalf() && z >= -b.porch() && z < 0;
		}

		private boolean inTower(int x, int z) {
			return spec.tower() && x >= -half - 2 && x <= -half && z >= -2 && z <= 0;
		}

		/** Fills holes under the house with its foundation, and under a garden or pool with dirt. */
		private void foundations() {
			int depth = MehburityConfig.get().build.maxFoundationDepth;
			for (int x = b.plotX0(); x <= b.plotX1(); x++) {
				for (int z = b.plotZ0(); z <= b.plotZ1(); z++) {
					boolean house = inFootprint(x, z);
					if (!house && !b.levelled()) {
						continue;
					}
					List<BlockPos> column = new ArrayList<>();
					for (int y = -1; y >= -depth; y--) {
						BlockPos pos = at(x, y, z);
						if (!level.getBlockState(pos).canBeReplaced()) {
							break;
						}
						column.add(pos);
					}
					BlockState fill = house ? palette.foundation() : Blocks.DIRT.defaultBlockState();
					for (int i = column.size() - 1; i >= 0; i--) {
						put(column.get(i), fill, false);
					}
				}
			}
		}

		private void groundFloor() {
			for (int x = b.plotX0(); x <= b.plotX1(); x++) {
				for (int z = b.plotZ0(); z <= b.plotZ1(); z++) {
					if (Math.abs(x) <= half && z >= 0 && z <= back) {
						put(x, 0, z, isEdge(x, z) ? palette.foundation() : palette.floor());
					} else if (inPorch(x, z)) {
						put(x, 0, z, palette.floor());
					} else if (inTower(x, z)) {
						put(x, 0, z, palette.corners());
					} else if (inChimney(x, z)) {
						put(x, 0, z, Blocks.BRICKS.defaultBlockState());
					} else if (b.levelled() && !(spec.pool() && b.inPool(x, z))) {
						put(x, 0, z, Blocks.GRASS_BLOCK.defaultBlockState());
					}
				}
			}
		}

		// ------------------------------------------------------------ the house

		private boolean isEdge(int x, int z) {
			return Math.abs(x) == half || z == 0 || z == back;
		}

		private boolean isCorner(int x, int z) {
			return Math.abs(x) == half && (z == 0 || z == back);
		}

		/** The front door on the ground floor, the balcony door on the first floor. */
		private boolean isDoor(int floor, int x, int row, int z) {
			boolean door = floor == 0 || (floor == 1 && spec.balcony());
			return door && x == 0 && z == 0 && (row == 1 || row == 2);
		}

		/**
		 * Normal: one row of windows, every other block. Big: from the second row to the one under the top, with a
		 * solid pillar every fourth block and next to the corners.
		 */
		private boolean isWindow(int x, int row, int z) {
			if (isCorner(x, z)) {
				return false;
			}
			if (!spec.bigWindows()) {
				if (row != 2) {
					return false;
				}
				if (Math.abs(x) == half) {
					return z % 2 == 0 && z > 0 && z < back;
				}
				if (z == back) {
					return Math.abs(x) % 2 == 1 && Math.abs(x) < half;
				}
				return Math.abs(x) % 2 == 0 && x != 0 && Math.abs(x) < half;
			}
			if (row < 2 || row > Math.max(2, rows - 1)) {
				return false;
			}
			if (Math.abs(x) == half) {
				return z >= 2 && z <= back - 2 && z % 4 != 0;
			}
			return Math.abs(x) <= half - 2 && Math.abs(x) % 4 != 0;
		}

		/** One storey's walls and corner pillars, then the floor of the storey above (a band outside). */
		private void walls(int floor) {
			int base = base(floor);
			for (int row = 1; row <= rows; row++) {
				for (int x = -half; x <= half; x++) {
					for (int z = 0; z <= back; z++) {
						if (!isEdge(x, z) || isDoor(floor, x, row, z) || isWindow(x, row, z)) {
							continue;
						}
						put(x, base + row, z, isCorner(x, z) ? palette.corners() : palette.walls());
					}
				}
			}
			if (floor < spec.floors() - 1) {
				for (int x = -half; x <= half; x++) {
					for (int z = 0; z <= back; z++) {
						put(x, base + rows + 1, z, isEdge(x, z) ? palette.corners() : palette.floor());
					}
				}
			}
		}

		/** After the walls, so the panes connect to them. */
		private void windows(int floor) {
			int base = base(floor);
			for (int row = 1; row <= rows; row++) {
				for (int x = -half; x <= half; x++) {
					for (int z = 0; z <= back; z++) {
						if (isEdge(x, z) && !isDoor(floor, x, row, z) && isWindow(x, row, z)) {
							put(x, base + row, z, spec.window());
						}
					}
				}
			}
		}

		/** Stepped pyramid, gable along the depth, or flat with a low parapet. The first layer is also the ceiling. */
		private void roof() {
			switch (spec.roof()) {
				case PYRAMID -> {
					int middle = back / 2;
					for (int layer = 0; ; layer++) {
						int hx = half + 1 - layer;
						int hz = back / 2 + 1 - layer;
						if (hx < 0 || hz < 0) {
							break;
						}
						for (int x = -hx; x <= hx; x++) {
							for (int z = middle - hz; z <= middle + hz; z++) {
								put(x, roofY + layer, z, palette.roof());
							}
						}
					}
				}
				case GABLE -> {
					for (int layer = 0; layer <= half + 1; layer++) {
						int hx = half + 1 - layer;
						for (int x = -hx; x <= hx; x++) {
							for (int z = -1; z <= back + 1; z++) {
								put(x, roofY + layer, z, palette.roof());
							}
						}
					}
				}
				case FLAT -> {
					for (int x = -half; x <= half; x++) {
						for (int z = 0; z <= back; z++) {
							put(x, roofY, z, palette.roof());
							if (isEdge(x, z)) {
								put(x, roofY + 1, z, palette.corners());
							}
						}
					}
				}
			}
		}

		/** A 3x3 tower at the front left corner, a few blocks above the roof, with battlements and a lantern. */
		private void tower() {
			int height = roofTop + 3;
			for (int y = 1; y <= height; y++) {
				for (int x = -half - 2; x <= -half; x++) {
					for (int z = -2; z <= 0; z++) {
						put(x, y, z, palette.corners());
					}
				}
			}
			for (int x = -half - 2; x <= -half; x++) {
				for (int z = -2; z <= 0; z++) {
					boolean middle = x == -half - 1 && z == -1;
					if (middle) {
						raw(x, height + 1, z, Blocks.LANTERN.defaultBlockState());
					} else if ((x + z) % 2 == 0) {
						put(x, height + 1, z, palette.corners());
					}
				}
			}
		}

		/** A brick chimney behind the house, a little higher than the roof, smoking from a campfire on top. */
		private void chimney() {
			int x = half - 1;
			int z = back + 1;
			for (int y = 1; y <= roofTop + 2; y++) {
				put(x, y, z, Blocks.BRICKS.defaultBlockState());
			}
			raw(x, roofTop + 3, z, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
		}

		/** A roofed porch in front of the door on fence posts; with a balcony, its roof is the balcony's floor. */
		private void porchAndBalcony() {
			int p = b.porchHalf();
			int y = rows + 1;
			if (spec.porch()) {
				for (int row = 1; row <= rows; row++) {
					put(-p, row, -b.porch(), Blocks.OAK_FENCE.defaultBlockState());
					put(p, row, -b.porch(), Blocks.OAK_FENCE.defaultBlockState());
				}
			}
			for (int x = -p; x <= p; x++) {
				for (int z = -b.porch(); z < 0; z++) {
					put(x, y, z, spec.balcony() ? palette.floor() : palette.roof());
				}
			}
			if (spec.balcony()) {
				for (int x = -p; x <= p; x++) {
					put(x, y + 1, -b.porch(), Blocks.OAK_FENCE.defaultBlockState());
				}
				for (int z = -b.porch() + 1; z < 0; z++) {
					put(-p, y + 1, z, Blocks.OAK_FENCE.defaultBlockState());
					put(p, y + 1, z, Blocks.OAK_FENCE.defaultBlockState());
				}
			}
		}

		private void doors() {
			BlockState door = palette.door().defaultBlockState()
					.setValue(DoorBlock.FACING, facing)
					.setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
			for (int floor = 0; floor < spec.floors(); floor++) {
				if (isDoor(floor, 0, 1, 0)) {
					int base = base(floor);
					raw(0, base + 1, 0, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
					raw(0, base + 2, 0, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
				}
			}
		}

		/** Up the left wall by the front, through a hole in each floor. */
		private void ladders() {
			BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, right);
			for (int floor = 0; floor < spec.floors() - 1; floor++) {
				int base = base(floor);
				for (int y = base + 1; y <= base + rows + 1; y++) {
					raw(-half + 1, y, 1, ladder);
				}
			}
		}

		// ------------------------------------------------------------ outside

		/** Two blocks deep, beside the house, lined with smooth stone and lit from below. */
		private void pool() {
			int x0 = b.poolX0();
			int x1 = b.poolX1();
			int z1 = b.poolZ1();
			for (int x = x0 - 1; x <= x1 + 1; x++) {
				for (int z = 0; z <= z1 + 1; z++) {
					boolean rim = x < x0 || x > x1 || z < 1 || z > z1;
					if (rim) {
						put(x, 0, z, Blocks.SMOOTH_STONE.defaultBlockState());
						put(x, -1, z, Blocks.SMOOTH_STONE.defaultBlockState());
					} else {
						boolean light = x == (x0 + x1) / 2 && z == (1 + z1) / 2;
						put(x, -2, z, light ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.PRISMARINE_BRICKS.defaultBlockState());
					}
				}
			}
			for (int x = x0; x <= x1; x++) {
				for (int z = 1; z <= z1; z++) {
					raw(x, -1, z, Blocks.WATER.defaultBlockState());
					raw(x, 0, z, Blocks.WATER.defaultBlockState());
				}
			}
		}

		/** A fence round the plot with a gate, a path to the door, flowers, and lanterns on the corner posts. */
		private void garden() {
			int frontOfHouse = b.porch() > 0 ? -b.porch() : 0;
			for (int z = b.plotZ0() + 1; z < frontOfHouse; z++) {
				put(0, 0, z, Blocks.DIRT_PATH.defaultBlockState());
			}
			for (int x = b.plotX0(); x <= b.plotX1(); x++) {
				for (int z = b.plotZ0(); z <= b.plotZ1(); z++) {
					boolean edge = x == b.plotX0() || x == b.plotX1() || z == b.plotZ0() || z == b.plotZ1();
					if (edge) {
						if (x == 0 && z == b.plotZ0()) {
							put(x, 1, z, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, facing));
						} else {
							put(x, 1, z, Blocks.OAK_FENCE.defaultBlockState());
						}
						continue;
					}
					if (b.inHouse(x, z) || inFootprint(x, z) || (spec.pool() && b.inPool(x, z)) || x == 0) {
						continue;
					}
					int hash = Math.floorMod(x * 734287 + z * 912931, 97);
					if (hash % 5 == 0) {
						put(x, 1, z, FLOWERS[hash % FLOWERS.length].defaultBlockState());
					}
				}
			}
			for (int x : new int[] {b.plotX0(), b.plotX1()}) {
				for (int z : new int[] {b.plotZ0(), b.plotZ1()}) {
					raw(x, 2, z, Blocks.LANTERN.defaultBlockState());
				}
			}
		}

		// ------------------------------------------------------------ inside

		/**
		 * Torches high on the walls (the nights are pitch black with Verity darkness): two outside the door, and on
		 * every storey one on the back wall and one on each side; a big house also gets a lantern in the middle.
		 */
		private void lights() {
			Direction away = facing.getOpposite();
			raw(-1, rows, -1, torch(away));
			raw(1, rows, -1, torch(away));
			boolean big = spec.width() >= 9 || spec.depth() >= 9;
			for (int floor = 0; floor < spec.floors(); floor++) {
				int y = base(floor) + rows;
				raw(0, y, back - 1, torch(away));
				raw(-half + 1, y, back / 2, torch(right));
				raw(half - 1, y, back / 2, torch(right.getOpposite()));
				if (big) {
					raw(0, y, back / 2, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
				}
			}
		}

		/**
		 * Workbench, furnace and chest along the right wall downstairs (plus a smoker and a barrel for a kitchen),
		 * beds along the back wall (upstairs first), bookshelves and a lectern along the left wall for a library.
		 */
		private void furniture() {
			Direction left = right.getOpposite();
			put(half - 1, 1, back - 1, Blocks.CRAFTING_TABLE.defaultBlockState());
			raw(half - 1, 1, back - 2, Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, left));
			raw(half - 1, 1, back - 3, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, left));
			if (spec.kitchen()) {
				if (back - 4 >= 2) {
					raw(half - 1, 1, back - 4, Blocks.SMOKER.defaultBlockState().setValue(SmokerBlock.FACING, left));
				}
				if (back - 5 >= 2) {
					raw(half - 1, 1, back - 5, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, left));
				}
			}
			int bedsLeft = spec.beds();
			for (int floor = spec.floors() - 1; floor >= 0 && bedsLeft > 0; floor--) {
				int base = base(floor);
				// Downstairs the right wall belongs to the kitchen.
				int lastX = floor == 0 ? half - 3 : half - 1;
				for (int x = -half + 1; x <= lastX && bedsLeft > 0; x += 2) {
					BlockState bed = Blocks.BED.cyan().defaultBlockState().setValue(BedBlock.FACING, facing);
					raw(x, base + 1, back - 2, bed.setValue(BedBlock.PART, BedPart.FOOT));
					raw(x, base + 1, back - 1, bed.setValue(BedBlock.PART, BedPart.HEAD));
					bedsLeft--;
				}
				if (floor > 0) {
					raw(half - 1, base + 1, back - 1, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, left));
				}
			}
			if (spec.library()) {
				int base = base(spec.floors() - 1);
				for (int z = 2; z <= back - 3; z++) {
					put(-half + 1, base + 1, z, Blocks.BOOKSHELF.defaultBlockState());
					put(-half + 1, base + 2, z, Blocks.BOOKSHELF.defaultBlockState());
				}
				if (back / 2 <= back - 3 && -half + 2 < half - 1) {
					raw(-half + 2, base + 1, back / 2, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, left));
				}
			}
		}

		private static BlockState torch(Direction pointing) {
			return Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, pointing);
		}
	}
}
