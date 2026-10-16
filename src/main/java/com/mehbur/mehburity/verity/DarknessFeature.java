package com.mehbur.mehburity.verity;

import com.mehbur.mehburity.config.ConfigReloadedCallback;
import com.mehbur.mehburity.config.MehburityConfig;
import com.mehbur.mehburity.network.DarknessSettingsPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * Verity's "extreme darkness": nights and caves turn pitch black unless there is a light source.
 * The server owns the setting and sends it to each client; the client applies it to the lightmap.
 */
final class DarknessFeature implements VerityFeature {
	@Override
	public String id() {
		return "darkness";
	}

	@Override
	public void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.player));
		ConfigReloadedCallback.EVENT.register(server -> server.getPlayerList().getPlayers().forEach(DarknessFeature::send));
	}

	static DarknessSettingsPayload currentSettings() {
		MehburityConfig.Verity.Darkness darkness = MehburityConfig.get().verity.darkness;
		return new DarknessSettingsPayload(darkness.enabled, (float) darkness.strength);
	}

	private static void send(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, DarknessSettingsPayload.TYPE)) {
			ServerPlayNetworking.send(player, currentSettings());
		}
	}
}
