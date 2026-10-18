package com.mehbur.mehburity.test;

import com.mehbur.mehburity.ai.AiRouter;
import com.mehbur.mehburity.anger.Anger;
import com.mehbur.mehburity.anger.AngerChat;
import com.mehbur.mehburity.anger.Revenge;
import com.mehbur.mehburity.config.MehburityConfig;
import com.mehbur.mehburity.entity.CompanionManager;
import com.mehbur.mehburity.entity.CompanionMode;
import com.mehbur.mehburity.entity.CompanionRegistry;
import com.mehbur.mehburity.entity.MehburityEntity;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import static com.mehbur.mehburity.test.MehburityGameTests.instantRage;
import static com.mehbur.mehburity.test.MehburityGameTests.owner;
import static com.mehbur.mehburity.test.MehburityGameTests.survivalOwner;
import static com.mehbur.mehburity.test.MehburityGameTests.tempConfig;
import static com.mehbur.mehburity.test.MehburityGameTests.withCompanion;

/** Phase 6: the anger meter, what raises and lowers it, and the rage at a single culprit. */
public final class MehburityAngerGameTests {
	private static final String SOLO = "mehburity-test:solo";
	/** More than the per-attacker hit cooldown, so every hit counts. */
	private static final int HIT_GAP = 12;

	/** Hits Mehburity {@code times} times as {@code attacker}, {@link #HIT_GAP} ticks apart. */
	private static GameTestSequence hit(GameTestSequence sequence, GameTestHelper helper, MehburityEntity mehburity,
			ServerPlayer attacker, int times) {
		ServerLevel level = helper.getLevel();
		for (int i = 0; i < times; i++) {
			// The rage breaks out at once (revenge is tested on its own).
			sequence = sequence.thenExecuteAfter(HIT_GAP, () -> instantRage(
					() -> mehburity.hurtServer(level, level.damageSources().playerAttack(attacker), 1.0F)));
		}
		return sequence;
	}

	private static ServerPlayer stranger(GameTestHelper helper) {
		ServerPlayer stranger = survivalOwner(helper);
		stranger.teleportTo(stranger.getX() + 3.0, stranger.getY(), stranger.getZ() + 3.0);
		return stranger;
	}

	// ---------------------------------------------------------------- chat

	@GameTest
	public void apologies_and_insults_are_understood(GameTestHelper helper) {
		helper.assertValueEqual(AngerChat.Intent.APOLOGY, AngerChat.parse("özür dilerim"), "Turkish apology");
		helper.assertValueEqual(AngerChat.Intent.APOLOGY, AngerChat.parse("Affet beni lütfen"), "affet");
		helper.assertValueEqual(AngerChat.Intent.APOLOGY, AngerChat.parse("I'm so sorry!"), "English apology");
		helper.assertValueEqual(AngerChat.Intent.INSULT, AngerChat.parse("sen çok APTALSIN"), "Turkish suffixes still match");
		helper.assertValueEqual(AngerChat.Intent.INSULT, AngerChat.parse("salak"), "Turkish insult");
		helper.assertValueEqual(AngerChat.Intent.INSULT, AngerChat.parse("you are stupid"), "English insult");
		helper.assertValueEqual(AngerChat.Intent.INSULT, AngerChat.parse("I hate you"), "a phrase");
		helper.assertTrue(AngerChat.parse("nasılsın?") == null, "small talk is neither");
		helper.assertTrue(AngerChat.parse("maden kaz") == null, "orders are neither");
		helper.succeed();
	}

	@GameTest(maxTicks = 120)
	public void insults_raise_and_apologies_lower_the_meter(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		withCompanion(helper, owner, mehburity -> {
			double insult = MehburityConfig.get().anger.insultAnger;
			double apology = MehburityConfig.get().anger.apologyCalm;
			for (int i = 0; i < 3; i++) {
				helper.assertTrue(AiRouter.onPlayerChat(owner, "Mehburity, sen bir aptalsın, salak"), "for Mehburity");
				AiRouter.runOrders();
			}
			helper.assertTrue(Math.abs(mehburity.getAnger() - 3 * insult) < 0.01, "three insults: " + mehburity.getAnger());
			helper.assertTrue(AiRouter.onPlayerChat(owner, "Mehburity, özür dilerim"), "for Mehburity");
			AiRouter.runOrders();
			float afterApology = mehburity.getAnger();
			helper.assertTrue(Math.abs(afterApology - (3 * insult - apology)) < 0.01, "an apology calms: " + afterApology);
			AiRouter.onPlayerChat(owner, "Mehburity, özür dilerim");
			AiRouter.runOrders();
			helper.assertTrue(mehburity.getAnger() == afterApology, "a second apology right away does not count");
			helper.assertTrue(CompanionRegistry.companion(helper.getLevel().getServer(), owner.getUUID()).anger() == afterApology,
					"the meter is saved in the registry");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the meter

	@GameTest(maxTicks = 300)
	public void mob_hits_count_once_per_cooldown_and_the_meter_cools_down(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		withCompanion(helper, owner, mehburity -> {
			ServerLevel level = helper.getLevel();
			Husk husk = helper.spawn(EntityTypes.HUSK, new BlockPos(6, 1, 6));
			husk.setNoAi(true);
			mehburity.hurtServer(level, level.damageSources().mobAttack(husk), 1.0F);
			mehburity.hurtServer(level, level.damageSources().mobAttack(husk), 1.0F);
			double mobHit = MehburityConfig.get().anger.mobHitAnger;
			helper.assertTrue(Math.abs(mehburity.getAnger() - mobHit) < 0.01, "two hits in one tick count once: " + mehburity.getAnger());
			helper.assertFalse(mehburity.isRaging(), "a few mob hits are no reason to rage");
			helper.assertTrue(mehburity.isAlive() && mehburity.getHealth() == mehburity.getMaxHealth(), "still immortal");

			float before = mehburity.getAnger();
			helper.runAfterDelay(161, () -> { // it cools down once it has been left alone for 5 seconds
				helper.assertTrue(mehburity.getAnger() < before, "the meter must cool down over time");
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = 200)
	public void the_meter_survives_mehburity_being_recreated(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		withCompanion(helper, owner, mehburity -> {
			Husk husk = helper.spawn(EntityTypes.HUSK, new BlockPos(6, 1, 6));
			husk.setNoAi(true);
			Anger.provoke(mehburity, husk, 42.0);
			mehburity.discard();
			helper.assertTrue(CompanionManager.summon(owner) == CompanionManager.SummonResult.RESPAWNED, "a new Mehburity");
			MehburityEntity again = CompanionManager.find(helper.getLevel().getServer(), owner.getUUID());
			helper.assertTrue(again != null && again != mehburity, "it must be a new entity");
			helper.assertTrue(Math.abs(again.getAnger() - 42.0F) < 1.0F, "it remembers how angry it was: " + again.getAnger());
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- rages

	@GameTest(maxTicks = 600)
	public void a_player_who_keeps_hitting_it_gets_hunted_down(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		ServerPlayer stranger = stranger(helper);
		withCompanion(helper, owner, mehburity -> {
			int hits = (int) Math.ceil(MehburityConfig.get().anger.rageThreshold / MehburityConfig.get().anger.playerHitAnger);
			hit(helper.startSequence(), helper, mehburity, stranger, hits)
					.thenExecute(() -> {
						helper.assertTrue(mehburity.isRaging(), "enough hits start a rage");
						helper.assertTrue(mehburity.getRageCulprit() == stranger, "at the one who hit it");
					})
					.thenWaitUntil(() -> helper.assertTrue(stranger.isDeadOrDying(), "the culprit must be hunted down"))
					.thenWaitUntil(() -> {
						MehburityEntity current = CompanionManager.find(helper.getLevel().getServer(), owner.getUUID());
						helper.assertTrue(current != null && !current.isRaging(), "the rage ends with the culprit");
						helper.assertTrue(current.getAnger() == 0.0F, "and Mehburity is calm again");
						helper.assertTrue(current.distanceTo(owner) < 6.0F, "back at its owner's side");
						helper.assertTrue(owner.isAlive() && owner.getHealth() == owner.getMaxHealth(), "the owner was never touched");
					})
					.thenSucceed();
		});
	}

	@GameTest(maxTicks = 300)
	public void a_player_it_rages_at_finds_it_back_days_later(GameTestHelper helper) {
		ServerPlayer owner = survivalOwner(helper);
		ServerPlayer stranger = stranger(helper);
		withCompanion(helper, owner, mehburity -> {
			MinecraftServer server = helper.getLevel().getServer();
			Anger.provoke(mehburity, stranger, 100.0);
			helper.assertTrue(mehburity.isRemoved(), "no rage at once: it vanishes");
			CompanionRegistry.Companion gone = CompanionRegistry.companion(server, owner.getUUID());
			helper.assertValueEqual(CompanionMode.GONE, gone.mode(), "the registry knows it is gone");
			helper.assertTrue(gone.revengeOn().orElseThrow().equals(stranger.getUUID()), "and whom it will come back for");
			long wait = gone.revengeIn();
			long days = MehburityConfig.get().anger.revengeDays;
			helper.assertTrue(wait >= days * Revenge.DAY_TICKS && wait < (days + 1) * Revenge.DAY_TICKS,
					"it comes back on the day after the wait, was " + wait);
			helper.assertValueEqual(CompanionManager.SummonResult.GONE, CompanionManager.summon(owner), "it cannot be called back");
			helper.assertTrue(CompanionManager.find(server, owner.getUUID()) == null, "and is not re-created meanwhile");

			Revenge.advance(server, wait - 1);
			helper.assertTrue(CompanionManager.find(server, owner.getUUID()) == null, "not a tick early");
			Revenge.advance(server, 1);
			MehburityEntity back = CompanionManager.find(server, owner.getUUID());
			helper.assertTrue(back != null && back.isRaging() && back.getRageCulprit() == stranger, "back for its revenge");
			helper.assertTrue(back.distanceTo(stranger) < 8.0F, "right behind the culprit");
			helper.assertTrue(stranger.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS), "in the dark");
			helper.assertTrue(back.isTalking(), "saying it came for its revenge");
			CompanionRegistry.Companion after = CompanionRegistry.companion(server, owner.getUUID());
			helper.assertTrue(after.mode() == CompanionMode.FOLLOW && after.revengeOn().isEmpty(), "no revenge pending any more");
			Anger.endRage(back, Anger.RageEnd.FORCED);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void a_calm_down_order_calls_off_the_revenge(GameTestHelper helper) {
		ServerPlayer owner = survivalOwner(helper);
		withCompanion(helper, owner, mehburity -> {
			MinecraftServer server = helper.getLevel().getServer();
			Anger.provoke(mehburity, owner, 100.0);
			helper.assertTrue(mehburity.isRemoved(), "it vanished");
			helper.assertTrue(CompanionManager.forgive(server, owner.getUUID()), "the revenge is called off");
			helper.succeedWhen(() -> {
				MehburityEntity back = CompanionManager.find(server, owner.getUUID());
				helper.assertTrue(back != null && !back.isRaging() && back.getAnger() == 0.0F, "back, calm, beside its owner");
			});
		});
	}

	@GameTest(maxTicks = 200)
	public void once_calm_it_makes_up_with_a_cake(GameTestHelper helper) {
		ServerPlayer owner = survivalOwner(helper);
		withCompanion(helper, owner, mehburity -> {
			Anger.provoke(mehburity, owner, 70.0);
			helper.startSequence()
					.thenExecuteAfter(5, () -> {
						helper.assertTrue(owner.getInventory().countItem(Items.CAKE) == 0, "no cake while it is still angry");
						Anger.calm(mehburity, 60.0);
					})
					.thenWaitUntil(() -> helper.assertTrue(owner.getInventory().countItem(Items.CAKE) == 1,
							"calm again, it says sorry with a cake"))
					.thenExecute(() -> {
						// Every time it calms down, also just annoyed (two hits), and right away after a cake or a sorry.
						Anger.provoke(mehburity, owner, 50.0);
						Anger.calm(mehburity, 40.0);
						helper.assertTrue(owner.getInventory().countItem(Items.CAKE) == 2, "a second cake the second time");
						// A server can limit it; then only the sorry comes.
						MehburityConfig.get().anger.makeUpCakeCooldownSeconds = 600;
						try {
							Anger.provoke(mehburity, owner, 50.0);
							Anger.calm(mehburity, 40.0);
						} finally {
							MehburityConfig.get().anger.makeUpCakeCooldownSeconds = 0;
						}
						helper.assertTrue(owner.getInventory().countItem(Items.CAKE) == 2, "no cake within the server's cooldown");
					})
					.thenSucceed();
		});
	}

	@GameTest(maxTicks = 200)
	public void the_make_up_cake_survives_being_carried(GameTestHelper helper) {
		ServerPlayer owner = survivalOwner(helper);
		withCompanion(helper, owner, mehburity -> {
			Anger.provoke(mehburity, owner, 50.0);
			CompanionManager.carry(owner, mehburity);
			helper.assertTrue(CompanionManager.release(owner, null), "let out again");
			MehburityEntity out = CompanionManager.find(helper.getLevel().getServer(), owner.getUUID());
			helper.assertTrue(out != null && out != mehburity, "a new Mehburity entity");
			Anger.calm(out, 40.0);
			helper.assertTrue(owner.getInventory().countItem(Items.CAKE) == 1, "it still remembers whom to make up with");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 300)
	public void cake_calms_a_rage_even_at_the_owner(GameTestHelper helper) {
		ServerPlayer owner = survivalOwner(helper);
		withCompanion(helper, owner, mehburity -> {
			int hits = (int) Math.ceil(MehburityConfig.get().anger.rageThreshold / MehburityConfig.get().anger.playerHitAnger);
			hit(helper.startSequence(), helper, mehburity, owner, hits)
					.thenExecute(() -> {
						helper.assertTrue(mehburity.isRaging() && mehburity.getRageCulprit() == owner,
								"hitting your own Mehburity makes it rage at you");
						owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CAKE, 2));
						mehburity.interact(owner, InteractionHand.MAIN_HAND, Vec3.ZERO);
						helper.assertFalse(mehburity.isRaging(), "a cake ends the rage");
						helper.assertTrue(owner.getMainHandItem().getCount() == 1, "one cake is eaten");
						helper.assertTrue(mehburity.getAnger() <= MehburityConfig.get().anger.calmBelow, "the meter went down");
						helper.assertTrue(owner.getInventory().countItem(Items.CAKE) == 2, "the rage over, it gives a cake back as a sorry");
					})
					.thenSucceed();
		});
	}

	@GameTest(maxTicks = 600)
	public void killing_the_owner_enrages_it_at_the_killer(GameTestHelper helper) {
		ServerPlayer owner = survivalOwner(helper);
		withCompanion(helper, owner, mehburity -> {
			ServerLevel level = helper.getLevel();
			Husk husk = helper.spawn(EntityTypes.HUSK, new BlockPos(6, 1, 6));
			husk.setNoAi(true);
			husk.setPersistenceRequired();
			ServerLivingEntityEvents.AFTER_DEATH.invoker().afterDeath(owner, level.damageSources().mobAttack(husk));
			helper.assertTrue(mehburity.isRaging() && mehburity.getRageCulprit() == husk, "instant rage at the killer");
			helper.succeedWhen(() -> {
				helper.assertFalse(husk.isAlive(), "the killer must be dead");
				helper.assertTrue(husk.getLastHurtByMob() instanceof MehburityEntity, "Mehburity did it");
				helper.assertFalse(mehburity.isRaging(), "and calmed down afterwards");
			});
		});
	}

	@GameTest(environment = SOLO, maxTicks = 300)
	public void players_are_spared_when_rages_at_players_are_off(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		ServerPlayer stranger = stranger(helper);
		MehburityConfig.get().anger.rageAtPlayers = false;
		withCompanion(helper, owner, mehburity -> hit(helper.startSequence(), helper, mehburity, stranger, 6)
				.thenExecute(() -> {
					MehburityConfig.get().anger.rageAtPlayers = true;
					helper.assertFalse(mehburity.isRaging(), "no rage at a player when anger.rageAtPlayers is off");
					helper.assertTrue(mehburity.getAnger() < MehburityConfig.get().anger.rageThreshold, "it only sulks");
				})
				.thenSucceed());
	}

	// ---------------------------------------------------------------- config

	@GameTest
	public void anger_config_is_clamped(GameTestHelper helper) {
		MehburityConfig config = tempConfig("""
				{"anger": {"rageThreshold": 0, "calmBelow": 500, "playerHitAnger": -3, "maxRageSeconds": 1,
				 "insults": ["aptal", "", null]}}""").load().value();
		helper.assertTrue(config.anger.rageThreshold == 1.0, "the threshold is at least 1");
		helper.assertTrue(config.anger.calmBelow <= config.anger.rageThreshold, "calm level is not above the threshold");
		helper.assertTrue(config.anger.playerHitAnger == 0.0, "anger amounts are never negative");
		helper.assertTrue(config.anger.maxRageSeconds == 5, "a rage lasts at least 5 seconds");
		helper.assertTrue(config.anger.insults.size() == 1, "blank insults are dropped");
		MehburityConfig repaired = tempConfig("{\"anger\": null}").load().value();
		helper.assertTrue(repaired.anger != null && repaired.anger.enabled, "a missing anger section gets the defaults");
		helper.succeed();
	}
}
