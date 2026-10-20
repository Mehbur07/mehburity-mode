package com.mehbur.mehburity.guide;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Finds a way a player can walk, the way Baritone does it: A* over the blocks with terrain costs, one segment at a
 * time. A spot is walkable when a player fits in it (two blocks of room, no hazards) on top of solid ground, or swims at
 * the water surface. Steps go to the eight neighbours: on the level, one block up (a jump) or up to three down, never
 * cutting a corner. Leaves, fences, walls, cobwebs, lava, fire, cactus, magma and the like are never on the way;
 * swimming costs more, village paths cost less.
 *
 * <p>The search only looks at loaded terrain within {@link #RADIUS} blocks, and runs a slice per tick
 * ({@link #step}). It ends at the target, at the first spot {@link #SEGMENT} blocks away (on open land that takes a
 * few ticks), or (out of budget, out of loaded terrain) with a partial route to the spot it reached closest to the
 * target; the next segment goes on from there. Dead ends cost nothing to back out of:
 * A* keeps every open alternative.
 */
public final class RoutePlanner {
	/** Close enough to the target (horizontally) to end the route. */
	public static final double REACH = 6.0;
	private static final int RADIUS = 160;
	/** A segment ends once it is this far from where it started; the next one goes on from there. */
	private static final double SEGMENT = 64.0;
	private static final int EXPANSIONS_PER_TICK = 1500;
	private static final int MAX_EXPANSIONS = 40000;
	/** A partial route must get at least this far from its start (else it found no way at all). */
	private static final double MIN_PROGRESS = 5.0;
	/** Weighted A*: a little greedy towards the target, which saves a lot of search on open land. */
	private static final double HEURISTIC_WEIGHT = 1.15;
	private static final int MAX_DROP = 3;
	private static final double SWIM_COST = 3.0;
	private static final double PATH_BLOCK_COST = 0.6;
	private static final double JUMP_COST = 0.6;
	private static final double DROP_COST = 0.3;

	private static final byte UNKNOWN = 0;
	private static final byte NO = 1;
	private static final byte WALK = 2;
	private static final byte SWIM = 3;
	private static final int[][] NEIGHBOURS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

	/** A finished search: the feet positions to walk through, and whether they reach the target. */
	public record Route(List<BlockPos> steps, boolean complete) {
	}

	private static final class Step {
		final long pos;
		final double g;
		final double h;
		final @Nullable Step parent;

		Step(long pos, double g, double h, @Nullable Step parent) {
			this.pos = pos;
			this.g = g;
			this.h = h;
			this.parent = parent;
		}

		double f() {
			return g + h * HEURISTIC_WEIGHT;
		}
	}

	private final ServerLevel level;
	private final BlockPos start;
	private final BlockPos target;
	private final double reach;
	private final PriorityQueue<Step> open = new PriorityQueue<>((a, b) -> Double.compare(a.f(), b.f()));
	private final Long2ObjectOpenHashMap<Step> best = new Long2ObjectOpenHashMap<>();
	private final Long2ByteOpenHashMap kinds = new Long2ByteOpenHashMap();
	private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
	private Step closest;
	private int expansions;
	private @Nullable Route result;
	private boolean done;

	/**
	 * Plans from {@code from} (a walkable feet position, see {@link #walkableNear}) towards {@code target}; only x and z
	 * of the target matter.
	 */
	public RoutePlanner(ServerLevel level, BlockPos from, BlockPos target) {
		this(level, from, target, REACH);
	}

	/** The same, done once within {@code reach} blocks of the target (horizontally). */
	public RoutePlanner(ServerLevel level, BlockPos from, BlockPos target, double reach) {
		this.level = level;
		this.start = from;
		this.target = target;
		this.reach = reach;
		Step first = new Step(from.asLong(), 0.0, horizontal(from), null);
		open.add(first);
		best.put(first.pos, first);
		closest = first;
	}

	public boolean isDone() {
		return done;
	}

	/** The route once {@link #isDone()}: null when there is no way at all from here. */
	public @Nullable Route result() {
		return result;
	}

	/** Searches one slice; true when the search is over. */
	public boolean step() {
		if (done) {
			return true;
		}
		int budget = EXPANSIONS_PER_TICK;
		while (budget-- > 0) {
			Step current = open.poll();
			if (current == null) {
				return finish(closest, false);
			}
			if (best.get(current.pos) != current) {
				continue; // a cheaper way here was found after this one was queued
			}
			if (current.h <= reach) {
				return finish(current, true);
			}
			if (fromStart(current.pos) >= SEGMENT) {
				return finish(current, false);
			}
			if (current.h < closest.h - 0.5 || (Math.abs(current.h - closest.h) <= 0.5 && current.g < closest.g)) {
				closest = current;
			}
			if (++expansions >= MAX_EXPANSIONS) {
				return finish(closest, false);
			}
			expand(current);
		}
		return false;
	}

	private boolean finish(Step end, boolean complete) {
		done = true;
		BlockPos endPos = BlockPos.of(end.pos);
		if (!complete && Math.sqrt(endPos.distSqr(start)) < MIN_PROGRESS) {
			result = null;
			return true;
		}
		List<BlockPos> steps = new ArrayList<>();
		for (Step step = end; step != null; step = step.parent) {
			steps.add(BlockPos.of(step.pos));
		}
		Collections.reverse(steps);
		result = new Route(steps, complete);
		return true;
	}

	private void expand(Step current) {
		BlockPos at = BlockPos.of(current.pos);
		byte here = kind(at);
		for (int[] offset : NEIGHBOURS) {
			int dx = offset[0];
			int dz = offset[1];
			BlockPos next = neighbour(at, here, dx, dz);
			if (next == null) {
				continue;
			}
			if (dx != 0 && dz != 0 && !(clear(at.offset(dx, 0, 0), Math.max(at.getY(), next.getY()))
					&& clear(at.offset(0, 0, dz), Math.max(at.getY(), next.getY())))) {
				continue; // no cutting corners past a wall
			}
			double cost = (dx != 0 && dz != 0 ? Math.sqrt(2.0) : 1.0);
			int rise = next.getY() - at.getY();
			if (rise > 0) {
				cost += JUMP_COST;
			} else if (rise < 0) {
				cost += DROP_COST * -rise;
			}
			byte kind = kind(next);
			if (kind == SWIM || here == SWIM) {
				cost *= SWIM_COST;
			} else if (level.getBlockState(next.below()).is(Blocks.DIRT_PATH)) {
				cost *= PATH_BLOCK_COST;
			}
			double g = current.g + cost;
			long key = next.asLong();
			Step known = best.get(key);
			if (known != null && known.g <= g) {
				continue;
			}
			Step step = new Step(key, g, horizontal(next), current);
			best.put(key, step);
			open.add(step);
		}
	}

	/** Where a player gets to from {@code at} going one block (dx, dz): level, a jump up, or a drop down. */
	private @Nullable BlockPos neighbour(BlockPos at, byte here, int dx, int dz) {
		int x = at.getX() + dx;
		int z = at.getZ() + dz;
		if ((x - start.getX()) * (x - start.getX()) + (z - start.getZ()) * (z - start.getZ()) > RADIUS * RADIUS) {
			return null;
		}
		int y = at.getY();
		if (!level.isLoaded(cursor.set(x, y, z))) {
			return null;
		}
		if (kind(new BlockPos(x, y, z)) != NO) {
			return new BlockPos(x, y, z);
		}
		// A jump: room above the head where it stands, then a spot one block up.
		if (passable(at.above(2)) && kind(new BlockPos(x, y + 1, z)) != NO) {
			return new BlockPos(x, y + 1, z);
		}
		if (here == SWIM) {
			return null; // no diving
		}
		// A drop: the column in front is open down to a spot at most three blocks lower.
		if (!passable(new BlockPos(x, y, z)) || !passable(new BlockPos(x, y + 1, z))) {
			return null;
		}
		for (int down = 1; down <= MAX_DROP; down++) {
			BlockPos below = new BlockPos(x, y - down, z);
			if (kind(below) != NO) {
				return below;
			}
			if (!passable(below)) {
				return null;
			}
		}
		return null;
	}

	/** Feet and head room at {@code pos} (only x and z) at height {@code y}. */
	private boolean clear(BlockPos column, int y) {
		BlockPos feet = new BlockPos(column.getX(), y, column.getZ());
		return passable(feet) && passable(feet.above());
	}

	/** Whether a player can stand (or swim) with their feet at {@code feet}; remembered per block. */
	private byte kind(BlockPos feet) {
		long key = feet.asLong();
		byte known = kinds.get(key);
		if (known != UNKNOWN) {
			return known;
		}
		byte kind = computeKind(level, feet);
		kinds.put(key, kind);
		return kind;
	}

	private static byte computeKind(ServerLevel level, BlockPos feet) {
		if (!level.isLoaded(feet)) {
			return NO;
		}
		BlockState feetState = level.getBlockState(feet);
		BlockState headState = level.getBlockState(feet.above());
		if (!passable(level, feet, feetState) || !passable(level, feet.above(), headState)) {
			return NO;
		}
		FluidState feetFluid = feetState.getFluidState();
		if (feetFluid.is(FluidTags.WATER)) {
			// Swimming at the surface: the head is out of the water.
			return headState.getFluidState().isEmpty() ? SWIM : NO;
		}
		BlockPos floorPos = feet.below();
		BlockState floor = level.getBlockState(floorPos);
		return isFloor(level, floorPos, floor) ? WALK : NO;
	}

	/** A player's feet or head fit in this block: nothing to bump into and nothing that hurts or traps. */
	private boolean passable(BlockPos pos) {
		return level.isLoaded(pos) && passable(level, pos, level.getBlockState(pos));
	}

	private static boolean passable(ServerLevel level, BlockPos pos, BlockState state) {
		if (!state.getCollisionShape(level, pos).isEmpty()) {
			return false;
		}
		if (state.getFluidState().is(FluidTags.LAVA)) {
			return false;
		}
		return !(state.is(BlockTags.FIRE) || state.is(Blocks.COBWEB) || state.is(Blocks.POWDER_SNOW)
				|| state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE) || state.is(BlockTags.CAMPFIRES));
	}

	/** Solid ground a player can stand on: not a leaf, a fence or a wall (too tall to step on), nor a hazard. */
	private static boolean isFloor(ServerLevel level, BlockPos pos, BlockState floor) {
		VoxelShape shape = floor.getCollisionShape(level, pos);
		if (shape.isEmpty() || shape.max(Direction.Axis.Y) > 1.0) {
			return false;
		}
		return !(floor.is(BlockTags.LEAVES) || floor.is(Blocks.MAGMA_BLOCK) || floor.is(Blocks.CACTUS)
				|| floor.is(BlockTags.CAMPFIRES) || floor.is(Blocks.POWDER_SNOW));
	}

	/** A walkable feet position at or next to {@code around} (a little up or down), or null. */
	public static @Nullable BlockPos walkableNear(ServerLevel level, BlockPos around) {
		for (int radius = 0; radius <= 2; radius++) {
			for (int dy : new int[] {0, 1, -1, 2, -2}) {
				for (int dx = -radius; dx <= radius; dx++) {
					for (int dz = -radius; dz <= radius; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
							continue;
						}
						BlockPos pos = around.offset(dx, dy, dz);
						if (computeKind(level, pos) != NO) {
							return pos;
						}
					}
				}
			}
		}
		return null;
	}

	private double fromStart(long pos) {
		double dx = BlockPos.getX(pos) - start.getX();
		double dz = BlockPos.getZ(pos) - start.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	private double horizontal(BlockPos pos) {
		double dx = target.getX() + 0.5 - (pos.getX() + 0.5);
		double dz = target.getZ() + 0.5 - (pos.getZ() + 0.5);
		return Math.sqrt(dx * dx + dz * dz);
	}
}
