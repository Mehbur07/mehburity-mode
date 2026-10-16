package com.mehbur.mehburity.test;

import com.mehbur.mehburity.command.MehburityCommands;
import com.mehbur.mehburity.config.JsonConfigFile;
import com.mehbur.mehburity.config.MehburityConfig;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Server game tests, run headless by {@code gradlew build}. */
public final class MehburityGameTests {
	// ---------------------------------------------------------------- helpers

	private static JsonConfigFile<MehburityConfig> tempConfig(String content) {
		try {
			Path path = Files.createTempDirectory("mehburity-test").resolve(MehburityConfig.FILE_NAME);
			if (content != null) {
				Files.writeString(path, content, StandardCharsets.UTF_8);
			}
			return new JsonConfigFile<>(path, MehburityConfig.class, MehburityConfig::new);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	// ---------------------------------------------------------------- config

	@GameTest
	public void config_is_created_with_defaults(GameTestHelper helper) {
		JsonConfigFile<MehburityConfig> file = tempConfig(null);
		JsonConfigFile.Result<MehburityConfig> result = file.load();

		helper.assertTrue(result.status() == JsonConfigFile.Status.CREATED, "missing file must be created");
		helper.assertTrue(Files.exists(file.path()), "config file must exist on disk");
		helper.assertTrue(result.value().general != null, "general section must exist");
		helper.succeed();
	}

	@GameTest
	public void broken_config_is_backed_up_and_replaced(GameTestHelper helper) {
		JsonConfigFile<MehburityConfig> file = tempConfig("{ this is not json");
		JsonConfigFile.Result<MehburityConfig> result = file.load();

		helper.assertTrue(result.status() == JsonConfigFile.Status.RECOVERED_FROM_BROKEN, "broken file must be detected");
		helper.assertTrue(Files.exists(file.path().resolveSibling(MehburityConfig.FILE_NAME + ".broken")),
				"the broken file must be kept as .broken");
		helper.assertFalse(result.value().general.debugLogging, "defaults must be used");
		helper.succeed();
	}

	@GameTest
	public void user_values_survive_and_missing_sections_are_filled(GameTestHelper helper) {
		JsonConfigFile<MehburityConfig> kept = tempConfig("{\"general\": {\"debugLogging\": true}}");
		helper.assertTrue(kept.load().value().general.debugLogging, "user value must be kept");

		JsonConfigFile<MehburityConfig> partial = tempConfig("{\"general\": null}");
		JsonConfigFile.Result<MehburityConfig> result = partial.load();
		helper.assertTrue(result.status() == JsonConfigFile.Status.LOADED, "partial file is still valid");
		helper.assertTrue(result.value().general != null, "null section must be repaired");
		helper.succeed();
	}

	// ---------------------------------------------------------------- command

	@GameTest
	public void command_tree_is_registered(GameTestHelper helper) {
		CommandNode<CommandSourceStack> root = helper.getLevel().getServer().getCommands().getDispatcher()
				.getRoot().getChild(MehburityCommands.ROOT);
		helper.assertTrue(root != null, "/mehburity must be registered");
		helper.assertTrue(root.getChild("yenile") != null, "/mehburity yenile must be registered");
		helper.succeed();
	}
}
