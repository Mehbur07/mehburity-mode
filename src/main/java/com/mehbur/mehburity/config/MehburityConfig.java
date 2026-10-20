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
	public Build build = new Build();
	public Anger anger = new Anger();
	public Monster monster = new Monster();
	public Ai ai = new Ai();
	public Verity verity = new Verity();

	public static final class General {
		/** Extra log lines for troubleshooting. */
		public boolean debugLogging = false;
	}

	/** How Mehburity moves around its owner. Distances are in blocks. */
	public static final class Companion {
		/** Starts rolling towards the owner when farther than this. */
		public double followStartDistance = 4.0;
		/** Stops when this close. */
		public double followStopDistance = 2.5;
		/** Teleports next to the owner when farther than this (or in another dimension). */
		public double teleportDistance = 20.0;
		/** Multiplier on the base rolling speed while following. */
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
	 * Building a house for the owner ("Mehburity, ev yap"). Creative-style: no materials are used up. The last house
	 * can be taken down again ("evi geri al"), which puts back what was there before.
	 */
	public static final class Build {
		public boolean enabled = true;
		/** Style when none is asked for: "ahsap" (wood), "tas" (stone), "tugla" (brick). */
		public String defaultStyle = "ahsap";
		/** Lets the owner build walls from the block in their hand ("elimdeki bloktan ev yap"). */
		public boolean allowHeldBlock = true;
		/** Placing speed. */
		public double blocksPerSecond = 15.0;
		/** Fills holes under the floor down to this depth; deeper gaps are left open. */
		public int maxFoundationDepth = 6;
		/** Furniture: bed, chest, crafting table, furnace. Torches are always placed (nights are dark). */
		public boolean furnish = true;
		/** Places blocks from this far away; it flies closer otherwise. */
		public double reach = 8.0;
	}

	/**
	 * Mehburity's temper, a meter from 0 to 100 that cools down over time. Hitting it, insulting it or killing its
	 * owner makes it angrier; at {@code rageThreshold} it goes into a rage and hunts down only the culprit. Cake and
	 * apologies ("Mehburity, özür dilerim") calm it down.
	 */
	public static final class Anger {
		public boolean enabled = true;
		/** A player hits Mehburity (its owner too). */
		public double playerHitAnger = 25.0;
		/** A mob hits Mehburity (an arrow counts for its shooter). */
		public double mobHitAnger = 5.0;
		/** Someone insults it in chat (words below). */
		public double insultAnger = 15.0;
		/** Someone kills its owner; 100 means an instant rage at the killer. */
		public double ownerKilledAnger = 100.0;
		/** Cools down by this much per minute. */
		public double decayPerMinute = 10.0;
		/** Goes into a rage at this level. */
		public double rageThreshold = 100.0;
		/** A rage ends when cake or apologies bring the meter down to this (or lower). */
		public double calmBelow = 40.0;
		/** Right-clicking Mehburity with a cake. */
		public double cakeCalm = 60.0;
		/** "Mehburity, özür dilerim" / "sorry". */
		public double apologyCalm = 25.0;
		/** One apology per player per this many seconds counts. */
		public int apologyCooldownSeconds = 30;
		/** Beam damage during a rage (the normal beam does combat.damage). */
		public double rageDamage = 10.0;
		public int rageCooldownTicks = 10;
		/** Speed multiplier while chasing the culprit. */
		public double rageSpeed = 3.0;
		/** Gives up after this long. */
		public int maxRageSeconds = 90;
		/** Lets a rage target players at all (other players and the owner). Off: only mobs can be culprits. */
		public boolean rageAtPlayers = true;
		/** Lets a rage target its own owner (Verity-like). */
		public boolean rageAtOwner = true;
		/** Once it has calmed down, it says sorry to the player it got really angry with and gives them a cake. */
		public boolean makeUpCake = true;
		/**
		 * A player who drives it into a rage is not hunted at once: it vanishes for this many in-game days and comes
		 * back for them some time on the day after. 0: rages at once. Mobs are always hunted at once.
		 */
		public int revengeDays = 3;
		/**
		 * The revenge starts this many chunks behind the culprit (a random distance in between), and the demon walks
		 * up to them from there. Closer when that is not loaded; 0 and 0: right behind them.
		 */
		public int revengeMinChunks = 5;
		public int revengeMaxChunks = 10;
		/** At most one make-up cake per player in this many seconds (0: every time it calms down). */
		public int makeUpCakeCooldownSeconds = 0;
		/** Words that count as insults when said to Mehburity (accents and case do not matter). */
		public List<String> insults = new ArrayList<>(List.of(
				"aptal", "salak", "gerizekali", "beyinsiz", "seni sevmiyorum", "nefret ediyorum",
				"stupid", "idiot", "dumb", "i hate you", "useless"));
	}

	/**
	 * The rage form: during a rage Mehburity turns into a tall, skinny demon that walks through walls after the
	 * culprit and tears at it with its claws. Off: a rage stays a red sphere with a stronger beam (anger.rage*).
	 */
	public static final class Monster {
		public boolean enabled = true;
		/** Height of the demon in blocks. */
		public double height = 18.0;
		/** How long the transformation takes (ticks); the culprit gets this head start. */
		public int transformTicks = 40;
		/** Walking speed; a sprinting player does about 5.6 blocks per second. */
		public double blocksPerSecond = 6.5;
		/** Claw reach, measured on the ground from the demon's feet. */
		public double reach = 5.0;
		/** Damage of one claw swipe (a player has 20 health; armor still helps). */
		public double damage = 24.0;
		public int attackCooldownTicks = 30;
		/** How hard a swipe throws the culprit. */
		public double knockback = 1.2;
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
		public Flashlight flashlight = new Flashlight();
		public Events events = new Events();

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

		/**
		 * The flashlight: switched on (right click) and held in a hand, it lights up what the player looks at.
		 * Off: the item still exists, but it gives no light.
		 */
		public static final class Flashlight {
			public boolean enabled = true;
			/** Mehburity hands one over when it comes out of its box. */
			public boolean giveWithBox = true;
			/** How far the beam reaches, in blocks. */
			public int range = 24;
			/** Light level where the beam lands (a dimmer one lights the middle of the beam). */
			public int lightLevel = 15;
			/** Minutes of light from a full battery; 0 = never runs out. Recharged with redstone in an anvil. */
			public double batteryMinutes = 20.0;
		}

		/**
		 * Scripted scares, now and then while the player is in the dark: sounds behind them, Mehburity whispering,
		 * and rarely a tall demon watching from far away that vanishes when looked at.
		 */
		public static final class Events {
			public boolean enabled = true;
			/** Time between two events, picked at random between these (minutes of play). */
			public double minMinutes = 6.0;
			public double maxMinutes = 15.0;
			/** Only when the player stands in the dark (nights outside, caves). */
			public boolean onlyInDarkness = true;
			public boolean sounds = true;
			public boolean whispers = true;
			public boolean watcher = true;
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
		if (build == null) {
			build = new Build();
		}
		if (anger == null) {
			anger = new Anger();
		}
		if (monster == null) {
			monster = new Monster();
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
		if (verity.flashlight == null) {
			verity.flashlight = new Verity.Flashlight();
		}
		if (verity.events == null) {
			verity.events = new Verity.Events();
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
		if (build.defaultStyle == null || build.defaultStyle.isBlank()) {
			build.defaultStyle = new Build().defaultStyle;
		}
		build.blocksPerSecond = Mth.clamp(build.blocksPerSecond, 1.0, 400.0);
		build.maxFoundationDepth = Mth.clamp(build.maxFoundationDepth, 0, 32);
		build.reach = Mth.clamp(build.reach, 3.0, 16.0);
		anger.playerHitAnger = Mth.clamp(anger.playerHitAnger, 0.0, 100.0);
		anger.mobHitAnger = Mth.clamp(anger.mobHitAnger, 0.0, 100.0);
		anger.insultAnger = Mth.clamp(anger.insultAnger, 0.0, 100.0);
		anger.ownerKilledAnger = Mth.clamp(anger.ownerKilledAnger, 0.0, 100.0);
		anger.decayPerMinute = Mth.clamp(anger.decayPerMinute, 0.0, 6000.0);
		anger.rageThreshold = Mth.clamp(anger.rageThreshold, 1.0, 100.0);
		anger.calmBelow = Mth.clamp(anger.calmBelow, 0.0, anger.rageThreshold);
		anger.cakeCalm = Mth.clamp(anger.cakeCalm, 0.0, 100.0);
		anger.apologyCalm = Mth.clamp(anger.apologyCalm, 0.0, 100.0);
		anger.apologyCooldownSeconds = Mth.clamp(anger.apologyCooldownSeconds, 0, 3600);
		anger.makeUpCakeCooldownSeconds = Mth.clamp(anger.makeUpCakeCooldownSeconds, 0, 86400);
		anger.revengeDays = Mth.clamp(anger.revengeDays, 0, 30);
		anger.revengeMinChunks = Mth.clamp(anger.revengeMinChunks, 0, 32);
		anger.revengeMaxChunks = Mth.clamp(anger.revengeMaxChunks, anger.revengeMinChunks, 32);
		anger.rageDamage = Mth.clamp(anger.rageDamage, 0.0, 1000.0);
		anger.rageCooldownTicks = Mth.clamp(anger.rageCooldownTicks, 0, 200);
		anger.rageSpeed = Mth.clamp(anger.rageSpeed, 0.1, 10.0);
		anger.maxRageSeconds = Mth.clamp(anger.maxRageSeconds, 5, 3600);
		if (anger.insults == null) {
			anger.insults = new ArrayList<>();
		}
		anger.insults.removeIf(word -> word == null || word.isBlank());
		monster.height = Mth.clamp(monster.height, 4.0, 32.0);
		monster.transformTicks = Mth.clamp(monster.transformTicks, 0, 200);
		monster.blocksPerSecond = Mth.clamp(monster.blocksPerSecond, 0.0, 40.0);
		monster.reach = Mth.clamp(monster.reach, 1.0, 16.0);
		monster.damage = Mth.clamp(monster.damage, 0.0, 1000.0);
		monster.attackCooldownTicks = Mth.clamp(monster.attackCooldownTicks, 1, 200);
		monster.knockback = Mth.clamp(monster.knockback, 0.0, 5.0);
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
		verity.flashlight.range = Mth.clamp(verity.flashlight.range, 4, 64);
		verity.flashlight.lightLevel = Mth.clamp(verity.flashlight.lightLevel, 1, 15);
		verity.flashlight.batteryMinutes = Mth.clamp(verity.flashlight.batteryMinutes, 0.0, 600.0);
		verity.events.minMinutes = Mth.clamp(verity.events.minMinutes, 0.5, 240.0);
		verity.events.maxMinutes = Mth.clamp(verity.events.maxMinutes, verity.events.minMinutes, 240.0);
	}
}
