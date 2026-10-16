package com.mehbur.mehburity.config;

import com.mehbur.mehburity.MehburityMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * All gameplay numbers and word lists, stored in {@code config/mehburity.json}.
 * Each feature adds its own section here, so there is exactly one file to edit.
 * Client-only secrets (the Gemini API key) live in a separate client file and never reach a server.
 */
public final class MehburityConfig implements JsonConfigFile.Sanitizable {
	public static final int CURRENT_VERSION = 1;
	public static final String FILE_NAME = MehburityMod.MOD_ID + ".json";

	private static JsonConfigFile<MehburityConfig> file;
	private static MehburityConfig instance = new MehburityConfig();

	/** Bumped when a field changes meaning, so old files can be migrated. */
	public int configVersion = CURRENT_VERSION;
	public General general = new General();
	public Companion companion = new Companion();
	public Combat combat = new Combat();
	public Mining mining = new Mining();
	public Ai ai = new Ai();
	public Verity verity = new Verity();

	public static final class General {
		/** Extra log lines for troubleshooting. */
		public boolean debugLogging = false;
	}

	/** How Mehburity moves around its owner. Distances are in blocks. */
	public static final class Companion {
		/** Starts flying towards the owner when farther than this. */
		public double followStartDistance = 4.0;
		/** Stops when this close. */
		public double followStopDistance = 2.5;
		/** Teleports next to the owner when farther than this (or in another dimension). */
		public double teleportDistance = 20.0;
		/** Multiplier on the base flying speed while following. */
		public double followSpeed = 2.0;
	}

	/**
	 * Defending the owner with a neon beam. Each owner can also switch it off for their own Mehburity with
	 * {@code /mehburity savas kapat}. Distances are in blocks, times in ticks (20 ticks = 1 second).
	 */
	public static final class Combat {
		/** Server-wide switch; off means no Mehburity ever fights. */
		public boolean enabled = true;
		/** Damage of one beam shot (a zombie has 20 health). */
		public double damage = 6.0;
		/** Longest beam; Mehburity flies closer when the target is farther than this. */
		public double beamRange = 12.0;
		/** How long the beam charges before it hits. */
		public int chargeTicks = 12;
		/** Pause between two shots. */
		public int cooldownTicks = 16;
		/** Attacks whatever hurts the owner. */
		public boolean defendOwner = true;
		/** Attacks whatever the owner hits. */
		public boolean helpOwnerAttack = true;
		/** Attacks hostile mobs that are hunting the owner, before they strike. */
		public boolean guardAgainstHostiles = true;
		/** Attacks every hostile mob near the owner, even the ones that have not noticed them. */
		public boolean attackAllHostiles = false;
		/** Hostile mobs this close to the owner are noticed. */
		public double guardRadius = 16.0;
		/** Gives up on targets farther than this from the owner, so Mehburity never wanders off. */
		public double chaseRadius = 20.0;
		/** Lets Mehburity fight players who attack its owner (or whom the owner attacks). */
		public boolean fightPlayers = false;
		/** Kills count as the owner's: mobs drop their experience and player-only loot. */
		public boolean ownerGetsDrops = true;
	}

	/**
	 * Mining for the owner ("Mehburity, maden kaz"): Mehburity breaks ore blocks by itself, keeps the drops in its
	 * bag and hands them over when asked ("kazdığın madenleri ver"). The bag is saved with the owner.
	 */
	public static final class Mining {
		public boolean enabled = true;
		/** Blocks Mehburity mines when asked to mine. "#namespace:tag" for a block tag, or a block id. */
		public List<String> targets = new ArrayList<>(List.of("#c:ores"));
		/** Looks for ores this far around the owner (around Mehburity itself in STAY mode). */
		public int searchRadius = 12;
		/** Only mines ores with an open side, so it never "sees" ores hidden inside stone. */
		public boolean exposedOnly = true;
		/** Breaks blocks from this far away. */
		public double reach = 4.0;
		/** Breaking time per point of block hardness (iron ore has 3), in ticks. */
		public double ticksPerHardness = 8.0;
		public int minBreakTicks = 6;
		public int maxBreakTicks = 100;
		/** Drops are worked out as if this tool broke the block, so every ore drops its item. */
		public String tool = "minecraft:diamond_pickaxe";
		/** Bag size in stacks. */
		public int bagSlots = 27;
		/** Stops after this many blocks in one go. */
		public int maxBlocksPerTrip = 64;
		/** How far away a block the owner points at ("Mehburity, bunu kaz") may be. */
		public double pointRange = 24.0;
	}

	/**
	 * Talking to Mehburity. The Gemini key itself is a client setting: the server sends each question to the
	 * owner's client, which asks Gemini and sends the answer back.
	 */
	public static final class Ai {
		public boolean enabled = true;
		/** A chat message starting with one of these words (case-insensitive) is a question for Mehburity. */
		public List<String> chatPrefixes = new ArrayList<>(List.of("mehburity", "mehbur"));
		/** Players this close to the owner see Mehburity's answers. */
		public double hearingRadius = 32.0;
		/** After this, the server answers with a built-in reply instead. */
		public int requestTimeoutSeconds = 30;
		/** Minimum time between two questions from the same player. */
		public double cooldownSeconds = 2.0;
		public int maxMessageLength = 256;
		public int maxReplyLength = 400;
		/** Mehburity comments on things that happen to its owner (getting hurt, dying...). */
		public boolean commentOnEvents = true;
		public int eventCommentCooldownSeconds = 90;
		/** Said (and spoken) when Mehburity comes out of its box. */
		public String greeting = "Hello! I'm Mehburity. Your personal helper friend. Ask me anything. I know everything.";
		/** Extra personality notes added to Mehburity's instructions, e.g. "Talk like a pirate." */
		public String extraPersona = "";
	}

	/** Features adapted from Verity. Each one can be switched off on its own. */
	public static final class Verity {
		public Box box = new Box();
		public Darkness darkness = new Darkness();

		public static final class Box {
			/** Gives every player a box with their Mehburity inside on their first join. */
			public boolean enabled = true;
			/** How long the opening animation takes, in ticks (20 ticks = 1 second). */
			public int openTicks = 40;
		}

		public static final class Darkness {
			/** Much darker nights and caves: the brightness option stops helping and moonlight fades out. */
			public boolean enabled = true;
			/** 0 = vanilla lighting, 1 = pitch black without a light source. */
			public double strength = 0.85;
		}
	}

	public static MehburityConfig get() {
		return instance;
	}

	public static JsonConfigFile<MehburityConfig> file() {
		if (file == null) {
			file = new JsonConfigFile<>(FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME),
					MehburityConfig.class, MehburityConfig::new);
		}
		return file;
	}

	/** Loads the file (creating or repairing it if needed) and makes it the active config. */
	public static JsonConfigFile.Result<MehburityConfig> load() {
		JsonConfigFile.Result<MehburityConfig> result = file().load();
		instance = result.value();
		return result;
	}

	public static boolean save() {
		return file().save(instance);
	}

	@Override
	public void sanitize() {
		configVersion = CURRENT_VERSION;
		if (general == null) {
			general = new General();
		}
		if (companion == null) {
			companion = new Companion();
		}
		if (combat == null) {
			combat = new Combat();
		}
		if (mining == null) {
			mining = new Mining();
		}
		if (ai == null) {
			ai = new Ai();
		}
		if (verity == null) {
			verity = new Verity();
		}
		if (verity.box == null) {
			verity.box = new Verity.Box();
		}
		if (verity.darkness == null) {
			verity.darkness = new Verity.Darkness();
		}

		companion.followStopDistance = Mth.clamp(companion.followStopDistance, 1.0, 16.0);
		companion.followStartDistance = Mth.clamp(companion.followStartDistance, companion.followStopDistance, 32.0);
		companion.teleportDistance = Mth.clamp(companion.teleportDistance, companion.followStartDistance + 1.0, 128.0);
		companion.followSpeed = Mth.clamp(companion.followSpeed, 0.1, 10.0);
		combat.damage = Mth.clamp(combat.damage, 0.0, 1000.0);
		combat.beamRange = Mth.clamp(combat.beamRange, 2.0, 32.0);
		combat.chargeTicks = Mth.clamp(combat.chargeTicks, 1, 200);
		combat.cooldownTicks = Mth.clamp(combat.cooldownTicks, 0, 200);
		combat.guardRadius = Mth.clamp(combat.guardRadius, 1.0, 64.0);
		// Mehburity is pulled back to its owner beyond the teleport distance, so chasing farther is pointless.
		combat.chaseRadius = Mth.clamp(combat.chaseRadius, 2.0, companion.teleportDistance);
		if (mining.targets == null) {
			mining.targets = new ArrayList<>();
		}
		mining.targets.removeIf(target -> target == null || target.isBlank());
		if (mining.tool == null || mining.tool.isBlank()) {
			mining.tool = new Mining().tool;
		}
		mining.searchRadius = Mth.clamp(mining.searchRadius, 1, 32);
		mining.reach = Mth.clamp(mining.reach, 1.5, 6.0);
		mining.ticksPerHardness = Mth.clamp(mining.ticksPerHardness, 0.0, 100.0);
		mining.minBreakTicks = Mth.clamp(mining.minBreakTicks, 1, 1200);
		mining.maxBreakTicks = Mth.clamp(mining.maxBreakTicks, mining.minBreakTicks, 1200);
		mining.bagSlots = Mth.clamp(mining.bagSlots, 1, 216);
		mining.maxBlocksPerTrip = Mth.clamp(mining.maxBlocksPerTrip, 1, 4096);
		mining.pointRange = Mth.clamp(mining.pointRange, 2.0, 64.0);
		if (ai.chatPrefixes == null) {
			ai.chatPrefixes = new ArrayList<>();
		}
		ai.chatPrefixes.removeIf(prefix -> prefix == null || prefix.isBlank());
		if (ai.greeting == null || ai.greeting.isBlank()) {
			ai.greeting = new Ai().greeting;
		}
		if (ai.extraPersona == null) {
			ai.extraPersona = "";
		}
		ai.hearingRadius = Mth.clamp(ai.hearingRadius, 0.0, 256.0);
		ai.requestTimeoutSeconds = Mth.clamp(ai.requestTimeoutSeconds, 5, 120);
		ai.cooldownSeconds = Mth.clamp(ai.cooldownSeconds, 0.0, 60.0);
		ai.maxMessageLength = Mth.clamp(ai.maxMessageLength, 16, 1000);
		ai.maxReplyLength = Mth.clamp(ai.maxReplyLength, 32, 1000);
		ai.eventCommentCooldownSeconds = Mth.clamp(ai.eventCommentCooldownSeconds, 5, 3600);
		verity.box.openTicks = Mth.clamp(verity.box.openTicks, 1, 400);
		verity.darkness.strength = Mth.clamp(verity.darkness.strength, 0.0, 1.0);
	}
}
