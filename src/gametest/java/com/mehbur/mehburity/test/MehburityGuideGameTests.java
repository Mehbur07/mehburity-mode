package com.mehbur.mehburity.test;

import com.mehbur.mehburity.entity.MehburityEntity;
import com.mehbur.mehburity.guide.Destination;
import com.mehbur.mehburity.guide.GuideChat;
import com.mehbur.mehburity.guide.Guiding;
import com.mehbur.mehburity.guide.RoutePlanner;
import com.mehbur.mehburity.mining.MiningJob;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.atomic.AtomicReference;

import static com.mehbur.mehburity.test.MehburityGameTests.owner;
import static com.mehbur.mehburity.test.MehburityGameTests.withCompanion;

/** Mehburity leads its owner to the nearest structure ("Mehburity, beni en yakın köye götür"). */
public final class MehburityGuideGameTests {
	private static final String SOLO = "mehburity-test:solo";
	/** The walk is tried out on open ground this far from the test areas (the house test builds at -400 z). */
	private static final int FAR_SITE = 400;

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

	private static RoutePlanner.Route plan(GameTestHelper helper, BlockPos from, BlockPos to) {
		RoutePlanner planner = new RoutePlanner(helper.getLevel(), helper.absolutePos(from), helper.absolutePos(to), 0.5);
		while (!planner.step()) {
			// runs to the end in slices
		}
		return planner.result();
	}

	/**
	 * A stone wall across the area, three blocks high. Straight ahead it has a hole only one block high (Mehburity
	 * would roll through, a player cannot), and off to the side a proper doorway: the route goes round through the
	 * doorway. Without the doorway there is no way at all.
	 */
	@GameTest
	public void the_route_only_takes_ways_a_player_can_walk(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		for (int z = 0; z <= 7; z++) {
			for (int y = 1; y <= 3; y++) {
				helper.setBlock(new BlockPos(4, y, z), Blocks.STONE);
			}
		}
		helper.setBlock(new BlockPos(4, 1, 2), Blocks.AIR); // the low hole
		helper.setBlock(new BlockPos(4, 1, 6), Blocks.AIR);
		helper.setBlock(new BlockPos(4, 2, 6), Blocks.AIR); // the doorway
		helper.setBlock(new BlockPos(2, 0, 6), Blocks.OAK_LEAVES); // no walking on leaves: steps round it

		RoutePlanner.Route route = plan(helper, new BlockPos(1, 1, 2), new BlockPos(6, 1, 2));
		helper.assertTrue(route != null && route.complete(), "there is a way through the doorway");
		// Compared in absolute positions: the test area may be rotated.
		BlockPos doorway = helper.absolutePos(new BlockPos(4, 1, 6));
		BlockPos lowHole = helper.absolutePos(new BlockPos(4, 1, 2));
		BlockPos aboveLeaves = helper.absolutePos(new BlockPos(2, 1, 6));
		helper.assertTrue(route.steps().contains(doorway), "through the doorway: " + route.steps());
		helper.assertFalse(route.steps().contains(lowHole), "never through the low hole: " + route.steps());
		helper.assertFalse(route.steps().contains(aboveLeaves), "never on the leaves: " + route.steps());
		for (int z = 0; z <= 7; z++) {
			BlockPos wall = helper.absolutePos(new BlockPos(4, 1, z));
			helper.assertTrue(wall.equals(doorway) || !route.steps().contains(wall), "never through the wall: " + wall);
		}

		helper.setBlock(new BlockPos(4, 2, 6), Blocks.STONE); // the doorway is now a low hole as well
		helper.assertTrue(plan(helper, new BlockPos(1, 1, 2), new BlockPos(6, 1, 2)) == null, "no way a player could walk");
		helper.succeed();
	}

	@GameTest(environment = SOLO, maxTicks = 1500)
	public void it_leads_the_way_and_waits_for_the_owner(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		withCompanion(helper, owner, mehburity -> {
			ServerLevel level = helper.getLevel();
			BlockPos site = helper.absolutePos(new BlockPos(FAR_SITE, 0, 2));
			ChunkPos chunk = new ChunkPos(site.getX() >> 4, site.getZ() >> 4);
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 5; dz++) {
					level.setChunkForced(chunk.x() + dx, chunk.z() + dz, true);
				}
			}
			AtomicReference<Vec3> start = new AtomicReference<>();
			AtomicReference<BlockPos> target = new AtomicReference<>();
			helper.startSequence()
					.thenIdle(20) // the chunks out there load first
					.thenExecute(() -> {
						if (start.get() != null) {
							return;
						}
						int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, site.getX(), site.getZ());
						owner.teleportTo(site.getX() + 0.5, ground, site.getZ() + 0.5);
						MehburityEntity moved = mehburity.teleportToOwner(owner);
						target.set(new BlockPos(site.getX(), ground, site.getZ() + 50));
						helper.assertTrue(Guiding.startTo(owner, moved, Destination.VILLAGE, target.get()), "it starts leading the way");
						start.set(moved.position());
					})
					.thenWaitUntil(() -> helper.assertTrue(mehburity.position().distanceTo(start.get()) > 4.0,
							"it rolls off ahead along the route"))
					// The owner stays put: it does not run off, it waits for them and keeps the job.
					.thenIdle(100)
					.thenExecute(() -> {
						helper.assertTrue(mehburity.getGuideJob() != null, "still leading the way");
						helper.assertTrue(mehburity.distanceTo(owner) < 15.0F, "waiting for the owner, not running off");
						helper.assertTrue(mehburity.getZ() > owner.getZ() + 3.0, "ahead of the owner, towards the target");
						Vec3 there = Vec3.atBottomCenterOf(target.get()).add(0.0, 0.0, -15.0);
						owner.teleportTo(there.x, there.y, there.z);
					})
					.thenWaitUntil(() -> helper.assertTrue(mehburity.getGuideJob() == null, "arrived: no guide job left"))
					.thenExecute(() -> {
						for (int dx = -2; dx <= 2; dx++) {
							for (int dz = -2; dz <= 5; dz++) {
								level.setChunkForced(chunk.x() + dx, chunk.z() + dz, false);
							}
						}
					})
					.thenSucceed();
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
