package com.mehbur.mehburity.entity;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** What Mehburity does when it has nothing else to do. */
public enum CompanionMode implements StringRepresentable {
	/** Rolls after its owner and teleports to them when left behind. */
	FOLLOW("follow"),
	/** Waits where it was told to wait. */
	STAY("stay"),
	/** In its owner's inventory as an item ({@link MehburityItem}); not in the world until let out again. */
	CARRIED("carried"),
	/**
	 * Vanished after a player drove it into a rage; not in the world until it comes back for revenge
	 * ({@link com.mehbur.mehburity.anger.Revenge}).
	 */
	GONE("gone");

	public static final Codec<CompanionMode> CODEC = StringRepresentable.fromEnum(CompanionMode::values);

	private final String name;

	CompanionMode(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return name;
	}
}
