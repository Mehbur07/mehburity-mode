package com.mehbur.mehburity.mining;

import com.mehbur.mehburity.MehburityMod;
import com.mehbur.mehburity.config.MehburityConfig;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * What Mehburity mined and has not handed over yet. Saved on the owner (not on the entity, which is re-created
 * on every login), and kept when the owner dies, so nothing mined is ever lost.
 */
public final class MiningBag {
	private static final AttachmentType<List<ItemStack>> BAG = AttachmentRegistry.create(MehburityMod.id("mining_bag"),
			builder -> builder.persistent(ItemStack.CODEC.listOf().xmap(List::copyOf, List::copyOf))
					.initializer(List::of)
					.copyOnDeath());

	private MiningBag() {
	}

	/** Forces class loading so the attachment type is registered during mod init. */
	public static void register() {
	}

	/** A read-only copy of the bag. */
	public static List<ItemStack> contents(ServerPlayer owner) {
		return owner.getAttachedOrCreate(BAG).stream().map(ItemStack::copy).toList();
	}

	public static boolean isEmpty(ServerPlayer owner) {
		return owner.getAttachedOrCreate(BAG).isEmpty();
	}

	/** Every slot is taken: a new kind of item would not fit, so mining stops here. */
	public static boolean isFull(ServerPlayer owner) {
		return owner.getAttachedOrCreate(BAG).size() >= slots();
	}

	/** Puts {@code stack} in the bag, filling existing stacks first. Returns what did not fit (maybe empty). */
	public static ItemStack add(ServerPlayer owner, ItemStack stack) {
		List<ItemStack> bag = new ArrayList<>(owner.getAttachedOrCreate(BAG).stream().map(ItemStack::copy).toList());
		ItemStack rest = stack.copy();
		for (ItemStack held : bag) {
			if (rest.isEmpty()) {
				break;
			}
			if (ItemStack.isSameItemSameComponents(held, rest)) {
				int moved = Math.min(rest.getCount(), held.getMaxStackSize() - held.getCount());
				held.grow(moved);
				rest.shrink(moved);
			}
		}
		while (!rest.isEmpty() && bag.size() < slots()) {
			bag.add(rest.split(rest.getMaxStackSize()));
		}
		owner.setAttached(BAG, List.copyOf(bag));
		return rest;
	}

	/** Empties the bag and returns everything that was in it. */
	public static List<ItemStack> takeAll(ServerPlayer owner) {
		List<ItemStack> bag = contents(owner);
		owner.setAttached(BAG, List.of());
		return bag;
	}

	/** "12x Raw Iron, 3x Diamond" (counts merged across stacks), or "" when empty. */
	public static String describe(ServerPlayer owner) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (ItemStack stack : owner.getAttachedOrCreate(BAG)) {
			counts.merge(stack.getHoverName().getString(), stack.getCount(), Integer::sum);
		}
		return counts.entrySet().stream()
				.map(entry -> entry.getValue() + "x " + entry.getKey())
				.collect(Collectors.joining(", "));
	}

	private static int slots() {
		return MehburityConfig.get().mining.bagSlots;
	}
}
