package com.mehbur.mehburity.anger;

import com.mehbur.mehburity.ai.GameEvents;
import com.mehbur.mehburity.config.MehburityConfig;
import com.mehbur.mehburity.entity.CompanionManager;
import com.mehbur.mehburity.entity.CompanionRegistry;
import com.mehbur.mehburity.entity.MehburityEntity;
import com.mehbur.mehburity.entity.MehburitySounds;
import com.mehbur.mehburity.entity.MehburitySpeech;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mehburity's temper. A meter from 0 to 100 on the entity (saved in the companion registry when the owner leaves):
 * hits, insults and the owner's death raise it, time, cake and apologies lower it. At {@code anger.rageThreshold}
 * Mehburity goes into a rage and hunts down only the culprit; the rage ends when the culprit dies, gets away, time
 * runs out, or the meter is calmed below {@code anger.calmBelow}. Afterwards it returns to its owner.
 */
public final class Anger {
	public enum Mood { CALM, ANNOYED, ANGRY, RAGING }

	public enum RageEnd { KILLED, CALMED, GONE, TIMEOUT, FORCED }

	private static final int DECAY_INTERVAL = 20;
	/** The registry copy is refreshed this often while the meter cools down. */
	private static final int SAVE_INTERVAL = 600;
	/** Repeated hits from the same attacker within this many ticks count once. */
	private static final int HIT_COOLDOWN_TICKS = 10;
	/** Blinks next to the culprit when they get farther than this. */
	private static final double CHASE_BLINK_DISTANCE = 24.0;
	private static final int SPARK_INTERVAL = 10;
	/** The meter only cools down once Mehburity has been left alone for this long. */
	private static final int COOL_DOWN_AFTER_TICKS = 100;
	/** At most one make-up cake per player in this long, so making it angry is no cake farm. */
	private static final long MAKE_UP_CAKE_COOLDOWN_TICKS = 10L * 60L * 20L;

	private record HitKey(UUID mehburity, UUID attacker) {
	}

	private static final Map<HitKey, Long> LAST_HIT = new HashMap<>();
	private static final Map<UUID, Long> LAST_APOLOGY = new HashMap<>();
	/** Per Mehburity: when it was last made angrier. */
	private static final Map<UUID, Long> LAST_PROVOKED = new HashMap<>();
	/** Per Mehburity: the player it got really angry with (or raged at), to make up with once it is calm. */
	private static final Map<UUID, UUID> GRUDGE = new HashMap<>();
	/** Per player: when they last got a make-up cake. */
	private static final Map<UUID, Long> LAST_CAKE = new HashMap<>();

	private Anger() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register(Anger::onDeath);
		AngerChat.register();
	}

	// ---------------------------------------------------------------- what makes it angry

	/** Somebody tried to hurt Mehburity (it never takes damage, but it notices). */
	public static void onAttacked(MehburityEntity mehburity, DamageSource source) {
		MehburityConfig.Anger config = MehburityConfig.get().anger;
		if (!config.enabled || !(source.getEntity() instanceof LivingEntity attacker) || attacker instanceof MehburityEntity) {
			return;
		}
		long now = mehburity.level().getGameTime();
		Long last = LAST_HIT.put(new HitKey(mehburity.getUUID(), attacker.getUUID()), now);
		if (last != null && now - last < HIT_COOLDOWN_TICKS) {
			return;
		}
		provoke(mehburity, attacker, attacker instanceof Player ? config.playerHitAnger : config.mobHitAnger);
	}

	/** Its owner was killed: the killer is in big trouble. */
	private static void onDeath(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof ServerPlayer owner) || !MehburityConfig.get().anger.enabled
				|| !(source.getEntity() instanceof LivingEntity killer) || killer == owner || killer instanceof MehburityEntity) {
			return;
		}
		MehburityEntity mehburity = CompanionManager.find(owner.level().getServer(), owner.getUUID());
		if (mehburity != null) {
			GameEvents.record(owner, killer.getType().getDescription().getString() + " killed the player, and Mehburity saw it");
			provoke(mehburity, killer, MehburityConfig.get().anger.ownerKilledAnger);
		}
	}

	static void insult(MehburityEntity mehburity, ServerPlayer speaker) {
		tell(mehburity, speaker, MehburitySpeech.text("anger.insulted", "Hey! That wasn't nice."));
		provoke(mehburity, speaker, MehburityConfig.get().anger.insultAnger);
	}

	/** Raises the meter because of {@code culprit}; a rage starts at the threshold. */
	public static void provoke(MehburityEntity mehburity, LivingEntity culprit, double amount) {
		MehburityConfig.Anger config = MehburityConfig.get().anger;
		if (!config.enabled || amount <= 0.0) {
			return;
		}
		if (mehburity.isRaging()) {
			if (mehburity.getRageCulprit() == culprit) {
				set(mehburity, 100.0F); // poking a raging Mehburity only keeps it raging
			}
			return; // one culprit at a time
		}
		LAST_PROVOKED.put(mehburity.getUUID(), mehburity.level().getGameTime());
		float before = mehburity.getAnger();
		float after = (float) Math.min(100.0, before + amount);
		boolean mayRage = mayRageAt(mehburity, culprit);
		if (!mayRage) {
			after = (float) Math.min(after, config.rageThreshold - 1.0); // sulks, but never rages at them
		}
		set(mehburity, after);
		spark(mehburity, 6);

		double annoyed = config.rageThreshold / 3.0;
		double angry = config.rageThreshold * 2.0 / 3.0;
		if (culprit instanceof Player player && after >= angry) {
			GRUDGE.put(mehburity.getUUID(), player.getUUID());
		}
		if (mayRage && after >= config.rageThreshold) {
			startRage(mehburity, culprit);
		} else if (before < angry && after >= angry) {
			tell(mehburity, culprit, MehburitySpeech.text("anger.angry", "You're making me really angry... Stop it!"));
		} else if (before < annoyed && after >= annoyed) {
			tell(mehburity, culprit, MehburitySpeech.text("anger.annoyed", "Hey, don't do that!"));
		}
	}

	private static boolean mayRageAt(MehburityEntity mehburity, LivingEntity culprit) {
		MehburityConfig.Anger config = MehburityConfig.get().anger;
		if (!culprit.isAlive() || culprit.isSpectator()) {
			return false;
		}
		if (culprit instanceof Player player) {
			if (!config.rageAtPlayers) {
				return false;
			}
			return config.rageAtOwner || !player.getUUID().equals(mehburity.getOwnerId());
		}
		return true;
	}

	// ---------------------------------------------------------------- what calms it down

	/** Right-click with a cake: a big calm-down, one cake used up (unless in creative). */
	public static void feedCake(MehburityEntity mehburity, ServerPlayer player, InteractionHand hand) {
		if (mehburity.getAnger() <= 0.0F && !mehburity.isRaging()) {
			MehburitySpeech.say(player, MehburitySpeech.text("anger.cake_not_angry", "Thanks, but I'm not angry! Keep your cake."));
			return;
		}
		ItemStack stack = player.getItemInHand(hand);
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		ServerLevel level = (ServerLevel) mehburity.level();
		level.playSound(null, mehburity.getX(), mehburity.getY(), mehburity.getZ(), SoundEvents.GENERIC_EAT.value(),
				SoundSource.NEUTRAL, 1.0F, 1.2F);
		level.sendParticles(ParticleTypes.HEART, mehburity.getX(), mehburity.getY() + 0.8, mehburity.getZ(), 6, 0.3, 0.3, 0.3, 0.0);
		tell(mehburity, player, MehburitySpeech.text("anger.cake", "Mmm, cake! Okay... I feel better."));
		GameEvents.record(player, "the player gave Mehburity a cake to calm it down");
		calm(mehburity, MehburityConfig.get().anger.cakeCalm);
	}

	/** "Mehburity, özür dilerim": counts once per {@code apologyCooldownSeconds} per player. */
	static void apologize(MehburityEntity mehburity, ServerPlayer player) {
		MehburityConfig.Anger config = MehburityConfig.get().anger;
		if (mehburity.getAnger() <= 0.0F && !mehburity.isRaging()) {
			MehburitySpeech.say(player, MehburitySpeech.text("anger.not_angry", "It's okay, I'm not angry!"));
			return;
		}
		long now = mehburity.level().getGameTime();
		Long last = LAST_APOLOGY.get(player.getUUID());
		if (last != null && now - last < config.apologyCooldownSeconds * 20L) {
			MehburitySpeech.say(player, MehburitySpeech.text("anger.apology_too_soon", "You just said that. Show me you mean it!"));
			return;
		}
		LAST_APOLOGY.put(player.getUUID(), now);
		tell(mehburity, player, MehburitySpeech.text("anger.apology", "Hmph... Okay, apology accepted."));
		calm(mehburity, config.apologyCalm);
	}

	/** Lowers the meter; a rage ends once it drops to {@code calmBelow} or lower. */
	public static void calm(MehburityEntity mehburity, double amount) {
		float after = (float) Math.max(0.0, mehburity.getAnger() - amount);
		set(mehburity, after);
		if (mehburity.isRaging() && after <= MehburityConfig.get().anger.calmBelow) {
			endRage(mehburity, RageEnd.CALMED);
		}
	}

	// ---------------------------------------------------------------- the rage

	private static void startRage(MehburityEntity mehburity, LivingEntity culprit) {
		mehburity.setMiningJob(null);
		mehburity.setBuildJob(null);
		mehburity.setRageCulprit(culprit);
		set(mehburity, 100.0F);
		ServerLevel level = (ServerLevel) mehburity.level();
		level.playSound(null, mehburity.getX(), mehburity.getY(), mehburity.getZ(), MehburitySounds.RAGE, SoundSource.HOSTILE, 1.5F, 1.0F);
		level.sendParticles(ParticleTypes.ANGRY_VILLAGER, mehburity.getX(), mehburity.getY() + 0.5, mehburity.getZ(), 12, 0.6, 0.6, 0.6, 0.0);
		MehburityConfig.Monster monster = MehburityConfig.get().monster;
		if (monster.enabled) {
			mehburity.setMonster((float) monster.height, monster.transformTicks);
			level.playSound(null, mehburity.getX(), mehburity.getY(), mehburity.getZ(), MehburitySounds.MONSTER_TRANSFORM,
					SoundSource.HOSTILE, 3.0F, 0.8F);
		}
		tell(mehburity, culprit, MehburitySpeech.text("anger.rage", "THAT'S IT! %s, I'm coming for you!", culprit.getDisplayName()));
		ServerPlayer owner = mehburity.getOwnerPlayer();
		if (owner != null) {
			GameEvents.record(owner, "Mehburity flew into a rage at " + culprit.getName().getString());
		}
	}

	public static void endRage(MehburityEntity mehburity, RageEnd end) {
		LivingEntity culprit = mehburity.getRageCulprit();
		boolean wasMonster = mehburity.isMonster();
		float monsterHeight = mehburity.getMonsterHeight();
		mehburity.setRageCulprit(null); // also shrinks it back into a sphere
		if (wasMonster) {
			ServerLevel level = (ServerLevel) mehburity.level();
			level.sendParticles(ParticleTypes.LARGE_SMOKE, mehburity.getX(), mehburity.getY() + monsterHeight * 0.5, mehburity.getZ(),
					60, 0.8, monsterHeight * 0.3, 0.8, 0.03);
			level.playSound(null, mehburity.getX(), mehburity.getY(), mehburity.getZ(), MehburitySounds.MONSTER_SHRINK,
					SoundSource.HOSTILE, 2.0F, 0.6F);
		}
		MehburityConfig.Anger config = MehburityConfig.get().anger;
		switch (end) {
			case KILLED, FORCED -> set(mehburity, 0.0F);
			case GONE, TIMEOUT -> set(mehburity, (float) Math.min(mehburity.getAnger(), config.calmBelow / 2.0));
			case CALMED -> {
				// The meter is already below calmBelow.
			}
		}
		Component line = switch (end) {
			case KILLED -> MehburitySpeech.text("anger.rage_killed", "...Okay. I feel calm now.");
			case CALMED -> MehburitySpeech.text("anger.rage_calmed", "Fine... I calmed down. Don't do that again.");
			case GONE -> MehburitySpeech.text("anger.rage_gone", "They got away... this time.");
			case TIMEOUT -> MehburitySpeech.text("anger.rage_timeout", "I'm tired of chasing. You're lucky.");
			case FORCED -> MehburitySpeech.text("anger.rage_forced", "Okay, okay, I'm calm.");
		};
		ServerPlayer owner = mehburity.getOwnerPlayer();
		MehburityEntity back = mehburity;
		if (owner != null && !owner.isDeadOrDying()) {
			back = mehburity.teleportToOwner(owner);
		} else if (wasMonster) {
			// The demon walked through rock; the sphere must not be left inside it.
			BlockPos top = mehburity.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, mehburity.blockPosition());
			if (mehburity.getY() < top.getY()) {
				mehburity.teleportTo(mehburity.getX(), top.getY() + 0.5, mehburity.getZ());
			}
		}
		if (culprit != null) {
			tell(back, culprit, line);
		} else if (owner != null) {
			MehburitySpeech.speak(owner, line);
		}
		if (owner != null) {
			GameEvents.record(owner, "Mehburity's rage ended (" + end.name().toLowerCase(java.util.Locale.ROOT) + ")");
		}
		if (culprit instanceof ServerPlayer player && player.getUUID().equals(GRUDGE.get(mehburity.getUUID()))) {
			makeUp(back, player);
		}
	}

	/** Every server tick: cooling down, and keeping the rage on track. */
	public static void tick(MehburityEntity mehburity) {
		if (mehburity.isRaging()) {
			tickRage(mehburity);
			return;
		}
		Long provoked = LAST_PROVOKED.get(mehburity.getUUID());
		boolean leftAlone = provoked == null || mehburity.level().getGameTime() - provoked > COOL_DOWN_AFTER_TICKS;
		if (leftAlone && mehburity.tickCount % DECAY_INTERVAL == 0 && mehburity.getAnger() > 0.0F) {
			float cooled = (float) (mehburity.getAnger() - MehburityConfig.get().anger.decayPerMinute * DECAY_INTERVAL / 1200.0);
			mehburity.setAnger(cooled);
			if (cooled <= 0.0F || mehburity.tickCount % SAVE_INTERVAL == 0) {
				store(mehburity);
			}
		}
		double angry = MehburityConfig.get().anger.rageThreshold * 2.0 / 3.0;
		if (mehburity.getAnger() >= angry && mehburity.tickCount % (SPARK_INTERVAL * 3) == 0) {
			spark(mehburity, 1);
		}
		UUID grudge = GRUDGE.get(mehburity.getUUID());
		if (grudge != null && mehburity.getAnger() < MehburityConfig.get().anger.rageThreshold / 3.0
				&& mehburity.level() instanceof ServerLevel level
				&& level.getServer().getPlayerList().getPlayer(grudge) instanceof ServerPlayer player) {
			makeUp(mehburity, player);
		}
	}

	/**
	 * Calm again after being really angry with {@code player}: it says sorry and hands them a cake (into the
	 * inventory, or at their feet). Waits while they are dead; one cake per player every 10 minutes.
	 */
	private static void makeUp(MehburityEntity mehburity, ServerPlayer player) {
		if (player.isDeadOrDying()) {
			return; // keeps the grudge: makes up once they are back
		}
		GRUDGE.remove(mehburity.getUUID());
		if (!MehburityConfig.get().anger.makeUpCake) {
			return;
		}
		long now = player.level().getGameTime();
		Long last = LAST_CAKE.get(player.getUUID());
		if (last != null && now - last < MAKE_UP_CAKE_COOLDOWN_TICKS) {
			tell(mehburity, player, MehburitySpeech.text("anger.make_up", "Sorry I got so mad at you... Friends again?"));
			return;
		}
		LAST_CAKE.put(player.getUUID(), now);
		ItemStack cake = new ItemStack(Items.CAKE);
		if (!player.getInventory().add(cake)) {
			player.drop(cake, false);
		}
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.7F, 1.2F);
		level.sendParticles(ParticleTypes.HEART, mehburity.getX(), mehburity.getY() + 0.8, mehburity.getZ(), 5, 0.3, 0.3, 0.3, 0.0);
		tell(mehburity, player, MehburitySpeech.text("anger.make_up_cake",
				"Sorry I got so mad at you... Here, this cake is for you. Friends again?"));
		GameEvents.record(player, "Mehburity calmed down, said sorry and gave the player a cake");
	}

	private static void tickRage(MehburityEntity mehburity) {
		LivingEntity culprit = mehburity.getRageCulprit();
		if (culprit == null) {
			endRage(mehburity, RageEnd.GONE);
			return;
		}
		if (culprit.isDeadOrDying()) {
			endRage(mehburity, RageEnd.KILLED);
			return;
		}
		if (culprit.isRemoved() || culprit.level() != mehburity.level()) {
			endRage(mehburity, RageEnd.GONE);
			return;
		}
		if (mehburity.getRageTicks() > MehburityConfig.get().anger.maxRageSeconds * 20) {
			endRage(mehburity, RageEnd.TIMEOUT);
			return;
		}
		if (mehburity.distanceToSqr(culprit) > CHASE_BLINK_DISTANCE * CHASE_BLINK_DISTANCE) {
			ServerLevel level = (ServerLevel) mehburity.level();
			level.sendParticles(ParticleTypes.LARGE_SMOKE, mehburity.getX(), mehburity.getY() + 0.4, mehburity.getZ(), 15, 0.3, 0.3, 0.3, 0.02);
			// Lands a few steps behind them: the demon farther back, the ball close by.
			Vec3 near = mehburity.isMonster()
					? culprit.position().add(culprit.getLookAngle().multiply(-6.0, 0.0, -6.0))
					: culprit.position().add(culprit.getLookAngle().multiply(-3.0, 0.0, -3.0));
			mehburity.teleportTo(near.x, near.y, near.z);
		}
		if (mehburity.tickCount % SPARK_INTERVAL == 0) {
			spark(mehburity, 2);
		}
	}

	// ---------------------------------------------------------------- helpers

	private static void set(MehburityEntity mehburity, float anger) {
		mehburity.setAnger(anger);
		store(mehburity);
	}

	/** Copies the meter into the companion registry, so it survives logouts. */
	public static void store(MehburityEntity mehburity) {
		UUID ownerId = mehburity.getOwnerId();
		if (ownerId == null || !(mehburity.level() instanceof ServerLevel level)) {
			return;
		}
		MinecraftServer server = level.getServer();
		CompanionRegistry.Companion companion = CompanionRegistry.companion(server, ownerId);
		if (companion != null && companion.entityId().equals(mehburity.getUUID()) && companion.anger() != mehburity.getAnger()) {
			CompanionRegistry.putCompanion(server, ownerId, companion.withAnger(mehburity.getAnger()));
		}
	}

	private static void spark(MehburityEntity mehburity, int count) {
		// Above the head: the sphere's, or the demon's way up there.
		double above = mehburity.isMonster() ? mehburity.getMonsterHeight() * mehburity.getTransformProgress(0.0F) + 0.3 : 0.9;
		((ServerLevel) mehburity.level()).sendParticles(ParticleTypes.ANGRY_VILLAGER,
				mehburity.getX(), mehburity.getY() + above, mehburity.getZ(), count, 0.3, 0.2, 0.3, 0.0);
	}

	/** Says {@code line} to the owner (with voice) and to the culprit if that is another player. */
	private static void tell(MehburityEntity mehburity, @Nullable LivingEntity culprit, Component line) {
		ServerPlayer owner = mehburity.getOwnerPlayer();
		if (owner != null) {
			MehburitySpeech.speak(owner, line);
		}
		if (culprit instanceof ServerPlayer player && player != owner) {
			MehburitySpeech.speak(player, line);
		}
	}

	public static Mood mood(MehburityEntity mehburity) {
		if (mehburity.isRaging()) {
			return Mood.RAGING;
		}
		double threshold = MehburityConfig.get().anger.rageThreshold;
		float anger = mehburity.getAnger();
		return anger >= threshold * 2.0 / 3.0 ? Mood.ANGRY : anger >= threshold / 3.0 ? Mood.ANNOYED : Mood.CALM;
	}

	/** One line for Gemini. */
	public static String describeFor(@Nullable MehburityEntity mehburity) {
		if (mehburity == null) {
			return "";
		}
		StringBuilder out = new StringBuilder("Mehburity's anger: ").append(Math.round(mehburity.getAnger())).append("/100 (")
				.append(mood(mehburity).name().toLowerCase(java.util.Locale.ROOT)).append(')');
		LivingEntity culprit = mehburity.getRageCulprit();
		if (culprit != null) {
			out.append(", raging at ").append(culprit.getName().getString());
		}
		if (mehburity.isMonster()) {
			out.append(", in its ").append(Math.round(mehburity.getMonsterHeight())).append("-block demon form");
		}
		return out.append('\n').toString();
	}
}
