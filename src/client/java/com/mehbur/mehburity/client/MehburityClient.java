package com.mehbur.mehburity.client;

import com.mehbur.mehburity.MehburityMod;
import com.mehbur.mehburity.client.ai.ClientAi;
import com.mehbur.mehburity.client.config.MehburityClientConfig;
import com.mehbur.mehburity.client.render.MehburityBoxModel;
import com.mehbur.mehburity.client.render.MehburityBoxRenderer;
import com.mehbur.mehburity.client.render.MehburityRenderer;
import com.mehbur.mehburity.client.render.WatcherRenderer;
import com.mehbur.mehburity.client.screen.MehburityConfigScreen;
import com.mehbur.mehburity.client.verity.DarknessClient;
import com.mehbur.mehburity.entity.MehburityEntities;
import com.mehbur.mehburity.network.OpenSettingsPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.renderer.entity.EntityRenderers;

/** Client-only entry point: rendering, screens, key bindings and the Gemini/voice client live under here. */
public final class MehburityClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MehburityClientConfig.load();

		ModelLayerRegistry.registerModelLayer(MehburityBoxModel.LAYER, MehburityBoxModel::createLayer);
		EntityRenderers.register(MehburityEntities.MEHBURITY, MehburityRenderer::new);
		EntityRenderers.register(MehburityEntities.BOX, MehburityBoxRenderer::new);
		EntityRenderers.register(MehburityEntities.WATCHER, WatcherRenderer::new);

		DarknessClient.register();
		ClientAi.register();
		// /mehburity ayarlar (server command) asks us to open the settings screen.
		ClientPlayNetworking.registerGlobalReceiver(OpenSettingsPayload.TYPE,
				(payload, context) -> context.client().gui.setScreen(new MehburityConfigScreen(null)));
		MehburityMod.LOGGER.info("Mehburity client initialized");
	}
}
