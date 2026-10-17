package com.mehbur.mehburity.anger;

import com.mehbur.mehburity.ai.AiRouter;
import com.mehbur.mehburity.ai.ChatAddress;
import com.mehbur.mehburity.ai.ChatCommandCallback;
import com.mehbur.mehburity.config.MehburityConfig;
import com.mehbur.mehburity.entity.CompanionManager;
import com.mehbur.mehburity.entity.CompanionRegistry;
import com.mehbur.mehburity.entity.MehburityEntity;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Apologies and insults said to Mehburity. The owner's own Mehburity hears them through the chat orders; any other
 * player (the culprit of a rage, usually) can address the nearest Mehburity: "Mehburity, özür dilerim!".
 */
public final class AngerChat {
	public enum Intent { APOLOGY, INSULT }

	private static final Pattern APOLOGY = Pattern.compile(
			"(\\bozur\\w*|\\baffet\\w*|\\bpardon\\b|\\bkusura bakma\\w*|\\bsorry\\b|\\bapologi[sz]e\\w*|\\bforgive me\\b)");

	private static @Nullable List<String> insultsFrom;
	private static @Nullable Pattern insults;

	private AngerChat() {
	}

	static void register() {
		ChatCommandCallback.EVENT.register(AngerChat::fromOwner);
		ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> fromOthers(sender, message.signedContent()));
	}

	public static @Nullable Intent parse(String message) {
		String text = ChatAddress.fold(message);
		if (APOLOGY.matcher(text).find()) {
			return Intent.APOLOGY;
		}
		Pattern insult = insultPattern();
		return insult != null && insult.matcher(text).find() ? Intent.INSULT : null;
	}

	/** Words or phrases from {@code anger.insults} at the start of a word, rebuilt when the list changes. */
	private static @Nullable Pattern insultPattern() {
		List<String> words = MehburityConfig.get().anger.insults;
		if (!words.equals(insultsFrom)) {
			// Word starts only, so Turkish suffixes still match ("aptalsın", "salaksın").
			insults = words.isEmpty() ? null : Pattern.compile("\\b(" + String.join("|", words.stream()
					.map(word -> Pattern.quote(ChatAddress.fold(word.strip()))).toList()) + ")");
			insultsFrom = List.copyOf(words);
		}
		return insults;
	}

	private static @Nullable Runnable fromOwner(ServerPlayer owner, String message) {
		Intent intent = MehburityConfig.get().anger.enabled ? parse(message) : null;
		if (intent == null) {
			return null;
		}
		return () -> {
			MehburityEntity mehburity = CompanionManager.find(owner.level().getServer(), owner.getUUID());
			if (mehburity != null) {
				react(mehburity, owner, intent);
			}
		};
	}

	/** A player without their own Mehburity (or talking to someone else's) speaks to the nearest one. */
	private static void fromOthers(ServerPlayer sender, String text) {
		MehburityConfig config = MehburityConfig.get();
		if (!config.anger.enabled || CompanionRegistry.companion(sender.level().getServer(), sender.getUUID()) != null) {
			return; // owners go through the chat orders
		}
		String message = ChatAddress.extract(text, config.ai.chatPrefixes);
		Intent intent = message == null ? null : parse(message);
		if (intent == null) {
			return;
		}
		double radius = config.ai.hearingRadius;
		MehburityEntity nearest = sender.level().getEntitiesOfClass(MehburityEntity.class, sender.getBoundingBox().inflate(radius))
				.stream()
				// The one raging at them first, then the closest.
				.min(Comparator.comparing((MehburityEntity m) -> m.getRageCulprit() != sender).thenComparingDouble(m -> m.distanceToSqr(sender)))
				.orElse(null);
		if (nearest != null) {
			AiRouter.later(() -> react(nearest, sender, intent));
		}
	}

	private static void react(MehburityEntity mehburity, ServerPlayer speaker, Intent intent) {
		switch (intent) {
			case APOLOGY -> Anger.apologize(mehburity, speaker);
			case INSULT -> Anger.insult(mehburity, speaker);
		}
	}
}
