package com.mehbur.mehburity.ai;

import com.mehbur.mehburity.config.MehburityConfig;
import com.mehbur.mehburity.anger.Anger;
import com.mehbur.mehburity.build.Building;
import com.mehbur.mehburity.entity.CompanionManager;
import com.mehbur.mehburity.entity.MehburityEntity;
import com.mehbur.mehburity.entity.CompanionRegistry;
import com.mehbur.mehburity.mining.Mining;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.Mob;

import java.util.Map;
import java.util.TreeMap;

/** Describes the player's situation in a few lines of plain English for Gemini. Built on the server. */
public final class GameContext {
	private static final double NEARBY = 16.0;

	private GameContext() {
	}

	public static String describe(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos pos = player.blockPosition();
		StringBuilder out = new StringBuilder();
		out.append("Player: ").append(player.getGameProfile().name())
				.append(" (game language ").append(player.clientInformation().language()).append(")\n");
		out.append("Dimension: ").append(level.dimension().identifier().getPath())
				.append(", biome: ").append(level.getBiome(pos).unwrapKey().map(key -> key.identifier().getPath()).orElse("unknown"))
				.append(", position: ").append(pos.getX()).append(' ').append(pos.getY()).append(' ').append(pos.getZ()).append('\n');
		long dayTime = level.getOverworldClockTime() % 24000L;
		out.append("Time: ").append(dayTime < 13000 ? "day" : "night")
				.append(level.isRaining() ? ", raining" : "").append(level.isThundering() ? ", thunderstorm" : "").append('\n');
		out.append("Health: ").append(Math.round(player.getHealth())).append('/').append(Math.round(player.getMaxHealth()))
				.append(", food: ").append(player.getFoodData().getFoodLevel()).append("/20")
				.append(", game mode: ").append(player.gameMode().getName()).append('\n');

		Map<String, Integer> enemies = new TreeMap<>();
		for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(NEARBY), mob -> mob instanceof Enemy)) {
			enemies.merge(mob.getType().getDescription().getString(), 1, Integer::sum);
		}
		out.append("Hostile mobs within 16 blocks: ").append(enemies.isEmpty() ? "none" : enemies.toString()).append('\n');

		CompanionRegistry.Companion companion = CompanionRegistry.companion(level.getServer(), player.getUUID());
		if (companion != null) {
			out.append("Mehburity mode: ").append(companion.mode().getSerializedName())
					.append(", protecting the player: ").append(companion.combat() ? "yes" : "no").append('\n');
			MehburityEntity mehburity = CompanionManager.find(level.getServer(), player.getUUID());
			out.append(Mining.describeFor(player, mehburity));
			out.append(Building.describeFor(mehburity));
			out.append(Anger.describeFor(mehburity));
		}
		String recent = GameEvents.recent(player);
		if (!recent.isEmpty()) {
			out.append("Recent events:\n").append(recent);
		}
		String extra = MehburityConfig.get().ai.extraPersona;
		if (!extra.isBlank()) {
			out.append("Extra personality notes from the server owner: ").append(extra).append('\n');
		}
		return out.toString();
	}
}
