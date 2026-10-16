package com.mehbur.mehburity.config;

import com.mehbur.mehburity.MehburityMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;

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
		verity.box.openTicks = Mth.clamp(verity.box.openTicks, 1, 400);
		verity.darkness.strength = Mth.clamp(verity.darkness.strength, 0.0, 1.0);
	}
}
