package com.mehbur.mehburity.entity;

import com.mehbur.mehburity.MehburityMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Mehburity carried in the inventory. Its owner picks it up with a right click (see {@link MehburityEntity}); right
 * clicking a block (or the air) with it lets Mehburity out again there, and so does throwing it out of the inventory
 * (Q; see {@link CompanionManager}). Only the owner can let it out, and a stale
 * copy (Mehburity was called back with {@code /mehburity cagir} meanwhile) just goes away.
 */
public final class MehburityItem extends Item {
	/** Whose Mehburity this is. */
	public static final DataComponentType<UUID> OWNER = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			MehburityMod.id("owner"),
			DataComponentType.<UUID>builder().persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC).build());

	public static final Item ITEM = register();

	private MehburityItem(Item.Properties properties) {
		super(properties);
	}

	private static Item register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MehburityMod.id("mehburity"));
		return Registry.register(BuiltInRegistries.ITEM, key, new MehburityItem(new Item.Properties().setId(key).stacksTo(1).fireResistant()));
	}

	/** Forces class loading (and so registration) during mod init, before the registries freeze. */
	static void init() {
	}

	public static ItemStack of(UUID owner) {
		ItemStack stack = new ItemStack(ITEM);
		stack.set(OWNER, owner);
		return stack;
	}

	/** On a block: Mehburity comes out just above the side that was clicked. */
	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (context.getPlayer() instanceof ServerPlayer player) {
			BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
			release(context.getItemInHand(), player, Vec3.atCenterOf(pos));
		}
		return InteractionResult.SUCCESS;
	}

	/** In the air: Mehburity comes out beside the player. */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			release(player.getItemInHand(hand), serverPlayer, null);
		}
		return InteractionResult.SUCCESS;
	}

	private static void release(ItemStack stack, ServerPlayer player, @Nullable Vec3 spot) {
		UUID owner = stack.get(OWNER);
		if (owner != null && !owner.equals(player.getUUID())) {
			ServerPlayer realOwner = player.level().getServer().getPlayerList().getPlayer(owner);
			MehburitySpeech.say(player, realOwner != null
					? MehburitySpeech.text("carry.not_yours", "I belong to %s. Only they can let me out.", realOwner.getDisplayName())
					: MehburitySpeech.text("carry.not_yours_unknown", "You're not my owner. Only they can let me out."));
			return;
		}
		if (!CompanionManager.release(player, spot)) {
			MehburitySpeech.say(player, MehburitySpeech.text("already_have", "You already have me!"));
		}
		stack.shrink(1);
	}
}
