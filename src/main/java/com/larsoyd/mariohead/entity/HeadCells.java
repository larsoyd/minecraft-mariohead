package com.larsoyd.mariohead.entity;

import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

// a bounded search through the block cells his smallest box fits, from the player out to him
final class HeadCells {
	enum Kind { FOUND, SEALED, OPEN }

	// the cells run from him to the player, sealed means all the player can reach came up short of him
	record Route(Kind kind, List<BlockPos> cells, int searched) {
	}

	private HeadCells() {
	}

	static Route search(Entity head, BlockPos from, AABB him, int limit) {
		Level level = head.level();
		AABB goal = him.inflate(0.5);
		BlockPos centre = BlockPos.containing(him.getCenter());
		Long2IntOpenHashMap cost = new Long2IntOpenHashMap();
		Long2LongOpenHashMap parent = new Long2LongOpenHashMap();
		Long2BooleanOpenHashMap open = new Long2BooleanOpenHashMap();
		// cell, cost plus estimate, estimate, so ties go to the cell nearer him
		PriorityQueue<long[]> queue = new PriorityQueue<>((a, b) -> a[1] != b[1] ? Long.compare(a[1], b[1]) : Long.compare(a[2], b[2]));
		long start = from.asLong();
		cost.put(start, 0);
		queue.add(new long[] { start, estimate(from, centre), estimate(from, centre) });
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int searched = 0;
		while (!queue.isEmpty()) {
			long[] top = queue.poll();
			pos.set(top[0]);
			int g = cost.get(top[0]);
			if (top[1] > g + top[2]) {
				continue;
			}
			if (goal.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) {
				return new Route(Kind.FOUND, trace(parent, top[0], start), searched);
			}
			if (++searched > limit) {
				return new Route(Kind.OPEN, List.of(), searched);
			}
			for (Direction d : Direction.values()) {
				BlockPos next = pos.relative(d);
				long key = next.asLong();
				if (cost.containsKey(key) && cost.get(key) <= g + 1 || !open.computeIfAbsent(key, k -> fits(level, head, next))) {
					continue;
				}
				cost.put(key, g + 1);
				parent.put(key, top[0]);
				int h = estimate(next, centre);
				queue.add(new long[] { key, g + 1 + h, h });
			}
		}
		return new Route(Kind.SEALED, List.of(), searched);
	}

	private static boolean fits(Level level, Entity head, BlockPos cell) {
		return !level.isOutsideBuildHeight(cell)
			&& level.noBlockCollision(head, HeadRoom.box(cell.getX() + 0.5, cell.getY() + 0.5, cell.getZ() + 0.5, HeadTuning.MIN_SQUEEZE));
	}

	private static int estimate(BlockPos a, BlockPos b) {
		return a.distManhattan(b);
	}

	private static List<BlockPos> trace(Long2LongOpenHashMap parent, long end, long start) {
		List<BlockPos> cells = new ArrayList<>();
		for (long cell = end; ; cell = parent.get(cell)) {
			cells.add(BlockPos.of(cell));
			if (cell == start) {
				return cells;
			}
		}
	}
}
