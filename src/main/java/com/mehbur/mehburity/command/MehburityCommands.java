package com.mehbur.mehburity.command;

import com.mehbur.mehburity.MehburityMod;
import com.mehbur.mehburity.config.JsonConfigFile;
import com.mehbur.mehburity.config.MehburityConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Root of {@code /mehburity}. Subcommands are ASCII-only on purpose: Brigadier cannot parse
 * unquoted literals with characters like "ç" or "ğ", so Turkish words are written without them
 * ({@code cagir}, {@code yenile}).
 */
public final class MehburityCommands {
	public static final String ROOT = "mehburity";
	private static final String KEY = MehburityMod.MOD_ID + ".command.";

	private MehburityCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> build(dispatcher));
	}

	private static void build(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal(ROOT)
				.executes(MehburityCommands::info)
				.then(Commands.literal("yenile")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(MehburityCommands::reload)));
	}

	private static int info(CommandContext<CommandSourceStack> ctx) {
		ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(KEY + "info",
				"Mehburity %s is running.", MehburityMod.version()).withStyle(ChatFormatting.AQUA), false);
		return 1;
	}

	private static int reload(CommandContext<CommandSourceStack> ctx) {
		JsonConfigFile.Result<MehburityConfig> result = MehburityConfig.load();
		if (!result.ok()) {
			ctx.getSource().sendFailure(Component.translatableWithFallback(KEY + "reload_failed",
					"The config file had a problem, defaults are in use. See the log."));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(KEY + "reloaded",
				"Mehburity config reloaded."), true);
		return 1;
	}
}
