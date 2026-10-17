package com.mehbur.mehburity.test;

import com.mehbur.mehburity.MehburityMod;
import com.mehbur.mehburity.client.ai.MicRecorder;
import com.mehbur.mehburity.client.ai.VoiceInput;
import com.mehbur.mehburity.client.config.MehburityClientConfig;
import com.mehbur.mehburity.client.screen.MehburityConfigScreen;
import com.mehbur.mehburity.config.ConfigReloadedCallback;
import com.mehbur.mehburity.config.MehburityConfig;
import com.mehbur.mehburity.entity.CompanionManager;
import com.mehbur.mehburity.entity.CompanionMode;
import com.mehbur.mehburity.entity.CompanionRegistry;
import com.mehbur.mehburity.entity.MehburityBoxEntity;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.loader.api.FabricLoader;
import com.mehbur.mehburity.entity.MehburityEntity;
import com.mehbur.mehburity.verity.EventsFeature;
import com.mehbur.mehburity.verity.FlashlightItem;
import com.mehbur.mehburity.verity.WatcherEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.lwjgl.glfw.GLFW;

/**
 * End-to-end client test (run with {@code gradlew runClientGameTest}). A real client: finds the first-join box,
 * opens it with a left click, checks that Verity darkness really darkens the night, then leaves and rejoins
 * to make sure Mehburity comes back. Screenshots land in {@code build/run/clientGameTest/screenshots}.
 */
public final class MehburityClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Files.exists(FabricLoader.getInstance().getConfigDir().resolve(MehburityClientConfig.FILE_NAME))) {
			throw new AssertionError("client config must be created on startup");
		}

		TestWorldSave save;
		UUID playerId;
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			save = singleplayer.getWorldSave();
			TestServerContext server = singleplayer.getServer();
			singleplayer.getConnection().waitForChunksRender();
			server.runCommand("time set noon");
			playerId = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getUUID());

			// The first-join box is next to the player.
			server.waitFor(s -> CompanionRegistry.box(s, playerId) != null, 100);
			BlockPos boxPos = server.computeOnServer(s -> {
				UUID boxId = CompanionRegistry.box(s, playerId);
				return s.overworld().getEntity(boxId) instanceof MehburityBoxEntity box ? box.blockPosition() : null;
			});
			if (boxPos == null) {
				throw new AssertionError("the first-join box must be in the world");
			}
			context.getInput().lookAt(boxPos);
			context.waitTicks(10);
			context.takeScreenshot("mehburity-1-box");

			// Left click opens it; Mehburity rises out.
			context.getInput().pressKey(options -> options.keyAttack);
			context.waitTicks(15);
			context.takeScreenshot("mehburity-2-box-opening");
			server.waitFor(s -> CompanionManager.find(s, playerId) != null, 200);
			context.waitTicks(40);
			context.takeScreenshot("mehburity-3-out");

			checkTalking(context, server, playerId);
			checkSettingsAndCommands(context, server, playerId);
			checkCombat(context, server, playerId);
			checkMining(context, server, playerId);
			checkBuilding(context, server, playerId);
			checkAnger(context, server, playerId);
			checkDarkness(context, server);
			checkVerity(context, server, playerId);
			checkPushToTalk(context, server, playerId);
		}

		// Leaving and coming back: Mehburity travels with the player and greets them again.
		try (TestSingleplayerContext singleplayer = save.open()) {
			singleplayer.getConnection().waitForChunksRender();
			singleplayer.getServer().waitFor(s -> CompanionManager.find(s, playerId) != null, 100);
			context.waitTicks(20);
			context.takeScreenshot("mehburity-8-rejoined");
		}
		MehburityMod.LOGGER.info("Mehburity client game test passed");
	}

	/**
	 * Real chat → server → client → Gemini → server → chat, first without a key (built-in reply), then against a
	 * local fake Gemini, which must also get the text-to-speech request for Mehburity's voice.
	 */
	private static void checkTalking(ClientGameTestContext context, TestServerContext server, UUID playerId) {
		Map<UUID, List<String>> replies = MehburityAiGameTests.REPLIES;
		context.runOnClient(mc -> mc.player.connection.sendChat("Mehburity hello"));
		server.waitFor(s -> replies.getOrDefault(playerId, List.of()).size() >= 1, 100);
		assertEquals("Hi! I'm right here.", replies.get(playerId).getLast(), "built-in reply without a key");

		try (FakeGemini fake = new FakeGemini()) {
			fake.chatAnswer("Neon cyan, of course!");
			context.runOnClient(mc -> {
				MehburityClientConfig.get().geminiApiKey = "test-key-123";
				MehburityClientConfig.get().apiBaseUrl = fake.baseUri().toString();
			});
			context.waitTicks(50); // chat cooldown
			context.runOnClient(mc -> mc.player.connection.sendChat("Mehburity, what is your favourite colour?"));
			server.waitFor(s -> replies.get(playerId).size() >= 2, 200);
			assertEquals("Neon cyan, of course!", replies.get(playerId).getLast(), "Gemini's answer reaches the chat");
			context.waitFor(mc -> fake.requests().stream().anyMatch(FakeGemini.Request::isSpeech), 200);

			FakeGemini.Request chat = fake.requests().stream().filter(r -> !r.isSpeech()).findFirst().orElseThrow();
			String system = chat.body().get("system_instruction").getAsString();
			if (!system.contains("Mehburity") || !system.contains("Player: ")) {
				throw new AssertionError("system instruction must hold the persona and the game context: " + system);
			}
			assertEquals("test-key-123", chat.apiKey(), "the client's key is used");
			context.waitTicks(10);
			context.takeScreenshot("mehburity-4-chat");
		} finally {
			context.runOnClient(mc -> MehburityClientConfig.load());
		}
	}

	/** {@code /mehburity ayarlar} is client-side; every other subcommand must still reach the server. */
	private static void checkSettingsAndCommands(ClientGameTestContext context, TestServerContext server, UUID playerId) {
		context.runOnClient(mc -> mc.player.connection.sendCommand("mehburity ayarlar"));
		context.waitForScreen(MehburityConfigScreen.class);
		context.takeScreenshot("mehburity-5-settings");
		context.setScreen(() -> null);

		context.runOnClient(mc -> mc.player.connection.sendCommand("mehburity otur"));
		server.waitFor(s -> CompanionRegistry.companion(s, playerId).mode() == CompanionMode.STAY, 100);
		context.runOnClient(mc -> mc.player.connection.sendCommand("mehburity takip"));
		server.waitFor(s -> CompanionRegistry.companion(s, playerId).mode() == CompanionMode.FOLLOW, 100);
	}

	/** {@code /mehburity savas} through real chat, then a beam fight against a husk the player started. */
	private static void checkCombat(ClientGameTestContext context, TestServerContext server, UUID playerId) {
		context.runOnClient(mc -> mc.player.connection.sendCommand("mehburity savas kapat"));
		server.waitFor(s -> !CompanionManager.isCombatEnabled(s, playerId), 100);
		context.runOnClient(mc -> mc.player.connection.sendCommand("mehburity savas ac"));
		server.waitFor(s -> CompanionManager.isCombatEnabled(s, playerId), 100);

		server.runCommand("difficulty easy"); // peaceful would remove the husk at once
		// Mehburity guards a spot to the right (STAY mode) and the husk stands ahead: the beam crosses the view.
		BlockPos huskPos = server.computeOnServer(s -> {
			ServerPlayer player = s.getPlayerList().getPlayer(playerId);
			ServerLevel level = player.level();
			Vec3 forward = Vec3.directionFromRotation(0.0F, player.getYRot());
			Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
			MehburityEntity mehburity = CompanionManager.find(s, playerId);
			CompanionManager.setMode(player, CompanionMode.STAY);
			Vec3 post = player.position().add(forward.scale(5.0)).add(right.scale(2.5)).add(0.0, 2.0, 0.0);
			mehburity.teleportTo(post.x, post.y, post.z);

			Vec3 ahead = player.position().add(forward.scale(10.0)).add(right.scale(-1.5));
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(ahead.x), (int) Math.floor(ahead.z));
			Husk husk = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
			husk.snapTo(ahead.x, y, ahead.z, player.getYRot() + 180.0F, 0.0F);
			husk.setNoAi(true);
			husk.setPersistenceRequired();
			level.addFreshEntity(husk);
			player.setLastHurtMob(husk); // as if the player had just hit it
			return husk.blockPosition();
		});
		context.getInput().pressKey(options -> options.keyToggleGui); // F1: hide the HUD and chat
		context.getInput().lookAt(huskPos.above(2));

		// The beam is visible on the client while it charges and flashes when it hits.
		context.waitFor(mc -> {
			for (Entity entity : mc.level.entitiesForRendering()) {
				if (entity instanceof MehburityEntity mehburity && mehburity.getBeamTarget() instanceof Husk) {
					return true;
				}
			}
			return false;
		}, 200);
		for (int shot = 1; shot <= 4; shot++) {
			context.waitTicks(4);
			context.takeScreenshot("mehburity-c" + shot + "-beam");
		}
		server.waitFor(s -> s.overworld().getEntitiesOfClass(Husk.class, new AABB(huskPos).inflate(4.0)).isEmpty(), 400);
		context.waitTicks(10);
		context.takeScreenshot("mehburity-c5-husk-gone");
		context.getInput().pressKey(options -> options.keyToggleGui);
		context.runOnClient(mc -> mc.player.connection.sendCommand("mehburity takip"));
		server.waitFor(s -> CompanionRegistry.companion(s, playerId).mode() == CompanionMode.FOLLOW, 100);
	}

	/** "Mehburity, maden kaz" and "kazdığın madenleri ver" typed into the real chat. */
	private static void checkMining(ClientGameTestContext context, TestServerContext server, UUID playerId) {
		BlockPos ore = server.computeOnServer(s -> {
			ServerPlayer player = s.getPlayerList().getPlayer(playerId);
			ServerLevel level = player.level();
			Vec3 ahead = player.position().add(Vec3.directionFromRotation(0.0F, player.getYRot()).scale(4.0));
			int x = (int) Math.floor(ahead.x);
			int z = (int) Math.floor(ahead.z);
			BlockPos ground = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
			level.setBlockAndUpdate(ground, Blocks.IRON_ORE.defaultBlockState());
			level.setBlockAndUpdate(ground.above(), Blocks.IRON_ORE.defaultBlockState());
			return ground;
		});
		context.getInput().pressKey(options -> options.keyToggleGui);
		context.getInput().lookAt(ore);
		context.runOnClient(mc -> mc.player.connection.sendChat("Mehburity, maden kaz"));
		server.waitFor(s -> CompanionManager.find(s, playerId).getMiningJob() != null, 100);
		context.waitTicks(30);
		context.takeScreenshot("mehburity-m1-mining");
		server.waitFor(s -> s.overworld().getBlockState(ore).isAir() && s.overworld().getBlockState(ore.above()).isAir(), 600);
		context.waitTicks(5);
		context.takeScreenshot("mehburity-m2-mined");
		context.getInput().pressKey(options -> options.keyToggleGui);

		// Natural ores nearby could keep it busy; the test only needs ours.
		context.runOnClient(mc -> mc.player.connection.sendChat("Mehburity, kazmayı bırak"));
		server.waitFor(s -> CompanionManager.find(s, playerId).getMiningJob() == null, 100);
		context.runOnClient(mc -> mc.player.connection.sendChat("Mehburity, kazdığın madenleri ver"));
		context.waitFor(mc -> mc.player.getInventory().countItem(Items.RAW_IRON) >= 2, 100);
		context.waitTicks(10);
		context.takeScreenshot("mehburity-m3-given");
	}

	/** "Mehburity, taş ev yap" from the real chat, a look at the house, then "evi geri al". */
	private static void checkBuilding(ClientGameTestContext context, TestServerContext server, UUID playerId) {
		double speed = server.computeOnServer(s -> MehburityConfig.get().build.blocksPerSecond);
		server.runOnServer(s -> MehburityConfig.get().build.blocksPerSecond = 120.0);
		context.runOnClient(mc -> mc.player.connection.sendChat("Mehburity, taş ev yap"));
		server.waitFor(s -> CompanionManager.find(s, playerId).getBuildJob() != null, 100);
		BlockPos door = server.computeOnServer(s -> CompanionManager.find(s, playerId).getBuildJob().plan().origin().above());
		context.waitTicks(40);
		context.getInput().pressKey(options -> options.keyToggleGui);
		context.getInput().lookAt(door.above(3));
		context.takeScreenshot("mehburity-h1-building");
		server.waitFor(s -> CompanionManager.find(s, playerId).getBuildJob() == null, 1200);
		if (!server.computeOnServer(s -> s.overworld().getBlockState(door).getBlock() instanceof DoorBlock)) {
			throw new AssertionError("the house must have a door at " + door);
		}
		// Step back to see the whole house.
		server.runOnServer(s -> {
			ServerPlayer player = s.getPlayerList().getPlayer(playerId);
			Vec3 back = player.position().subtract(Vec3.directionFromRotation(0.0F, player.getYRot()).scale(7.0));
			player.teleportTo(back.x, player.getY() + 1.0, back.z);
		});
		context.waitTicks(20);
		context.getInput().lookAt(door.above(3));
		context.waitTicks(5);
		context.takeScreenshot("mehburity-h2-house");
		context.getInput().pressKey(options -> options.keyToggleGui);

		context.runOnClient(mc -> mc.player.connection.sendChat("Mehburity, evi geri al"));
		server.waitFor(s -> !(s.overworld().getBlockState(door).getBlock() instanceof DoorBlock), 100);
		// Taking the house down must not leave torches, doors or other blocks lying around as items.
		String dropped = server.computeOnServer(s -> s.overworld()
				.getEntitiesOfClass(ItemEntity.class, new AABB(door).inflate(12.0),
						item -> item.getItem().getItem() instanceof BlockItem)
				.toString());
		if (!dropped.equals("[]")) {
			throw new AssertionError("undo dropped blocks as items: " + dropped);
		}
		server.runOnServer(s -> MehburityConfig.get().build.blocksPerSecond = speed);
		context.waitTicks(10);
		context.takeScreenshot("mehburity-h3-undone");
	}

	/**
	 * Real left clicks make Mehburity rage: it turns into the 18-block demon (photographed while growing, by day, at
	 * night and walking), then a real right click with a cake shrinks it back.
	 */
	private static void checkAnger(ClientGameTestContext context, TestServerContext server, UUID playerId) {
		// Creative: the rage cannot hurt the player, so the test cannot die here.
		server.runCommand("gamemode creative @a");
		double speed = MehburityConfig.get().monster.blocksPerSecond;
		// The demon stands still for the photos.
		server.runOnServer(s -> MehburityConfig.get().monster.blocksPerSecond = 0.0);
		// Mehburity floats straight ahead at eye height, so looking level along the player's yaw hits it.
		float yaw = server.computeOnServer(s -> {
			ServerPlayer player = s.getPlayerList().getPlayer(playerId);
			MehburityEntity mehburity = CompanionManager.find(s, playerId);
			CompanionManager.setMode(player, CompanionMode.STAY);
			Vec3 front = player.getEyePosition().add(Vec3.directionFromRotation(0.0F, player.getYRot()).scale(2.5)).subtract(0.0, 0.4, 0.0);
			mehburity.teleportTo(front.x, front.y, front.z);
			return player.getYRot();
		});
		context.getInput().pressKey(options -> options.keyToggleGui);
		context.waitTicks(5);
		context.getInput().lookAt(yaw, 0.0F);
		int hits = (int) Math.ceil(MehburityConfig.get().anger.rageThreshold / MehburityConfig.get().anger.playerHitAnger);
		for (int i = 0; i < hits; i++) {
			context.getInput().pressKey(options -> options.keyAttack);
			context.waitTicks(15);
		}
		server.waitFor(s -> CompanionManager.find(s, playerId).isRaging(), 100);
		if (!server.computeOnServer(s -> CompanionManager.find(s, playerId).isMonster())) {
			throw new AssertionError("a rage must turn Mehburity into the demon");
		}

		// Step back and look up at it while it grows.
		Vec3 demon = server.computeOnServer(s -> CompanionManager.find(s, playerId).position());
		server.runOnServer(s -> {
			Vec3 back = demon.add(Vec3.directionFromRotation(0.0F, yaw).scale(-16.0));
			int ground = s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(back.x), (int) Math.floor(back.z));
			s.getPlayerList().getPlayer(playerId).teleportTo(back.x, ground, back.z);
		});
		context.getInput().lookAt(yaw, -18.0F);
		context.waitTicks(12);
		context.takeScreenshot("mehburity-m1-transforming");
		server.waitFor(s -> !CompanionManager.find(s, playerId).isTransforming(), 200);
		context.waitTicks(10);
		context.takeScreenshot("mehburity-m2-demon");
		float height = server.computeOnServer(s -> CompanionManager.find(s, playerId).getBbHeight());
		if (Math.abs(height - MehburityConfig.get().monster.height) > 0.01) {
			throw new AssertionError("the demon must be " + MehburityConfig.get().monster.height + " blocks tall, was " + height);
		}

		server.runCommand("time set midnight");
		context.waitTicks(20);
		context.takeScreenshot("mehburity-m3-demon-night");
		server.runCommand("time set noon");

		// Let it walk towards the player for the walk cycle.
		server.runOnServer(s -> MehburityConfig.get().monster.blocksPerSecond = speed);
		context.waitTicks(25);
		context.takeScreenshot("mehburity-m4-demon-walking");
		server.runOnServer(s -> MehburityConfig.get().monster.blocksPerSecond = 0.0);

		// Right in front of it, looking at its legs, a cake ends the rage.
		server.runOnServer(s -> {
			ServerPlayer player = s.getPlayerList().getPlayer(playerId);
			MehburityEntity mehburity = CompanionManager.find(s, playerId);
			Vec3 front = mehburity.position().add(Vec3.directionFromRotation(0.0F, yaw).scale(-2.5));
			player.teleportTo(front.x, mehburity.getY(), front.z);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CAKE));
		});
		context.waitTicks(5);
		context.getInput().lookAt(yaw, 10.0F);
		context.getInput().pressKey(options -> options.keyUse);
		server.waitFor(s -> !CompanionManager.find(s, playerId).isRaging(), 100);
		server.runOnServer(s -> MehburityConfig.get().monster.blocksPerSecond = speed);
		if (server.computeOnServer(s -> CompanionManager.find(s, playerId).isMonster())) {
			throw new AssertionError("calming down must shrink the demon back into a sphere");
		}
		context.waitTicks(10);
		context.takeScreenshot("mehburity-a2-calmed");
		context.getInput().pressKey(options -> options.keyToggleGui);
		context.runOnClient(mc -> mc.player.connection.sendCommand("mehburity takip"));
		server.waitFor(s -> CompanionRegistry.companion(s, playerId).mode() == CompanionMode.FOLLOW, 100);
	}

	private static void assertEquals(Object expected, Object actual, String what) {
		if (!expected.equals(actual)) {
			throw new AssertionError(what + ": expected <" + expected + "> but was <" + actual + ">");
		}
	}

	/**
	 * The Verity module at midnight: a real right click switches the flashlight on (the view must get brighter, and
	 * no light blocks may stay behind once it is off), then the watcher stands on the horizon until it is looked at,
	 * and Mehburity shows its scared face.
	 */
	private static void checkVerity(ClientGameTestContext context, TestServerContext server, UUID playerId) {
		// Calm, so the scared face is not hidden behind an annoyed one.
		server.runCommand("mehburity sakin");
		server.runCommand("time set midnight");
		context.getInput().lookAt(0.0F, 25.0F);
		context.waitTicks(30);
		double dark = luminance(context.takeScreenshot("mehburity-v1-dark"));

		server.runOnServer(s -> s.getPlayerList().getPlayer(playerId).setItemInHand(InteractionHand.MAIN_HAND,
				new ItemStack(FlashlightItem.ITEM)));
		context.waitTicks(5);
		context.getInput().pressKey(options -> options.keyUse);
		server.waitFor(s -> FlashlightItem.isOn(s.getPlayerList().getPlayer(playerId).getMainHandItem()), 100);
		context.waitTicks(30);
		double lit = luminance(context.takeScreenshot("mehburity-v2-flashlight"));
		MehburityMod.LOGGER.info("Flashlight luminance: off={} on={}", dark, lit);
		if (!(lit > dark * 1.3 + 2.0)) {
			throw new AssertionError("the flashlight must light up the dark (off=" + dark + ", on=" + lit + ")");
		}
		context.getInput().pressKey(options -> options.keyUse);
		server.waitFor(s -> !FlashlightItem.isOn(s.getPlayerList().getPlayer(playerId).getMainHandItem()), 100);
		context.waitTicks(5);
		int leftover = server.computeOnServer(s -> {
			ServerPlayer player = s.getPlayerList().getPlayer(playerId);
			int count = 0;
			for (BlockPos pos : BlockPos.betweenClosed(player.blockPosition().offset(-30, -30, -30), player.blockPosition().offset(30, 30, 30))) {
				if (player.level().getBlockState(pos).is(Blocks.LIGHT)) {
					count++;
				}
			}
			return count;
		});
		assertEquals(0, leftover, "light blocks left behind by the flashlight");

		// The watcher: photographed looking a little to the side of it, then looked at until it vanishes.
		Vec3 watcher = server.computeOnServer(s -> {
			WatcherEntity entity = EventsFeature.spawnWatcher(s.getPlayerList().getPlayer(playerId), 40.0);
			return entity == null ? null : entity.position();
		});
		if (watcher == null) {
			throw new AssertionError("the watcher must find a spot 40 blocks away");
		}
		Vec3 eye = server.computeOnServer(s -> s.getPlayerList().getPlayer(playerId).getEyePosition());
		Vec3 toHead = watcher.add(0.0, 16.5, 0.0).subtract(eye);
		float yaw = (float) (Math.atan2(toHead.z, toHead.x) * 180.0 / Math.PI) - 90.0F;
		float pitch = (float) -(Math.atan2(toHead.y, Math.sqrt(toHead.x * toHead.x + toHead.z * toHead.z)) * 180.0 / Math.PI);
		context.getInput().pressKey(options -> options.keyToggleGui);
		context.getInput().lookAt(yaw + 25.0F, pitch * 0.5F);
		context.waitTicks(20);
		context.takeScreenshot("mehburity-v3-watcher");
		context.getInput().lookAt(yaw, pitch);
		server.waitFor(s -> s.overworld().getEntitiesOfClass(WatcherEntity.class, new AABB(BlockPos.containing(watcher)).inflate(4.0)).isEmpty(), 100);
		if (!server.computeOnServer(s -> CompanionManager.find(s, playerId).isScared())) {
			throw new AssertionError("Mehburity must look scared once the watcher vanished");
		}
		server.runOnServer(s -> {
			ServerPlayer player = s.getPlayerList().getPlayer(playerId);
			Vec3 front = player.getEyePosition().add(Vec3.directionFromRotation(0.0F, player.getYRot()).scale(2.0)).subtract(0.0, 0.4, 0.0);
			CompanionManager.find(s, playerId).teleportTo(front.x, front.y, front.z);
		});
		context.getInput().lookAt(yaw, 0.0F);
		context.waitTicks(15); // it turns to look at the player
		context.takeScreenshot("mehburity-v4-scared");
		context.getInput().pressKey(options -> options.keyToggleGui);
		server.runCommand("time set noon");
	}

	/**
	 * Push-to-talk: the key is bound through the settings screen with a real key press, holding it starts the
	 * microphone (or says there is none, on machines without one), and a recording sent to (fake) Gemini comes back
	 * as a chat line for Mehburity that Mehburity then answers.
	 */
	private static void checkPushToTalk(ClientGameTestContext context, TestServerContext server, UUID playerId) {
		context.runOnClient(mc -> mc.player.connection.sendCommand("mehburity ayarlar"));
		context.waitForScreen(MehburityConfigScreen.class);
		context.runOnClient(mc -> ((MehburityConfigScreen) mc.gui.screen()).captureTalkKey());
		context.getInput().pressKey(GLFW.GLFW_KEY_V);
		context.waitTicks(2);
		String bound = context.computeOnClient(mc -> VoiceInput.PUSH_TO_TALK.saveString());
		assertEquals("key.keyboard.v", bound, "the talk key set in Mehburity's settings");
		context.takeScreenshot("mehburity-p1-talk-key");
		context.setScreen(() -> null);

		Map<UUID, List<String>> replies = MehburityAiGameTests.REPLIES;
		try (FakeGemini fake = new FakeGemini()) {
			context.runOnClient(mc -> {
				MehburityClientConfig.get().geminiApiKey = "test-key-123";
				MehburityClientConfig.get().apiBaseUrl = fake.baseUri().toString();
			});
			// Holding the real key: records if this machine has a microphone. Silence comes back as "nothing said".
			fake.chatAnswer("");
			context.getInput().holdKey(VoiceInput.PUSH_TO_TALK);
			context.waitTicks(15);
			boolean recorded = context.computeOnClient(mc -> VoiceInput.isRecording());
			MehburityMod.LOGGER.info("Push-to-talk microphone available: {}", recorded);
			context.takeScreenshot("mehburity-p2-listening");
			context.getInput().releaseKey(VoiceInput.PUSH_TO_TALK);
			context.waitFor(mc -> !VoiceInput.isBusy(), 200);

			// A finished recording: Gemini hears a question, it goes to chat, Mehburity answers it.
			fake.chatAnswer("Neon cyan, of course!");
			fake.enqueue(200, FakeGemini.textInteraction("what is your favourite colour?"));
			int before = replies.getOrDefault(playerId, List.of()).size();
			int requestsBefore = fake.requests().size();
			CompletableFuture<String> sent = context.computeOnClient(mc -> VoiceInput.submit(new byte[MicRecorder.SAMPLE_RATE * 2]));
			context.waitFor(mc -> sent.isDone(), 200);
			assertEquals("Mehburity, what is your favourite colour?", sent.join(), "the transcript goes to chat for Mehburity");
			FakeGemini.Request transcription = fake.requests().get(requestsBefore);
			String audioType = transcription.body().getAsJsonArray("input").get(0).getAsJsonObject()
					.getAsJsonArray("content").get(1).getAsJsonObject().get("mime_type").getAsString();
			assertEquals("audio/wav", audioType, "the recording is sent as WAV");
			server.waitFor(s -> replies.getOrDefault(playerId, List.of()).size() > before, 200);
			assertEquals("Neon cyan, of course!", replies.get(playerId).getLast(), "Mehburity answers what was said");
			context.waitTicks(10);
			context.takeScreenshot("mehburity-p3-voice-chat");
		} finally {
			context.runOnClient(mc -> MehburityClientConfig.load());
		}
	}

	/** Compares the same midnight view with Verity darkness on and off. */
	private static void checkDarkness(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("time set midnight");
		context.getInput().pressKey(options -> options.keyToggleGui); // F1: hide the HUD
		context.getInput().lookAt(0.0F, 40.0F);
		context.waitTicks(30);
		double dark = luminance(context.takeScreenshot("mehburity-6-night-darkness"));

		server.runOnServer(s -> {
			MehburityConfig.get().verity.darkness.enabled = false;
			ConfigReloadedCallback.EVENT.invoker().onReloaded(s);
		});
		context.waitTicks(30);
		double vanilla = luminance(context.takeScreenshot("mehburity-7-night-vanilla"));

		server.runOnServer(s -> {
			MehburityConfig.get().verity.darkness.enabled = true;
			ConfigReloadedCallback.EVENT.invoker().onReloaded(s);
		});
		context.getInput().pressKey(options -> options.keyToggleGui);
		MehburityMod.LOGGER.info("Night luminance: darkness={} vanilla={}", dark, vanilla);
		if (!(dark < vanilla * 0.6)) {
			throw new AssertionError("Verity darkness must clearly darken the night (dark=" + dark + ", vanilla=" + vanilla + ")");
		}
	}

	private static double luminance(Path screenshot) {
		try {
			BufferedImage image = ImageIO.read(screenshot.toFile());
			double sum = 0;
			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					int rgb = image.getRGB(x, y);
					sum += 0.2126 * ((rgb >> 16) & 0xFF) + 0.7152 * ((rgb >> 8) & 0xFF) + 0.0722 * (rgb & 0xFF);
				}
			}
			return sum / (image.getWidth() * (double) image.getHeight());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
