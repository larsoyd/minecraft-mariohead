package com.larsoyd.mariohead.playtest;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.larsoyd.mariohead.playtest.EscapePolicy.*;

class EscapePolicyTest {
	@Test
	void runsAwayInsteadOfIntoTheHead() {
		Route toward = new Route(0, 6, 0, -5, false);
		Route away = new Route(180, 6, 0, 5, false);
		assertSame(away, choose(List.of(toward, away), 0));
	}

	@Test
	void takesDetourInsteadOfRunningIntoWallOrOffCliff() {
		Route wall = new Route(0, 0, 0, 6, false);
		Route cliff = new Route(20, 6, 100, 6, false);
		Route detour = new Route(90, 5, 0, 1, false);
		assertSame(detour, choose(List.of(wall, cliff, detour), 0));
	}

	@Test
	void avoidsOscillationWhenRoutesAreEquivalent() {
		Route left = new Route(-45, 6, 0, 3, false);
		Route right = new Route(45, 6, 0, 3, false);
		assertSame(right, choose(List.of(left, right), 44));
		assertSame(left, choose(List.of(left, right), 315));
	}

	@Test
	void randomizedSafeRoutesAlwaysBeatLethalDrop() {
		Random random = new Random(174);
		for (int i = 0; i < 1000; i++) {
			Route safe = new Route(random.nextDouble() * 360, 4, 0, random.nextDouble() * 6 - 3, false);
			Route drop = new Route(random.nextDouble() * 360, 6, 100, 6, false);
			assertSame(safe, choose(List.of(drop, safe), random.nextDouble() * 360));
		}
	}

	@Test
	void boxedInReturnsNoRouteInsteadOfHoldingForward() {
		assertNull(choose(List.of(new Route(0, 0, 0, 4, false)), 0));
		assertNull(choose(List.of(), 0));
	}
}
