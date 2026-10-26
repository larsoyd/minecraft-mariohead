package com.larsoyd.mariohead.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.larsoyd.mariohead.entity.StallGrace.Stage.*;

class StallGraceTest {
	@Test
	void finishingFirstGracePermanentlyEndsIntroductoryGrowth() {
		StallGrace grace = new StallGrace();
		grace.finish();
		assertFalse(grace.completed());
		grace.start(0, 0.4, 1, 0);
		assertFalse(grace.completed());
		grace.tick(1800);
		assertTrue(grace.completed());
		grace.start(2000, 0.4, 1, 0);
		assertTrue(grace.completed());
		grace.end(true);
		grace.finish();
		assertTrue(grace.completed());
	}

	@Test
	void laterGraceShrinksToSixtyThenFortyFiveSecondsWithoutResetting() {
		StallGrace grace = new StallGrace();
		long now = 100;
		for (int duration : new int[] {1800, 1200, 900, 900, 900}) {
			grace.start(now, 0.4, 1, 0);
			assertFalse(grace.tick(now + duration - 1));
			assertTrue(grace.tick(now + duration), "wrong repeated grace duration " + duration);
			now += duration + 200;
		}
	}

	@Test
	void laterWithdrawalStillStartsWithTimeToLeaveEarshot() {
		StallGrace grace = new StallGrace();
		for (int duration : new int[] {1800, 1200, 900, 900}) {
			grace.start(0, 0.4, 0, 0.999999);
			grace.tick(duration - 500);
			assertEquals(WITHDRAW, grace.stage(), "withdrawal missed shorter grace window");
			assertTrue(grace.tick(duration));
		}
	}

	@Test
	void followsForExactlyNinetySecondsAfterStarting() {
		StallGrace grace = new StallGrace();
		grace.start(500, 0.4, 0.1, 0);
		assertEquals(FOLLOW, grace.stage());
		assertFalse(grace.tick(2299));
		assertEquals(FOLLOW, grace.stage());
		assertTrue(grace.tick(2300));
		assertEquals(NONE, grace.stage());
		assertFalse(grace.tick(2301));
	}

	@Test
	void distanceVariesSmoothlyWithoutApproachingContact() {
		StallGrace grace = new StallGrace();
		grace.start(0, 0.3, 1, 0);
		double min = 100, max = 0, previous = grace.gap(0);
		for (int tick = 1; tick < 1800; tick++) {
			double gap = grace.gap(tick);
			assertTrue(gap >= 11 && gap <= 22, "unsafe or distant gap " + gap);
			assertTrue(Math.abs(gap - previous) < 0.05, "abrupt change " + tick);
			min = Math.min(min, gap); max = Math.max(max, gap); previous = gap;
		}
		assertTrue(max - min > 4, "distance never varied");
	}

	@Test
	void followsRunnerWithoutDistanceBasedSpeedBoostOrFastRunnerMatching() {
		StallGrace grace = new StallGrace();
		grace.start(0, 0.5, 1, 0);
		for (double pace : new double[] {0.21, 0.2806, 0.356, 0.6, 1.5}) {
			for (double gap : new double[] {25, 50, 100, 1000}) {
				double speed = grace.speed(100, gap, pace);
				assertTrue(speed > 0.15, "head abandoned moving runner");
				assertTrue(speed <= Math.min(0.36, pace), "rubber-band speed " + speed + " pace=" + pace);
			}
		}
	}

	@Test
	void letsCloseRunnerOpenGapAndStopsShortOfStationaryPlayer() {
		StallGrace grace = new StallGrace();
		grace.start(0, 0.5, 1, 0);
		assertTrue(grace.speed(100, 4, 0.356) < 0.15);
		assertEquals(0, grace.speed(100, 8, 0));
		assertTrue(grace.speed(100, 40, 0) > 0);
		double gap = 4;
		for (int tick = 0; tick < 1800; tick++) gap += 0.356 - grace.speed(tick, gap, 0.356);
		assertTrue(gap > 12 && gap < 35, "runner gap " + gap);
	}

	@Test
	void approachingEndsGraceAndCannotReenableItByTurningAway() {
		StallGrace grace = new StallGrace();
		grace.start(0, 0, 1, 0);
		assertFalse(grace.approached(15, -0.3));
		assertFalse(grace.approached(40, 0.3));
		assertTrue(grace.approached(15, 0.3));
		grace.end(true);
		assertEquals(ENDING, grace.stage());
		assertTrue(grace.retreating());
		assertFalse(grace.tick(3000), "cutoff speech was canceled by grace deadline");
		assertFalse(grace.approached(15, -0.3));
		assertEquals(ENDING, grace.stage());
		grace.finish();
		assertEquals(NONE, grace.stage());
	}

	@Test
	void tenPercentWithdrawOnceBeforeStartingStalk() {
		for (double roll : new double[] {0, 0.099999, 0.1, 0.9}) {
			StallGrace grace = new StallGrace();
			grace.start(0, 0.4, roll, 0.5);
			for (int tick = 0; tick < 1800; tick++) {
				grace.tick(tick);
				if (grace.stage() == WITHDRAW) break;
			}
			assertEquals(roll < 0.1 ? WITHDRAW : FOLLOW, grace.stage());
			if (roll < 0.1) {
				grace.stalk();
				assertEquals(STALK, grace.stage());
				assertFalse(grace.tick(2400), "grace deadline interrupted stalk");
				grace.finish();
				assertEquals(NONE, grace.stage());
			}
		}
	}

	@Test
	void blockedWithdrawalCannotHoldGraceForeverAndExternalDisableCancelsIt() {
		StallGrace grace = new StallGrace();
		grace.start(0, 0, 0, 0);
		grace.tick(1500);
		assertEquals(WITHDRAW, grace.stage());
		assertTrue(grace.tick(1800));
		grace.start(2000, 0, 0, 0);
		grace.tick(3500);
		grace.end(false);
		assertEquals(ENDING, grace.stage());
		assertFalse(grace.retreating());
	}
}
