package com.larsoyd.mariohead.playtest;

import java.util.List;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Collections;
import java.util.function.ToDoubleFunction;

public final class EscapePlanner {
	public record Point(int x, double y, int z) {}
	public interface Terrain {
		Point step(Point from, int dx, int dz);
	}
	private record Node(Point point, Node parent, int distance) {}
	private static final int[][] STEPS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

	public static List<Point> plan(Point start, Terrain terrain, ToDoubleFunction<Point> score) {
		var queue = new ArrayDeque<Node>();
		var visited = new HashSet<Point>();
		Node best = new Node(start, null, 0);
		double bestScore = score.applyAsDouble(start);
		queue.add(best);
		visited.add(start);
		for (int count = 0; count < 2400 && !queue.isEmpty(); count++) {
			Node from = queue.removeFirst();
			for (int[] step : STEPS) {
				int x = from.point().x() + step[0] - start.x();
				int z = from.point().z() + step[1] - start.z();
				if (x * x + z * z > 24 * 24) continue;
				Point point = terrain.step(from.point(), step[0], step[1]);
				if (point == null || !visited.add(point)) continue;
				Node next = new Node(point, from, from.distance() + 1);
				queue.addLast(next);
				double value = score.applyAsDouble(point) - next.distance() * 0.6;
				if (value > bestScore) {
					bestScore = value;
					best = next;
				}
			}
		}
		var path = new ArrayList<Point>();
		for (Node node = best; node.parent() != null; node = node.parent()) path.add(node.point());
		Collections.reverse(path);
		return path;
	}
}
