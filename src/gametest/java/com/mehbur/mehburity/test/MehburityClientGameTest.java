package com.mehbur.mehburity.test;

import com.mehbur.mehburity.MehburityMod;
import com.mehbur.mehburity.client.config.MehburityClientConfig;
import com.mehbur.mehburity.config.ConfigReloadedCallback;
import com.mehbur.mehburity.config.MehburityConfig;
import com.mehbur.mehburity.entity.CompanionManager;
import com.mehbur.mehburity.entity.CompanionRegistry;
import com.mehbur.mehburity.entity.MehburityBoxEntity;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * End-to-end client test (run with {@code gradlew runClientGameTest}). A real client: finds the first-join box,
 * opens it with a left click, checks that Verity darkness really darkens the night, then leaves and rejoins
 * to make sure Mehburity comes back. Screenshots land in {@code build/run/clientGameTest/screenshots}.
 */
public final class MehburityClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Files.exists(FabricLoader.getInstance().getConfigDir().resolve(MehburityClientConfig.FILE_NAME))) {
			throw new AssertionError("client config must be created on startup");
		}

		TestWorldSave save;
		UUID playerId;
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			save = singleplayer.getWorldSave();
			TestServerContext server = singleplayer.getServer();
			singleplayer.getConnection().waitForChunksRender();
			server.runCommand("time set noon");
			playerId = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getUUID());

			// The first-join box is next to the player.
			server.waitFor(s -> CompanionRegistry.box(s, playerId) != null, 100);
			BlockPos boxPos = server.computeOnServer(s -> {
				UUID boxId = CompanionRegistry.box(s, playerId);
				return s.overworld().getEntity(boxId) instanceof MehburityBoxEntity box ? box.blockPosition() : null;
			});
			if (boxPos == null) {
				throw new AssertionError("the first-join box must be in the world");
			}
			context.getInput().lookAt(boxPos);
			context.waitTicks(10);
			context.takeScreenshot("mehburity-1-box");

			// Left click opens it; Mehburity rises out.
			context.getInput().pressKey(options -> options.keyAttack);
			context.waitTicks(15);
			context.takeScreenshot("mehburity-2-box-opening");
			server.waitFor(s -> CompanionManager.find(s, playerId) != null, 200);
			context.waitTicks(40);
			context.takeScreenshot("mehburity-3-out");

			checkDarkness(context, server);
		}

		// Leaving and coming back: Mehburity travels with the player and greets them again.
		try (TestSingleplayerContext singleplayer = save.open()) {
			singleplayer.getConnection().waitForChunksRender();
			singleplayer.getServer().waitFor(s -> CompanionManager.find(s, playerId) != null, 100);
			context.waitTicks(20);
			context.takeScreenshot("mehburity-6-rejoined");
		}
		MehburityMod.LOGGER.info("Mehburity client game test passed");
	}

	/** Compares the same midnight view with Verity darkness on and off. */
	private static void checkDarkness(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("time set midnight");
		context.getInput().pressKey(options -> options.keyToggleGui); // F1: hide the HUD
		context.getInput().lookAt(0.0F, 40.0F);
		context.waitTicks(30);
		double dark = luminance(context.takeScreenshot("mehburity-4-night-darkness"));

		server.runOnServer(s -> {
			MehburityConfig.get().verity.darkness.enabled = false;
			ConfigReloadedCallback.EVENT.invoker().onReloaded(s);
		});
		context.waitTicks(30);
		double vanilla = luminance(context.takeScreenshot("mehburity-5-night-vanilla"));

		server.runOnServer(s -> {
			MehburityConfig.get().verity.darkness.enabled = true;
			ConfigReloadedCallback.EVENT.invoker().onReloaded(s);
		});
		context.getInput().pressKey(options -> options.keyToggleGui);
		MehburityMod.LOGGER.info("Night luminance: darkness={} vanilla={}", dark, vanilla);
		if (!(dark < vanilla * 0.6)) {
			throw new AssertionError("Verity darkness must clearly darken the night (dark=" + dark + ", vanilla=" + vanilla + ")");
		}
	}

	private static double luminance(Path screenshot) {
		try {
			BufferedImage image = ImageIO.read(screenshot.toFile());
			double sum = 0;
			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					int rgb = image.getRGB(x, y);
					sum += 0.2126 * ((rgb >> 16) & 0xFF) + 0.7152 * ((rgb >> 8) & 0xFF) + 0.0722 * (rgb & 0xFF);
				}
			}
			return sum / (image.getWidth() * (double) image.getHeight());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
