package com.larsoyd.mariohead.playtest;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.larsoyd.mariohead.playtest.EscapePlanner.*;

class EscapePlannerTest {
	@Test
	void findsWayAroundWallWithClosedEndBehindIt() {
		var path = plan(new Point(0, 0, 0), (from, dx, dz) -> {
			int x = from.x() + dx, z = from.z() + dz;
			return z < -15 || x >= 12 && x <= 13 && z <= 11 ? null : new Point(x, 0, z);
		}, point -> Math.hypot(point.x() + 20, point.z()) * 3);
		assertFalse(path.isEmpty(), "no escape route");
		assertTrue(path.getLast().x() > 13, "did not get beyond wall: " + path);
		assertTrue(path.stream().anyMatch(p -> p.z() > 11), "did not detour round open end");
	}

	@Test
	void canTemporarilyGetCloserToThreatToEscapeDeadEnd() {
		var path = plan(new Point(0, 0, 0), (from, dx, dz) -> {
			int x = from.x() + dx, z = from.z() + dz;
			if (x >= -4 && Math.abs(z) == 2 || x == 2 && Math.abs(z) <= 2) return null;
			return new Point(x, 0, z);
		}, point -> Math.hypot(point.x() + 10, point.z()) * 3);
		assertFalse(path.isEmpty(), "no route out of dead end");
		assertTrue(path.stream().anyMatch(p -> p.x() < -4), "did not backtrack out of trap");
		assertTrue(path.getLast().x() > 2, "never escaped trap");
	}

	@Test
	void blockedPlayerStopsAndSearchRemainsBounded() {
		assertTrue(plan(new Point(0, 0, 0), (from, dx, dz) -> null, point -> point.x()).isEmpty());
		int[] calls = {0};
		var path = plan(new Point(0, 0, 0), (from, dx, dz) -> {
			calls[0]++;
			return new Point(from.x() + dx, 0, from.z() + dz);
		}, point -> point.x());
		assertFalse(path.isEmpty());
		assertTrue(calls[0] <= 20000, "unbounded terrain work: " + calls[0]);
		assertTrue(path.getLast().x() <= 24);
	}
}
