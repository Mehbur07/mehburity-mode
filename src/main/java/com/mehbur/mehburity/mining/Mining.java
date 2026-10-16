package com.mehbur.mehburity.mining;

import com.mehbur.mehburity.ai.GameEvents;
import com.mehbur.mehburity.config.MehburityConfig;
import com.mehbur.mehburity.entity.CompanionManager;
import com.mehbur.mehburity.entity.MehburityBoxEntity;
import com.mehbur.mehburity.entity.MehburityEntity;
import com.mehbur.mehburity.entity.MehburitySpeech;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Everything the owner can ask about mining, shared by {@code /mehburity kaz|ver|canta} and the chat
 * ("Mehburity, maden kaz", "kazdığın madenleri ver"). Each call answers the owner in Mehburity's voice and returns
 * whether it did something.
 */
public final class Mining {
	private Mining() {
	}

	public static void register() {
		MiningBag.register();
		MiningChat.register();
	}

	/** Starts mining every ore around the owner. */
	public static boolean startOres(ServerPlayer owner) {
		MehburityEntity mehburity = ready(owner);
		if (mehburity == null) {
			return false;
		}
		if (mehburity.getMiningJob() != null && mehburity.getMiningJob().kind() == MiningJob.Kind.ORES) {
			MehburitySpeech.say(owner, MehburitySpeech.text("mining.already", "I'm already mining!"));
			return true;
		}
		mehburity.setMiningJob(MiningJob.ores());
		MehburitySpeech.speak(owner, MehburitySpeech.text("mining.start", "Off I go! I'll mine the ores around here."));
		GameEvents.record(owner, "the player asked Mehburity to mine the ores nearby");
		return true;
	}

	/** Mines the block the owner is looking at, whatever it is (unless it is unbreakable or holds items). */
	public static boolean startLookedAt(ServerPlayer owner) {
		MehburityEntity mehburity = ready(owner);
		if (mehburity == null) {
			return false;
		}
		BlockPos pos = lookedAtBlock(owner);
		if (pos == null) {
			MehburitySpeech.say(owner, MehburitySpeech.text("mining.no_block", "Look at the block you want me to mine."));
			return false;
		}
		ServerLevel level = owner.level();
		BlockState state = level.getBlockState(pos);
		if (!MiningRules.isBreakable(level, pos, state)) {
			MehburitySpeech.say(owner, MehburitySpeech.text("mining.cannot_break",
					"I can't mine that one. (Unbreakable, or it holds items.)"));
			return false;
		}
		if (mehburity.level() != level) {
			mehburity = mehburity.teleportToOwner(owner);
		}
		mehburity.setMiningJob(MiningJob.single(pos));
		MehburitySpeech.speak(owner, MehburitySpeech.text("mining.start_one", "On it!"));
		return true;
	}

	public static boolean stop(ServerPlayer owner) {
		MehburityEntity mehburity = CompanionManager.find(owner.level().getServer(), owner.getUUID());
		if (mehburity == null || mehburity.getMiningJob() == null) {
			MehburitySpeech.say(owner, MehburitySpeech.text("mining.not_mining", "I'm not mining right now."));
			return false;
		}
		finish(mehburity, owner, MiningJob.End.STOPPED);
		return true;
	}

	/** Hands the whole bag to the owner; whatever does not fit in their inventory drops at their feet. */
	public static boolean give(ServerPlayer owner) {
		if (MiningBag.isEmpty(owner)) {
			MehburitySpeech.speak(owner, MehburitySpeech.text("mining.bag_empty", "My bag is empty. Want me to go mining?"));
			return false;
		}
		String summary = MiningBag.describe(owner);
		List<ItemStack> items = MiningBag.takeAll(owner);
		for (ItemStack stack : items) {
			owner.getInventory().placeItemBackInInventory(stack);
		}
		ServerLevel level = owner.level();
		level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
				0.6F, 1.0F);
		level.sendParticles(new DustParticleOptions(MehburityBoxEntity.NEON_CYAN, 1.0F),
				owner.getX(), owner.getY() + 1.0, owner.getZ(), 15, 0.3, 0.4, 0.3, 0.0);
		MehburitySpeech.speak(owner, MehburitySpeech.text("mining.given", "Here you go: %s", summary));
		GameEvents.record(owner, "Mehburity gave the player everything it had mined: " + summary);
		return true;
	}

	/** Says what is in the bag. */
	public static boolean list(ServerPlayer owner) {
		if (MiningBag.isEmpty(owner)) {
			MehburitySpeech.say(owner, MehburitySpeech.text("mining.bag_empty", "My bag is empty. Want me to go mining?"));
			return false;
		}
		MehburitySpeech.say(owner, MehburitySpeech.text("mining.bag", "In my bag: %s", MiningBag.describe(owner)));
		return true;
	}

	/** Ends the current job and tells the owner how it went. Called by the mining goal and {@link #stop}. */
	static void finish(MehburityEntity mehburity, ServerPlayer owner, MiningJob.End end) {
		MiningJob job = mehburity.getMiningJob();
		mehburity.setMiningJob(null);
		int mined = job == null ? 0 : job.mined();
		Component line = switch (end) {
			case NOTHING_LEFT -> mined == 0
					? MehburitySpeech.text("mining.none_found", "I can't see any ores around here.")
					: MehburitySpeech.text("mining.nothing_left", "No more ores around here! I mined %s blocks.", mined);
			case BAG_FULL -> MehburitySpeech.text("mining.bag_full",
					"My bag is full! Say \"kazdığın madenleri ver\" and I'll hand everything over.");
			case TRIP_LIMIT -> MehburitySpeech.text("mining.trip_limit", "That's %s blocks, I'm taking a break!", mined);
			case DONE -> MehburitySpeech.text("mining.done_one", "Done!");
			case UNREACHABLE -> MehburitySpeech.text("mining.unreachable", "I can't reach that block.");
			case STOPPED -> MehburitySpeech.text("mining.stopped", "Okay, I stopped mining.");
		};
		MehburitySpeech.speak(owner, line);
		if (mined > 0) {
			GameEvents.record(owner, "Mehburity finished mining (" + mined + " blocks). Its bag: " + MiningBag.describe(owner));
		}
	}

	/** Mehburity is out of its box and loaded; otherwise tells the owner why not. */
	private static @Nullable MehburityEntity ready(ServerPlayer owner) {
		if (!MehburityConfig.get().mining.enabled) {
			MehburitySpeech.say(owner, MehburitySpeech.text("mining.server_off", "Mining is turned off on this server."));
			return null;
		}
		MehburityEntity mehburity = CompanionManager.find(owner.level().getServer(), owner.getUUID());
		if (mehburity == null) {
			owner.sendSystemMessage(Component.translatableWithFallback("mehburity.command.not_nearby",
					"Your Mehburity is not nearby. Use /mehburity cagir first."));
			return null;
		}
		if (mehburity.getBuildJob() != null) {
			MehburitySpeech.say(owner, MehburitySpeech.text("mining.busy_building", "I'm building your house right now!"));
			return null;
		}
		if (MiningBag.isFull(owner)) {
			MehburitySpeech.say(owner, MehburitySpeech.text("mining.bag_full",
					"My bag is full! Say \"kazdığın madenleri ver\" and I'll hand everything over."));
			return null;
		}
		return mehburity;
	}

	private static @Nullable BlockPos lookedAtBlock(ServerPlayer owner) {
		HitResult hit = owner.pick(MehburityConfig.get().mining.pointRange, 1.0F, false);
		return hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK ? blockHit.getBlockPos() : null;
	}

	/** One line for Gemini, or "" when there is nothing to tell. */
	public static String describeFor(ServerPlayer owner, @Nullable MehburityEntity mehburity) {
		StringBuilder out = new StringBuilder();
		if (mehburity != null && mehburity.getMiningJob() != null) {
			out.append("Mehburity is mining right now (").append(mehburity.getMiningJob().mined()).append(" blocks so far)\n");
		}
		String bag = MiningBag.describe(owner);
		out.append("Mehburity's mining bag: ").append(bag.isEmpty() ? "empty" : bag).append('\n');
		return out.toString();
	}
}
