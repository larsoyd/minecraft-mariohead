package com.larsoyd.mariohead.entity;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class WaterVisit {
	private WaterVisit() {}

	public static boolean different(BlockPos previous, BlockPos entry, ToIntFunction<BlockPos> water) {
		if (previous.distSqr(entry) >= 32 * 32) return true;
		if (previous.equals(entry) || water.applyAsInt(previous) != 1 || water.applyAsInt(entry) != 1) return false;
		Flood a = new Flood(previous);
		Flood b = new Flood(entry);
		for (int step = 0; step < 4096; step++) {
			if (a.expand(b, water) || b.expand(a, water)) return false;
			if (a.closed() || b.closed()) return true;
			if (a.queue.isEmpty() && b.queue.isEmpty()) return false;
		}
		return false;
	}

	private static final class Flood {
		private final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		private final Set<BlockPos> visited = new HashSet<>();
		private final Set<BlockPos> wet = new HashSet<>();
		private boolean unknown;

		private Flood(BlockPos start) {
			this.queue.add(start);
			this.visited.add(start);
			this.wet.add(start);
		}

		private boolean closed() { return this.queue.isEmpty() && !this.unknown; }

		private boolean expand(Flood other, ToIntFunction<BlockPos> water) {
			BlockPos pos = this.queue.poll();
			if (pos == null) return false;
			for (Direction direction : Direction.values()) {
				BlockPos next = pos.relative(direction);
				if (!this.visited.add(next)) continue;
				int state = water.applyAsInt(next);
				if (state < 0) this.unknown = true;
				if (state != 1) continue;
				if (other.wet.contains(next)) return true;
				this.wet.add(next);
				this.queue.add(next);
			}
			return false;
		}
	}
}
