package com.mehbur.mehburity.test;

import com.mehbur.mehburity.anger.Anger;
import com.mehbur.mehburity.combat.MehburityDamage;
import com.mehbur.mehburity.config.MehburityConfig;
import com.mehbur.mehburity.entity.CompanionManager;
import com.mehbur.mehburity.entity.MehburityEntities;
import com.mehbur.mehburity.entity.MehburityEntity;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
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

/** Phase 7: the rage form, an 18-block demon that hunts the culprit down and shrinks back afterwards. */
public final class MehburityMonsterGameTests {
	private static final float SPHERE_HEIGHT = MehburityEntities.MEHBURITY.getDimensions().height();

	private static Husk culprit(GameTestHelper helper) {
		Husk husk = helper.spawn(EntityTypes.HUSK, new BlockPos(6, 1, 6));
		husk.setNoAi(true);
		husk.setPersistenceRequired();
		return husk;
	}

	private static void assertSphere(GameTestHelper helper, MehburityEntity mehburity) {
		helper.assertFalse(mehburity.isMonster(), "back to a sphere");
		helper.assertTrue(Math.abs(mehburity.getBbHeight() - SPHERE_HEIGHT) < 0.01F, "sphere-sized again: " + mehburity.getBbHeight());
		helper.assertFalse(mehburity.noPhysics, "a sphere collides with blocks again");
	}

	@GameTest(maxTicks = 400)
	public void a_rage_turns_it_into_a_demon_that_shrinks_back(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		withCompanion(helper, owner, mehburity -> {
			Husk husk = culprit(helper);
			MehburityConfig.Monster config = MehburityConfig.get().monster;
			Anger.provoke(mehburity, husk, 100.0);
			helper.assertTrue(mehburity.isRaging() && mehburity.isMonster(), "a rage starts the transformation");
			helper.assertTrue(mehburity.isTransforming(), "it grows first");
			helper.assertTrue(Math.abs(mehburity.getBbHeight() - config.height) < 0.01, "as tall as the config says: " + mehburity.getBbHeight());
			helper.assertTrue(mehburity.noPhysics, "the demon walks through blocks");
			helper.runAfterDelay(config.transformTicks + 1, () -> {
				helper.assertFalse(mehburity.isTransforming(), "fully grown after transformTicks");
				helper.assertTrue(Anger.describeFor(mehburity).contains("demon form"), "Gemini knows about the demon");
				Anger.endRage(mehburity, Anger.RageEnd.FORCED);
				MehburityEntity current = CompanionManager.find(helper.getLevel().getServer(), owner.getUUID());
				helper.assertTrue(current != null && !current.isRaging(), "the rage is over");
				assertSphere(helper, current);
				helper.assertTrue(current.distanceTo(owner) < 6.0F, "back at its owner's side");
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = 600)
	public void the_demon_tears_the_culprit_apart(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		ServerPlayer stranger = survivalOwner(helper);
		stranger.teleportTo(stranger.getX() + 3.0, stranger.getY(), stranger.getZ() + 3.0);
		withCompanion(helper, owner, mehburity -> {
			instantRage(() -> Anger.provoke(mehburity, stranger, 100.0));
			helper.assertTrue(mehburity.isMonster() && mehburity.getRageCulprit() == stranger, "the demon is after the stranger");
			helper.startSequence()
					.thenWaitUntil(() -> helper.assertTrue(stranger.isDeadOrDying(), "the culprit must be killed"))
					.thenExecute(() -> {
						helper.assertTrue(stranger.getLastDamageSource() != null
								&& stranger.getLastDamageSource().is(MehburityDamage.CLAW), "by the demon's claws");
						helper.assertTrue(owner.isAlive() && owner.getHealth() == owner.getMaxHealth(), "the owner was never touched");
					})
					.thenWaitUntil(() -> {
						MehburityEntity current = CompanionManager.find(helper.getLevel().getServer(), owner.getUUID());
						helper.assertTrue(current != null && !current.isRaging(), "the rage ends with the culprit");
						assertSphere(helper, current);
					})
					.thenSucceed();
		});
	}

	@GameTest(maxTicks = 300)
	public void cake_shrinks_the_demon_back(GameTestHelper helper) {
		ServerPlayer owner = survivalOwner(helper);
		withCompanion(helper, owner, mehburity -> {
			instantRage(() -> Anger.provoke(mehburity, owner, 100.0));
			helper.assertTrue(mehburity.isMonster() && mehburity.getRageCulprit() == owner, "a demon after its own owner");
			owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CAKE));
			mehburity.interact(owner, InteractionHand.MAIN_HAND, Vec3.ZERO);
			helper.assertFalse(mehburity.isRaging(), "a cake ends the rage");
			assertSphere(helper, mehburity);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 300)
	public void without_the_monster_form_a_rage_stays_a_sphere(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		withCompanion(helper, owner, mehburity -> {
			Husk husk = culprit(helper);
			// Switched off only while the rage starts: other tests run at the same time.
			MehburityConfig.get().monster.enabled = false;
			Anger.provoke(mehburity, husk, 100.0);
			MehburityConfig.get().monster.enabled = true;
			helper.assertTrue(mehburity.isRaging(), "still a rage");
			assertSphere(helper, mehburity);
			helper.succeedWhen(() -> helper.assertFalse(husk.isAlive(), "the red sphere's beam still hunts the culprit"));
		});
	}

	@GameTest
	public void monster_config_is_clamped(GameTestHelper helper) {
		MehburityConfig config = tempConfig("""
				{"monster": {"height": 500, "transformTicks": -4, "reach": 0, "attackCooldownTicks": 0}}""").load().value();
		helper.assertTrue(config.monster.height == 32.0, "at most 32 blocks tall");
		helper.assertTrue(config.monster.transformTicks == 0, "no negative transformation time");
		helper.assertTrue(config.monster.reach == 1.0, "some reach");
		helper.assertTrue(config.monster.attackCooldownTicks == 1, "at least one tick between swipes");
		MehburityConfig repaired = tempConfig("{\"monster\": null}").load().value();
		helper.assertTrue(repaired.monster != null && repaired.monster.enabled, "a missing monster section gets the defaults");
		helper.succeed();
	}
}
