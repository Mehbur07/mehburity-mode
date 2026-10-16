package com.mehbur.mehburity.command;

import com.mehbur.mehburity.MehburityMod;
import com.mehbur.mehburity.config.ConfigReloadedCallback;
import com.mehbur.mehburity.config.JsonConfigFile;
import com.mehbur.mehburity.config.MehburityConfig;
import com.mehbur.mehburity.entity.CompanionManager;
import com.mehbur.mehburity.entity.CompanionMode;
import com.mehbur.mehburity.entity.MehburitySpeech;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

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
				.then(Commands.literal("cagir").executes(MehburityCommands::summon))
				.then(Commands.literal("otur").executes(ctx -> setMode(ctx, CompanionMode.STAY)))
				.then(Commands.literal("takip").executes(ctx -> setMode(ctx, CompanionMode.FOLLOW)))
				.then(Commands.literal("yenile")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(MehburityCommands::reload)));
	}

	private static int info(CommandContext<CommandSourceStack> ctx) {
		ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(KEY + "info",
				"Mehburity %s is running.", MehburityMod.version()).withStyle(ChatFormatting.AQUA), false);
		return 1;
	}

	private static int summon(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		switch (CompanionManager.summon(player)) {
			case TELEPORTED, RESPAWNED -> MehburitySpeech.say(player, MehburitySpeech.text("coming", "I'm here!"));
			case BOX_GIVEN -> {
				// spawnBox already told the player where the box is.
			}
		}
		return 1;
	}

	private static int setMode(CommandContext<CommandSourceStack> ctx, CompanionMode mode) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		if (!CompanionManager.setMode(player, mode)) {
			ctx.getSource().sendFailure(Component.translatableWithFallback(KEY + "not_nearby",
					"Your Mehburity is not nearby. Use /mehburity cagir first."));
			return 0;
		}
		MehburitySpeech.say(player, mode == CompanionMode.STAY
				? MehburitySpeech.text("stay", "Okay, I'll wait here.")
				: MehburitySpeech.text("follow", "I'm right behind you."));
		return 1;
	}

	private static int reload(CommandContext<CommandSourceStack> ctx) {
		JsonConfigFile.Result<MehburityConfig> result = MehburityConfig.load();
		ConfigReloadedCallback.EVENT.invoker().onReloaded(ctx.getSource().getServer());
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
