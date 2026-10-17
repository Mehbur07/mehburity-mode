package com.mehbur.mehburity.test;

import com.mehbur.mehburity.config.MehburityConfig;
import com.mehbur.mehburity.verity.EventsFeature;
import com.mehbur.mehburity.verity.FlashlightItem;
import com.mehbur.mehburity.verity.WatcherEntity;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import static com.mehbur.mehburity.test.MehburityGameTests.owner;
import static com.mehbur.mehburity.test.MehburityGameTests.survivalOwner;
import static com.mehbur.mehburity.test.MehburityGameTests.tempConfig;
import static com.mehbur.mehburity.test.MehburityGameTests.withCompanion;

/** Phase 8: the Verity module's flashlight, scripted scares and Mehburity's scared face. */
public final class MehburityVerityGameTests {
	/** Looks along +X at eye height, towards a stone block a few blocks away. */
	private static void lookEast(ServerPlayer player) {
		player.setYRot(-90.0F);
		player.setYHeadRot(-90.0F);
		player.setXRot(0.0F);
	}

	private static ItemStack flashlightInHand(ServerPlayer player) {
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FlashlightItem.ITEM));
		return player.getMainHandItem();
	}

	// ---------------------------------------------------------------- flashlight

	@GameTest(maxTicks = 100)
	public void the_flashlight_lights_up_what_you_look_at(GameTestHelper helper) {
		ServerPlayer player = owner(helper);
		helper.setBlock(new BlockPos(6, 2, 2), Blocks.STONE);
		BlockPos spot = new BlockPos(5, 2, 2);
		lookEast(player);
		ItemStack flashlight = flashlightInHand(player);
		FlashlightItem.toggle(flashlight, player);
		helper.assertTrue(FlashlightItem.isOn(flashlight), "right click switches it on");
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertBlockPresent(Blocks.LIGHT, spot))
				.thenExecute(() -> FlashlightItem.toggle(flashlight, player))
				.thenWaitUntil(() -> helper.assertBlockPresent(Blocks.AIR, spot))
				.thenExecute(() -> {
					FlashlightItem.toggle(flashlight, player);
					player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); // put away while on
				})
				.thenIdle(3)
				.thenExecute(() -> helper.assertBlockPresent(Blocks.AIR, spot))
				.thenSucceed();
	}

	@GameTest(maxTicks = 120)
	public void an_empty_battery_switches_the_flashlight_off(GameTestHelper helper) {
		ServerPlayer player = survivalOwner(helper);
		ItemStack flashlight = flashlightInHand(player);
		flashlight.setDamageValue(FlashlightItem.BATTERY - 2);
		FlashlightItem.toggle(flashlight, player);
		helper.assertTrue(FlashlightItem.isOn(flashlight), "a little battery left");
		helper.succeedWhen(() -> {
			helper.assertFalse(FlashlightItem.isOn(flashlight), "an empty battery switches it off");
			helper.assertTrue(FlashlightItem.isEmpty(flashlight), "the battery is empty");
			FlashlightItem.toggle(flashlight, player);
			helper.assertFalse(FlashlightItem.isOn(flashlight), "and it cannot be switched on again");
			helper.assertTrue(!flashlight.isEmpty(), "the flashlight itself never breaks");
		});
	}

	@GameTest(maxTicks = 200)
	public void mehburity_hands_over_a_flashlight(GameTestHelper helper) {
		ServerPlayer player = owner(helper);
		withCompanion(helper, player, mehburity -> {
			helper.assertTrue(player.getInventory().contains(stack -> stack.is(FlashlightItem.ITEM)),
					"Mehburity gives a flashlight when it comes out of its box");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- scripted scares

	@GameTest(maxTicks = 200)
	public void a_whisper_scares_mehburity(GameTestHelper helper) {
		ServerPlayer player = owner(helper);
		withCompanion(helper, player, mehburity -> {
			helper.assertFalse(mehburity.isScared(), "calm at first");
			helper.assertTrue(EventsFeature.trigger(player, EventsFeature.Kind.SOUND), "sounds always work");
			helper.assertTrue(EventsFeature.trigger(player, EventsFeature.Kind.WHISPER), "Mehburity whispers");
			helper.assertTrue(mehburity.isScared(), "and looks scared");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void the_watcher_vanishes_when_the_player_comes_close(GameTestHelper helper) {
		ServerPlayer player = owner(helper);
		withCompanion(helper, player, mehburity -> {
			// Facing into the test area (+X +Z): within the ±60° spread the demon always lands inside it, where
			// entities tick. Outside, in a chunk that does not tick, it would never get the chance to vanish.
			player.setYRot(-45.0F);
			player.setYHeadRot(-45.0F);
			WatcherEntity watcher = EventsFeature.spawnWatcher(player, 5.0);
			helper.assertTrue(watcher != null && watcher.isAlive(), "the demon appears");
			helper.assertFalse(watcher.isPickable() || watcher.isAttackable(), "it cannot be touched");
			helper.succeedWhen(() -> {
				helper.assertTrue(watcher.isRemoved(), "too close: it vanishes");
				helper.assertTrue(mehburity.isScared(), "and Mehburity saw it too");
			});
		});
	}

	@GameTest
	public void verity_config_is_clamped(GameTestHelper helper) {
		MehburityConfig config = tempConfig("""
				{"verity": {"flashlight": {"range": 1000, "lightLevel": 0, "batteryMinutes": -5},
				 "events": {"minMinutes": 0, "maxMinutes": 0.1}}}""").load().value();
		helper.assertTrue(config.verity.flashlight.range == 64, "beam range at most 64");
		helper.assertTrue(config.verity.flashlight.lightLevel == 1, "some light");
		helper.assertTrue(config.verity.flashlight.batteryMinutes == 0.0, "0 = endless battery");
		helper.assertTrue(config.verity.events.minMinutes == 0.5, "at most one scare every 30 seconds");
		helper.assertTrue(config.verity.events.maxMinutes >= config.verity.events.minMinutes, "max is not below min");
		MehburityConfig repaired = tempConfig("{\"verity\": {\"flashlight\": null, \"events\": null}}").load().value();
		helper.assertTrue(repaired.verity.flashlight != null && repaired.verity.events != null, "missing sections get defaults");
		helper.succeed();
	}
}
