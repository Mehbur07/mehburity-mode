package com.mehbur.mehburity.ai;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;

/** Fired on the server thread each time Mehburity answers its owner (from Gemini or a built-in reply). */
@FunctionalInterface
public interface MehburityReplyCallback {
	Event<MehburityReplyCallback> EVENT = EventFactory.createArrayBacked(MehburityReplyCallback.class,
			listeners -> (owner, text, fromGemini) -> {
				for (MehburityReplyCallback listener : listeners) {
					listener.onReply(owner, text, fromGemini);
				}
			});

	void onReply(ServerPlayer owner, String text, boolean fromGemini);
}
