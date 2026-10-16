package com.mehbur.mehburity.config;

import com.mehbur.mehburity.MehburityMod;
import net.fabricmc.loader.api.FabricLoader;

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

	public static final class General {
		/** Extra log lines for troubleshooting. */
		public boolean debugLogging = false;
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
		if (general == null) {
			general = new General();
		}
		configVersion = CURRENT_VERSION;
	}
}
