package com.larsoyd.mariohead.entity;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class EarlyStallTest {
	@Test
	void chanceDeclinesWithSurvivalAcrossEachChosenWindow() {
		for (double duration : new double[] { 0, 0.5, 0.999999 }) {
			int cutoff = new EarlyStall(duration).cutoff();
			for (int tick : new int[] { 0, cutoff / 4, cutoff / 2, cutoff - 1 }) {
				double threshold = 0.55 - 0.45 * tick / cutoff;
				EarlyStall below = new EarlyStall(duration);
				below.beginDash(tick);
				assertTrue(below.tryStart(tick, true, threshold - 0.000001), "below threshold at " + tick);
				EarlyStall above = new EarlyStall(duration);
				above.beginDash(tick);
				assertFalse(above.tryStart(tick, true, threshold + 0.000001), "above threshold at " + tick);
			}
			EarlyStall ended = new EarlyStall(duration);
			ended.beginDash(cutoff);
			assertFalse(ended.tryStart(cutoff, true, 0));
		}
	}

	@Test
	void cutoffIsChosenOnceBetweenFiveAndTenMinutes() {
		assertEquals(6000, new EarlyStall(0).cutoff());
		assertEquals(12000, new EarlyStall(0.999999).cutoff());
		EarlyStall stall = new EarlyStall(0.5);
		assertTrue(stall.active(8999));
		assertTrue(stall.expire(9000, false, true));
		assertFalse(stall.active(9000));
		assertFalse(stall.expire(9001, false, true));
	}

	@Test
	void escalationAndOutOfEarshotExpiryNeverAnnounceLater() {
		EarlyStall stall = new EarlyStall(0);
		assertFalse(stall.expire(100, true, true));
		assertFalse(stall.active(101));
		assertFalse(stall.expire(6000, false, true));
		EarlyStall distant = new EarlyStall(0);
		assertFalse(distant.expire(6000, false, false));
		assertFalse(distant.expire(6001, false, true));
	}

	@Test
	void rollsOncePerDashAndCooldownCannotExpireIntoAnExistingDash() {
		EarlyStall stall = new EarlyStall(0);
		stall.beginDash(0);
		assertFalse(stall.tryStart(1, true, 0.55));
		assertFalse(stall.tryStart(2, true, 0));
		stall.beginDash(10);
		assertFalse(stall.tryStart(11, false, 0));
		assertTrue(stall.tryStart(12, true, 0.149999));
		stall.finished(50);
		stall.beginDash(229);
		assertFalse(stall.tryStart(230, true, 0));
		stall.beginDash(231);
		assertTrue(stall.tryStart(231, true, 0));
	}
}
