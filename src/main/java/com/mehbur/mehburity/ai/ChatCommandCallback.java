package com.mehbur.mehburity.ai;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Lets features act on what the owner tells Mehburity in chat ("Mehburity, maden kaz") before it becomes a
 * question for Gemini. The first listener that returns an action handles the message and Gemini is not asked.
 * The action runs at the end of the tick, so Mehburity's answer shows up after the owner's own chat line.
 */
@FunctionalInterface
public interface ChatCommandCallback {
	Event<ChatCommandCallback> EVENT = EventFactory.createArrayBacked(ChatCommandCallback.class,
			listeners -> (owner, message) -> {
				for (ChatCommandCallback listener : listeners) {
					Runnable action = listener.handle(owner, message);
					if (action != null) {
						return action;
					}
				}
				return null;
			});

	/** {@code message} is the text after Mehburity's name; returns null when it is not an order for this listener. */
	@Nullable Runnable handle(ServerPlayer owner, String message);
}
