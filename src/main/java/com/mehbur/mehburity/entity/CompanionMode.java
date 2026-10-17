package com.mehbur.mehburity.entity;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** What Mehburity does when it has nothing else to do. */
public enum CompanionMode implements StringRepresentable {
	/** Flies after its owner and teleports to them when left behind. */
	FOLLOW("follow"),
	/** Hovers where it was told to wait. */
	STAY("stay"),
	/** In its owner's inventory as an item ({@link MehburityItem}); not in the world until let out again. */
	CARRIED("carried");

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
