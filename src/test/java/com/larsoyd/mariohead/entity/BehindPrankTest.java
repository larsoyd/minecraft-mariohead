package com.larsoyd.mariohead.entity;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BehindPrankTest {
	@Test
	void selectedGraceStalkStartsWithoutAnotherChanceOrEscapeTimer() {
		BehindPrank prank = new BehindPrank();
		assertTrue(prank.begin(500));
		assertEquals(BehindPrank.Stage.APPROACH, prank.stage());
		assertFalse(prank.expired(1099));
		assertTrue(prank.expired(1100));
		assertFalse(prank.begin(600));
		prank.finish();
		assertFalse(prank.begin(1200));
	}

	@Test
	void needsFiveContinuousSecondsAndStrictFiftyFivePercentRoll() {
		for (double roll : new double[] { 0, 0.549999, 0.55, 0.999999 }) {
			BehindPrank prank = new BehindPrank();
			for (long tick = 200; tick < 300; tick++) assertFalse(prank.observe(tick, true, roll));
			assertEquals(roll < 0.55, prank.observe(300, true, roll));
			assertEquals(roll < 0.55 ? BehindPrank.Stage.APPROACH : BehindPrank.Stage.WAITING, prank.stage());
			for (long tick = 301; tick < 1000; tick++) assertFalse(prank.observe(tick, true, 0));
		}
	}

	@Test
	void HearingOrSeeingResetsTheEscapeTimer() {
		BehindPrank prank = new BehindPrank();
		assertFalse(prank.observe(0, true, 0));
		assertFalse(prank.observe(99, false, 0));
		assertFalse(prank.observe(100, true, 0));
		assertFalse(prank.observe(199, true, 0));
		assertTrue(prank.observe(200, true, 0));
	}

	@Test
	void failedRollCannotBeRetriedAfterAnotherEscape() {
		BehindPrank prank = new BehindPrank();
		prank.observe(0, true, 1);
		assertFalse(prank.observe(100, true, 1));
		prank.observe(101, false, 0);
		prank.observe(102, true, 0);
		assertFalse(prank.observe(202, true, 0));
	}

	@Test
	void approachHasAFiniteDeadline() {
		BehindPrank prank = new BehindPrank();
		prank.observe(0, true, 0);
		assertTrue(prank.observe(100, true, 0));
		assertFalse(prank.expired(699));
		assertTrue(prank.expired(700));
	}

	@Test
	void reactionWindowStartsAfterWarningAndRemembersEarlyLook() {
		BehindPrank prank = new BehindPrank();
		prank.warning(500, 31);
		prank.sawPlayerLook();
		assertTrue(prank.answered());
		assertFalse(prank.expired(550));
		assertTrue(prank.expired(551));
		prank.response();
		assertEquals(BehindPrank.Stage.RESPONSE, prank.stage());
		prank.dash(false);
		assertFalse(prank.punishment());
		prank.finish();
		assertEquals(BehindPrank.Stage.DONE, prank.stage());
	}
}
