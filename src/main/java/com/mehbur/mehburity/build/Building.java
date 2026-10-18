package com.mehbur.mehburity.build;

import com.mehbur.mehburity.MehburityMod;
import com.mehbur.mehburity.ai.GameEvents;
import com.mehbur.mehburity.config.MehburityConfig;
import com.mehbur.mehburity.entity.CompanionManager;
import com.mehbur.mehburity.entity.MehburityBoxEntity;
import com.mehbur.mehburity.entity.MehburityEntity;
import com.mehbur.mehburity.entity.MehburitySpeech;
import com.mehbur.mehburity.mining.MiningRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything the owner can ask about building, shared by {@code /mehburity ev} and the chat ("Mehburity, ev yap",
 * "evi geri al"). Creative-style: nothing is taken from the owner. Each call answers in Mehburity's voice and
 * returns whether it did something.
 */
public final class Building {
	/** How a build ended. */
	public enum End { DONE, STOPPED }

	/** What the last house replaced, per owner, to take it down again. Kept until the server stops. */
	private record Undo(ResourceKey<Level> dimension, Map<BlockPos, BlockState> before) {
	}

	private static final Map<UUID, Undo> UNDO = new HashMap<>();

	private Building() {
	}

	public static void register() {
		BuildChat.register();
		HouseDesigner.register();
	}

	/** Builds the classic house in front of the owner; {@code style} null means {@code build.defaultStyle}. */
	public static boolean start(ServerPlayer owner, @Nullable HouseStyle style) {
		return start(owner, style, null);
	}

	/**
	 * Builds the house {@code description} asks for ("iki katlı havuzlu beyaz bir ev"), designed from its words and,
	 * with a Gemini key, by Gemini; a bare "ev yap" (or a null description) builds the classic house at once.
	 */
	public static boolean start(ServerPlayer owner, @Nullable HouseStyle style, @Nullable String description) {
		if (busyOrMissing(owner) != null) {
			return busyOrMissing(owner);
		}
		HouseStyle chosen = style != null ? style : HouseStyle.byId(MehburityConfig.get().build.defaultStyle);
		HouseStyle.Palette palette = (chosen == null ? HouseStyle.WOOD : chosen).palette();
		if (chosen == HouseStyle.HELD) {
			BlockState walls = heldWallBlock(owner);
			if (walls == null) {
				return false;
			}
			palette = palette.withWalls(walls);
		}
		if (description == null || BuildChat.isPlain(description)) {
			return begin(owner, HouseSpec.standard(palette), null);
		}
		return HouseDesigner.design(owner, description, palette);
	}

	/** Null when Mehburity can build now; otherwise says why not and returns what {@link #start} returns. */
	private static @Nullable Boolean busyOrMissing(ServerPlayer owner) {
		if (!MehburityConfig.get().build.enabled) {
			MehburitySpeech.say(owner, MehburitySpeech.text("build.server_off", "Building is turned off on this server."));
			return false;
		}
		MehburityEntity mehburity = CompanionManager.find(owner.level().getServer(), owner.getUUID());
		if (mehburity == null) {
			owner.sendSystemMessage(Component.translatableWithFallback("mehburity.command.not_nearby",
					"Your Mehburity is not nearby. Use /mehburity cagir first."));
			return false;
		}
		if (mehburity.getBuildJob() != null) {
			MehburitySpeech.say(owner, MehburitySpeech.text("build.already", "I'm already building!"));
			return true;
		}
		return null;
	}

	/** Builds {@code spec} in front of the owner ({@code description}: what they asked for, null for the classic house). */
	static boolean begin(ServerPlayer owner, HouseSpec spec, @Nullable String description) {
		if (busyOrMissing(owner) != null) {
			return busyOrMissing(owner);
		}
		MehburityEntity mehburity = CompanionManager.find(owner.level().getServer(), owner.getUUID());
		ServerLevel level = owner.level();
		HousePlan plan = findSpot(level, owner, spec);
		if (plan == null) {
			MehburitySpeech.say(owner, MehburitySpeech.text("build.no_room",
					"I can't build here: there is something I must not touch, or no room. Try a flatter, open spot."));
			return false;
		}
		if (mehburity.level() != level) {
			mehburity = mehburity.teleportToOwner(owner);
		}
		mehburity.setMiningJob(null); // one job at a time
		BuildJob job = new BuildJob(plan);
		mehburity.setBuildJob(job);
		UNDO.put(owner.getUUID(), new Undo(level.dimension(), job.before()));
		if (description == null) {
			MehburitySpeech.say(owner, MehburitySpeech.text("build.start", "Leave it to me! I'm building you a house."));
			GameEvents.record(owner, "the player asked Mehburity to build a house; it started building right in front of them");
		} else {
			MehburitySpeech.say(owner, summary(spec));
			GameEvents.record(owner, "the player asked Mehburity to build: \"" + description
					+ "\"; it started building it right in front of them");
		}
		return true;
	}

	public static boolean stop(ServerPlayer owner) {
		MehburityEntity mehburity = CompanionManager.find(owner.level().getServer(), owner.getUUID());
		if (mehburity == null || mehburity.getBuildJob() == null) {
			MehburitySpeech.say(owner, MehburitySpeech.text("build.not_building", "I'm not building anything right now."));
			return false;
		}
		finish(mehburity, owner, End.STOPPED);
		return true;
	}

	/** Takes the last house down and puts back what was there before. */
	public static boolean undo(ServerPlayer owner) {
		MehburityEntity mehburity = CompanionManager.find(owner.level().getServer(), owner.getUUID());
		if (mehburity != null && mehburity.getBuildJob() != null) {
			mehburity.setBuildJob(null);
		}
		Undo undo = UNDO.get(owner.getUUID());
		ServerLevel level = undo == null ? null : owner.level().getServer().getLevel(undo.dimension());
		if (undo == null || level == null || undo.before().isEmpty()) {
			MehburitySpeech.say(owner, MehburitySpeech.text("build.nothing_to_undo", "I haven't built anything to take down."));
			return false;
		}
		UNDO.remove(owner.getUUID());
		List<Map.Entry<BlockPos, BlockState>> changes = new ArrayList<>(undo.before().entrySet());
		Vec3 middle = Vec3.ZERO;
		for (int i = changes.size() - 1; i >= 0; i--) {
			BlockPos pos = changes.get(i).getKey();
			// No neighbour updates: half a door or bed would otherwise pop off as an item while we go.
			level.setBlock(pos, changes.get(i).getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			middle = middle.add(Vec3.atCenterOf(pos));
		}
		middle = middle.scale(1.0 / changes.size());
		level.sendParticles(new DustParticleOptions(MehburityBoxEntity.NEON_CYAN, 1.5F), middle.x, middle.y, middle.z,
				60, 3.0, 2.0, 3.0, 0.0);
		level.playSound(null, middle.x, middle.y, middle.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.NEUTRAL, 1.0F, 1.0F);
		MehburitySpeech.say(owner, MehburitySpeech.text("build.undone", "Poof! The house is gone and everything is back as it was."));
		GameEvents.record(owner, "Mehburity took the house down again, as the player asked");
		return true;
	}

	/** Ends the current build and tells the owner. Called by the build goal and {@link #stop}. */
	static void finish(MehburityEntity mehburity, ServerPlayer owner, End end) {
		mehburity.setBuildJob(null);
		if (end == End.DONE) {
			MehburitySpeech.say(owner, MehburitySpeech.text("build.done",
					"Your house is ready! There's a bed, a chest and torches inside. Do you like it?"));
			GameEvents.record(owner, "Mehburity finished building the player's house");
		} else {
			MehburitySpeech.say(owner, MehburitySpeech.text("build.stopped",
					"Okay, I stopped building. Say \"evi geri al\" if you want it gone."));
		}
	}

	/**
	 * May Mehburity replace {@code current} at {@code pos}? Never unbreakable blocks, blocks holding items or data,
	 * or anything the owner could not change there themselves (spawn protection, protection mods).
	 */
	static boolean mayChange(ServerLevel level, ServerPlayer owner, BlockPos pos, BlockState current) {
		if (!level.isInWorldBounds(pos) || !level.mayInteract(owner, pos) || current.hasBlockEntity()) {
			return false;
		}
		if (current.isAir() || current.getBlock() instanceof LiquidBlock) {
			return true;
		}
		return MiningRules.isBreakable(level, pos, current) && MiningRules.ownerMayBreak(level, owner, pos, current);
	}

	/** "Leave it to me! A 9×9, 2-storey house (pool, garden)." in the owner's language. */
	static Component summary(HouseSpec spec) {
		List<Component> extras = new ArrayList<>();
		feature(extras, spec.pool(), "pool", "a pool");
		feature(extras, spec.garden(), "garden", "a garden");
		feature(extras, spec.balcony(), "balcony", "a balcony");
		feature(extras, spec.porch(), "porch", "a porch");
		feature(extras, spec.tower(), "tower", "a tower");
		feature(extras, spec.chimney(), "chimney", "a chimney");
		feature(extras, spec.library(), "library", "a library");
		feature(extras, spec.kitchen(), "kitchen", "a kitchen");
		feature(extras, spec.bigWindows(), "big_windows", "big windows");
		Component tail = Component.empty();
		if (!extras.isEmpty()) {
			var list = Component.literal(" (");
			for (int i = 0; i < extras.size(); i++) {
				list.append(i == 0 ? Component.empty() : Component.literal(", ")).append(extras.get(i));
			}
			tail = list.append(")");
		}
		return MehburitySpeech.text("build.start_spec", "Leave it to me! I'm building you a %1$s×%2$s, %3$s-storey house%4$s.",
				spec.width(), spec.depth(), spec.floors(), tail);
	}

	private static void feature(List<Component> extras, boolean has, String key, String fallback) {
		if (has) {
			extras.add(MehburitySpeech.text("build.feature." + key, fallback));
		}
	}

	/** Tries in front of the owner first, then to their right, behind and left. */
	private static @Nullable HousePlan findSpot(ServerLevel level, ServerPlayer owner, HouseSpec spec) {
		Direction facing = owner.getDirection();
		for (int turn = 0; turn < 4; turn++) {
			HousePlan plan = HousePlan.inFrontOf(level, owner.blockPosition(), facing, spec);
			if (isAllowed(level, owner, plan)) {
				return plan;
			}
			facing = facing.getClockWise();
		}
		return null;
	}

	private static boolean isAllowed(ServerLevel level, ServerPlayer owner, HousePlan plan) {
		for (BlockPos pos : plan.area()) {
			BlockState state = level.getBlockState(pos);
			if (!mayChange(level, owner, pos, state)) {
				if (MehburityConfig.get().general.debugLogging) {
					MehburityMod.LOGGER.info("House facing {} refused: may not change {} at {}", plan.facing(), state, pos);
				}
				return false;
			}
		}
		// The owner must not end up inside the walls.
		return !plan.area().contains(owner.blockPosition()) && !plan.area().contains(owner.blockPosition().above());
	}

	/**
	 * The block in the owner's hand, if it can make a wall: a full, breakable block that is not an operator block
	 * (command blocks, barriers...). Says why not otherwise.
	 */
	private static @Nullable BlockState heldWallBlock(ServerPlayer owner) {
		if (!MehburityConfig.get().build.allowHeldBlock) {
			MehburitySpeech.say(owner, MehburitySpeech.text("build.held_off", "Building with your own block is turned off on this server."));
			return null;
		}
		ItemStack held = owner.getMainHandItem();
		if (!(held.getItem() instanceof BlockItem blockItem)) {
			MehburitySpeech.say(owner, MehburitySpeech.text("build.hold_block", "Hold the block you want the walls made of."));
			return null;
		}
		Block block = blockItem.getBlock();
		BlockState state = block.defaultBlockState();
		boolean unbreakable = state.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) < 0.0F;
		if (unbreakable || block instanceof GameMasterBlock) {
			MehburitySpeech.say(owner, MehburitySpeech.text("build.held_unbreakable", "I can't build with unbreakable blocks."));
			return null;
		}
		if (!state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
			MehburitySpeech.say(owner, MehburitySpeech.text("build.held_not_full", "That won't make a wall. Hold a full block."));
			return null;
		}
		return state;
	}

	/** One line for Gemini, or "" when Mehburity is not building. */
	public static String describeFor(@Nullable MehburityEntity mehburity) {
		BuildJob job = mehburity == null ? null : mehburity.getBuildJob();
		if (job == null) {
			return "";
		}
		int percent = job.total() == 0 ? 100 : job.progress() * 100 / job.total();
		return "Mehburity is building a house for the player right now (" + percent + "% done)\n";
	}
}
