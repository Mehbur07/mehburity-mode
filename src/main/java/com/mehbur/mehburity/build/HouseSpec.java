package com.mehbur.mehburity.build;

import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What house to build: size, floors, roof, materials and extras. Comes from the owner's description
 * ({@link HouseDescriptions}, optionally refined by Gemini through {@link HouseDesignJson}); {@link #standard} is the
 * classic 7x7 one-storey house. Always within buildable limits: use {@link Draft#build()}.
 *
 * @param width     outer width in blocks, odd (5..21); the door is in the middle of the front
 * @param depth     outer depth in blocks (5..21)
 * @param floors    storeys (1..4), joined by ladders
 * @param wallRows  wall height of each storey (3..6)
 */
public record HouseSpec(int width, int depth, int floors, int wallRows, Roof roof, HouseStyle.Palette palette,
		BlockState window, boolean bigWindows, boolean chimney, boolean porch, boolean balcony, boolean garden,
		boolean pool, boolean tower, boolean library, boolean kitchen, int beds) {
	public enum Roof { PYRAMID, GABLE, FLAT }

	public static final int MIN_SIZE = 5;
	public static final int MAX_SIZE = 21;
	public static final int MAX_FLOORS = 4;
	public static final int MIN_WALL_ROWS = 3;
	public static final int MAX_WALL_ROWS = 6;
	public static final int MAX_BEDS = 8;

	/** The classic house: 7x7, one storey, walls 4 high, a stepped pyramid roof, one bed. */
	public static HouseSpec standard(HouseStyle.Palette palette) {
		return new Draft(palette).build();
	}

	/** Half the width: the side walls stand at x = ±half. */
	public int half() {
		return width / 2;
	}

	/** Mutable while a description is read; {@link #build()} clamps everything into range. */
	public static final class Draft {
		public int width = 7;
		public int depth = 7;
		public int floors = 1;
		public int wallRows = 4;
		public Roof roof = Roof.PYRAMID;
		public BlockState walls;
		public BlockState corners;
		public BlockState roofBlock;
		public BlockState floor;
		public BlockState foundation;
		public Block door;
		public BlockState window = Blocks.GLASS_PANE.defaultBlockState();
		public boolean bigWindows;
		public boolean chimney;
		public boolean porch;
		public boolean balcony;
		public boolean garden;
		public boolean pool;
		public boolean tower;
		public boolean library;
		public boolean kitchen;
		public int beds = 1;

		public Draft(HouseStyle.Palette palette) {
			usePalette(palette);
		}

		public Draft usePalette(HouseStyle.Palette palette) {
			walls = palette.walls();
			corners = palette.corners();
			roofBlock = palette.roof();
			floor = palette.floor();
			foundation = palette.foundation();
			door = palette.door();
			return this;
		}

		public HouseSpec build() {
			int w = Mth.clamp(width, MIN_SIZE, MAX_SIZE) | 1; // odd, so the door sits in the middle
			if (w > MAX_SIZE) {
				w = MAX_SIZE;
			}
			int f = Mth.clamp(floors, 1, MAX_FLOORS);
			return new HouseSpec(w, Mth.clamp(depth, MIN_SIZE, MAX_SIZE), f, Mth.clamp(wallRows, MIN_WALL_ROWS, MAX_WALL_ROWS),
					roof, new HouseStyle.Palette(walls, corners, roofBlock, floor, foundation, door), window, bigWindows,
					chimney, porch, balcony && f > 1, garden, pool, tower, library, kitchen, Mth.clamp(beds, 0, MAX_BEDS));
		}
	}
}
