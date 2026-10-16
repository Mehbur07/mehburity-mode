package com.mehbur.mehburity.test;

import com.mehbur.mehburity.MehburityMod;
import com.mehbur.mehburity.client.config.MehburityClientConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;

/**
 * End-to-end client test (run with {@code gradlew runClientGameTest}): starts a real client, opens a world
 * and runs {@code /mehburity}. Screenshots land in {@code build/run/clientGameTest/screenshots}.
 */
public final class MehburityClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Files.exists(FabricLoader.getInstance().getConfigDir().resolve(MehburityClientConfig.FILE_NAME))) {
			throw new AssertionError("client config must be created on startup");
		}

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			singleplayer.getServer().runCommand(MehburityMod.MOD_ID);
			context.waitTicks(20);
			context.takeScreenshot("mehburity-phase0-world");
		}
	}
}
