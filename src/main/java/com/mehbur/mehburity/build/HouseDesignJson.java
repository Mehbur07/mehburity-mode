package com.mehbur.mehburity.build;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mehbur.mehburity.MehburityMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Gemini's take on a house description: the instructions it gets, and reading its JSON answer into a
 * {@link HouseSpec.Draft}. Every value is checked; anything missing, unknown or unsafe (sand that falls, operator
 * blocks, doors that need redstone) keeps what the keyword reading of the description gave.
 */
public final class HouseDesignJson {
	/** Sent as Gemini's system instruction; the owner's description is the message. */
	public static final String SYSTEM_INSTRUCTION = """
			You design Minecraft houses for Mehburity, a companion that builds them block by block. Read the player's \
			request (any language) and answer with ONLY one JSON object: no prose, no code fences. Every field is \
			optional; leave out what the request does not suggest. Fields:
			"width" (5-21, blocks, odd), "depth" (5-21), "floors" (1-4), "floor_height" (3-6 wall blocks per floor),
			"roof": "pyramid" | "gable" | "flat",
			"walls", "corners", "roof_block", "floor", "foundation": Minecraft block ids such as "minecraft:white_concrete" \
			(full solid blocks only; never sand, gravel or concrete powder),
			"door": a wooden door id such as "minecraft:spruce_door",
			"window": a glass or glass pane id such as "minecraft:light_blue_stained_glass_pane",
			"big_windows", "chimney", "porch", "balcony", "garden", "pool", "tower", "library", "kitchen": true/false,
			"beds": 0-8.
			Choose materials and colours that fit the style asked for (modern: white_concrete walls, gray_concrete corners, \
			flat roof, big windows; medieval: cobblestone, stone_bricks, dark_oak_planks roof; japanese: white_terracotta, \
			dark_oak_log corners, gable roof; cosy cabin: spruce_planks, spruce_log, chimney). Keep it buildable, and \
			not huge unless the request asks for it.""";

	private HouseDesignJson() {
	}

	/** Applies the JSON object in {@code answer} (text around it is ignored) to {@code draft}. False if none found. */
	public static boolean apply(String answer, HouseSpec.Draft draft) {
		int start = answer.indexOf('{');
		int end = answer.lastIndexOf('}');
		if (start < 0 || end <= start) {
			return false;
		}
		JsonObject json;
		try {
			JsonElement parsed = JsonParser.parseString(answer.substring(start, end + 1));
			if (!parsed.isJsonObject()) {
				return false;
			}
			json = parsed.getAsJsonObject();
		} catch (RuntimeException e) {
			MehburityMod.LOGGER.debug("Unreadable house design from Gemini: {}", answer, e);
			return false;
		}
		draft.width = integer(json, "width", draft.width);
		draft.depth = integer(json, "depth", draft.depth);
		draft.floors = integer(json, "floors", draft.floors);
		draft.wallRows = integer(json, "floor_height", draft.wallRows);
		draft.beds = integer(json, "beds", draft.beds);
		String roof = text(json, "roof");
		if (roof != null) {
			switch (roof.toLowerCase(Locale.ROOT)) {
				case "pyramid" -> draft.roof = HouseSpec.Roof.PYRAMID;
				case "gable" -> draft.roof = HouseSpec.Roof.GABLE;
				case "flat" -> draft.roof = HouseSpec.Roof.FLAT;
				default -> {
				}
			}
		}
		draft.walls = solid(json, "walls", draft.walls);
		draft.corners = solid(json, "corners", draft.corners);
		draft.roofBlock = solid(json, "roof_block", draft.roofBlock);
		draft.floor = solid(json, "floor", draft.floor);
		draft.foundation = solid(json, "foundation", draft.foundation);
		Block door = block(text(json, "door"));
		if (door instanceof DoorBlock && door != Blocks.IRON_DOOR) {
			draft.door = door;
		}
		Block window = block(text(json, "window"));
		if (window != null && BuiltInRegistries.BLOCK.getKey(window).getPath().contains("glass")
				&& !window.defaultBlockState().hasBlockEntity()) {
			draft.window = window.defaultBlockState();
		}
		draft.bigWindows = flag(json, "big_windows", draft.bigWindows);
		draft.chimney = flag(json, "chimney", draft.chimney);
		draft.porch = flag(json, "porch", draft.porch);
		draft.balcony = flag(json, "balcony", draft.balcony);
		draft.garden = flag(json, "garden", draft.garden);
		draft.pool = flag(json, "pool", draft.pool);
		draft.tower = flag(json, "tower", draft.tower);
		draft.library = flag(json, "library", draft.library);
		draft.kitchen = flag(json, "kitchen", draft.kitchen);
		if (draft.balcony && draft.floors < 2) {
			draft.floors = 2;
		}
		return true;
	}

	/** A block that can be a wall: full, solid, breakable, staying where it is put, without contents. */
	static boolean isSolidBuildingBlock(Block block) {
		BlockState state = block.defaultBlockState();
		return !(block instanceof GameMasterBlock) && !(block instanceof FallingBlock) && !(block instanceof LiquidBlock)
				&& !state.hasBlockEntity() && state.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) >= 0.0F
				&& state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
	}

	private static BlockState solid(JsonObject json, String key, BlockState fallback) {
		Block block = block(text(json, key));
		return block != null && isSolidBuildingBlock(block) ? block.defaultBlockState() : fallback;
	}

	private static @Nullable Block block(@Nullable String id) {
		if (id == null) {
			return null;
		}
		Identifier key = Identifier.tryParse(id.strip().toLowerCase(Locale.ROOT));
		return key == null ? null : BuiltInRegistries.BLOCK.getOptional(key).filter(block -> block != Blocks.AIR).orElse(null);
	}

	private static @Nullable String text(JsonObject json, String key) {
		JsonElement value = json.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}

	private static int integer(JsonObject json, String key, int fallback) {
		JsonElement value = json.get(key);
		try {
			return value != null && value.isJsonPrimitive() ? (int) Math.round(value.getAsDouble()) : fallback;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static boolean flag(JsonObject json, String key, boolean fallback) {
		JsonElement value = json.get(key);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() ? value.getAsBoolean() : fallback;
	}
}
