package com.mehbur.mehburity.verity;

import com.mehbur.mehburity.MehburityMod;
import com.mehbur.mehburity.config.MehburityConfig;
import com.mehbur.mehburity.entity.MehburitySpeech;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Verity's flashlight. Right click switches it on and off; while it is on and in a hand, {@link FlashlightFeature}
 * lights up what the player looks at. The durability bar is the battery, recharged with redstone in an anvil.
 */
public final class FlashlightItem extends Item {
	/** Battery capacity in durability points; {@code verity.flashlight.batteryMinutes} sets how fast it drains. */
	public static final int BATTERY = 1200;

	/** Present while the flashlight is switched on; the item model shows the lit lens. */
	public static final DataComponentType<Unit> ON = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			MehburityMod.id("flashlight_on"),
			DataComponentType.<Unit>builder().persistent(Unit.CODEC).networkSynchronized(Unit.STREAM_CODEC).build());

	public static final Item ITEM = register();

	/** Forces class loading (and so registration) during mod init, before the registries freeze. */
	static void init() {
	}

	private FlashlightItem(Item.Properties properties) {
		super(properties);
	}

	private static Item register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MehburityMod.id("flashlight"));
		Item item = new FlashlightItem(new Item.Properties().setId(key).durability(BATTERY).repairable(Items.REDSTONE));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	public static boolean isOn(ItemStack stack) {
		return stack.is(ITEM) && stack.has(ON);
	}

	public static boolean isEmpty(ItemStack stack) {
		return stack.getDamageValue() >= stack.getMaxDamage() - 1;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			toggle(player.getItemInHand(hand), serverPlayer);
		}
		return InteractionResult.SUCCESS;
	}

	/** Switches the flashlight on or off; an empty battery cannot be switched on. */
	public static void toggle(ItemStack stack, ServerPlayer player) {
		if (isOn(stack)) {
			stack.remove(ON);
		} else if (isEmpty(stack)) {
			player.sendOverlayMessage(Component.translatableWithFallback("item.mehburity.flashlight.empty",
					"The battery is empty. Recharge it with redstone in an anvil."));
			return;
		} else {
			stack.set(ON, Unit.INSTANCE);
		}
		player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.LEVER_CLICK,
				SoundSource.PLAYERS, 0.4F, isOn(stack) ? 1.6F : 1.2F);
	}

	/** Called when Mehburity comes out of its box: a flashlight for the dark. */
	public static void gift(ServerPlayer player) {
		if (!MehburityConfig.get().verity.flashlight.giveWithBox) {
			return;
		}
		ItemStack flashlight = new ItemStack(ITEM);
		if (!player.getInventory().add(flashlight)) {
			player.drop(flashlight, false);
		}
		MehburitySpeech.say(player, MehburitySpeech.text("flashlight.gift",
				"Here, take this flashlight. It gets really dark around here. (Right click to switch it on.)"));
	}
}
