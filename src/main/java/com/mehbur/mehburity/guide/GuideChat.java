package com.mehbur.mehburity.guide;

import com.mehbur.mehburity.ai.ChatAddress;
import com.mehbur.mehburity.ai.ChatCommandCallback;
import com.mehbur.mehburity.entity.CompanionManager;
import com.mehbur.mehburity.entity.CompanionRegistry;
import com.mehbur.mehburity.entity.MehburityEntity;
import com.mehbur.mehburity.entity.MehburitySpeech;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * Guiding orders in plain chat, Turkish or English: "Mehburity, beni en yakın köye götür", "en yakın kaleye yol
 * göster", "take me to the nearest village", and "dur" / "vazgeç" / "stop" while it leads the way. Matching runs on
 * accent-free lower case ({@link ChatAddress#fold}). Anything else goes on (to the other orders, then Gemini).
 */
public final class GuideChat {
	/** A request to be taken somewhere: "götür", "yol göster", "take me", "lead me", "show me the way". */
	private static final Pattern GUIDE_VERB = Pattern.compile(
			"(\\bgotur\\w*|\\byol(u)? goster\\w*|\\brehberlik\\b|\\bkilavuzluk\\b|\\b(take|lead|guide|bring) (me|us)\\b"
					+ "|\\bshow (me|us) the way\\b)");
	private static final Pattern NEAREST = Pattern.compile("(\\ben yakin\\w*|\\bnearest\\b|\\bclosest\\b)");
	private static final Pattern STOP = Pattern.compile(
			"(\\bdur(dur\\w*|\\b)|\\bbirak\\w*|\\bvazgec\\w*|\\biptal\\w*|\\bbosver\\b|\\bstop\\b|\\bcancel\\b|\\bnever ?mind\\b)");

	public sealed interface Order permits Go, Stop, Unknown {
	}

	/** Lead the way to {@code destination}. */
	public record Go(Destination destination) implements Order {
	}

	/** Stop leading the way (only heard while it does). */
	public record Stop() implements Order {
	}

	/** Asked to be taken to the nearest something it does not know. */
	public record Unknown() implements Order {
	}

	private GuideChat() {
	}

	static void register() {
		ChatCommandCallback.EVENT.register(GuideChat::handle);
	}

	/** What the owner asked for, or null when it is not about guiding ({@code guiding}: it is leading the way now). */
	public static @Nullable Order parse(String message, boolean guiding) {
		String text = ChatAddress.fold(message);
		if (GUIDE_VERB.matcher(text).find()) {
			Destination destination = Destination.find(text);
			if (destination != null) {
				return new Go(destination);
			}
			if (NEAREST.matcher(text).find()) {
				return new Unknown();
			}
		}
		if (guiding && STOP.matcher(text).find()) {
			return new Stop();
		}
		return null;
	}

	private static @Nullable Runnable handle(ServerPlayer owner, String message) {
		if (CompanionRegistry.companion(owner.level().getServer(), owner.getUUID()) == null) {
			return null;
		}
		MehburityEntity mehburity = CompanionManager.find(owner.level().getServer(), owner.getUUID());
		Order order = parse(message, mehburity != null && mehburity.getGuideJob() != null);
		if (order == null) {
			return null;
		}
		return () -> {
			switch (order) {
				case Go go -> Guiding.start(owner, go.destination());
				case Stop stop -> Guiding.stop(owner);
				case Unknown unknown -> MehburitySpeech.say(owner, MehburitySpeech.text("guide.unknown",
						"Where to? Say a place like a village, stronghold, desert temple, ocean monument or ancient city."));
			}
		};
	}
}
