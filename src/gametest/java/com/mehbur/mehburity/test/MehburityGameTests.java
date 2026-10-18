package com.mehbur.mehburity.test;

import com.mehbur.mehburity.command.MehburityCommands;
import com.mehbur.mehburity.config.JsonConfigFile;
import com.mehbur.mehburity.config.MehburityConfig;
import com.mehbur.mehburity.entity.CompanionManager;
import com.mehbur.mehburity.entity.CompanionMode;
import com.mehbur.mehburity.entity.CompanionRegistry;
import com.mehbur.mehburity.anger.Anger;
import com.mehbur.mehburity.entity.MehburityBoxEntity;
import com.mehbur.mehburity.entity.MehburityItem;
import com.mehbur.mehburity.entity.MehburityEntities;
import com.mehbur.mehburity.entity.MehburityEntity;
import com.mehbur.mehburity.entity.MehburitySpeech;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;

/** Server game tests, run headless by {@code gradlew build}. */
public final class MehburityGameTests {
	private static final int AFTER_OPEN = MehburityConfig.get().verity.box.openTicks + 5;
	/** Box opened and Mehburity finished rising out of it. */
	static final int READY = AFTER_OPEN + MehburityEntity.EMERGE_DURATION;

	// ---------------------------------------------------------------- helpers

	static JsonConfigFile<MehburityConfig> tempConfig(String content) {
		try {
			Path path = Files.createTempDirectory("mehburity-test").resolve(MehburityConfig.FILE_NAME);
			if (content != null) {
				Files.writeString(path, content, StandardCharsets.UTF_8);
			}
			return new JsonConfigFile<>(path, MehburityConfig.class, MehburityConfig::new);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** A mock player standing inside the test area. */
	static ServerPlayer owner(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 pos = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5));
		player.teleportTo(pos.x, pos.y, pos.z);
		return player;
	}

	/**
	 * Like {@code GameTestHelper.makeMockServerPlayerInLevel}, but in survival: the vanilla mock player is always
	 * in creative,, mobs never target creative players and it never
	 * finishes "loading", which makes it invulnerable.
	 */
	static ServerPlayer survivalOwner(GameTestHelper helper) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-survivor"), false);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		// Also resets the creative abilities (invulnerability) the test server's default game mode gave.
		player.setGameMode(GameType.SURVIVAL);
		// A mock client never says it finished loading, and players that have not loaded cannot be hurt.
		player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		Vec3 pos = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5));
		player.teleportTo(pos.x, pos.y, pos.z);
		return player;
	}

	/** Gives the owner a box, opens it, and runs {@code then} once Mehburity is out. */
	static void withCompanion(GameTestHelper helper, ServerPlayer owner, Consumer<MehburityEntity> then) {
		MehburityBoxEntity box = CompanionManager.spawnBox(owner);
		box.interact(owner, InteractionHand.MAIN_HAND, Vec3.ZERO);
		helper.runAfterDelay(READY, () -> {
			MehburityEntity mehburity = CompanionManager.find(helper.getLevel().getServer(), owner.getUUID());
			helper.assertTrue(mehburity != null, "Mehburity must have come out of the box");
			then.accept(mehburity);
		});
	}

	private static long companionsOf(ServerLevel level, ServerPlayer owner) {
		long count = 0;
		for (var entity : level.getAllEntities()) {
			if (entity instanceof MehburityEntity m && !m.isRemoved() && owner.getUUID().equals(m.getOwnerId())) {
				count++;
			}
		}
		return count;
	}

	// ---------------------------------------------------------------- config

	@GameTest
	public void config_is_created_with_defaults(GameTestHelper helper) {
		JsonConfigFile<MehburityConfig> file = tempConfig(null);
		JsonConfigFile.Result<MehburityConfig> result = file.load();

		helper.assertTrue(result.status() == JsonConfigFile.Status.CREATED, "missing file must be created");
		helper.assertTrue(Files.exists(file.path()), "config file must exist on disk");
		helper.assertTrue(result.value().verity.darkness.enabled, "darkness is on by default");
		helper.assertTrue(result.value().verity.box.enabled, "the box is on by default");
		helper.succeed();
	}

	@GameTest
	public void broken_config_is_backed_up_and_replaced(GameTestHelper helper) {
		JsonConfigFile<MehburityConfig> file = tempConfig("{ this is not json");
		JsonConfigFile.Result<MehburityConfig> result = file.load();

		helper.assertTrue(result.status() == JsonConfigFile.Status.RECOVERED_FROM_BROKEN, "broken file must be detected");
		helper.assertTrue(Files.exists(file.path().resolveSibling(MehburityConfig.FILE_NAME + ".broken")),
				"the broken file must be kept as .broken");
		helper.assertFalse(result.value().general.debugLogging, "defaults must be used");
		helper.succeed();
	}

	@GameTest
	public void user_values_survive_and_missing_sections_are_filled(GameTestHelper helper) {
		JsonConfigFile<MehburityConfig> kept = tempConfig("{\"general\": {\"debugLogging\": true}}");
		helper.assertTrue(kept.load().value().general.debugLogging, "user value must be kept");

		JsonConfigFile<MehburityConfig> partial = tempConfig("{\"general\": null, \"verity\": {\"darkness\": null}}");
		JsonConfigFile.Result<MehburityConfig> result = partial.load();
		helper.assertTrue(result.status() == JsonConfigFile.Status.LOADED, "partial file is still valid");
		helper.assertTrue(result.value().general != null, "null section must be repaired");
		helper.assertTrue(result.value().companion != null, "missing section must be added");
		helper.assertTrue(result.value().verity.darkness != null, "nested null section must be repaired");
		helper.succeed();
	}

	@GameTest
	public void out_of_range_values_are_clamped(GameTestHelper helper) {
		MehburityConfig config = tempConfig("""
				{"verity": {"darkness": {"strength": 5.0}, "box": {"openTicks": -3}},
				 "companion": {"followStopDistance": 0.0, "teleportDistance": 1.0}}""").load().value();
		helper.assertTrue(config.verity.darkness.strength == 1.0, "strength is clamped to 1");
		helper.assertTrue(config.verity.box.openTicks == 1, "openTicks is at least 1");
		helper.assertTrue(config.companion.followStopDistance == 1.0, "stop distance is at least 1");
		helper.assertTrue(config.companion.teleportDistance > config.companion.followStartDistance,
				"teleport distance must exceed the follow distance");
		helper.succeed();
	}

	// ---------------------------------------------------------------- command

	@GameTest
	public void command_tree_is_registered(GameTestHelper helper) {
		CommandNode<CommandSourceStack> root = helper.getLevel().getServer().getCommands().getDispatcher()
				.getRoot().getChild(MehburityCommands.ROOT);
		helper.assertTrue(root != null, "/mehburity must be registered");
		for (String sub : new String[]{"yenile", "cagir", "otur", "takip", "ayarlar", "savas", "kaz", "ver", "canta", "ev", "ofke", "sakin"}) {
			helper.assertTrue(root.getChild(sub) != null, "/mehburity " + sub + " must be registered");
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- box

	@GameTest(maxTicks = 120)
	public void box_opens_and_mehburity_comes_out(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		MehburityBoxEntity box = CompanionManager.spawnBox(owner);
		helper.assertTrue(CompanionRegistry.hasEverReceived(helper.getLevel().getServer(), owner.getUUID()),
				"the box must be recorded");

		box.skipAttackInteraction(owner);
		helper.assertFalse(box.isOpening(), "hitting the box no longer opens it");
		box.interact(owner, InteractionHand.MAIN_HAND, Vec3.ZERO);
		helper.assertTrue(box.isOpening(), "the owner's right click must start the opening");

		helper.succeedWhen(() -> {
			MehburityEntity mehburity = CompanionManager.find(helper.getLevel().getServer(), owner.getUUID());
			helper.assertTrue(mehburity != null, "Mehburity must come out of the box");
			helper.assertTrue(owner.getUUID().equals(mehburity.getOwnerId()), "Mehburity must belong to the opener");
			helper.assertTrue(box.isRemoved(), "the box must disappear");
		});
	}

	@GameTest(maxTicks = 80)
	public void stranger_cannot_open_box(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		ServerPlayer stranger = owner(helper);
		MehburityBoxEntity box = CompanionManager.spawnBox(owner);

		helper.assertTrue(box.skipAttackInteraction(stranger), "the attack is always swallowed");
		box.interact(stranger, InteractionHand.MAIN_HAND, Vec3.ZERO);
		helper.assertFalse(box.isOpening(), "a stranger must not open someone else's box");
		helper.runAfterDelay(AFTER_OPEN, () -> {
			helper.assertTrue(CompanionManager.find(helper.getLevel().getServer(), stranger.getUUID()) == null,
					"the stranger must not get a Mehburity");
			helper.assertFalse(box.isRemoved(), "the box must still be there");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 160)
	public void second_box_does_not_create_second_mehburity(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		withCompanion(helper, owner, first -> {
			MehburityBoxEntity second = CompanionManager.spawnBox(owner);
			second.interact(owner, InteractionHand.MAIN_HAND, Vec3.ZERO);
			helper.runAfterDelay(AFTER_OPEN, () -> {
				helper.assertTrue(companionsOf(helper.getLevel(), owner) == 1, "there must be exactly one Mehburity");
				helper.assertFalse(first.isRemoved(), "the original Mehburity stays");
				helper.assertTrue(second.isRemoved(), "the extra box is used up");
				helper.succeed();
			});
		});
	}

	// ---------------------------------------------------------------- carrying

	/** Moves the first Mehburity item in the inventory into the main hand (it may already be the selected slot). */
	private static ItemStack mehburityInHand(ServerPlayer player) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(MehburityItem.ITEM)) {
				player.getInventory().setItem(slot, ItemStack.EMPTY);
				player.setItemInHand(InteractionHand.MAIN_HAND, stack);
				return stack;
			}
		}
		throw new AssertionError("no Mehburity in the inventory");
	}

	@GameTest(maxTicks = 300)
	public void mehburity_can_be_carried_and_let_out(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		ServerLevel level = helper.getLevel();
		withCompanion(helper, owner, mehburity -> {
			Husk husk = helper.spawn(EntityTypes.HUSK, new BlockPos(6, 1, 6));
			husk.setNoAi(true);
			Anger.provoke(mehburity, husk, 100.0);
			mehburity.interact(owner, InteractionHand.MAIN_HAND, Vec3.ZERO);
			helper.assertFalse(mehburity.isRemoved(), "a raging Mehburity cannot be picked up");
			Anger.endRage(mehburity, Anger.RageEnd.FORCED);

			mehburity.interact(owner, InteractionHand.MAIN_HAND, Vec3.ZERO);
			helper.assertTrue(mehburity.isRemoved(), "right click picks Mehburity up");
			helper.assertValueEqual(CompanionMode.CARRIED, CompanionRegistry.companion(level.getServer(), owner.getUUID()).mode(),
					"the registry knows it is carried");
			helper.assertTrue(owner.getInventory().contains(stack -> stack.is(MehburityItem.ITEM)
					&& owner.getUUID().equals(stack.get(MehburityItem.OWNER))), "it is in the owner's inventory");
			helper.runAfterDelay(30, () -> {
				helper.assertTrue(CompanionManager.find(level.getServer(), owner.getUUID()) == null,
						"a carried Mehburity is not re-created beside its owner");
				ItemStack item = mehburityInHand(owner);
				item.use(level, owner, InteractionHand.MAIN_HAND);
				MehburityEntity out = CompanionManager.find(level.getServer(), owner.getUUID());
				helper.assertTrue(out != null && out.getMode() == CompanionMode.FOLLOW, "right click with it lets it out");
				helper.assertTrue(owner.getMainHandItem().isEmpty(), "the item is used up");

				// Called back while carried: the old item is stale and must not make a second one.
				out.interact(owner, InteractionHand.MAIN_HAND, Vec3.ZERO);
				helper.assertValueEqual(CompanionManager.SummonResult.RESPAWNED, CompanionManager.summon(owner), "cagir brings it back");
				mehburityInHand(owner).use(level, owner, InteractionHand.MAIN_HAND);
				helper.assertTrue(companionsOf(level, owner) == 1, "still exactly one Mehburity");
				helper.assertTrue(owner.getMainHandItem().isEmpty(), "the stale item goes away");
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = 200)
	public void thrown_mehburity_comes_out_as_the_ball(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		ServerLevel level = helper.getLevel();
		withCompanion(helper, owner, mehburity -> {
			mehburity.interact(owner, InteractionHand.MAIN_HAND, Vec3.ZERO);
			helper.assertTrue(mehburity.isRemoved(), "right click picks Mehburity up");
			// Q: the held stack is thrown out of the hand.
			ItemStack stack = mehburityInHand(owner);
			owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			var thrown = owner.drop(stack, false, true);
			helper.assertTrue(thrown != null, "the item must be thrown");
			helper.succeedWhen(() -> {
				helper.assertTrue(thrown.isRemoved(), "the thrown item must not stay in the world");
				MehburityEntity out = CompanionManager.find(level.getServer(), owner.getUUID());
				helper.assertTrue(out != null && out.getMode() == CompanionMode.FOLLOW, "it comes out as the ball and follows");
				helper.assertTrue(out.distanceTo(owner) < 5.0F, "it comes out where it was thrown");
				helper.assertTrue(companionsOf(level, owner) == 1, "exactly one Mehburity");
				helper.assertFalse(owner.getInventory().contains(s -> s.is(MehburityItem.ITEM)), "no item left behind");
			});
		});
	}

	// ---------------------------------------------------------------- companion

	@GameTest(maxTicks = 120)
	public void mehburity_is_immortal(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		withCompanion(helper, owner, mehburity -> {
			ServerLevel level = helper.getLevel();
			float health = mehburity.getHealth();
			helper.assertFalse(mehburity.hurtServer(level, level.damageSources().playerAttack(owner), 1000.0F), "player attack");
			helper.assertFalse(mehburity.hurtServer(level, level.damageSources().lava(), 1000.0F), "lava");
			helper.assertFalse(mehburity.hurtServer(level, level.damageSources().inWall(), 1000.0F), "suffocation");
			helper.assertFalse(mehburity.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE), "/kill damage");
			mehburity.kill(level);
			helper.assertTrue(mehburity.isAlive() && !mehburity.isRemoved(), "kill() must not remove Mehburity");
			helper.assertTrue(mehburity.getHealth() == health, "health must not change");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 120)
	public void void_brings_mehburity_back_to_owner(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		withCompanion(helper, owner, mehburity -> {
			mehburity.teleportTo(mehburity.getX(), helper.getLevel().getMinY() - 80.0, mehburity.getZ());
			helper.succeedWhen(() -> helper.assertTrue(mehburity.distanceTo(owner) < 5.0F,
					"Mehburity must come back from the void"));
		});
	}

	@GameTest(maxTicks = 120)
	public void stale_copy_removes_itself(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		withCompanion(helper, owner, original -> {
			MehburityEntity copy = MehburityEntities.MEHBURITY.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
			helper.assertTrue(copy != null, "copy must be created");
			copy.snapTo(original.getX() + 1.0, original.getY(), original.getZ(), 0.0F, 0.0F);
			copy.setOwnerId(owner.getUUID());
			helper.getLevel().addFreshEntity(copy);
			helper.succeedWhen(() -> {
				helper.assertTrue(copy.isRemoved(), "an unregistered copy must remove itself");
				helper.assertFalse(original.isRemoved(), "the registered Mehburity stays");
			});
		});
	}

	@GameTest(maxTicks = 400)
	public void stay_holds_position_and_follow_catches_up(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		withCompanion(helper, owner, mehburity -> {
			helper.assertTrue(CompanionManager.setMode(owner, CompanionMode.STAY), "mode must be set");
			Vec3 waitingAt = mehburity.position();
			owner.teleportTo(owner.getX() + 10.0, owner.getY(), owner.getZ());

			helper.runAfterDelay(60, () -> {
				helper.assertTrue(mehburity.position().distanceTo(waitingAt) < 0.75, "STAY must hold position");
				helper.assertTrue(CompanionManager.setMode(owner, CompanionMode.FOLLOW), "mode must be set");
				double close = MehburityConfig.get().companion.followStartDistance + 1.5;
				helper.succeedWhen(() -> helper.assertTrue(mehburity.distanceTo(owner) < close,
						"FOLLOW must roll back to the owner"));
			});
		});
	}

	@GameTest(maxTicks = 500)
	public void mehburity_moves_its_mouth_while_it_talks(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		withCompanion(helper, owner, mehburity -> {
			helper.assertTrue(mehburity.isTalking(), "it talks while it greets its owner");
			helper.startSequence()
					.thenWaitUntil(() -> helper.assertFalse(mehburity.isTalking(), "quiet once the greeting is said"))
					.thenExecute(() -> {
						MehburitySpeech.say(owner, Component.literal("Hello there, nice to see you!"));
						helper.assertTrue(mehburity.isTalking(), "talking while it says something");
					})
					.thenWaitUntil(() -> helper.assertFalse(mehburity.isTalking(), "quiet again once the line is said"))
					.thenSucceed();
		});
	}

	@GameTest(maxTicks = 200)
	public void mehburity_rolls_on_the_ground(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		withCompanion(helper, owner, mehburity -> {
			helper.assertFalse(mehburity.isNoGravity(), "the ball must fall, not hover");
			CompanionManager.setMode(owner, CompanionMode.STAY);
			mehburity.teleportTo(mehburity.getX(), owner.getY() + 4.0, mehburity.getZ());
			helper.succeedWhen(() -> {
				helper.assertTrue(mehburity.onGround(), "Mehburity must land on the ground");
				// (The mock owner itself hangs a block above the floor: it has no physics.)
				BlockPos below = mehburity.blockPosition().below();
				helper.assertFalse(helper.getLevel().getBlockState(below).getCollisionShape(helper.getLevel(), below).isEmpty(),
						"Mehburity must rest on a solid floor");
				helper.assertTrue(mehburity.getY() < owner.getY() + 0.5, "Mehburity must have come down");
			});
		});
	}

	@GameTest(maxTicks = 200)
	public void teleports_to_owner_when_far(GameTestHelper helper) {
		ServerPlayer owner = owner(helper);
		withCompanion(helper, owner, mehburity -> {
			owner.teleportTo(owner.getX() + 40.0, owner.getY(), owner.getZ());
			helper.succeedWhen(() -> {
				MehburityEntity current = CompanionManager.find(helper.getLevel().getServer(), owner.getUUID());
				helper.assertTrue(current != null && current.distanceTo(owner) < 6.0F, "Mehburity must teleport to its owner");
			});
		});
	}
}
