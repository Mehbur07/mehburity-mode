package com.mehbur.mehburity.test;

import com.mehbur.mehburity.guide.Destination;
import com.mehbur.mehburity.guide.GuideChat;
import com.mehbur.mehburity.guide.Guiding;
import com.mehbur.mehburity.mining.MiningJob;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import static com.mehbur.mehburity.test.MehburityGameTests.owner;
import static com.mehbur.mehburity.test.MehburityGameTests.withCompanion;

/** Mehburity leads its owner to the nearest structure ("Mehburity, beni en yakın köye götür"). */
public final class MehburityGuideGameTests {
	private static void assertGo(GameTestHelper helper, Destination expected, String message) {
		GuideChat.Order order = GuideChat.parse(message, false);
		helper.assertTrue(order instanceof GuideChat.Go go && go.destination() == expected,
				"\"" + message + "\" should lead to " + expected + ", was " + order);
	}

	@GameTest
	public void guide_orders_are_understood(GameTestHelper helper) {
		assertGo(helper, Destination.VILLAGE, "beni en yakın köye götür");
		assertGo(helper, Destination.VILLAGE, "Bizi en yakın köye götürür müsün?");
		assertGo(helper, Destination.STRONGHOLD, "en yakın kaleye yol göster");
		assertGo(helper, Destination.FORTRESS, "beni en yakın Nether kalesine götür");
		assertGo(helper, Destination.ANCIENT_CITY, "en yakın antik şehre götür");
		assertGo(helper, Destination.MINESHAFT, "beni en yakın maden ocağına götür");
		assertGo(helper, Destination.DESERT_TEMPLE, "en yakın çöl tapınağına götür beni");
		assertGo(helper, Destination.OCEAN_RUIN, "en yakın okyanus harabesine götür");
		assertGo(helper, Destination.MONUMENT, "en yakın okyanus anıtına götür");
		assertGo(helper, Destination.VILLAGE, "take me to the nearest village");
		assertGo(helper, Destination.MONUMENT, "lead me to the closest ocean monument");
		assertGo(helper, Destination.TRIAL_CHAMBERS, "guide me to the nearest trial chambers");
		helper.assertTrue(GuideChat.parse("beni en yakın uzay üssüne götür", false) instanceof GuideChat.Unknown,
				"an unknown place: it asks where to");
		helper.assertTrue(GuideChat.parse("beni eve götür", false) == null, "home is not a structure: a question for Gemini");
		helper.assertTrue(GuideChat.parse("dur", true) instanceof GuideChat.Stop, "\"dur\" while leading the way");
		helper.assertTrue(GuideChat.parse("dur", false) == null, "\"dur\" means nothing to it otherwise");
		helper.assertTrue(GuideChat.parse("maden kaz", false) == null, "a mining order is not about guiding");
		helper.succeed();
	}

	@GameTest(maxTicks = 300)
	public void it_leads_waits_for_the_owner_and_stops_on_arrival(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		withCompanion(helper, owner, mehburity -> {
			BlockPos target = helper.absolutePos(new BlockPos(2, 1, 32));
			helper.assertTrue(Guiding.startTo(owner, mehburity, Destination.VILLAGE, target), "it starts leading the way");
			helper.assertTrue(mehburity.getGuideJob() != null, "with a guide job");
			helper.assertTrue(mehburity.isTalking(), "telling the owner how far and which way");

			// The owner stays behind: it waits for them instead of rolling off, and calls them.
			Vec3 behind = helper.absoluteVec(new Vec3(2.5, 1.0, -14.0));
			owner.teleportTo(behind.x, behind.y, behind.z);
			Vec3 waitedAt = mehburity.position();
			helper.runAfterDelay(60, () -> {
				helper.assertTrue(mehburity.getGuideJob() != null, "still leading the way");
				helper.assertTrue(mehburity.position().distanceTo(waitedAt) < 2.0, "it waits for the owner");
				helper.assertTrue(mehburity.isTalking(), "and calls them to follow");

				// The owner gets there: done.
				Vec3 there = helper.absoluteVec(new Vec3(2.5, 1.0, 14.0));
				owner.teleportTo(there.x, there.y, there.z);
				helper.succeedWhen(() -> helper.assertTrue(mehburity.getGuideJob() == null, "arrived: no guide job left"));
			});
		});
	}

	@GameTest(maxTicks = 200)
	public void another_job_or_stop_ends_the_guiding(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		withCompanion(helper, owner, mehburity -> {
			BlockPos target = helper.absolutePos(new BlockPos(2, 1, 40));
			Guiding.startTo(owner, mehburity, Destination.STRONGHOLD, target);
			mehburity.setMiningJob(MiningJob.ores());
			helper.assertTrue(mehburity.getGuideJob() == null, "mining replaces the guiding");
			mehburity.setMiningJob(null);

			Guiding.startTo(owner, mehburity, Destination.STRONGHOLD, target);
			helper.assertTrue(Guiding.stop(owner), "\"dur\" stops it");
			helper.assertTrue(mehburity.getGuideJob() == null, "no guide job left");
			helper.assertTrue(!Guiding.startTo(owner, mehburity, Destination.VILLAGE, helper.absolutePos(new BlockPos(4, 1, 4))),
					"already there: nothing to lead");
			helper.succeed();
		});
	}
}
