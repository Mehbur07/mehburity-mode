package com.mehbur.mehburity.client.config;

import com.mehbur.mehburity.MehburityMod;
import com.mehbur.mehburity.ai.GeminiClient;
import com.mehbur.mehburity.config.JsonConfigFile;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;

import java.net.URI;
import java.util.List;

/**
 * Per-player client settings in {@code config/mehburity-client.json}. It holds the Gemini API key, so it is ignored
 * by git and never sent to a server: AI requests are made from this client. Edited in Mods → Mehburity → Configure.
 */
public final class MehburityClientConfig implements JsonConfigFile.Sanitizable {
	public static final String FILE_NAME = MehburityMod.MOD_ID + "-client.json";
	/** Text models offered in the settings screen (verified on ai.google.dev, October 2026). */
	public static final List<String> CHAT_MODELS = List.of("gemini-3.5-flash-lite", "gemini-3.8-flash", "gemini-3.1-flash-lite");
	public static final List<String> VOICE_MODELS = List.of("gemini-3.8-flash-tts", "gemini-3.8-flash-lite-tts");

	private static JsonConfigFile<MehburityClientConfig> file;
	private static MehburityClientConfig instance = new MehburityClientConfig();

	/** Empty means Mehburity uses its built-in replies. */
	public String geminiApiKey = "";
	public String chatModel = CHAT_MODELS.getFirst();
	/** How many previous messages Gemini sees (player + Mehburity lines). */
	public int historyMessages = 10;

	public boolean voiceEnabled = true;
	public String voiceModel = VOICE_MODELS.getFirst();
	/** Puck ("upbeat") is the closest prebuilt voice to Verity's energetic, friendly young voice. */
	public String voiceName = "Puck";
	public String voiceStyle = "energetic, cheerful and friendly";
	/** 0-1, multiplied with Minecraft's master and voice volume. */
	public double voiceVolume = 1.0;

	/** Advanced: only change this for a proxy. */
	public String apiBaseUrl = GeminiClient.DEFAULT_BASE_URI.toString();

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

	public URI baseUri() {
		return URI.create(apiBaseUrl.endsWith("/") ? apiBaseUrl : apiBaseUrl + "/");
	}

	@Override
	public void sanitize() {
		MehburityClientConfig defaults = new MehburityClientConfig();
		geminiApiKey = geminiApiKey == null ? "" : geminiApiKey.strip();
		chatModel = blankToDefault(chatModel, defaults.chatModel);
		voiceModel = blankToDefault(voiceModel, defaults.voiceModel);
		voiceName = blankToDefault(voiceName, defaults.voiceName);
		voiceStyle = voiceStyle == null ? "" : voiceStyle.strip();
		voiceVolume = Mth.clamp(voiceVolume, 0.0, 1.0);
		historyMessages = Mth.clamp(historyMessages, 0, 40);
		try {
			baseUri();
		} catch (IllegalArgumentException | NullPointerException e) {
			apiBaseUrl = defaults.apiBaseUrl;
		}
	}

	private static String blankToDefault(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.strip();
	}
}
