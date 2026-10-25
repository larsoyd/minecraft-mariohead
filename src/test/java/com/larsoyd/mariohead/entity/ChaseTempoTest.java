package com.larsoyd.mariohead.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChaseTempoTest {
	@Test
	void boatsCompoundBothDashSpeedAndLength() {
		ChaseTempo tempo = new ChaseTempo();
		tempo.observe(true, false, false, 0.0);
		for (int dash = 1; dash <= 100; dash++) {
			tempo.dash();
			assertEquals(Math.pow(1.03, dash), tempo.multiplier(), 1e-9);
		}
	}

	@Test
	void walkingResetsBoatGrowthButElytraUsePersists() {
		ChaseTempo tempo = new ChaseTempo();
		tempo.observe(true, false, false, 0.0);
		tempo.dash();
		for (int tick = 0; tick < 100; tick++) {
			tempo.observe(false, false, true, 0.28);
		}
		assertEquals(1.0, tempo.multiplier());
		tempo.observe(false, true, false, 0.0);
		tempo.dash();
		tempo.observe(false, false, true, 0.28);
		tempo.dash();
		assertEquals(1.03 * 1.03, tempo.multiplier(), 1e-9);
	}

	@Test
	void dismountInAirWaitsForGroundBeforeResetting() {
		ChaseTempo tempo = new ChaseTempo();
		tempo.observe(true, false, false, 0.0);
		tempo.dash();
		tempo.observe(false, false, false, 0.0);
		assertEquals(1.03, tempo.multiplier());
		for (int tick = 0; tick < 100; tick++) {
			tempo.observe(false, false, true, 0.28);
		}
		assertEquals(1.0, tempo.multiplier());
	}

	@Test
	void ordinaryRunningDoesNotCompound() {
		ChaseTempo tempo = new ChaseTempo();
		for (int dash = 0; dash < 100; dash++) {
			tempo.observe(false, false, true, 0.28);
			tempo.dash();
		}
		assertEquals(1.0, tempo.multiplier());
	}

	@Test
	void rapidDismountsAndBoatChangesCannotEraseProgress() {
		ChaseTempo tempo = new ChaseTempo();
		for (int cycle = 1; cycle <= 200; cycle++) {
			tempo.observe(true, false, false, 0);
			tempo.observe(false, false, true, 0.2);
			tempo.dash();
			assertEquals(Math.pow(1.03, cycle), tempo.multiplier(), 1e-8);
		}
	}

	@Test
	void resettingRequiresBothTimeAndActualTravel() {
		ChaseTempo tempo = new ChaseTempo();
		tempo.observe(true, false, false, 0);
		tempo.dash();
		for (int tick = 0; tick < 200; tick++) {
			tempo.observe(false, false, true, 0);
		}
		assertEquals(1.03, tempo.multiplier());
		tempo.observe(true, false, false, 0);
		for (int tick = 0; tick < 99; tick++) {
			tempo.observe(false, false, true, 0.3);
		}
		assertEquals(1.03, tempo.multiplier());
		tempo.observe(false, false, true, 0.3);
		assertEquals(1.0, tempo.multiplier());
	}

	@Test
	void tauntHasStrictTimeChanceAndReachBoundaries() {
		assertTrue(ChaseTempo.canTaunt(5999, true, 4, 4, 0.549999));
		assertFalse(ChaseTempo.canTaunt(6000, true, 4, 4, 0));
		assertFalse(ChaseTempo.canTaunt(100, true, 4, 4, 0.55));
		assertFalse(ChaseTempo.canTaunt(100, false, 4, 4, 0));
		assertFalse(ChaseTempo.canTaunt(100, true, 4.01, 4, 0));
	}
}
