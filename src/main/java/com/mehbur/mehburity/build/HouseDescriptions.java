package com.mehbur.mehburity.build;

import com.mehbur.mehburity.ai.ChatAddress;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a house description in plain Turkish or English ("iki katlı, havuzlu, kırmızı çatılı beyaz bir ev",
 * "a big medieval stone house with a tower") into a {@link HouseSpec.Draft}: size, storeys, roof, materials, colours
 * and extras. Works without Gemini; with Gemini the design is refined further ({@link HouseDesignJson}). Matching
 * runs on accent-free lower case ({@link ChatAddress#fold}).
 */
public final class HouseDescriptions {
	/** Walls, corner pillars, roof, floor and door of one material. */
	private record Material(Pattern words, BlockState walls, BlockState corners, BlockState roof, BlockState floor, Block door) {
	}

	private static final List<Material> MATERIALS = List.of(
			// Before the plain woods and stones whose words they contain ("koyu mese" vs "mese", "kumtasi" vs "tas").
			wood("(koyu mese|dark oak)", Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_LOG, Blocks.DEEPSLATE_TILES, Blocks.DARK_OAK_DOOR),
			material("(kum ?tasi|\\bcol\\w*|sandstone|desert)", Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.RED_SANDSTONE,
					Blocks.SMOOTH_SANDSTONE, Blocks.ACACIA_DOOR),
			material("(kirik tas|parke tas|cobble\\w*)", Blocks.COBBLESTONE, pillar(Blocks.OAK_LOG), Blocks.SPRUCE_PLANKS,
					Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_DOOR),
			material("(kara ?tas|blackstone)", Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.POLISHED_BLACKSTONE, Blocks.BLACKSTONE,
					Blocks.DARK_OAK_PLANKS, Blocks.CRIMSON_DOOR),
			wood("(\\bhus\\w*|birch)", Blocks.BIRCH_PLANKS, Blocks.BIRCH_LOG, Blocks.SPRUCE_PLANKS, Blocks.BIRCH_DOOR),
			wood("(ladin|cam agac\\w*|spruce|pine)", Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG, Blocks.DARK_OAK_PLANKS, Blocks.SPRUCE_DOOR),
			wood("(akasya|acacia)", Blocks.ACACIA_PLANKS, Blocks.ACACIA_LOG, Blocks.DARK_OAK_PLANKS, Blocks.ACACIA_DOOR),
			wood("(kiraz|sakura|cherry)", Blocks.CHERRY_PLANKS, Blocks.CHERRY_LOG, Blocks.DARK_OAK_PLANKS, Blocks.CHERRY_DOOR),
			wood("(\\borman\\w*|jungle)", Blocks.JUNGLE_PLANKS, Blocks.JUNGLE_LOG, Blocks.SPRUCE_PLANKS, Blocks.JUNGLE_DOOR),
			wood("(mangrov\\w*)", Blocks.MANGROVE_PLANKS, Blocks.MANGROVE_LOG, Blocks.DARK_OAK_PLANKS, Blocks.MANGROVE_DOOR),
			material("(bambu|bamboo)", Blocks.BAMBOO_PLANKS, pillar(Blocks.BAMBOO_BLOCK), Blocks.BAMBOO_MOSAIC, Blocks.BAMBOO_PLANKS,
					Blocks.BAMBOO_DOOR),
			wood("(\\bmese\\w*|\\boak\\b)", Blocks.OAK_PLANKS, Blocks.OAK_LOG, Blocks.SPRUCE_PLANKS, Blocks.OAK_DOOR),
			material("(kuvars|mermer|quartz|marble)", Blocks.QUARTZ_BLOCK, pillar(Blocks.QUARTZ_PILLAR), Blocks.SMOOTH_QUARTZ,
					Blocks.BIRCH_PLANKS, Blocks.BIRCH_DOOR),
			material("(derin ?tas|deepslate)", Blocks.DEEPSLATE_BRICKS, Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_TILES,
					Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_DOOR),
			material("(\\bnether\\b)", Blocks.NETHER_BRICKS, Blocks.RED_NETHER_BRICKS, Blocks.NETHER_BRICKS, Blocks.CRIMSON_PLANKS,
					Blocks.CRIMSON_DOOR),
			material("(prizmarin|prismarine|okyanus|ocean)", Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE, Blocks.DARK_PRISMARINE,
					Blocks.WARPED_PLANKS, Blocks.WARPED_DOOR),
			material("(kerpic|camur|\\bmud\\b)", Blocks.MUD_BRICKS, pillar(Blocks.OAK_LOG), Blocks.SPRUCE_PLANKS, Blocks.OAK_PLANKS,
					Blocks.OAK_DOOR),
			material("(\\bbuz\\w*|\\bice\\b)", Blocks.PACKED_ICE, Blocks.BLUE_ICE, Blocks.SNOW_BLOCK, Blocks.SPRUCE_PLANKS,
					Blocks.SPRUCE_DOOR),
			material("(\\bkar\\b|kardan|snow|igloo)", Blocks.SNOW_BLOCK, Blocks.PACKED_ICE, Blocks.SNOW_BLOCK, Blocks.SPRUCE_PLANKS,
					Blocks.SPRUCE_DOOR),
			material("(altin|\\bgold\\w*)", Blocks.GOLD_BLOCK, pillar(Blocks.QUARTZ_PILLAR), Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BLOCK,
					Blocks.BIRCH_DOOR),
			material("(elmas|diamond)", Blocks.DIAMOND_BLOCK, pillar(Blocks.QUARTZ_PILLAR), Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BLOCK,
					Blocks.BIRCH_DOOR),
			material("(zumrut|emerald)", Blocks.EMERALD_BLOCK, pillar(Blocks.QUARTZ_PILLAR), Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BLOCK,
					Blocks.BIRCH_DOOR),
			material("(obsidyen|obsidian)", Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.OBSIDIAN, Blocks.POLISHED_BLACKSTONE,
					Blocks.CRIMSON_DOOR),
			material("(ametist|amethyst)", Blocks.AMETHYST_BLOCK, pillar(Blocks.PURPUR_PILLAR), Blocks.PURPUR_BLOCK, Blocks.PURPUR_BLOCK,
					Blocks.WARPED_DOOR),
			// Waxed, so it never turns green.
			material("(bakir|copper)", Blocks.CUT_COPPER.waxed().unaffected(), Blocks.COPPER_BLOCK.waxed().unaffected(),
					Blocks.CUT_COPPER.waxed().unaffected(), Blocks.SPRUCE_PLANKS, Blocks.COPPER_DOOR.waxed().unaffected()),
			material("(\\bcam\\b|\\bcamdan\\b|\\bglass\\b)", Blocks.GLASS, Blocks.CONCRETE.white(), Blocks.CONCRETE.white(), Blocks.BIRCH_PLANKS,
					Blocks.BIRCH_DOOR),
			material("(beton|concrete)", Blocks.CONCRETE.white(), Blocks.CONCRETE.gray(), Blocks.CONCRETE.gray(), Blocks.SMOOTH_STONE,
					Blocks.BIRCH_DOOR),
			material("(tugla|brick)", Blocks.BRICKS, Blocks.STONE_BRICKS, Blocks.DEEPSLATE_TILES, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_DOOR),
			material("(\\btas(tan|li|i)?\\b|\\bstone\\b)", Blocks.STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS, Blocks.DARK_OAK_PLANKS,
					Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_DOOR),
			wood("(ahsap|tahta|\\bodun\\w*|\\bwood\\w*|timber|log cabin)", Blocks.OAK_PLANKS, Blocks.OAK_LOG, Blocks.SPRUCE_PLANKS,
					Blocks.OAK_DOOR));

	/** Colour words (longest first) to concrete. */
	private static final Map<String, Block> COLOURS = colours();
	private static final Pattern COLOUR = Pattern.compile("\\b(" + String.join("|", COLOURS.keySet()) + ")\\b");
	/** A colour right before "çatı(lı)" / "roof" is the roof's. */
	private static final String ROOF_WORD = "\\s+(cati\\w*|roof\\w*)";

	private static final String NUMBER = "(\\d{1,2}|bir|tek|iki|uc|dort|bes|alti|yedi|sekiz|one|two|three|four|five|six|seven|eight|single|double)";
	private static final Pattern FLOORS = Pattern.compile(NUMBER + "[ -]?(kat\\w*|stor(e)?y|stories|storeys|floors?)");
	private static final Pattern MANY_FLOORS = Pattern.compile("(cok katli|multi[ -]?stor\\w*|dubleks|duplex)");
	private static final Pattern BEDS = Pattern.compile(NUMBER + "[ -]?(yatak\\w*|kisilik|beds?|bedrooms?)");
	private static final Pattern SIZE = Pattern.compile("(\\d{1,2})\\s*[x*]\\s*(\\d{1,2})");
	private static final Pattern SMALL = Pattern.compile("(kucuk|minik|ufak|small|tiny|little|kulube\\w*|\\bhut\\b|cabin)");
	/** Not "büyük pencereli" (big windows), which says nothing about the size of the house. */
	private static final Pattern BIG = Pattern.compile("(buyuk|genis|ferah|\\bbig\\b|large|spacious|wide)(?!\\s+(pencere|window))");
	private static final Pattern HUGE = Pattern.compile("(kocaman|devasa|\\bdev\\b|cok buyuk|huge|giant|enormous|massive)");
	private static final Pattern HIGH = Pattern.compile("(yuksek tavan\\w*|high ceiling\\w*|tall ceiling\\w*)");
	private static final Pattern LOW = Pattern.compile("(basik|alcak tavan\\w*|low ceiling\\w*)");
	private static final Pattern FLAT = Pattern.compile("(duz cati\\w*|flat roof\\w*|teras cati\\w*|cati(si)? yok)");
	private static final Pattern GABLE = Pattern.compile("(sivri|ucgen|besik|\\ba cati\\w*|gable\\w*|pitched|a-frame|slanted)");
	private static final Pattern PYRAMID = Pattern.compile("(piramit|pyramid)");
	private static final Pattern POOL = Pattern.compile("(havuz\\w*|\\bpool\\b|swimming)");
	private static final Pattern GARDEN = Pattern.compile("(bahce\\w*|\\bcit\\w*|cicek\\w*|garden|\\byard\\b|fence\\w*|flower\\w*)");
	private static final Pattern CHIMNEY = Pattern.compile("(baca\\w*|somine\\w*|chimney|fireplace)");
	private static final Pattern PORCH = Pattern.compile("(veranda\\w*|sundurma\\w*|porch)");
	private static final Pattern BALCONY = Pattern.compile("(balkon\\w*|balcon\\w*|teras\\w*|terrace)");
	private static final Pattern TOWER = Pattern.compile("(kule\\w*|tower|\\bkale\\w*|sato\\w*|castle)");
	private static final Pattern LIBRARY = Pattern.compile("(kutuphane\\w*|kitap\\w*|library|bookshel\\w*)");
	private static final Pattern KITCHEN = Pattern.compile("(mutfak\\w*|kitchen)");
	private static final Pattern BIG_WINDOWS = Pattern.compile(
			"((buyuk|genis|bol|cok) pencere\\w*|cam duvar\\w*|panoramik|big windows?|large windows?|lots of windows|glass walls?)");
	private static final Pattern MODERN = Pattern.compile("(modern|minimalist|cagdas)");
	private static final Pattern MEDIEVAL = Pattern.compile("(ortacag\\w*|medieval|\\bkale\\w*|sato\\w*|castle)");
	private static final Pattern JAPANESE = Pattern.compile("(japon\\w*|japanese|pagoda)");
	private static final Pattern WINTER = Pattern.compile("(\\bkis\\b|kislik|karli|winter|snowy)");
	private static final Pattern VILLA = Pattern.compile("(villa\\w*)");
	private static final Pattern MANSION = Pattern.compile("(malikane\\w*|konak\\w*|saray\\w*|mansion|palace)");
	private static final Pattern FAMILY = Pattern.compile("(aile\\w*|family)");

	private HouseDescriptions() {
	}

	/** {@code base} (the style asked for, or the default one) changed by everything {@code message} says. */
	public static HouseSpec.Draft read(String message, HouseStyle.Palette base) {
		String text = ChatAddress.fold(message);
		HouseSpec.Draft draft = new HouseSpec.Draft(base);

		// Kinds of houses first; everything said explicitly below wins over them.
		if (MODERN.matcher(text).find()) {
			use(draft, Blocks.CONCRETE.white(), Blocks.CONCRETE.gray(), Blocks.CONCRETE.gray(), Blocks.SMOOTH_STONE, Blocks.BIRCH_DOOR);
			draft.roof = HouseSpec.Roof.FLAT;
			draft.bigWindows = true;
		}
		if (MEDIEVAL.matcher(text).find()) {
			use(draft, Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.DARK_OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_DOOR);
		}
		if (JAPANESE.matcher(text).find()) {
			use(draft, Blocks.DYED_TERRACOTTA.white(), pillar(Blocks.DARK_OAK_LOG), Blocks.DEEPSLATE_TILES, Blocks.DARK_OAK_PLANKS,
					Blocks.CHERRY_DOOR);
			draft.roof = HouseSpec.Roof.GABLE;
			draft.garden = true;
		}
		if (WINTER.matcher(text).find()) {
			use(draft, Blocks.SPRUCE_PLANKS, pillar(Blocks.SPRUCE_LOG), Blocks.SNOW_BLOCK, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_DOOR);
			draft.roof = HouseSpec.Roof.GABLE;
			draft.chimney = true;
		}
		if (VILLA.matcher(text).find()) {
			use(draft, Blocks.QUARTZ_BLOCK, pillar(Blocks.QUARTZ_PILLAR), Blocks.SMOOTH_QUARTZ, Blocks.BIRCH_PLANKS, Blocks.BIRCH_DOOR);
			draft.width = 11;
			draft.depth = 9;
			draft.floors = 2;
			draft.pool = true;
			draft.garden = true;
			draft.bigWindows = true;
			draft.balcony = true;
			draft.beds = 2;
		}
		if (MANSION.matcher(text).find()) {
			draft.width = 15;
			draft.depth = 13;
			draft.floors = 3;
			draft.garden = true;
			draft.library = true;
			draft.chimney = true;
			draft.kitchen = true;
			draft.beds = 4;
		}
		if (SMALL.matcher(text).find()) {
			draft.width = 5;
			draft.depth = 5;
		}
		if (BIG.matcher(text).find()) {
			draft.width = Math.max(draft.width, 11);
			draft.depth = Math.max(draft.depth, 9);
		}
		if (HUGE.matcher(text).find()) {
			draft.width = Math.max(draft.width, 17);
			draft.depth = Math.max(draft.depth, 13);
			draft.floors = Math.max(draft.floors, 2);
		}
		Matcher size = SIZE.matcher(text);
		if (size.find()) {
			draft.width = Integer.parseInt(size.group(1));
			draft.depth = Integer.parseInt(size.group(2));
		}

		Matcher floors = FLOORS.matcher(text);
		if (floors.find()) {
			draft.floors = number(floors.group(1));
		} else if (MANY_FLOORS.matcher(text).find()) {
			draft.floors = Math.max(draft.floors, text.contains("dubleks") || text.contains("duplex") ? 2 : 3);
		}
		Matcher beds = BEDS.matcher(text);
		if (beds.find()) {
			draft.beds = number(beds.group(1));
		} else if (FAMILY.matcher(text).find()) {
			draft.beds = Math.max(draft.beds, 3);
		}
		if (HIGH.matcher(text).find()) {
			draft.wallRows = 5;
		} else if (LOW.matcher(text).find()) {
			draft.wallRows = 3;
		}

		if (FLAT.matcher(text).find()) {
			draft.roof = HouseSpec.Roof.FLAT;
		} else if (GABLE.matcher(text).find()) {
			draft.roof = HouseSpec.Roof.GABLE;
		} else if (PYRAMID.matcher(text).find()) {
			draft.roof = HouseSpec.Roof.PYRAMID;
		}

		materials(text, draft);
		colours(text, draft);

		draft.pool |= POOL.matcher(text).find();
		draft.garden |= GARDEN.matcher(text).find();
		draft.chimney |= CHIMNEY.matcher(text).find();
		draft.porch |= PORCH.matcher(text).find();
		draft.balcony |= BALCONY.matcher(text).find();
		draft.tower |= TOWER.matcher(text).find();
		draft.library |= LIBRARY.matcher(text).find();
		draft.kitchen |= KITCHEN.matcher(text).find();
		draft.bigWindows |= BIG_WINDOWS.matcher(text).find();
		if (draft.balcony && draft.floors < 2) {
			draft.floors = 2; // a balcony needs an upper floor
		}
		return draft;
	}

	/** The first material named; a second one becomes the roof ("taş duvarlı, ahşap çatılı"). */
	private static void materials(String text, HouseSpec.Draft draft) {
		Material walls = null;
		int at = Integer.MAX_VALUE;
		for (Material material : MATERIALS) {
			Matcher matcher = material.words().matcher(text);
			if (matcher.find() && matcher.start() < at) {
				walls = material;
				at = matcher.start();
			}
		}
		if (walls == null) {
			return;
		}
		use(draft, walls);
		if (text.contains("cati") || text.contains("roof")) {
			for (Material material : MATERIALS) {
				Matcher matcher = Pattern.compile(material.words().pattern() + ROOF_WORD).matcher(text);
				if (material != walls && matcher.find()) {
					draft.roofBlock = material.roof();
				}
			}
		}
	}

	/** "kırmızı çatılı" colours the roof; any other colour word the walls. */
	private static void colours(String text, HouseSpec.Draft draft) {
		Matcher matcher = COLOUR.matcher(text);
		while (matcher.find()) {
			Block concrete = COLOURS.get(matcher.group(1));
			boolean roof = Pattern.compile("^" + ROOF_WORD).matcher(text.substring(matcher.end())).find();
			if (roof) {
				draft.roofBlock = concrete.defaultBlockState();
			} else {
				draft.walls = concrete.defaultBlockState();
			}
		}
	}

	private static int number(String word) {
		return switch (word) {
			case "bir", "tek", "one", "single" -> 1;
			case "iki", "two", "double" -> 2;
			case "uc", "three" -> 3;
			case "dort", "four" -> 4;
			case "bes", "five" -> 5;
			case "alti", "six" -> 6;
			case "yedi", "seven" -> 7;
			case "sekiz", "eight" -> 8;
			default -> Integer.parseInt(word);
		};
	}

	private static void use(HouseSpec.Draft draft, Material material) {
		draft.walls = material.walls();
		draft.corners = material.corners();
		draft.roofBlock = material.roof();
		draft.floor = material.floor();
		draft.door = material.door();
	}

	private static void use(HouseSpec.Draft draft, Block walls, Object corners, Block roof, Block floor, Block door) {
		use(draft, new Material(Pattern.compile("$^"), walls.defaultBlockState(), state(corners), roof.defaultBlockState(),
				floor.defaultBlockState(), door));
	}

	private static Material wood(String words, Block planks, Block log, Block roof, Block door) {
		return new Material(Pattern.compile(words), planks.defaultBlockState(), pillar(log), roof.defaultBlockState(),
				planks.defaultBlockState(), door);
	}

	private static Material material(String words, Block walls, Object corners, Block roof, Block floor, Block door) {
		return new Material(Pattern.compile(words), walls.defaultBlockState(), state(corners), roof.defaultBlockState(),
				floor.defaultBlockState(), door);
	}

	private static BlockState state(Object block) {
		return block instanceof BlockState state ? state : ((Block) block).defaultBlockState();
	}

	private static BlockState pillar(Block log) {
		return log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
	}

	private static Map<String, Block> colours() {
		// Ordered so longer names match before the colours they contain ("acik mavi" before "mavi").
		var map = new java.util.LinkedHashMap<String, Block>();
		map.put("acik mavi", Blocks.CONCRETE.lightBlue());
		map.put("light blue", Blocks.CONCRETE.lightBlue());
		map.put("acik gri", Blocks.CONCRETE.lightGray());
		map.put("light gray", Blocks.CONCRETE.lightGray());
		map.put("acik yesil", Blocks.CONCRETE.lime());
		map.put("beyaz", Blocks.CONCRETE.white());
		map.put("white", Blocks.CONCRETE.white());
		map.put("siyah", Blocks.CONCRETE.black());
		map.put("black", Blocks.CONCRETE.black());
		map.put("kirmizi", Blocks.CONCRETE.red());
		map.put("red", Blocks.CONCRETE.red());
		map.put("lacivert", Blocks.CONCRETE.blue());
		map.put("mavi", Blocks.CONCRETE.blue());
		map.put("blue", Blocks.CONCRETE.blue());
		map.put("turkuaz", Blocks.CONCRETE.cyan());
		map.put("cyan", Blocks.CONCRETE.cyan());
		map.put("yesil", Blocks.CONCRETE.green());
		map.put("green", Blocks.CONCRETE.green());
		map.put("lime", Blocks.CONCRETE.lime());
		map.put("sari", Blocks.CONCRETE.yellow());
		map.put("yellow", Blocks.CONCRETE.yellow());
		map.put("turuncu", Blocks.CONCRETE.orange());
		map.put("orange", Blocks.CONCRETE.orange());
		map.put("mor", Blocks.CONCRETE.purple());
		map.put("purple", Blocks.CONCRETE.purple());
		map.put("pembe", Blocks.CONCRETE.pink());
		map.put("pink", Blocks.CONCRETE.pink());
		map.put("gri", Blocks.CONCRETE.gray());
		map.put("gray", Blocks.CONCRETE.gray());
		map.put("grey", Blocks.CONCRETE.gray());
		map.put("kahverengi", Blocks.CONCRETE.brown());
		map.put("kahve", Blocks.CONCRETE.brown());
		map.put("brown", Blocks.CONCRETE.brown());
		return map;
	}
}
