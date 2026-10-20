package com.mehbur.mehburity.guide;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Leading the owner to a structure: where it is (only x and z matter) and in which dimension. The goal keeps a little
 * state here too, so a fight that pauses the guiding does not reset it.
 */
public final class GuideJob {
	private final Destination destination;
	private final BlockPos target;
	private final ResourceKey<Level> dimension;
	/** Ticks spent waiting for the owner to catch up. */
	int waitTicks;
	/** 0 straight at the target; otherwise the n-th way round an obstacle. */
	int detour;

	public GuideJob(Destination destination, BlockPos target, ResourceKey<Level> dimension) {
		this.destination = destination;
		this.target = target;
		this.dimension = dimension;
	}

	public Destination destination() {
		return destination;
	}

	public BlockPos target() {
		return target;
	}

	public ResourceKey<Level> dimension() {
		return dimension;
	}
}
