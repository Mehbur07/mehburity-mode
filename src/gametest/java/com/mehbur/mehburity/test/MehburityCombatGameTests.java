package com.mehbur.mehburity.test;

import com.mehbur.mehburity.ai.GameEvents;
import com.mehbur.mehburity.config.MehburityConfig;
import com.mehbur.mehburity.entity.CompanionManager;
import com.mehbur.mehburity.entity.CompanionMode;
import com.mehbur.mehburity.entity.MehburityEntity;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

import static com.mehbur.mehburity.test.MehburityGameTests.owner;
import static com.mehbur.mehburity.test.MehburityGameTests.survivalOwner;
import static com.mehbur.mehburity.test.MehburityGameTests.tempConfig;
import static com.mehbur.mehburity.test.MehburityGameTests.withCompanion;

/**
 * Phase 3: Mehburity defends its owner with its beam. Husks are used because they do not burn in daylight, and
 * they get no AI so the only thing that can kill them is Mehburity.
 */
public final class MehburityCombatGameTests {
	/** Long enough for several shots, if Mehburity wrongly decided to fire. */
	private static final int QUIET_TICKS = 80;

	private static Husk husk(GameTestHelper helper, int x, int z) {
		Husk husk = helper.spawn(EntityTypes.HUSK, new BlockPos(x, 1, z));
		husk.setNoAi(true);
		husk.setPersistenceRequired();
		return husk;
	}

	private static void assertKilledByMehburity(GameTestHelper helper, Husk husk) {
		helper.assertFalse(husk.isAlive(), "the husk must be dead");
		helper.assertTrue(husk.getLastHurtByMob() instanceof MehburityEntity, "Mehburity must be the one that killed it");
	}

	// ---------------------------------------------------------------- picking fights

	@GameTest(maxTicks = 500)
	public void defends_owner_from_attacker(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		withCompanion(helper, owner, mehburity -> {
			Husk husk = husk(helper, 6, 6);
			// What vanilla damage records on the victim; a fresh mock player may still be spawn-invulnerable.
			owner.setLastHurtByMob(husk);
			helper.succeedWhen(() -> {
				assertKilledByMehburity(helper, husk);
				helper.assertTrue(husk.getLastHurtByPlayer() == owner, "the kill counts as the owner's (experience, loot)");
				helper.assertTrue(GameEvents.recent(owner).contains("Mehburity zapped and killed"),
						"the kill must be remembered for Gemini");
			});
		});
	}

	@GameTest(maxTicks = 500)
	public void helps_owner_fight(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		withCompanion(helper, owner, mehburity -> {
			Husk husk = husk(helper, 6, 2);
			owner.setLastHurtMob(husk);
			helper.succeedWhen(() -> assertKilledByMehburity(helper, husk));
		});
	}

	@GameTest(maxTicks = 500)
	public void strikes_hostiles_hunting_owner(GameTestHelper helper) {
		ServerPlayer owner = survivalOwner(helper);
		withCompanion(helper, owner, mehburity -> {
			Husk husk = husk(helper, 2, 7);
			husk.setTarget(owner);
			helper.assertTrue(husk.getTarget() == owner, "the husk must be hunting the owner");
			helper.succeedWhen(() -> assertKilledByMehburity(helper, husk));
		});
	}

	@GameTest(maxTicks = 300)
	public void leaves_unaware_hostiles_alone_while_following(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		withCompanion(helper, owner, mehburity -> {
			Husk husk = husk(helper, 6, 6);
			helper.runAfterDelay(QUIET_TICKS, () -> {
				helper.assertTrue(husk.isAlive() && husk.getHealth() == husk.getMaxHealth(),
						"a husk that ignores the owner is left alone (combat.attackAllHostiles is off)");
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = 500)
	public void guards_its_post_in_stay_mode(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		withCompanion(helper, owner, mehburity -> {
			helper.assertTrue(CompanionManager.setMode(owner, CompanionMode.STAY), "mode must be set");
			Vec3 post = mehburity.position();
			Husk husk = husk(helper, 6, 6);
			helper.succeedWhen(() -> {
				assertKilledByMehburity(helper, husk);
				helper.assertTrue(mehburity.position().distanceTo(post) < 0.75, "a guard does not leave its post");
			});
		});
	}

	// ---------------------------------------------------------------- never

	@GameTest(maxTicks = 300)
	public void never_attacks_villagers_or_the_owners_pets(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		withCompanion(helper, owner, mehburity -> {
			Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(6, 1, 6));
			villager.setNoAi(true);
			Wolf wolf = helper.spawn(EntityTypes.WOLF, new BlockPos(6, 1, 2));
			wolf.setNoAi(true);
			wolf.tame(owner);
			// The villager "hurt" the owner and the owner hit their own wolf: Mehburity must still stay calm.
			owner.setLastHurtByMob(villager);
			owner.setLastHurtMob(wolf);
			helper.runAfterDelay(QUIET_TICKS, () -> {
				helper.assertTrue(villager.getHealth() == villager.getMaxHealth(), "villagers are never hurt");
				helper.assertTrue(wolf.getHealth() == wolf.getMaxHealth(), "the owner's pets are never hurt");
				helper.assertTrue(mehburity.getTarget() == null, "nothing to fight");
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = 300)
	public void leaves_players_alone_by_default(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		ServerPlayer stranger = owner(helper);
		stranger.teleportTo(stranger.getX() + 3.0, stranger.getY(), stranger.getZ() + 3.0);
		withCompanion(helper, owner, mehburity -> {
			owner.setLastHurtByMob(stranger);
			helper.runAfterDelay(QUIET_TICKS, () -> {
				helper.assertTrue(stranger.getHealth() == stranger.getMaxHealth(), "players are safe (combat.fightPlayers is off)");
				helper.assertTrue(mehburity.getTarget() == null, "nothing to fight");
				helper.succeed();
			});
		});
	}

	// ---------------------------------------------------------------- the owner's switch

	@GameTest(maxTicks = 600)
	public void savas_kapat_stops_fighting_and_ac_resumes(GameTestHelper helper) {
		ServerPlayer owner = survivalOwner(helper);
		withCompanion(helper, owner, mehburity -> {
			ServerLevel level = helper.getLevel();
			helper.assertTrue(CompanionManager.setCombat(owner, false), "combat must be switched off");
			helper.assertTrue(CompanionManager.setMode(owner, CompanionMode.FOLLOW), "mode must be set");
			helper.assertFalse(CompanionManager.isCombatEnabled(level.getServer(), owner.getUUID()),
					"changing the mode must keep the combat choice");

			Husk husk = husk(helper, 6, 6);
			husk.setTarget(owner);
			owner.setLastHurtByMob(husk);
			helper.runAfterDelay(QUIET_TICKS, () -> {
				helper.assertTrue(husk.isAlive() && husk.getHealth() == husk.getMaxHealth(), "a switched-off Mehburity must not fight");
				helper.assertTrue(CompanionManager.setCombat(owner, true), "combat must be switched on");
				helper.succeedWhen(() -> assertKilledByMehburity(helper, husk));
			});
		});
	}

	// ---------------------------------------------------------------- config

	@GameTest
	public void combat_config_is_clamped(GameTestHelper helper) {
		MehburityConfig config = tempConfig("""
				{"combat": {"beamRange": 500.0, "chargeTicks": 0, "chaseRadius": 999.0, "damage": -4.0},
				 "companion": {"teleportDistance": 30.0}}""").load().value();
		helper.assertTrue(config.combat.beamRange == 32.0, "beam range is at most 32");
		helper.assertTrue(config.combat.chargeTicks == 1, "charge takes at least one tick");
		helper.assertTrue(config.combat.damage == 0.0, "damage is never negative");
		helper.assertTrue(config.combat.chaseRadius == 30.0, "chasing never goes past the teleport distance");

		MehburityConfig repaired = tempConfig("{\"combat\": null}").load().value();
		helper.assertTrue(repaired.combat != null && repaired.combat.enabled, "a missing combat section gets the defaults");
		helper.succeed();
	}
}
