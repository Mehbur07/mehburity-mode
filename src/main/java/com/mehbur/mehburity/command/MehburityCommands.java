package com.mehbur.mehburity.command;

import com.mehbur.mehburity.MehburityMod;
import com.mehbur.mehburity.anger.Anger;
import com.mehbur.mehburity.anger.Revenge;
import com.mehbur.mehburity.build.Building;
import com.mehbur.mehburity.build.HouseStyle;
import com.mehbur.mehburity.config.ConfigReloadedCallback;
import com.mehbur.mehburity.config.JsonConfigFile;
import com.mehbur.mehburity.config.MehburityConfig;
import com.mehbur.mehburity.entity.CompanionManager;
import com.mehbur.mehburity.entity.CompanionMode;
import com.mehbur.mehburity.entity.CompanionRegistry;
import com.mehbur.mehburity.entity.MehburityEntity;
import com.mehbur.mehburity.entity.MehburitySpeech;
import com.mehbur.mehburity.guide.Destination;
import com.mehbur.mehburity.guide.Guiding;
import com.mehbur.mehburity.mining.Mining;
import com.mehbur.mehburity.network.OpenSettingsPayload;
import com.mehbur.mehburity.verity.EventsFeature;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.entity.EntityTypeTest;

import java.util.UUID;
import java.util.function.Predicate;

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
				.then(Commands.literal("savas")
						.executes(MehburityCommands::combatStatus)
						.then(Commands.literal("ac").executes(ctx -> setCombat(ctx, true)))
						.then(Commands.literal("kapat").executes(ctx -> setCombat(ctx, false))))
				.then(Commands.literal("kaz")
						.executes(ctx -> mining(ctx, Mining::startOres))
						.then(Commands.literal("bunu").executes(ctx -> mining(ctx, Mining::startLookedAt)))
						.then(Commands.literal("agac").executes(ctx -> mining(ctx, Mining::startTree)))
						.then(Commands.literal("dur").executes(ctx -> mining(ctx, Mining::stop))))
				.then(houseCommand())
				.then(guideCommand())
				.then(Commands.literal("ver").executes(ctx -> mining(ctx, Mining::give)))
				.then(Commands.literal("canta").executes(ctx -> mining(ctx, Mining::list)))
				.then(Commands.literal("ofke").executes(MehburityCommands::angerStatus))
				.then(Commands.literal("sakin")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(MehburityCommands::calmAll))
				.then(Commands.literal("intikam")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(MehburityCommands::revengeNow))
				.then(Commands.literal("olay")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("ses").executes(ctx -> scare(ctx, EventsFeature.Kind.SOUND)))
						.then(Commands.literal("fisilti").executes(ctx -> scare(ctx, EventsFeature.Kind.WHISPER)))
						.then(Commands.literal("izleyen").executes(ctx -> scare(ctx, EventsFeature.Kind.WATCHER))))
				.then(Commands.literal("ayarlar").executes(MehburityCommands::openSettings))
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
			case GONE -> player.sendSystemMessage(Component.translatableWithFallback(KEY + "gone",
					"Mehburity is nowhere to be found... It will be back.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
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

	/** {@code /mehburity savas}: tells whether Mehburity fights for the player. */
	private static int combatStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		if (CompanionRegistry.companion(player.level().getServer(), player.getUUID()) == null) {
			return noCompanion(ctx);
		}
		if (!MehburityConfig.get().combat.enabled) {
			MehburitySpeech.say(player, MehburitySpeech.text("combat.server_off", "Fighting is turned off on this server."));
		} else if (CompanionManager.isCombatEnabled(player.level().getServer(), player.getUUID())) {
			MehburitySpeech.say(player, MehburitySpeech.text("combat.status_on",
					"I'm protecting you. (/mehburity savas kapat to stop me)"));
		} else {
			MehburitySpeech.say(player, MehburitySpeech.text("combat.status_off",
					"I'm not fighting right now. (/mehburity savas ac to let me protect you)"));
		}
		return 1;
	}

	/** {@code /mehburity savas ac|kapat}: the player's own switch, saved with the world. */
	private static int setCombat(CommandContext<CommandSourceStack> ctx, boolean combat) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		if (!CompanionManager.setCombat(player, combat)) {
			return noCompanion(ctx);
		}
		MehburitySpeech.say(player, combat
				? MehburitySpeech.text("combat.on", "Got it, I'll protect you!")
				: MehburitySpeech.text("combat.off", "Okay, I won't fight anymore."));
		if (combat && !MehburityConfig.get().combat.enabled) {
			MehburitySpeech.say(player, MehburitySpeech.text("combat.server_off", "Fighting is turned off on this server."));
		}
		return 1;
	}

	/** {@code /mehburity ofke}: how angry the player's Mehburity is. */
	private static int angerStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MehburityEntity mehburity = CompanionManager.find(player.level().getServer(), player.getUUID());
		if (mehburity == null) {
			return noCompanion(ctx);
		}
		int anger = Math.round(mehburity.getAnger());
		MehburitySpeech.say(player, switch (Anger.mood(mehburity)) {
			case CALM -> MehburitySpeech.text("anger.status_calm", "I'm calm. (anger %s/100)", anger);
			case ANNOYED -> MehburitySpeech.text("anger.status_annoyed", "I'm a bit annoyed. (anger %s/100)", anger);
			case ANGRY -> MehburitySpeech.text("anger.status_angry", "I'm angry! Careful... (anger %s/100)", anger);
			case RAGING -> MehburitySpeech.text("anger.status_raging", "I'M IN A RAGE! (anger %s/100)", anger);
		});
		return 1;
	}

	/** {@code /mehburity sakin} (operators): ends every rage and empties every loaded Mehburity's anger. */
	private static int calmAll(CommandContext<CommandSourceStack> ctx) {
		int calmed = 0;
		for (ServerLevel level : ctx.getSource().getServer().getAllLevels()) {
			for (MehburityEntity mehburity : level.getEntities(EntityTypeTest.forClass(MehburityEntity.class), m -> true)) {
				if (mehburity.isRaging()) {
					Anger.endRage(mehburity, Anger.RageEnd.FORCED);
				} else {
					Anger.calm(mehburity, 100.0);
				}
				calmed++;
			}
		}
		// A Mehburity that vanished for revenge forgives and comes back calm.
		MinecraftServer server = ctx.getSource().getServer();
		for (UUID owner : java.util.List.copyOf(CompanionRegistry.companions(server).keySet())) {
			if (CompanionManager.forgive(server, owner)) {
				calmed++;
			}
		}
		int count = calmed;
		ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(KEY + "calmed",
				"Calmed %s Mehburity.", count), true);
		return count;
	}

	/** {@code /mehburity intikam} (operators): every vanished Mehburity whose culprit is online comes back now. */
	private static int revengeNow(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		long pending = CompanionRegistry.companions(server).values().stream()
				.filter(companion -> companion.mode() == CompanionMode.GONE).count();
		Revenge.advance(server, Long.MAX_VALUE / 2);
		ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(KEY + "revenge_now",
				"%s vanished Mehburity were sent for their revenge.", pending), true);
		return (int) pending;
	}

	/** {@code /mehburity olay ses|fisilti|izleyen} (operators): one of Verity's scripted scares, right now. */
	private static int scare(CommandContext<CommandSourceStack> ctx, EventsFeature.Kind kind) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String name = kind.name().toLowerCase(java.util.Locale.ROOT);
		if (!EventsFeature.trigger(player, kind)) {
			ctx.getSource().sendFailure(Component.translatableWithFallback(KEY + "event_failed",
					"That scare cannot happen here right now: %s", name));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(KEY + "event_done", "Scare started: %s", name), false);
		return 1;
	}

	/** {@code /mehburity ev [ahsap|tas|tugla|elimdeki]}, {@code ev dur}, {@code ev geri}. */
	private static LiteralArgumentBuilder<CommandSourceStack> houseCommand() {
		LiteralArgumentBuilder<CommandSourceStack> house = Commands.literal("ev")
				.executes(ctx -> mining(ctx, player -> Building.start(player, null)))
				.then(Commands.literal("dur").executes(ctx -> mining(ctx, Building::stop)))
				.then(Commands.literal("geri").executes(ctx -> mining(ctx, Building::undo)));
		for (HouseStyle style : HouseStyle.values()) {
			house.then(Commands.literal(style.id()).executes(ctx -> mining(ctx, player -> Building.start(player, style))));
		}
		return house;
	}

	/** {@code /mehburity gotur <yer>}: leads the way to the nearest one; {@code gotur dur} stops. */
	private static LiteralArgumentBuilder<CommandSourceStack> guideCommand() {
		LiteralArgumentBuilder<CommandSourceStack> guide = Commands.literal("gotur")
				.then(Commands.literal("dur").executes(ctx -> mining(ctx, Guiding::stop)));
		for (Destination destination : Destination.values()) {
			guide.then(Commands.literal(destination.id()).executes(ctx -> mining(ctx, player -> Guiding.start(player, destination))));
		}
		return guide;
	}

	/** Job subcommands (mining, building, guiding); the action itself answers the player. */
	private static int mining(CommandContext<CommandSourceStack> ctx, Predicate<ServerPlayer> action) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		if (CompanionRegistry.companion(player.level().getServer(), player.getUUID()) == null) {
			return noCompanion(ctx);
		}
		return action.test(player) ? 1 : 0;
	}

	private static int noCompanion(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		CompanionRegistry.Companion companion = player == null ? null
				: CompanionRegistry.companion(ctx.getSource().getServer(), player.getUUID());
		ctx.getSource().sendFailure(companion != null && companion.mode() == CompanionMode.CARRIED
				? Component.translatableWithFallback(KEY + "carried",
						"Mehburity is in your inventory. Right click with it to let it out (or /mehburity cagir).")
				: Component.translatableWithFallback(KEY + "no_companion",
						"You don't have a Mehburity yet. Open your box first (/mehburity cagir)."));
		return 0;
	}

	/** Opens the client settings screen (Gemini key, voice). Same screen as Mods → Mehburity → Configure. */
	private static int openSettings(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		if (!ServerPlayNetworking.canSend(player, OpenSettingsPayload.TYPE)) {
			return 0;
		}
		ServerPlayNetworking.send(player, OpenSettingsPayload.INSTANCE);
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
