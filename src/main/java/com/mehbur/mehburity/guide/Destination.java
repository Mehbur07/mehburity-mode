package com.mehbur.mehburity.guide;

import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A kind of structure Mehburity can lead the way to, with the words that name it in chat (accent-free lower case,
 * Turkish and English). Listed in matching order: the more specific names come first ("nether kalesi" before "kale",
 * "antik şehir" before "şehir").
 */
public enum Destination {
	FORTRESS("nether_kalesi", false, "nether kale\\w*|\\bfortress\\b", BuiltinStructures.FORTRESS),
	BASTION("bastion", false, "\\bbastion\\w*|piglin kale\\w*", BuiltinStructures.BASTION_REMNANT),
	END_CITY("end_sehri", false, "\\b(end|son) seh(i|r)\\w*|\\bend city\\b", BuiltinStructures.END_CITY),
	ANCIENT_CITY("antik_sehir", true, "\\b(antik|kadim) seh(i|r)\\w*|\\bancient city\\b", BuiltinStructures.ANCIENT_CITY),
	OCEAN_RUIN("okyanus_harabesi", false, "okyanus harabe\\w*|\\bocean ruins?\\b", StructureTags.OCEAN_RUIN),
	MONUMENT("okyanus_aniti", false, "\\banit\\w*|\\bmonument\\b", BuiltinStructures.OCEAN_MONUMENT),
	MANSION("orman_konagi", false, "\\bkona(k|g)\\w*|\\bmalikane\\w*|\\bmansion\\b", BuiltinStructures.WOODLAND_MANSION),
	DESERT_TEMPLE("col_tapinagi", false, "\\bcol tapina\\w*|\\bpiramit\\w*|\\bdesert (temple|pyramid)\\b|\\bpyramid\\b",
			BuiltinStructures.DESERT_PYRAMID),
	JUNGLE_TEMPLE("orman_tapinagi", false, "\\b(orman|cengel|balta) tapina\\w*|\\bjungle (temple|pyramid)\\b",
			BuiltinStructures.JUNGLE_TEMPLE),
	SWAMP_HUT("cadi_kulubesi", false, "\\bcadi (kulube|evi)\\w*|\\b(witch|swamp) hut\\b", BuiltinStructures.SWAMP_HUT),
	IGLOO("iglo", false, "\\biglo\\w*|\\bigloo\\b", BuiltinStructures.IGLOO),
	OUTPOST("karakol", false, "\\bkarakol\\w*|\\boutpost\\b", BuiltinStructures.PILLAGER_OUTPOST),
	SHIPWRECK("batik_gemi", false, "\\bbatik gemi\\w*|\\bgemi enkaz\\w*|\\bshipwreck\\b", StructureTags.SHIPWRECK),
	RUINED_PORTAL("harap_portal", false, "\\b(harap|yikik|bozuk|kirik|eski) portal\\w*|\\bruined portal\\b",
			StructureTags.RUINED_PORTAL),
	MINESHAFT("maden_ocagi", true, "\\bmaden ocag\\w*|\\bterk edilmis maden\\w*|\\bmadene\\b|\\bmineshaft\\b",
			StructureTags.MINESHAFT),
	TRAIL_RUINS("patika_harabeleri", true, "\\b(patika|iz) harabe\\w*|\\btrail ruins?\\b", BuiltinStructures.TRAIL_RUINS),
	TRIAL_CHAMBERS("deneme_odalari", true, "\\b(deneme|sinav) oda\\w*|\\btrial chambers?\\b", BuiltinStructures.TRIAL_CHAMBERS),
	BURIED_TREASURE("gomulu_hazine", true, "\\bhazine\\w*|\\b(buried )?treasure\\b", BuiltinStructures.BURIED_TREASURE),
	STRONGHOLD("kale", true, "\\bkale\\w*|\\bstronghold\\b|\\b(end|son) portal\\w*", BuiltinStructures.STRONGHOLD),
	VILLAGE("koy", false, "\\bkoy\\w*|\\bkasaba\\w*|\\bseh(i|r)\\w*|\\bvillages?\\b|\\btown\\b|\\bcity\\b", StructureTags.VILLAGE);

	private final String id;
	private final boolean underground;
	private final Pattern words;
	private final @Nullable TagKey<Structure> tag;
	private final @Nullable ResourceKey<Structure> key;

	Destination(String id, boolean underground, String words, TagKey<Structure> tag) {
		this.id = id;
		this.underground = underground;
		this.words = Pattern.compile(words);
		this.tag = tag;
		this.key = null;
	}

	Destination(String id, boolean underground, String words, ResourceKey<Structure> key) {
		this.id = id;
		this.underground = underground;
		this.words = Pattern.compile(words);
		this.tag = null;
		this.key = key;
	}

	/** The ASCII name used by {@code /mehburity gotur <id>} and in the language keys. */
	public String id() {
		return id;
	}

	/** Built (mostly) under the ground: Mehburity can only lead to the spot above it. */
	public boolean underground() {
		return underground;
	}

	/** The first destination named in {@code folded} (accent-free lower case), or null. */
	public static @Nullable Destination find(String folded) {
		for (Destination destination : values()) {
			if (destination.words.matcher(folded).find()) {
				return destination;
			}
		}
		return null;
	}

	public static @Nullable Destination byId(String id) {
		for (Destination destination : values()) {
			if (destination.id.equals(id)) {
				return destination;
			}
		}
		return null;
	}

	/** In the Nether, "kale" means its fortress rather than a stronghold, which only exists in the overworld. */
	public Destination in(ServerLevel level) {
		return this == STRONGHOLD && level.dimension() == Level.NETHER ? FORTRESS : this;
	}

	/** The structures this stands for in {@code registry}; empty when a data pack removed them. */
	Optional<? extends HolderSet<Structure>> holders(Registry<Structure> registry) {
		if (tag != null) {
			return registry.get(tag);
		}
		return registry.get(key).map(holder -> HolderSet.direct(holder));
	}

	/** Every id, for command suggestions. */
	public static List<String> ids() {
		List<String> ids = new ArrayList<>();
		for (Destination destination : values()) {
			ids.add(destination.id);
		}
		return ids;
	}
}
