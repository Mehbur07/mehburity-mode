package com.mehbur.mehburity.mining;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What Mehburity was asked to mine: every ore around ({@link Kind#ORES}), one block the owner pointed at
 * ({@link Kind#SINGLE}) or the logs of one tree ({@link Kind#TREE}, found when the job starts). Lives on the entity only; logging out or changing dimension ends it.
 */
public final class MiningJob {
	public enum Kind { ORES, SINGLE, TREE }

	/** Why a job ended; each has its own line in {@link Mining}. */
	public enum End { NOTHING_LEFT, BAG_FULL, TRIP_LIMIT, DONE, TREE_DONE, UNREACHABLE, STOPPED }

	private final Kind kind;
	private final @Nullable BlockPos single;
	private final List<BlockPos> treeLogs;
	/** Blocks that could not be reached or were protected; never tried again in this job. */
	private final Set<BlockPos> skipped = new HashSet<>();
	private int mined;

	private MiningJob(Kind kind, @Nullable BlockPos single, List<BlockPos> treeLogs) {
		this.kind = kind;
		this.single = single;
		this.treeLogs = treeLogs;
	}

	public static MiningJob ores() {
		return new MiningJob(Kind.ORES, null, List.of());
	}

	public static MiningJob single(BlockPos pos) {
		return new MiningJob(Kind.SINGLE, pos.immutable(), List.of());
	}

	public static MiningJob tree(Collection<BlockPos> logs) {
		return new MiningJob(Kind.TREE, null, logs.stream().map(BlockPos::immutable).toList());
	}

	/** The tree's logs as found at the start (some may be gone already). */
	public List<BlockPos> treeLogs() {
		return treeLogs;
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
