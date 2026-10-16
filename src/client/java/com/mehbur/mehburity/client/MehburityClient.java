package com.mehbur.mehburity.client;

import com.mehbur.mehburity.MehburityMod;
import com.mehbur.mehburity.client.config.MehburityClientConfig;
import net.fabricmc.api.ClientModInitializer;

/** Client-only entry point: rendering, screens, key bindings and the Gemini/voice client live under here. */
public final class MehburityClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MehburityClientConfig.load();
		MehburityMod.LOGGER.info("Mehburity client initialized");
	}
}
