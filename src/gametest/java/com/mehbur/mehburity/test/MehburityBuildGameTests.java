package com.mehbur.mehburity.test;

import com.mehbur.mehburity.build.BuildChat;
import com.mehbur.mehburity.build.BuildJob;
import com.mehbur.mehburity.build.Building;
import com.mehbur.mehburity.build.HouseDescriptions;
import com.mehbur.mehburity.build.HouseDesignJson;
import com.mehbur.mehburity.build.HousePlan;
import com.mehbur.mehburity.build.HouseSpec;
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
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

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

	@GameTest
	public void house_descriptions_are_understood(GameTestHelper helper) {
		HouseStyle.Palette wood = HouseStyle.WOOD.palette();
		HouseSpec spec = HouseDescriptions.read("Mehburity, iki katlı, havuzlu, bahçeli, kırmızı çatılı beyaz bir ev yap", wood).build();
		helper.assertTrue(spec.floors() == 2 && spec.pool() && spec.garden(), "two floors, a pool and a garden: " + spec);
		helper.assertTrue(spec.palette().walls().is(Blocks.CONCRETE.white()), "white walls: " + spec.palette().walls());
		helper.assertTrue(spec.palette().roof().is(Blocks.CONCRETE.red()), "a red roof: " + spec.palette().roof());

		spec = HouseDescriptions.read("build me a big medieval stone house with a tower and a chimney", wood).build();
		helper.assertTrue(spec.width() >= 11 && spec.tower() && spec.chimney(), "big, a tower, a chimney: " + spec);
		helper.assertTrue(spec.palette().walls().is(Blocks.STONE_BRICKS), "stone walls: " + spec.palette().walls());

		spec = HouseDescriptions.read("modern bir villa", wood).build();
		helper.assertTrue(spec.roof() == HouseSpec.Roof.FLAT && spec.pool() && spec.bigWindows() && spec.floors() == 2,
				"a modern villa: " + spec);
		helper.assertTrue(HouseDescriptions.read("küçük ahşap kulübe", wood).build().width() == 5, "small");
		spec = HouseDescriptions.read("9x13 boyutunda 3 katlı, 4 yataklı, sivri çatılı ev", wood).build();
		helper.assertTrue(spec.width() == 9 && spec.depth() == 13 && spec.floors() == 3 && spec.beds() == 4
				&& spec.roof() == HouseSpec.Roof.GABLE, "size, floors, beds and roof: " + spec);
		spec = HouseDescriptions.read("büyük pencereli bir ev", wood).build();
		helper.assertTrue(spec.bigWindows() && spec.width() == 7, "big windows, not a big house: " + spec);
		helper.assertTrue(HouseDescriptions.read("tasarımı güzel bir ev", wood).build().palette().walls().is(Blocks.OAK_PLANKS),
				"\"tasarım\" (design) is not stone");
		helper.assertTrue(HouseDescriptions.read("balkonlu ev", wood).build().floors() == 2, "a balcony needs an upper floor");

		helper.assertTrue(BuildChat.isPlain("Mehburity, ev yap"), "a bare order");
		helper.assertTrue(BuildChat.isPlain("taş ev yap"), "a style is still the classic house");
		helper.assertTrue(BuildChat.isPlain("build me a house"), "a bare order (English)");
		helper.assertFalse(BuildChat.isPlain("iki katlı ev yap"), "floors are a description");
		helper.assertFalse(BuildChat.isPlain("deniz kenarında romantik bir ev yap"), "any wish is a description");
		helper.assertTrue(HouseSpec.standard(wood).equals(HouseDescriptions.read("ev yap", wood).build()),
				"nothing said: the classic house");
		helper.succeed();
	}

	@GameTest
	public void gemini_house_designs_are_checked(GameTestHelper helper) {
		HouseSpec.Draft draft = new HouseSpec.Draft(HouseStyle.WOOD.palette());
		String answer = "Sure! ```json {\"width\": 12, \"floors\": 9, \"roof\": \"gable\", \"walls\": \"minecraft:white_concrete\","
				+ " \"corners\": \"minecraft:sand\", \"roof_block\": \"minecraft:command_block\", \"door\": \"minecraft:iron_door\","
				+ " \"window\": \"minecraft:blue_stained_glass_pane\", \"pool\": true, \"beds\": 3} ```";
		helper.assertTrue(HouseDesignJson.apply(answer, draft), "the JSON inside the answer is read");
		HouseSpec spec = draft.build();
		helper.assertTrue(spec.width() == 13, "widths are odd: " + spec.width());
		helper.assertTrue(spec.floors() == HouseSpec.MAX_FLOORS, "floors are limited: " + spec.floors());
		helper.assertTrue(spec.roof() == HouseSpec.Roof.GABLE && spec.pool() && spec.beds() == 3, "roof, pool, beds");
		helper.assertTrue(spec.palette().walls().is(Blocks.CONCRETE.white()), "a valid wall block is used");
		HouseStyle.Palette wood = HouseStyle.WOOD.palette();
		helper.assertTrue(spec.palette().corners() == wood.corners(), "sand would fall: refused");
		helper.assertTrue(spec.palette().roof() == wood.roof(), "operator blocks are refused");
		helper.assertTrue(spec.palette().door() == wood.door(), "an iron door needs redstone: refused");
		helper.assertTrue(spec.window().is(Blocks.STAINED_GLASS_PANE.blue()), "a stained glass pane window");
		helper.assertFalse(HouseDesignJson.apply("I'd build a lovely house!", draft), "no JSON, nothing changes");
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

	@GameTest(environment = SOLO, maxTicks = 5000)
	public void builds_a_house_and_takes_it_down_again(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		double speed = MehburityConfig.get().build.blocksPerSecond;
		MehburityConfig.get().build.blocksPerSecond = 200.0;
		AtomicReference<HousePlan> described = new AtomicReference<>();
		AtomicReference<Map<BlockPos, BlockState>> describedBefore = new AtomicReference<>();
		withCompanion(helper, owner, mehburity -> {
			ServerLevel level = helper.getLevel();
			// Test areas are walled in with barriers, which Mehburity rightly refuses to touch: build on open ground
			// beside the area instead (this test runs alone, so nothing else is there).
			BlockPos outside = helper.absolutePos(new BlockPos(2, 0, -24));
			// Only the test area itself is kept ticking: force-load the building site for the duration of the test.
			ChunkPos site = new ChunkPos(outside.getX() >> 4, outside.getZ() >> 4);
			// The described house is built far away (see below); its chunks need time to load, so ask for them now.
			BlockPos far = helper.absolutePos(new BlockPos(2, 0, -FAR_SITE));
			ChunkPos farSite = new ChunkPos(far.getX() >> 4, far.getZ() >> 4);
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					level.setChunkForced(site.x() + dx, site.z() + dz, true);
					level.setChunkForced(farSite.x() + dx, farSite.z() + dz, true);
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
					// Stopped half-way: once at least one block is down (flying to the site takes a varying time).
					.thenWaitUntil(() -> helper.assertTrue(mehburity.getBuildJob() != null && mehburity.getBuildJob().placed() > 0,
							"the second house must get started"))
					.thenExecute(() -> stopAndUndo(helper, owner, mehburity))
					.thenExecute(() -> checkProtection(helper, owner, mehburity))
					// A described house with everything: two floors, balcony, pool, garden, chimney, tower, gable roof.
					.thenExecute(() -> {
						if (described.get() != null) {
							return; // already started (a step is run again when a later check in it fails)
						}
						// A big plot on open ground, far from every test area.
						int farGround = level.getHeight(Heightmap.Types.MOTION_BLOCKING, far.getX(), far.getZ());
						owner.teleportTo(far.getX() + 0.5, farGround, far.getZ() + 0.5);
						mehburity.teleportToOwner(owner);
						helper.assertTrue(Building.start(owner, null, DESCRIBED), "a described house must start");
						helper.assertTrue(mehburity.getBuildJob() != null, "and Mehburity must be building it");
						described.set(mehburity.getBuildJob().plan());
						describedBefore.set(snapshot(level, described.get()));
						helper.assertFalse(described.get().area().contains(owner.blockPosition()), "the owner must not be walled in");
					})
					.thenWaitUntil(() -> {
						helper.assertTrue(described.get() != null, "the described house must have been planned");
						helper.assertTrue(mehburity.getBuildJob() == null, "the described house must get finished");
					})
					.thenExecute(() -> {
						checkDescribed(helper, owner, described.get(), describedBefore.get());
					})
					.thenExecute(() -> {
						MehburityConfig.get().build.blocksPerSecond = speed;
						for (int dx = -2; dx <= 2; dx++) {
							for (int dz = -2; dz <= 2; dz++) {
								level.setChunkForced(site.x() + dx, site.z() + dz, false);
								level.setChunkForced(farSite.x() + dx, farSite.z() + dz, false);
							}
						}
					})
					.thenSucceed();
		});
	}

	/**
	 * The described house is big: it is built this far from the test area, beyond the whole grid of test areas
	 * (whose structure blocks Mehburity rightly refuses to touch).
	 */
	private static final int FAR_SITE = 400;

	private static final String DESCRIBED =
			"iki katlı, havuzlu, bahçeli, balkonlu, verandalı, bacalı, kuleli, sivri çatılı, 2 yataklı tuğla bir ev yap";

	/** Everything the description asked for is there, built as planned, and undoing restores the ground. */
	private static void checkDescribed(GameTestHelper helper, ServerPlayer owner, HousePlan plan, Map<BlockPos, BlockState> before) {
		ServerLevel level = helper.getLevel();
		HouseSpec spec = plan.spec();
		helper.assertTrue(spec.floors() == 2 && spec.pool() && spec.garden() && spec.balcony() && spec.porch() && spec.chimney()
				&& spec.tower() && spec.roof() == HouseSpec.Roof.GABLE && spec.beds() == 2, "read the description: " + spec);
		Map<BlockPos, BlockState> expected = new HashMap<>();
		plan.steps().forEach(step -> expected.put(step.pos(), step.state()));
		expected.forEach((pos, want) -> {
			BlockState state = level.getBlockState(pos);
			helper.assertTrue(state.getBlock() == want.getBlock(),
					"at " + pos + " expected " + want.getBlock() + " but found " + state.getBlock());
		});
		helper.assertTrue(count(level, plan, DoorBlock.class) == 4, "the front door and the balcony door");
		helper.assertTrue(count(level, plan, BedBlock.class) == 4, "two beds");
		helper.assertTrue(count(level, plan, LadderBlock.class) >= spec.wallRows() + 1, "a ladder up to the first floor");
		helper.assertTrue(count(level, plan, CampfireBlock.class) == 1, "smoke from the chimney");
		helper.assertTrue(count(level, plan, FenceGateBlock.class) == 1, "a garden gate");
		helper.assertTrue(plan.area().stream().anyMatch(pos -> level.getBlockState(pos).is(Blocks.WATER)), "a pool full of water");
		helper.assertTrue(plan.area().stream().anyMatch(pos -> level.getBlockState(pos).is(Blocks.BRICKS)), "brick walls");

		helper.assertTrue(Building.undo(owner), "undo must work");
		for (Map.Entry<BlockPos, BlockState> entry : before.entrySet()) {
			helper.assertTrue(level.getBlockState(entry.getKey()) == entry.getValue(), "undo must restore " + entry.getKey());
		}
		List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, bounds(plan).inflate(2.0));
		helper.assertTrue(drops.isEmpty(), "building and undoing must not drop items: " + drops);
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
