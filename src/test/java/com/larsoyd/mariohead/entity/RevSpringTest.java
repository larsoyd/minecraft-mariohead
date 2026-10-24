package com.larsoyd.mariohead.entity;

import static com.larsoyd.mariohead.entity.HeadTuning.SAG;
import static com.larsoyd.mariohead.entity.HeadTuning.SPEED_MAX;
import static com.larsoyd.mariohead.entity.HeadTuning.SPEED_START;
import static com.larsoyd.mariohead.entity.HeadTuning.SPEED_STEP;
import static com.larsoyd.mariohead.entity.HeadTuning.SPRINT_SPEED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RevSpringTest {
	private static final double EPS = 1e-9;

	@Test
	void firstHumStartsBelowASprint() {
		assertEquals(SPRINT_SPEED * 0.75, SPEED_START, EPS);
		assertEquals(SPEED_START, RevSpring.peak(0), EPS);
		assertTrue(RevSpring.peak(3) < SPRINT_SPEED, "the first four revs are outrunnable");
		assertTrue(RevSpring.peak(4) > SPRINT_SPEED, "the fifth rev outruns a sprint");
	}

	@Test
	void eachRevIsFasterUntilTheCeiling() {
		for (int rev = 0; rev < 11; rev++) {
			assertEquals(SPEED_STEP, RevSpring.peak(rev + 1) - RevSpring.peak(rev), EPS, "rev " + rev);
		}
		assertEquals(SPEED_MAX, RevSpring.peak(12), EPS);
		assertEquals(SPEED_MAX, RevSpring.peak(500), EPS);
		assertEquals(SPRINT_SPEED * 1.625, SPEED_MAX, EPS);
	}

	@Test
	void speedSagsOverTheHum() {
		assertEquals(RevSpring.peak(2), RevSpring.speed(2, 0.0, 0.0), EPS);
		assertEquals(RevSpring.peak(2) * (1 - SAG * 0.5), RevSpring.speed(2, 0.5, 0.0), EPS);
		assertEquals(RevSpring.peak(2) * 0.55, RevSpring.speed(2, 1.0, 0.0), EPS);
	}

	@Test
	void breathBeforeTheFirstHumIsTheSlowest() {
		assertEquals((SPEED_START - SPEED_STEP) * 0.55, RevSpring.speed(-1, 1.0, 0.0), EPS);
	}

	@Test
	void paceOnlyCountsFromTheFifthRev() {
		assertEquals(RevSpring.peak(3), RevSpring.speed(3, 0.0, SPEED_MAX), EPS);
		double pace = SPEED_STEP * 20;
		assertEquals(pace * 1.02, RevSpring.speed(4, 0.0, pace), EPS);
		assertEquals(pace * 1.02 * 0.55, RevSpring.speed(4, 1.0, pace), EPS);
	}

	@Test
	void paceIsClamped() {
		assertEquals(SPEED_MAX, RevSpring.speed(4, 0.0, 10.0), EPS);
		assertEquals(RevSpring.peak(4), RevSpring.speed(4, 0.0, 0.01), EPS);
	}
}
