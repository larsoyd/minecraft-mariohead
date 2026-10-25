package com.larsoyd.mariohead.entity;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DashChatterTest {
	@Test
	void chanceAndFiveSecondCooldownHaveExactBoundaries() {
		DashChatter chatter = new DashChatter();
		assertEquals(-1, chatter.choose(0, 0.2, 0));
		assertEquals(0, chatter.choose(0, 0.199999, 0));
		chatter.finishedAt(80);
		assertEquals(-1, chatter.choose(179, 0, 0));
		assertEquals(1, chatter.choose(180, 0, 0));
	}

	@Test
	void exhaustsEveryLineAndNeverRepeatsAcrossCycleBoundaries() {
		for (int seed = 0; seed < 100; seed++) {
			var random = new java.util.Random(seed);
			DashChatter chatter = new DashChatter();
			int last = -1;
			for (int cycle = 0; cycle < 30; cycle++) {
				var seen = new java.util.HashSet<Integer>();
				for (int line = 0; line < 4; line++) {
					int next = chatter.choose(cycle * 1000L + line * 300L, 0, random.nextInt(100));
					assertNotEquals(last, next);
					assertTrue(seen.add(next));
					last = next;
				}
			}
		}
	}
}
