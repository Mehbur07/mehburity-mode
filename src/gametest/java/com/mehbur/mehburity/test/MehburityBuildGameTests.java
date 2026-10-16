package com.mehbur.mehburity.test;

import com.mehbur.mehburity.build.BuildChat;
import com.mehbur.mehburity.build.BuildJob;
import com.mehbur.mehburity.build.Building;
import com.mehbur.mehburity.build.HousePlan;
import com.mehbur.mehburity.build.HouseStyle;
import com.mehbur.mehburity.config.MehburityConfig;
import com.mehbur.mehburity.entity.MehburityEntity;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.mehbur.mehburity.test.MehburityGameTests.owner;
import static com.mehbur.mehburity.test.MehburityGameTests.tempConfig;
import static com.mehbur.mehburity.test.MehburityGameTests.withCompanion;

/**
 * Phase 5: Mehburity builds a house and takes it down again. The house is bigger than a test area, so the world test
 * runs alone in its own environment ({@code mehburity-test:solo}) and never builds over another test.
 */
public final class MehburityBuildGameTests {
	private static final String SOLO = "mehburity-test:solo";
	/** Absolute positions a pretend protection mod refuses to let anyone break. */
	private static final Set<BlockPos> PROTECTED = ConcurrentHashMap.newKeySet();

	static {
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> !PROTECTED.contains(pos));
	}

	private static Map<BlockPos, BlockState> snapshot(ServerLevel level, HousePlan plan) {
		Map<BlockPos, BlockState> states = new HashMap<>();
		for (BlockPos pos : plan.area()) {
			states.put(pos, level.getBlockState(pos));
		}
		return states;
	}

	private static AABB bounds(HousePlan plan) {
		AABB box = null;
		for (BlockPos pos : plan.area()) {
			AABB block = new AABB(pos);
			box = box == null ? block : box.minmax(block);
		}
		return box;
	}

	private static long count(ServerLevel level, HousePlan plan, Class<?> blockClass) {
		return plan.area().stream().filter(pos -> blockClass.isInstance(level.getBlockState(pos).getBlock())).count();
	}

	// ---------------------------------------------------------------- chat orders

	@GameTest
	public void chat_orders_are_understood(GameTestHelper helper) {
		assertOrder(helper, "ev yap", BuildChat.Action.BUILD, null);
		assertOrder(helper, "Bana bir ev yapar mısın?", BuildChat.Action.BUILD, null);
		assertOrder(helper, "taş ev yap", BuildChat.Action.BUILD, HouseStyle.STONE);
		assertOrder(helper, "TUĞLA EV KUR", BuildChat.Action.BUILD, HouseStyle.BRICK);
		assertOrder(helper, "ahşap bir kulübe yap", BuildChat.Action.BUILD, HouseStyle.WOOD);
		assertOrder(helper, "elimdeki bloktan ev yap", BuildChat.Action.BUILD, HouseStyle.HELD);
		assertOrder(helper, "build me a house", BuildChat.Action.BUILD, null);
		assertOrder(helper, "build a stone house", BuildChat.Action.BUILD, HouseStyle.STONE);
		assertOrder(helper, "evi geri al", BuildChat.Action.UNDO, null);
		assertOrder(helper, "evi yık", BuildChat.Action.UNDO, null);
		assertOrder(helper, "remove the house", BuildChat.Action.UNDO, null);
		assertOrder(helper, "inşaatı durdur", BuildChat.Action.STOP, null);
		assertOrder(helper, "ev yapmayı bırak", BuildChat.Action.STOP, null);
		helper.assertTrue(BuildChat.parse("evet") == null, "\"evet\" (yes) is not a house");
		helper.assertTrue(BuildChat.parse("eve gidiyorum") == null, "going home is not an order");
		helper.assertTrue(BuildChat.parse("maden kaz") == null, "mining is someone else's order");
		helper.succeed();
	}

	private static void assertOrder(GameTestHelper helper, String text, BuildChat.Action action, HouseStyle style) {
		BuildChat.Order order = BuildChat.parse(text);
		helper.assertTrue(order != null && order.action() == action && order.style() == style,
				"'" + text + "' must be " + action + " " + style + " but was " + order);
	}

	@GameTest
	public void build_config_is_clamped(GameTestHelper helper) {
		MehburityConfig config = tempConfig("""
				{"build": {"blocksPerSecond": 0, "maxFoundationDepth": 99, "reach": 1.0, "defaultStyle": ""}}""").load().value();
		helper.assertTrue(config.build.blocksPerSecond == 1.0, "at least one block per second");
		helper.assertTrue(config.build.maxFoundationDepth == 32, "foundation depth is at most 32");
		helper.assertTrue(config.build.reach == 3.0, "reach is at least 3");
		helper.assertValueEqual("ahsap", config.build.defaultStyle, "a blank style gets the default");
		helper.assertTrue(HouseStyle.byId(config.build.defaultStyle) == HouseStyle.WOOD, "the default style exists");
		helper.succeed();
	}

	// ---------------------------------------------------------------- building in the world

	@GameTest(environment = SOLO, maxTicks = 2400)
	public void builds_a_house_and_takes_it_down_again(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		double speed = MehburityConfig.get().build.blocksPerSecond;
		MehburityConfig.get().build.blocksPerSecond = 200.0;
		withCompanion(helper, owner, mehburity -> {
			ServerLevel level = helper.getLevel();
			// Test areas are walled in with barriers, which Mehburity rightly refuses to touch: build on open ground
			// beside the area instead (this test runs alone, so nothing else is there).
			BlockPos outside = helper.absolutePos(new BlockPos(2, 0, -24));
			// Only the test area itself is kept ticking: force-load the building site for the duration of the test.
			ChunkPos site = new ChunkPos(outside.getX() >> 4, outside.getZ() >> 4);
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					level.setChunkForced(site.x() + dx, site.z() + dz, true);
				}
			}
			int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, outside.getX(), outside.getZ());
			owner.teleportTo(outside.getX() + 0.5, groundY, outside.getZ() + 0.5);
			mehburity.teleportToOwner(owner);
			helper.assertTrue(Building.start(owner, HouseStyle.STONE), "building must start");
			BuildJob job = mehburity.getBuildJob();
			helper.assertTrue(job != null, "Mehburity must have a build job");
			HousePlan plan = job.plan();
			Map<BlockPos, BlockState> before = snapshot(level, plan);
			helper.assertFalse(plan.area().contains(owner.blockPosition()), "the owner must not be walled in");

			helper.startSequence()
					.thenWaitUntil(() -> helper.assertTrue(mehburity.getBuildJob() == null, "the house must get finished"))
					.thenExecute(() -> {
						// The last step for a position wins (the space is cleared first, then built).
						Map<BlockPos, BlockState> expected = new HashMap<>();
						plan.steps().forEach(step -> expected.put(step.pos(), step.state()));
						expected.forEach((pos, want) -> {
							BlockState state = level.getBlockState(pos);
							helper.assertTrue(state.getBlock() == want.getBlock(),
									"at " + pos + " expected " + want.getBlock() + " but found " + state.getBlock());
						});
						helper.assertTrue(count(level, plan, DoorBlock.class) == 2, "a door, both halves");
						helper.assertTrue(count(level, plan, BedBlock.class) == 2, "a bed, both halves");
						helper.assertTrue(count(level, plan, WallTorchBlock.class) >= 4, "torches against the dark");
						helper.assertTrue(level.getBlockState(plan.origin().above()).getBlock() instanceof DoorBlock,
								"the door is in the middle of the front wall");

						helper.assertTrue(Building.undo(owner), "undo must work");
						for (Map.Entry<BlockPos, BlockState> entry : before.entrySet()) {
							helper.assertTrue(level.getBlockState(entry.getKey()) == entry.getValue(),
									"undo must restore " + entry.getKey());
						}
						helper.assertFalse(Building.undo(owner), "nothing left to undo");
						List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, bounds(plan).inflate(2.0));
						helper.assertTrue(drops.isEmpty(), "building and undoing must not drop items: " + drops);
					})
					.thenExecute(() -> checkHeldBlocks(helper, owner, mehburity))
					.thenExecuteAfter(10, () -> stopAndUndo(helper, owner, mehburity))
					.thenExecute(() -> checkProtection(helper, owner, mehburity))
					.thenExecute(() -> {
						MehburityConfig.get().build.blocksPerSecond = speed;
						for (int dx = -2; dx <= 2; dx++) {
							for (int dz = -2; dz <= 2; dz++) {
								level.setChunkForced(site.x() + dx, site.z() + dz, false);
							}
						}
					})
					.thenSucceed();
		});
	}

	/** "Elimdeki bloktan ev yap": a full block works, unbreakable, operator and non-full blocks are refused. */
	private static void checkHeldBlocks(GameTestHelper helper, ServerPlayer owner, MehburityEntity mehburity) {
		owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BEDROCK));
		helper.assertFalse(Building.start(owner, HouseStyle.HELD), "bedrock is refused");
		owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COMMAND_BLOCK));
		helper.assertFalse(Building.start(owner, HouseStyle.HELD), "operator blocks are refused");
		owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TORCH));
		helper.assertFalse(Building.start(owner, HouseStyle.HELD), "a torch is no wall");
		owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		helper.assertFalse(Building.start(owner, HouseStyle.HELD), "an empty hand is refused");

		owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.AMETHYST_BLOCK));
		helper.assertTrue(Building.start(owner, HouseStyle.HELD), "a full block works");
		HousePlan plan = mehburity.getBuildJob().plan();
		helper.assertTrue(plan.steps().stream().anyMatch(step -> step.state().is(Blocks.AMETHYST_BLOCK)),
				"the walls are made of the held block");
		owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
	}

	/** A half-built house can be stopped and taken down. */
	private static void stopAndUndo(GameTestHelper helper, ServerPlayer owner, MehburityEntity mehburity) {
		helper.assertTrue(Building.stop(owner), "stop must work");
		helper.assertTrue(mehburity.getBuildJob() == null, "no job after stopping");
		helper.assertTrue(Building.undo(owner), "even a stopped house can be undone");
	}

	/** A protected block in front of the owner: Mehburity builds somewhere else and never touches it. */
	private static void checkProtection(GameTestHelper helper, ServerPlayer owner, MehburityEntity mehburity) {
		ServerLevel level = helper.getLevel();
		BlockPos guarded = owner.blockPosition().relative(owner.getDirection(), 5).above();
		level.setBlockAndUpdate(guarded, Blocks.STONE.defaultBlockState());
		PROTECTED.add(guarded);
		try {
			helper.assertTrue(Building.start(owner, HouseStyle.WOOD), "another direction must still work");
			HousePlan plan = mehburity.getBuildJob().plan();
			helper.assertFalse(plan.area().contains(guarded), "the protected block is outside the house");
			helper.assertTrue(plan.facing() != owner.getDirection(), "the house turned away from the protected block");
			Building.stop(owner);
			Building.undo(owner);
		} finally {
			PROTECTED.remove(guarded);
			level.setBlockAndUpdate(guarded, Blocks.AIR.defaultBlockState());
		}
	}
}
