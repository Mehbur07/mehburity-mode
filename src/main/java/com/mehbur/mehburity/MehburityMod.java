package com.mehbur.mehburity;

import com.mehbur.mehburity.ai.AiRouter;
import com.mehbur.mehburity.ai.GameEvents;
import com.mehbur.mehburity.command.MehburityCommands;
import com.mehbur.mehburity.config.MehburityConfig;
import com.mehbur.mehburity.entity.CompanionManager;
import com.mehbur.mehburity.entity.CompanionRegistry;
import com.mehbur.mehburity.entity.MehburityEntities;
import com.mehbur.mehburity.entity.MehburitySounds;
import com.mehbur.mehburity.mining.Mining;
import com.mehbur.mehburity.network.MehburityNetwork;
import com.mehbur.mehburity.verity.VerityFeatures;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common (client + server) entry point. Each feature package registers itself from here,
 * so the order of initialization stays visible in one place.
 */
public final class MehburityMod implements ModInitializer {
	public static final String MOD_ID = "mehburity";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static String version() {
		return FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("dev");
	}

	@Override
	public void onInitialize() {
		MehburityConfig.load();
		MehburitySounds.register();
		MehburityEntities.register();
		CompanionRegistry.register();
		MehburityNetwork.register();
		CompanionManager.register();
		GameEvents.register();
		AiRouter.register();
		Mining.register();
		VerityFeatures.registerAll();
		MehburityCommands.register();
		LOGGER.info("Mehburity {} initialized", version());
	}
}
