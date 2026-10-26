package com.larsoyd.mariohead.entity;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class EarlyStallTest {
	@Test
	void chanceDeclinesWithSurvivalAcrossEachChosenWindow() {
		for (double duration : new double[] { 0, 0.5, 0.999999 }) {
			int cutoff = new EarlyStall(duration).cutoff();
			for (int tick : new int[] { 0, cutoff / 4, cutoff / 2, cutoff - 1 }) {
				double threshold = 0.70 - 0.60 * tick / cutoff;
				EarlyStall below = usedGuarantee(duration);
				below.beginDash(tick);
				assertTrue(below.tryStart(tick, true, threshold - 0.000001), "below threshold at " + tick);
				EarlyStall above = usedGuarantee(duration);
				above.beginDash(tick);
				assertFalse(above.tryStart(tick, true, threshold + 0.000001), "above threshold at " + tick);
			}
			EarlyStall ended = new EarlyStall(duration);
			ended.beginDash(cutoff);
			assertFalse(ended.tryStart(cutoff, true, 0));
		}
	}

	private static EarlyStall usedGuarantee(double duration) {
		EarlyStall stall = new EarlyStall(duration);
		stall.beginDash(0);
		assertTrue(stall.tryStart(0, true, 0));
		return stall;
	}

	@Test
	void firstImminentCatchIsGuaranteedOnlyBeforeFiveMinutes() {
		for (int tick : new int[] {0, 1, 3000, 5999}) {
			EarlyStall stall = new EarlyStall(0.999999);
			stall.beginDash(tick);
			assertFalse(stall.tryStart(tick, false, 0), "distance consumed guarantee");
			assertTrue(stall.tryStart(tick, true, 0.999999), "first catch was not guaranteed at " + tick);
			stall.finished(tick);
			stall.beginDash(tick + 180);
			assertFalse(stall.tryStart(tick + 180, true, 0.999999), "guarantee returned after cooldown");
		}
		for (int tick : new int[] {6000, 6001, 23999, 24000}) {
			EarlyStall stall = new EarlyStall(0.999999);
			stall.beginDash(tick);
			assertFalse(stall.tryStart(tick, true, 0.999999), "late catch received guarantee at " + tick);
		}
	}

	@Test
	void disabledStallsCannotSpendTheGuarantee() {
		EarlyStall stall = new EarlyStall(0.999999);
		stall.expire(10, true, false);
		stall.beginDash(11);
		assertFalse(stall.tryStart(11, true, 0));
		stall.beginDash(200);
		assertFalse(stall.tryStart(200, true, 0));
	}

	@Test
	void cutoffIsChosenOnceBetweenFiveAndTwentyMinutes() {
		assertEquals(6000, new EarlyStall(0).cutoff());
		assertEquals(24000, new EarlyStall(0.999999).cutoff());
		EarlyStall stall = new EarlyStall(0.5);
		assertTrue(stall.active(14999));
		assertTrue(stall.expire(15000, false, true));
		assertFalse(stall.active(15000));
		assertFalse(stall.expire(15001, false, true));
	}

	@Test
	void everyRandomWindowAllowsFiveMinutesAndEndsByTwenty() {
		java.util.Random random = new java.util.Random(829);
		for (int i = 0; i < 10000; i++) {
			EarlyStall stall = new EarlyStall(random.nextDouble());
			assertTrue(stall.active(5999));
			assertFalse(stall.expire(5999, false, true));
			assertTrue(stall.expire(24000, false, true));
			assertFalse(stall.active(24000));
		}
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
		EarlyStall stall = usedGuarantee(0);
		stall.beginDash(0);
		assertFalse(stall.tryStart(1, true, 0.70));
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
