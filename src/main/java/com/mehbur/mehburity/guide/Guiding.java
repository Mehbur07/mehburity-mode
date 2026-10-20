package com.mehbur.mehburity.guide;

import com.mehbur.mehburity.ai.GameEvents;
import com.mehbur.mehburity.entity.CompanionManager;
import com.mehbur.mehburity.entity.MehburityEntity;
import com.mehbur.mehburity.entity.MehburitySpeech;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Leading the owner to the nearest structure of a kind, shared by {@code /mehburity gotur} and the chat
 * ("Mehburity, beni en yakın köye götür"). Finds it like {@code /locate} does, tells the owner how far and which
 * way, then {@link GuideGoal} rolls ahead and waits for them to keep up.
 */
public final class Guiding {
	/** Searches this many chunks around the owner, like {@code /locate}. */
	private static final int SEARCH_CHUNKS = 100;
	/** Arrived once the owner is this close to the structure (horizontally). */
	static final double ARRIVE_DISTANCE = 24.0;
	private static final String[] DIRECTIONS = {"north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest"};

	private Guiding() {
	}

	public static void register() {
		GuideChat.register();
	}

	/** Finds the nearest {@code destination} and starts leading the owner there. */
	public static boolean start(ServerPlayer owner, Destination destination) {
		MehburityEntity mehburity = ready(owner);
		if (mehburity == null) {
			return false;
		}
		ServerLevel level = owner.level();
		Destination wanted = destination.in(level);
		BlockPos found = locate(level, wanted, owner.blockPosition());
		if (found == null) {
			MehburitySpeech.say(owner, MehburitySpeech.text("guide.not_found", "I couldn't find any %s around here.", name(wanted)));
			return false;
		}
		return startTo(owner, mehburity, wanted, found);
	}

	/** Leads the owner to {@code target} (the structure was found already). */
	public static boolean startTo(ServerPlayer owner, MehburityEntity mehburity, Destination destination, BlockPos target) {
		double distance = horizontalDistance(owner, target);
		if (distance < ARRIVE_DISTANCE) {
			MehburitySpeech.say(owner, MehburitySpeech.text("guide.already_here", "We're already there, the %s is right here!",
					name(destination)));
			return false;
		}
		if (mehburity.level() != owner.level()) {
			mehburity = mehburity.teleportToOwner(owner);
		}
		mehburity.setMiningJob(null);
		mehburity.setBuildJob(null);
		mehburity.setGuideJob(new GuideJob(destination, target, owner.level().dimension()));
		MehburitySpeech.say(owner, MehburitySpeech.text("guide.start", "The nearest %s is %s blocks away, to the %s. Follow me!",
				name(destination), (int) Math.round(distance), direction(owner, target)));
		GameEvents.record(owner, "the player asked Mehburity to lead them to the nearest "
				+ destination.id().replace('_', ' ') + "; it is leading the way");
		return true;
	}

	/** The owner called it off. */
	public static boolean stop(ServerPlayer owner) {
		MehburityEntity mehburity = CompanionManager.find(owner.level().getServer(), owner.getUUID());
		if (mehburity == null || mehburity.getGuideJob() == null) {
			MehburitySpeech.say(owner, MehburitySpeech.text("guide.not_guiding", "I'm not leading you anywhere right now."));
			return false;
		}
		mehburity.setGuideJob(null);
		mehburity.getNavigation().stop();
		MehburitySpeech.say(owner, MehburitySpeech.text("guide.stopped", "Okay, I'll stop leading the way."));
		return true;
	}

	/** The owner reached the structure. */
	static void arrive(MehburityEntity mehburity, ServerPlayer owner, GuideJob job) {
		mehburity.setGuideJob(null);
		mehburity.getNavigation().stop();
		Component name = name(job.destination());
		MehburitySpeech.say(owner, job.destination().underground()
				? MehburitySpeech.text("guide.arrived_below", "Here we are, the %s is right below us! You'll have to dig down.", name)
				: MehburitySpeech.text("guide.arrived", "Here we are, the %s is right here!", name));
		GameEvents.record(owner, "Mehburity led the player to the " + job.destination().id().replace('_', ' '));
	}

	/** The owner went to another dimension: the way there is gone. */
	static void lost(MehburityEntity mehburity, ServerPlayer owner) {
		mehburity.setGuideJob(null);
		mehburity.getNavigation().stop();
		MehburitySpeech.say(owner, MehburitySpeech.text("guide.lost", "You went somewhere else, so I stopped leading the way."));
	}

	/** There is no way a player can walk (or swim) there from here. */
	static void noWay(MehburityEntity mehburity, ServerPlayer owner) {
		GuideJob job = mehburity.getGuideJob();
		mehburity.setGuideJob(null);
		mehburity.getNavigation().stop();
		MehburitySpeech.say(owner, MehburitySpeech.text("guide.no_way",
				"I can't find a way to the %s on foot from here. Maybe from somewhere else?",
				job == null ? Component.empty() : name(job.destination())));
	}

	/** Nudges the owner to keep up. */
	static void callOwner(ServerPlayer owner) {
		MehburitySpeech.say(owner, MehburitySpeech.text("guide.follow", "Come on, follow me! This way."));
	}

	/** What Gemini should know about it. */
	public static String describeFor(@Nullable MehburityEntity mehburity) {
		GuideJob job = mehburity == null ? null : mehburity.getGuideJob();
		if (job == null) {
			return "";
		}
		return "Mehburity is leading the player to the nearest " + job.destination().id().replace('_', ' ') + " ("
				+ (int) Math.round(horizontalDistance(mehburity, job.target())) + " blocks to go)\n";
	}

	// ---------------------------------------------------------------- helpers

	/** The nearest one within {@link #SEARCH_CHUNKS} chunks, or null (none, or none in this dimension). */
	static @Nullable BlockPos locate(ServerLevel level, Destination destination, BlockPos from) {
		Optional<? extends HolderSet<Structure>> holders = destination.holders(level.registryAccess().lookupOrThrow(Registries.STRUCTURE));
		if (holders.isEmpty()) {
			return null;
		}
		Pair<BlockPos, Holder<Structure>> nearest = level.getChunkSource().getGenerator()
				.findNearestMapStructure(level, holders.get(), from, SEARCH_CHUNKS, false);
		return nearest == null ? null : nearest.getFirst();
	}

	static double horizontalDistance(Entity entity, BlockPos target) {
		double dx = target.getX() + 0.5 - entity.getX();
		double dz = target.getZ() + 0.5 - entity.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** One of the eight compass directions from the owner to {@code target} (north is -Z). */
	static Component direction(Entity from, BlockPos target) {
		double dx = target.getX() + 0.5 - from.getX();
		double dz = target.getZ() + 0.5 - from.getZ();
		double degrees = Math.toDegrees(Math.atan2(dx, -dz));
		int index = Mth.positiveModulo((int) Math.round(degrees / 45.0), DIRECTIONS.length);
		String id = DIRECTIONS[index];
		return Component.translatableWithFallback("mehburity.direction." + id, id);
	}

	static Component name(Destination destination) {
		return Component.translatableWithFallback("mehburity.structure." + destination.id(),
				destination.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '));
	}

	private static @Nullable MehburityEntity ready(ServerPlayer owner) {
		MehburityEntity mehburity = CompanionManager.find(owner.level().getServer(), owner.getUUID());
		if (mehburity == null) {
			owner.sendSystemMessage(Component.translatableWithFallback("mehburity.command.not_nearby",
					"Your Mehburity is not nearby. Use /mehburity cagir first."));
			return null;
		}
		if (mehburity.isRaging()) {
			return null;
		}
		return mehburity;
	}
}
