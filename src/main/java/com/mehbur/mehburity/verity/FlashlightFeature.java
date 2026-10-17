package com.mehbur.mehburity.verity;

import com.mehbur.mehburity.config.MehburityConfig;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The flashlight's light. Vanilla has no moving lights, so every tick an invisible light block is put where the beam
 * lands (and a dimmer one halfway along long beams) for each player holding a switched-on flashlight; the old ones are
 * taken away as the beam moves. Only blocks this feature placed are ever removed, and all of them go when the player
 * puts the flashlight away, logs out or the server stops.
 */
public final class FlashlightFeature implements VerityFeature {
	/** Beams longer than this also get a light in the middle. */
	private static final double MIDDLE_LIGHT_FROM = 10.0;
	private static final int MIDDLE_LIGHT_DIM = 4;
	/** Steps back towards the player (half blocks) when the spot is taken by a solid block or water. */
	private static final int MAX_STEPS_BACK = 4;

	private record Lit(ResourceKey<Level> dimension, List<BlockPos> positions) {
	}

	private static final Map<UUID, Lit> LIT = new HashMap<>();

	@Override
	public String id() {
		return "flashlight";
	}

	@Override
	public void register() {
		FlashlightItem.init();
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(FlashlightItem.ITEM));
		ServerTickEvents.END_SERVER_TICK.register(FlashlightFeature::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clear(server, handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> new ArrayList<>(LIT.keySet()).forEach(id -> clear(server, id)));
	}

	/** The switched-on flashlight in the player's main hand, or else the off hand. */
	public static @Nullable ItemStack litFlashlight(ServerPlayer player) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = player.getItemInHand(hand);
			if (FlashlightItem.isOn(stack)) {
				return stack;
			}
		}
		return null;
	}

	private static void tick(MinecraftServer server) {
		MehburityConfig.Verity.Flashlight config = MehburityConfig.get().verity.flashlight;
		Set<UUID> shining = new HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ItemStack stack = config.enabled && !player.isSpectator() ? litFlashlight(player) : null;
			if (stack == null || !drain(stack, player, server.getTickCount(), config)) {
				continue;
			}
			shining.add(player.getUUID());
			update(player, beam(player, config));
		}
		for (UUID id : new ArrayList<>(LIT.keySet())) {
			if (!shining.contains(id)) {
				clear(server, id);
			}
		}
	}

	/** Uses up battery; false (and switched off) once it is empty. Creative players have endless batteries. */
	private static boolean drain(ItemStack stack, ServerPlayer player, int tick, MehburityConfig.Verity.Flashlight config) {
		if (config.batteryMinutes <= 0.0 || player.getAbilities().instabuild) {
			return true;
		}
		// batteryMinutes * 1200 ticks spread over BATTERY points: one point every batteryMinutes ticks.
		int interval = Math.max(1, (int) Math.round(config.batteryMinutes * 1200.0 / FlashlightItem.BATTERY));
		if (tick % interval == 0) {
			stack.setDamageValue(Math.min(stack.getMaxDamage() - 1, stack.getDamageValue() + 1));
		}
		if (FlashlightItem.isEmpty(stack)) {
			stack.remove(FlashlightItem.ON);
			player.sendOverlayMessage(Component.translatableWithFallback("item.mehburity.flashlight.died",
					"The flashlight flickers and dies. Recharge it with redstone in an anvil."));
			player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.LEVER_CLICK,
					SoundSource.PLAYERS, 0.4F, 0.8F);
			return false;
		}
		return true;
	}

	/** Where the light goes: the spot the beam lands on, plus a dimmer one halfway along a long beam. */
	private static Map<BlockPos, Integer> beam(ServerPlayer player, MehburityConfig.Verity.Flashlight config) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 end = eye.add(look.scale(config.range));
		BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		// Just in front of the surface it hits, so the light sits in the air next to it.
		Vec3 spot = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation().subtract(look.scale(0.3));
		Map<BlockPos, Integer> lights = new HashMap<>();
		BlockPos main = freeSpot(level, spot, look);
		if (main != null) {
			lights.put(main, config.lightLevel);
		}
		double length = eye.distanceTo(spot);
		if (length > MIDDLE_LIGHT_FROM) {
			BlockPos middle = freeSpot(level, eye.add(look.scale(length * 0.5)), look);
			if (middle != null && !lights.containsKey(middle)) {
				lights.put(middle, Math.max(1, config.lightLevel - MIDDLE_LIGHT_DIM));
			}
		}
		return lights;
	}

	private static @Nullable BlockPos freeSpot(ServerLevel level, Vec3 spot, Vec3 look) {
		for (int step = 0; step <= MAX_STEPS_BACK; step++) {
			BlockPos pos = BlockPos.containing(spot.subtract(look.scale(step * 0.5)));
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || state.is(Blocks.LIGHT)) {
				return pos;
			}
		}
		return null;
	}

	private static void update(ServerPlayer player, Map<BlockPos, Integer> lights) {
		ServerLevel level = player.level();
		Lit old = LIT.get(player.getUUID());
		if (old != null) {
			ServerLevel oldLevel = level.getServer().getLevel(old.dimension());
			for (BlockPos pos : old.positions()) {
				if (oldLevel != null && (oldLevel != level || !lights.containsKey(pos))) {
					removeLight(oldLevel, pos);
				}
			}
		}
		List<BlockPos> placed = new ArrayList<>();
		lights.forEach((pos, light) -> {
			BlockState state = level.getBlockState(pos);
			BlockState wanted = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, light);
			if (state.isAir()) {
				level.setBlock(pos, wanted, Block.UPDATE_CLIENTS);
			} else if (state.is(Blocks.LIGHT) && state.getValue(LightBlock.LEVEL) != light.intValue()) {
				level.setBlock(pos, wanted, Block.UPDATE_CLIENTS);
			}
			if (level.getBlockState(pos).is(Blocks.LIGHT)) {
				placed.add(pos);
			}
		});
		LIT.put(player.getUUID(), new Lit(level.dimension(), placed));
	}

	/** Takes away every light this player's flashlight put down. */
	static void clear(MinecraftServer server, UUID playerId) {
		Lit lit = LIT.remove(playerId);
		ServerLevel level = lit == null ? null : server.getLevel(lit.dimension());
		if (level != null) {
			lit.positions().forEach(pos -> removeLight(level, pos));
		}
	}

	private static void removeLight(ServerLevel level, BlockPos pos) {
		if (level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.LIGHT)) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
	}
}
