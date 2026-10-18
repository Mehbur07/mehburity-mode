package com.mehbur.mehburity.build;

import com.mehbur.mehburity.ai.ChatAddress;
import com.mehbur.mehburity.ai.ChatCommandCallback;
import com.mehbur.mehburity.entity.CompanionRegistry;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * Building orders in plain chat, Turkish or English: "Mehburity, ev yap", "taş ev yap", "elimdeki bloktan ev yap",
 * "inşaatı durdur", "evi geri al". Matching runs on accent-free lower case ({@link ChatAddress#fold}).
 */
public final class BuildChat {
	public enum Action { BUILD, STOP, UNDO }

	/**
	 * The order and, for {@link Action#BUILD}, the style asked for (null: the default one) and the whole description,
	 * which may ask for much more ("iki katlı, havuzlu, kırmızı çatılı bir ev").
	 */
	public record Order(Action action, @Nullable HouseStyle style, String description) {
		public Order(Action action, @Nullable HouseStyle style) {
			this(action, style, "");
		}
	}

	/** Words of the order itself, which describe nothing about the house (accent-free). */
	private static final Pattern ORDER_WORDS = Pattern.compile(
			"\\b(mehburity|lutfen|please|bana|bize|benim|icin|bir|ev|evi|evim|eve|kulube\\w*|insa\\w*|yap\\w*|kur\\w*|"
			+ "misin|musun|mi|mu|hadi|haydi|simdi|bi|house|home|hut|cabin|build\\w*|make|me|us|a|an|the|for|can|you|could|"
			+ "would|now|please|tas|stone|tugla|brick\\w*|ahsap|tahta\\w*|wood\\w*|oak|elimdeki\\w*|elindeki\\w*|bu|blok\\w*|"
			+ "bununla|holding|this|block|my|in|hand)\\b");

	/** "ev", "evi", "evim"... but not "evet" (yes); a building verb is needed too, so "eve gidiyorum" is no order. */
	private static final Pattern HOUSE = Pattern.compile(
			"(\\bev(i|im|imi|ini|e|imiz)?\\b|\\bkulube\\w*|\\binsa\\w*|\\bhouse\\b|\\bhome\\b|\\bhut\\b|\\bcabin\\b)");
	private static final Pattern BUILD_VERB = Pattern.compile("(\\byap\\w*|\\binsa\\w*|\\bkur\\w*|\\bbuild\\w*|\\bmake\\b)");
	private static final Pattern STOP_VERB = Pattern.compile("(\\bdur(dur\\w*|\\b)|birak|\\bkes\\b|\\byeter\\b|\\bstop\\b|\\bquit\\b)");
	private static final Pattern UNDO_VERB = Pattern.compile(
			"(geri al|\\bkaldir\\w*|\\byik\\w*|\\bsok\\w*|\\bsil\\w*|\\bundo\\b|\\bremove\\b|tear (it )?down|take (it )?down|\\bdemolish\\b)");

	private static final Pattern STONE = Pattern.compile("(\\btas\\b|\\bstone\\b)");
	private static final Pattern BRICK = Pattern.compile("(\\btugla\\w*|\\bbrick\\w*)");
	private static final Pattern WOOD = Pattern.compile("(\\bahsap\\b|\\btahta\\w*|\\bwood\\w*|\\boak\\b)");
	private static final Pattern HELD = Pattern.compile(
			"(\\belimdeki\\w*|\\belindeki\\w*|\\bbu blok\\w*|\\bbununla\\b|\\bholding\\b|\\bthis block\\b|\\bmy block\\b|\\bin my hand\\b)");

	private BuildChat() {
	}

	static void register() {
		ChatCommandCallback.EVENT.register(BuildChat::handle);
	}

	/** What the owner asked for, or null when the message is not about building. */
	public static @Nullable Order parse(String message) {
		String text = ChatAddress.fold(message);
		if (!HOUSE.matcher(text).find()) {
			return null;
		}
		if (UNDO_VERB.matcher(text).find()) {
			return new Order(Action.UNDO, null);
		}
		if (STOP_VERB.matcher(text).find()) {
			return new Order(Action.STOP, null);
		}
		if (BUILD_VERB.matcher(text).find()) {
			return new Order(Action.BUILD, style(text), message);
		}
		return null;
	}

	/** Nothing asked beyond a house (and a style): build the classic one without designing anything. */
	public static boolean isPlain(String description) {
		String rest = ORDER_WORDS.matcher(ChatAddress.fold(description)).replaceAll(" ").replaceAll("[^a-z0-9]+", "");
		return rest.isEmpty();
	}

	private static @Nullable HouseStyle style(String text) {
		if (HELD.matcher(text).find()) {
			return HouseStyle.HELD;
		}
		if (BRICK.matcher(text).find()) {
			return HouseStyle.BRICK;
		}
		if (STONE.matcher(text).find()) {
			return HouseStyle.STONE;
		}
		if (WOOD.matcher(text).find()) {
			return HouseStyle.WOOD;
		}
		return null;
	}

	private static @Nullable Runnable handle(ServerPlayer owner, String message) {
		Order order = parse(message);
		if (order == null || CompanionRegistry.companion(owner.level().getServer(), owner.getUUID()) == null) {
			return null;
		}
		return () -> {
			switch (order.action()) {
				case BUILD -> Building.start(owner, order.style(), order.description());
				case STOP -> Building.stop(owner);
				case UNDO -> Building.undo(owner);
			}
		};
	}
}
