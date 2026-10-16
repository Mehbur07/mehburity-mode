package com.mehbur.mehburity.verity;

import com.mehbur.mehburity.config.MehburityConfig;
import com.mehbur.mehburity.entity.CompanionManager;
import com.mehbur.mehburity.entity.CompanionRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

/** Like Verity, Mehburity arrives in its own box: every player gets one next to them on their first join. */
final class BoxFeature implements VerityFeature {
	@Override
	public String id() {
		return "box";
	}

	@Override
	public void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.player;
			if (MehburityConfig.get().verity.box.enabled && !CompanionRegistry.hasEverReceived(server, player.getUUID())) {
				CompanionManager.spawnBox(player);
			}
		});
	}
}
