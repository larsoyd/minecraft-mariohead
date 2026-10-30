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
		boolean approached = false;
		for (int tick = 0; tick < 16; tick++) approached = grace.approached(15, 0.28);
		assertTrue(approached, "sprinting straight at him for 0.8 s did not count");
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
	void lookingBackMidRunNeverEndsGrace() {
		StallGrace grace = new StallGrace();
		grace.start(0, 0, 1, 0);
		for (int look = 0; look < 40; look++) {
			for (int tick = 0; tick < 12; tick++) assertFalse(grace.approached(15, 0.2), "look back ended grace look=" + look);
			for (int tick = 0; tick < 20; tick++) assertFalse(grace.approached(15, -0.28), "fleeing ended grace look=" + look);
		}
	}

	@Test
	void walkingAtHimEndsGraceButSneakingInBurstsDoesNot() {
		StallGrace walker = new StallGrace();
		walker.start(0, 0, 1, 0);
		boolean reached = false;
		for (int tick = 0; tick < 24 && !reached; tick++) reached = walker.approached(15, 0.216);
		assertTrue(reached, "walking at him for 1.2 s did not count");
		StallGrace pauser = new StallGrace();
		pauser.start(0, 0, 1, 0);
		for (int burst = 0; burst < 20; burst++) {
			for (int tick = 0; tick < 8; tick++) assertFalse(pauser.approached(15, 0.2), "short steps added up burst=" + burst);
			for (int tick = 0; tick < 200; tick++) assertFalse(pauser.approached(15, 0));
		}
	}

	@Test
	void runningAwayFirstBanksNoCreditAgainstARealApproach() {
		StallGrace grace = new StallGrace();
		grace.start(0, 0, 1, 0);
		for (int tick = 0; tick < 1000; tick++) assertFalse(grace.approached(15, -0.28));
		boolean approached = false;
		for (int tick = 0; tick < 16; tick++) approached = grace.approached(15, 0.28);
		assertTrue(approached, "long flight excused a real approach");
	}

	@Test
	void closingOutsideRangeAndBeforeARestartIsForgotten() {
		StallGrace grace = new StallGrace();
		grace.start(0, 0, 1, 0);
		for (int tick = 0; tick < 12; tick++) grace.approached(15, 0.28);
		assertFalse(grace.approached(30, 0.28));
		for (int tick = 0; tick < 12; tick++) assertFalse(grace.approached(15, 0.28), "distant closing was kept");
		grace.start(100, 0, 1, 0);
		for (int tick = 0; tick < 12; tick++) assertFalse(grace.approached(15, 0.28), "restart kept old closing");
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

	@Test
	void standingStillNearHimEndsFollowAfterTwoSeconds() {
		StallGrace grace = new StallGrace();
		grace.start(0, 0.4, 1, 0);
		grace.standing(15, 0, 64, 0);
		for (int tick = 1; tick < 40; tick++) assertFalse(grace.standing(15, 0, 64, 0), "ended early at " + tick);
		assertTrue(grace.standing(15, 0, 64, 0));
	}

	@Test
	void standingStillFarFromHimNeverCounts() {
		StallGrace grace = new StallGrace();
		grace.start(0, 0.4, 1, 0);
		for (int tick = 0; tick < 500; tick++) assertFalse(grace.standing(24, 0, 64, 0));
		for (int tick = 0; tick < 39; tick++) assertFalse(grace.standing(23.9, 0, 64, 0), "counted far ticks at " + tick);
		assertTrue(grace.standing(23.9, 0, 64, 0));
	}

	@Test
	void shufflingWhileChoppingStillCountsAsStanding() {
		StallGrace grace = new StallGrace();
		grace.start(0, 0.4, 1, 0);
		boolean ended = false;
		for (int tick = 0; tick <= 40; tick++) ended = grace.standing(15, tick % 2 * 0.8, 64 + (tick % 4 == 0 ? 1.2 : 0), 0.5);
		assertTrue(ended);
	}

	@Test
	void aPauseOnTheRunRestartsTheCount() {
		StallGrace grace = new StallGrace();
		grace.start(0, 0.4, 1, 0);
		for (int tick = 0; tick < 39; tick++) assertFalse(grace.standing(15, 0, 64, 0));
		assertFalse(grace.standing(15, 1.6, 64, 0));
		for (int tick = 0; tick < 39; tick++) assertFalse(grace.standing(15, 1.6, 64, 0));
		assertTrue(grace.standing(15, 1.6, 64, 0));
	}

	@Test
	void standingThroughTheStallLineEndsFollowOnceItBegins() {
		StallGrace grace = new StallGrace();
		for (int tick = 0; tick < 60; tick++) assertFalse(grace.standing(5, 0, 64, 0));
		grace.start(60, 0.4, 1, 0);
		assertTrue(grace.standing(5, 0, 64, 0));
	}

	@Test
	void standingNeverCutsAWithdrawalShort() {
		StallGrace grace = new StallGrace();
		grace.start(0, 0.4, 0, 0);
		grace.tick(400);
		assertEquals(WITHDRAW, grace.stage());
		for (int tick = 0; tick < 200; tick++) assertFalse(grace.standing(15, 0, 64, 0));
	}
}
