package com.mehbur.mehburity.mining;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * What Mehburity was asked to mine: every ore around ({@link Kind#ORES}) or one block the owner pointed at
 * ({@link Kind#SINGLE}). Lives on the entity only; logging out or changing dimension ends it.
 */
public final class MiningJob {
	public enum Kind { ORES, SINGLE }

	/** Why a job ended; each has its own line in {@link Mining}. */
	public enum End { NOTHING_LEFT, BAG_FULL, TRIP_LIMIT, DONE, UNREACHABLE, STOPPED }

	private final Kind kind;
	private final @Nullable BlockPos single;
	/** Blocks that could not be reached or were protected; never tried again in this job. */
	private final Set<BlockPos> skipped = new HashSet<>();
	private int mined;

	private MiningJob(Kind kind, @Nullable BlockPos single) {
		this.kind = kind;
		this.single = single;
	}

	public static MiningJob ores() {
		return new MiningJob(Kind.ORES, null);
	}

	public static MiningJob single(BlockPos pos) {
		return new MiningJob(Kind.SINGLE, pos.immutable());
	}

	public Kind kind() {
		return kind;
	}

	public @Nullable BlockPos single() {
		return single;
	}

	public int mined() {
		return mined;
	}

	void countMined() {
		mined++;
	}

	void skip(BlockPos pos) {
		skipped.add(pos.immutable());
	}

	boolean isSkipped(BlockPos pos) {
		return skipped.contains(pos);
	}
}
