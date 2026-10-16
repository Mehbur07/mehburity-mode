package com.mehbur.mehburity.config;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.MinecraftServer;

/** Fired on the server thread after {@code /mehburity yenile} loaded the config, so features can push new values. */
@FunctionalInterface
public interface ConfigReloadedCallback {
	Event<ConfigReloadedCallback> EVENT = EventFactory.createArrayBacked(ConfigReloadedCallback.class,
			listeners -> server -> {
				for (ConfigReloadedCallback listener : listeners) {
					listener.onReloaded(server);
				}
			});

	void onReloaded(MinecraftServer server);
}
