package com.mehbur.mehburity.build;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * The house looks. {@link #HELD} uses the block in the owner's hand for the walls and the wood palette for the rest.
 * Names are the ASCII words used in {@code /mehburity ev <style>} and in {@code build.defaultStyle}.
 */
public enum HouseStyle {
	WOOD("ahsap", new Palette(Blocks.OAK_PLANKS.defaultBlockState(), pillar(Blocks.OAK_LOG),
			Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(),
			Blocks.OAK_DOOR)),
	STONE("tas", new Palette(Blocks.STONE_BRICKS.defaultBlockState(), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(),
			Blocks.DARK_OAK_PLANKS.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(),
			Blocks.SPRUCE_DOOR)),
	BRICK("tugla", new Palette(Blocks.BRICKS.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(),
			Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(),
			Blocks.DARK_OAK_DOOR)),
	HELD("elimdeki", WOOD.palette);

	/** Blocks for each part of the house. */
	public record Palette(BlockState walls, BlockState corners, BlockState roof, BlockState floor, BlockState foundation, Block door) {
		Palette withWalls(BlockState newWalls) {
			return new Palette(newWalls, corners, roof, floor, foundation, door);
		}
	}

	private final String id;
	private final Palette palette;

	HouseStyle(String id, Palette palette) {
		this.id = id;
		this.palette = palette;
	}

	public String id() {
		return id;
	}

	public Palette palette() {
		return palette;
	}

	public static @Nullable HouseStyle byId(String id) {
		String wanted = id.strip().toLowerCase(Locale.ROOT);
		for (HouseStyle style : values()) {
			if (style.id.equals(wanted)) {
				return style;
			}
		}
		return null;
	}

	private static BlockState pillar(Block log) {
		return log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
	}
}
