package com.larsoyd.mariohead.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class HeadHouseRoute {
	private record Step(BlockPos cell, double score) {
	}

	private HeadHouseRoute() {
	}

	static List<Vec3> approach(TheHead head, List<HeadShelter.Opening> openings, Vec3 eyes, double floor) {
		List<Vec3> goals = new ArrayList<>();
		for (var opening : openings) {
			Vec3 wall = Vec3.atCenterOf(opening.wall());
			Vec3 outward = Vec3.atLowerCornerOf(opening.outward().getUnitVec3i());
			Vec3 sight = wall.subtract(eyes).multiply(1, 0, 1);
			double depth = sight.dot(outward);
			if (depth < 0.1) continue;
			Vec3 across = new Vec3(outward.z, 0, -outward.x);
			for (double offset : new double[] { 0, -0.25, 0.25 }) {
				Vec3 aperture = wall.add(across.scale(offset));
				Vec3 aligned = aperture.subtract(eyes).multiply(1, 0, 1).scale(1 / depth);
				for (Vec3 direction : offset == 0 ? List.of(aligned, outward) : List.of(aligned)) {
					for (double extra = 0.65; extra <= 3.65; extra += 1) {
						Vec3 goal = aperture.add(direction.scale(head.getBbWidth() / 2 + extra));
						goal = new Vec3(goal.x, Math.max(floor + head.getBbHeight() / 2 + 0.1, wall.y), goal.z);
						if (goal.distanceTo(wall) <= 12 && Math.abs(goal.y - floor) < 3.5
							&& head.level().noCollision(head, HeadRoom.box(goal.x, goal.y, goal.z, head.squeeze()).deflate(1e-6))) {
							goals.add(goal);
							break;
						}
					}
				}
			}
		}
		goals.sort(Comparator.comparing((Vec3 goal) -> !throughWindow(head, eyes, goal, openings))
			.thenComparingDouble(head.centre()::distanceToSqr));
		for (Vec3 goal : goals) {
			List<Vec3> route = path(head.centre(), goal, (a, b) -> head.level().noCollision(head, HeadRoom.box(a.x, a.y, a.z, head.squeeze()).minmax(HeadRoom.box(b.x, b.y, b.z, head.squeeze())).deflate(1e-6)), 4096);
			if (!route.isEmpty()) return route;
		}
		return List.of();
	}

	private static boolean throughWindow(TheHead head, Vec3 eyes, Vec3 goal, List<HeadShelter.Opening> openings) {
		BlockPos hit = head.level().clip(new net.minecraft.world.level.ClipContext(eyes, goal,
			net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, head)).getBlockPos();
		return openings.stream().anyMatch(opening -> opening.wall().equals(hit));
	}

	static Vec3 chestView(TheHead head, BlockPos chest) {
		Vec3 centre = Vec3.atCenterOf(chest);
		double y = Math.clamp(head.centre().y, centre.y - 1, centre.y + 1);
		double reach = head.getBbWidth() / 2 + 0.65;
		return List.of(new Vec3(centre.x - reach, y, centre.z), new Vec3(centre.x + reach, y, centre.z),
			new Vec3(centre.x, y, centre.z - reach), new Vec3(centre.x, y, centre.z + reach)).stream()
			.min(Comparator.comparingDouble(head.centre()::distanceToSqr)).orElseThrow();
	}

	static Vec3 clearOfChests(TheHead head, Vec3 goal, List<BlockPos> chests) {
		double half = head.getBbWidth() / 2 + 0.08;
		double height = head.getBbHeight() / 2 + 0.08;
		List<AABB> boxes = chests.stream().map(pos -> new AABB(pos).inflate(half, height, half)).toList();
		Vec3 start = head.centre();
		for (AABB box : boxes) {
			if (!box.contains(goal)) continue;
			goal = new Vec3(goal.x, box.maxY + 0.1, goal.z);
		}
		BiPredicate<Vec3, Vec3> clear = (a, b) -> boxes.stream().noneMatch(box -> box.contains(b) || box.clip(a, b).isPresent());
		if (clear.test(start, goal)) return goal;
		List<Vec3> route = path(start, goal, clear, 1024);
		return route.isEmpty() ? start : route.getFirst();
	}

	private static List<Vec3> path(Vec3 start, Vec3 goal, BiPredicate<Vec3, Vec3> clear, int limit) {
		if (clear.test(start, goal)) return List.of(goal);
		var cost = new HashMap<BlockPos, Double>();
		var parents = new HashMap<BlockPos, BlockPos>();
		var visited = new java.util.HashSet<BlockPos>();
		var queue = new PriorityQueue<Step>(Comparator.comparingDouble(Step::score));
		cost.put(BlockPos.ZERO, 0.0);
		queue.add(new Step(BlockPos.ZERO, start.distanceTo(goal)));
		for (int searched = 0; !queue.isEmpty() && searched < limit; searched++) {
			BlockPos cell = queue.remove().cell();
			if (!visited.add(cell)) continue;
			Vec3 at = start.add(cell.getX(), cell.getY(), cell.getZ());
			if (at.distanceTo(goal) < 3 && clear.test(at, goal)) {
				List<Vec3> route = new ArrayList<>();
				route.add(goal);
				for (BlockPos p = cell; !p.equals(BlockPos.ZERO); p = parents.get(p)) route.addFirst(start.add(p.getX(), p.getY(), p.getZ()));
				List<Vec3> smooth = new ArrayList<>();
				Vec3 from = start;
				for (int i = 0; i < route.size();) {
					int next = route.size() - 1;
					while (next > i && !clear.test(from, route.get(next))) next--;
					from = route.get(next);
					smooth.add(from);
					i = next + 1;
				}
				return smooth;
			}
			for (Direction dir : Direction.values()) {
				BlockPos next = cell.relative(dir);
				Vec3 point = start.add(next.getX(), next.getY(), next.getZ());
				double distance = cost.get(cell) + 1;
				if (point.y < Math.min(start.y, goal.y) - 0.01 || next.distManhattan(BlockPos.ZERO) > 48 || cost.getOrDefault(next, Double.MAX_VALUE) <= distance || !clear.test(at, point)) continue;
				cost.put(next, distance);
				parents.put(next, cell);
				Vec3 remaining = goal.subtract(point);
				queue.add(new Step(next, distance + Math.abs(remaining.x) + Math.abs(remaining.y) + Math.abs(remaining.z)));
			}
		}
		return List.of();
	}
}
