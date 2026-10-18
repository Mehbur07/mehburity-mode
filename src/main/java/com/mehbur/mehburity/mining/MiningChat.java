package com.mehbur.mehburity.mining;

import com.mehbur.mehburity.ai.ChatAddress;
import com.mehbur.mehburity.ai.ChatCommandCallback;
import com.mehbur.mehburity.entity.CompanionRegistry;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * Mining orders in plain chat, Turkish or English: "Mehburity, maden kaz", "bunu kaz", "bu ağacı kes", "kazmayı bırak",
 * "ne kazdın?", "kazdığın madenleri ver". Matching runs on accent-free lower case ({@link ChatAddress#fold}).
 * Anything else goes on to Gemini as a normal question.
 */
public final class MiningChat {
	public enum Intent { GIVE, STOP, LIST, TREE, MINE_HERE, MINE }

	/** "kaz..." (dig) but not "kazan..." (win, cauldron) or "kazık". */
	private static final String DIG = "(\\bkaz(?!an|ik)\\w*|\\bmine\\b|\\bmining\\b|\\bdig\\w*|\\bdug\\b)";
	private static final String MINED_THINGS = "(maden|kazd|cevher|esya|topla|canta|odun|kutuk|\\blogs?\\b|\\bwood\\b|\\bores?\\b|\\bmined\\b|\\bitems?\\b|\\bloot\\b|\\bstuff\\b|\\bbag\\b|\\bdrops\\b)";

	private static final Pattern GIVE_VERB = Pattern.compile("(\\bver\\w*|\\bgive\\b|\\bhand\\b)");
	private static final Pattern GIVE_OBJECT = Pattern.compile(MINED_THINGS);
	private static final Pattern STOP_VERB = Pattern.compile("(\\bdur(dur\\w*|\\b)|birak|\\bkes\\b|\\byeter\\b|\\bstop\\b|\\benough\\b|\\bquit\\b)");
	private static final Pattern MINING_WORD = Pattern.compile("(" + DIG + "|maden)");
	private static final Pattern LIST = Pattern.compile(
			"(\\bne(ler)?\\b.*\\b(kazd|topladi)|\\bcantan|\\bwhat\\b.*\\b(mine|mined|dug|bag|got)\\b|\\binventory\\b|\\bin your bag\\b)");
	private static final Pattern HERE = Pattern.compile("(\\b(bunu|sunu|onu)\\b|\\b(bu|su|o) blo|\\b(this|that) block\\b|\\b(this|that)\\b)");
	private static final Pattern DIG_WORD = Pattern.compile(DIG);
	private static final Pattern TREE_WORD = Pattern.compile("(\\bagac\\w*|\\btrees?\\b|\\bodun\\w*|\\bkutu?k\\w*|\\blogs?\\b|\\bwood\\b)");
	private static final Pattern TREE_VERB = Pattern.compile(DIG + "|\\bkes\\w*|\\bkir\\w*|\\bdevir\\w*|\\btopla\\w*|\\bcut\\w*|\\bchop\\w*|\\bfell\\b");
	/** Stopping a tree job; without "kes", which means "cut" next to a tree. */
	private static final Pattern TREE_STOP = Pattern.compile("(\\bdur(dur\\w*|\\b)|birak|\\byeter\\b|\\bstop\\b|\\benough\\b|\\bquit\\b)");
	private static final Pattern GO_MINING = Pattern.compile("(maden\\w* (bul|topla|ara|cikar)|\\bmadene\\b)");

	private MiningChat() {
	}

	static void register() {
		ChatCommandCallback.EVENT.register(MiningChat::handle);
	}

	/** What the owner asked for, or null when the message is not about mining. */
	public static @Nullable Intent parse(String message) {
		String text = ChatAddress.fold(message);
		if (GIVE_VERB.matcher(text).find() && GIVE_OBJECT.matcher(text).find()) {
			return Intent.GIVE;
		}
		if (STOP_VERB.matcher(text).find() && MINING_WORD.matcher(text).find()) {
			return Intent.STOP;
		}
		boolean tree = TREE_WORD.matcher(text).find();
		if (tree && TREE_STOP.matcher(text).find()) {
			return Intent.STOP;
		}
		if (LIST.matcher(text).find()) {
			return Intent.LIST;
		}
		if (tree && TREE_VERB.matcher(text).find()) {
			return Intent.TREE;
		}
		boolean dig = DIG_WORD.matcher(text).find();
		if (dig && HERE.matcher(text).find()) {
			return Intent.MINE_HERE;
		}
		if (dig || GO_MINING.matcher(text).find()) {
			return Intent.MINE;
		}
		return null;
	}

	private static @Nullable Runnable handle(ServerPlayer owner, String message) {
		Intent intent = parse(message);
		if (intent == null || CompanionRegistry.companion(owner.level().getServer(), owner.getUUID()) == null) {
			return null;
		}
		return () -> {
			switch (intent) {
				case GIVE -> Mining.give(owner);
				case STOP -> Mining.stop(owner);
				case LIST -> Mining.list(owner);
				case TREE -> Mining.startTree(owner);
				case MINE_HERE -> Mining.startLookedAt(owner);
				case MINE -> Mining.startOres(owner);
			}
		};
	}
}
