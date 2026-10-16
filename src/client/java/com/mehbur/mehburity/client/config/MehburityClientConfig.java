package com.mehbur.mehburity.client.config;

import com.mehbur.mehburity.MehburityMod;
import com.mehbur.mehburity.config.JsonConfigFile;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Per-player client settings in {@code config/mehburity-client.json}. It holds the Gemini API key,
 * so it is ignored by git and is never sent to a server: AI requests are made from this client.
 */
public final class MehburityClientConfig implements JsonConfigFile.Sanitizable {
	public static final String FILE_NAME = MehburityMod.MOD_ID + "-client.json";

	private static JsonConfigFile<MehburityClientConfig> file;
	private static MehburityClientConfig instance = new MehburityClientConfig();

	/** Entered in Mods → Mehburity → Configure. Empty means Mehburity uses its built-in replies. */
	public String geminiApiKey = "";

	public static MehburityClientConfig get() {
		return instance;
	}

	private static JsonConfigFile<MehburityClientConfig> file() {
		if (file == null) {
			file = new JsonConfigFile<>(FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME),
					MehburityClientConfig.class, MehburityClientConfig::new);
		}
		return file;
	}

	public static JsonConfigFile.Result<MehburityClientConfig> load() {
		JsonConfigFile.Result<MehburityClientConfig> result = file().load();
		instance = result.value();
		return result;
	}

	public static boolean save() {
		return file().save(instance);
	}

	@Override
	public void sanitize() {
		geminiApiKey = geminiApiKey == null ? "" : geminiApiKey.strip();
	}
}
