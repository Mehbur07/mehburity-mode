package com.mehbur.mehburity.test;

import com.mehbur.mehburity.ai.AiRouter;
import com.mehbur.mehburity.config.MehburityConfig;
import com.mehbur.mehburity.mining.Mining;
import com.mehbur.mehburity.mining.MiningBag;
import com.mehbur.mehburity.mining.MiningChat;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.mehbur.mehburity.test.MehburityGameTests.owner;
import static com.mehbur.mehburity.test.MehburityGameTests.tempConfig;
import static com.mehbur.mehburity.test.MehburityGameTests.withCompanion;

/**
 * Phase 4: Mehburity mines ores into its bag and hands them over. Everything that places ores is one sequential
 * test, because Mehburity searches beyond its own test area and parallel tests would mine each other's ores.
 */
public final class MehburityMiningGameTests {
	/** Absolute positions a pretend protection mod refuses to let anyone break. */
	private static final Set<BlockPos> PROTECTED = ConcurrentHashMap.newKeySet();

	static {
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> !PROTECTED.contains(pos));
	}

	private static int count(List<ItemStack> stacks, Item item) {
		return stacks.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
	}

	private static int inventoryCount(ServerPlayer player, Item item) {
		return player.getInventory().countItem(item);
	}

	private static void lookAt(GameTestHelper helper, ServerPlayer player, BlockPos relative) {
		player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(helper.absolutePos(relative)));
	}

	// ---------------------------------------------------------------- chat orders

	@GameTest
	public void chat_orders_are_understood(GameTestHelper helper) {
		helper.assertValueEqual(MiningChat.Intent.GIVE, MiningChat.parse("kazdığın madenleri ver"), "give (Turkish)");
		helper.assertValueEqual(MiningChat.Intent.GIVE, MiningChat.parse("Madenleri bana verir misin?"), "give, polite");
		helper.assertValueEqual(MiningChat.Intent.GIVE, MiningChat.parse("give me what you mined"), "give (English)");
		helper.assertValueEqual(MiningChat.Intent.MINE, MiningChat.parse("maden kaz"), "mine");
		helper.assertValueEqual(MiningChat.Intent.MINE, MiningChat.parse("KAZMAYA BAŞLA"), "mine, upper case");
		helper.assertValueEqual(MiningChat.Intent.MINE, MiningChat.parse("go mining"), "mine (English)");
		helper.assertValueEqual(MiningChat.Intent.MINE_HERE, MiningChat.parse("bunu kaz"), "this block");
		helper.assertValueEqual(MiningChat.Intent.MINE_HERE, MiningChat.parse("mine this block"), "this block (English)");
		helper.assertValueEqual(MiningChat.Intent.STOP, MiningChat.parse("kazmayı bırak"), "stop");
		helper.assertValueEqual(MiningChat.Intent.STOP, MiningChat.parse("kazmayı durdur"), "stop, durdur");
		helper.assertValueEqual(MiningChat.Intent.STOP, MiningChat.parse("stop mining"), "stop (English)");
		helper.assertValueEqual(MiningChat.Intent.LIST, MiningChat.parse("ne kazdın?"), "list");
		helper.assertValueEqual(MiningChat.Intent.LIST, MiningChat.parse("what did you mine?"), "list (English)");
		helper.assertTrue(MiningChat.parse("nasılsın?") == null, "small talk goes to Gemini");
		helper.assertTrue(MiningChat.parse("oyunu kazandın mı?") == null, "\"kazandın\" (won) is not digging");
		helper.assertTrue(MiningChat.parse("en sevdiğin renk ne?") == null, "a question is not an order");
		helper.succeed();
	}

	@GameTest(maxTicks = 120)
	public void chat_give_hands_over_the_bag_without_asking_gemini(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		withCompanion(helper, owner, mehburity -> {
			int repliesBefore = MehburityAiGameTests.REPLIES.getOrDefault(owner.getUUID(), List.of()).size();
			MiningBag.add(owner, new ItemStack(Items.RAW_GOLD, 5));
			helper.assertTrue(AiRouter.onPlayerChat(owner, "Mehburity, kazdığın madenleri ver"), "the message is for Mehburity");
			AiRouter.runOrders(); // normally at the end of the tick
			helper.assertTrue(inventoryCount(owner, Items.RAW_GOLD) == 5, "the owner must get the raw gold");
			helper.assertTrue(MiningBag.isEmpty(owner), "the bag must be empty afterwards");
			helper.assertTrue(MehburityAiGameTests.REPLIES.getOrDefault(owner.getUUID(), List.of()).size() == repliesBefore,
					"an order is carried out, not sent to Gemini");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the bag

	@GameTest
	public void bag_merges_stacks_and_has_a_limit(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		int slots = MehburityConfig.get().mining.bagSlots;
		helper.assertTrue(MiningBag.add(owner, new ItemStack(Items.COAL, 40)).isEmpty(), "fits");
		helper.assertTrue(MiningBag.add(owner, new ItemStack(Items.COAL, 40)).isEmpty(), "fits");
		helper.assertTrue(MiningBag.contents(owner).size() == 2, "80 coal = one full stack and one of 16");
		helper.assertValueEqual("80x Coal", MiningBag.describe(owner), "description merges stacks");

		ItemStack rest = MiningBag.add(owner, new ItemStack(Items.COBBLESTONE, 64 * slots));
		// The two coal stacks keep their slots, so two stacks of cobblestone do not fit.
		helper.assertTrue(rest.getCount() == 64 * 2, "what does not fit is handed back: " + rest.getCount());
		helper.assertTrue(MiningBag.isFull(owner), "the bag is full");
		helper.assertTrue(MiningBag.takeAll(owner).size() == slots, "every slot comes out");
		helper.assertTrue(MiningBag.isEmpty(owner), "and the bag is empty");
		helper.succeed();
	}

	// ---------------------------------------------------------------- mining in the world

	@GameTest(maxTicks = 1500)
	public void mines_ores_hands_them_over_and_mines_what_it_is_shown(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		withCompanion(helper, owner, mehburity -> {
			BlockPos iron1 = new BlockPos(5, 1, 5);
			BlockPos iron2 = new BlockPos(5, 2, 5);
			BlockPos coal = new BlockPos(6, 1, 2);
			BlockPos buried = new BlockPos(2, 2, 6);
			BlockPos guarded = new BlockPos(0, 1, 5);
			helper.setBlock(iron1, Blocks.IRON_ORE);
			helper.setBlock(iron2, Blocks.IRON_ORE);
			helper.setBlock(coal, Blocks.COAL_ORE);
			// A diamond ore with stone on every side: Mehburity must not "see" it.
			helper.setBlock(buried, Blocks.DIAMOND_ORE);
			for (Direction direction : Direction.values()) {
				helper.setBlock(buried.relative(direction), Blocks.STONE);
			}
			helper.setBlock(guarded, Blocks.COPPER_ORE);
			PROTECTED.add(helper.absolutePos(guarded));

			helper.assertTrue(Mining.startOres(owner), "mining must start");
			BlockPos dirt = new BlockPos(2, 1, 4);
			helper.startSequence()
					.thenWaitUntil(() -> {
						helper.assertTrue(mehburity.getMiningJob() == null, "Mehburity must finish mining");
						helper.assertBlockPresent(Blocks.AIR, iron1);
						helper.assertBlockPresent(Blocks.AIR, iron2);
						helper.assertBlockPresent(Blocks.AIR, coal);
					})
					.thenExecute(() -> {
						helper.assertBlockPresent(Blocks.DIAMOND_ORE, buried);
						helper.assertBlockPresent(Blocks.COPPER_ORE, guarded);
						List<ItemStack> bag = MiningBag.contents(owner);
						helper.assertTrue(count(bag, Items.RAW_IRON) == 2, "two raw iron in the bag: " + bag);
						helper.assertTrue(count(bag, Items.COAL) >= 1, "coal in the bag: " + bag);
						helper.assertTrue(count(bag, Items.RAW_COPPER) == 0, "the protected ore was left alone");

						helper.assertTrue(Mining.give(owner), "giving must work");
						helper.assertTrue(inventoryCount(owner, Items.RAW_IRON) == 2, "the owner got the raw iron");
						helper.assertTrue(MiningBag.isEmpty(owner), "the bag is empty after giving");

						// "Mehburity, bunu kaz": any block the owner looks at.
						helper.setBlock(dirt, Blocks.DIRT);
						lookAt(helper, owner, dirt);
						helper.assertTrue(Mining.startLookedAt(owner), "mining the looked-at block must start");
					})
					.thenWaitUntil(() -> {
						helper.assertBlockPresent(Blocks.AIR, dirt);
						helper.assertTrue(mehburity.getMiningJob() == null, "a single block is a single job");
					})
					.thenExecute(() -> {
						helper.assertTrue(count(MiningBag.contents(owner), Items.DIRT) == 1, "the dirt went into the bag");
						helper.setBlock(dirt, Blocks.BEDROCK);
						lookAt(helper, owner, dirt);
						helper.assertFalse(Mining.startLookedAt(owner), "bedrock is refused");
						helper.setBlock(dirt, Blocks.CHEST);
						helper.assertFalse(Mining.startLookedAt(owner), "blocks holding items are refused");
						helper.assertTrue(mehburity.getMiningJob() == null, "no job after a refusal");
						PROTECTED.remove(helper.absolutePos(guarded));
					})
					.thenSucceed();
		});
	}

	// ---------------------------------------------------------------- config

	@GameTest
	public void mining_config_is_clamped(GameTestHelper helper) {
		MehburityConfig config = tempConfig("""
				{"mining": {"searchRadius": 500, "reach": 0.0, "minBreakTicks": 50, "maxBreakTicks": 10,
				 "bagSlots": 0, "targets": ["#c:ores", "", null]}}""").load().value();
		helper.assertTrue(config.mining.searchRadius == 32, "search radius is at most 32");
		helper.assertTrue(config.mining.reach == 1.5, "reach is at least 1.5");
		helper.assertTrue(config.mining.maxBreakTicks >= config.mining.minBreakTicks, "max break time is not below the min");
		helper.assertTrue(config.mining.bagSlots == 1, "the bag has at least one slot");
		helper.assertTrue(config.mining.targets.equals(List.of("#c:ores")), "blank targets are dropped");

		MehburityConfig repaired = tempConfig("{\"mining\": null}").load().value();
		helper.assertTrue(repaired.mining != null && repaired.mining.enabled, "a missing mining section gets the defaults");
		helper.succeed();
	}
}
