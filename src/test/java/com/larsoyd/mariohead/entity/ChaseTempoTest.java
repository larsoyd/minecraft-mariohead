package com.larsoyd.mariohead.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChaseTempoTest {
	@Test
	void stallClearsAccumulatedSpeedWithoutForgettingTransportEscalation() {
		ChaseTempo tempo = new ChaseTempo();
		tempo.observe(true, false, false, 0);
		for (int i = 0; i < 30; i++) {
			tempo.dash();
			tempo.hum();
		}
		tempo.stall();
		assertEquals(1.0, tempo.multiplier());
		assertEquals(1.0, tempo.humMultiplier());
		tempo.dash();
		assertEquals(1.03, tempo.multiplier());
	}

	@Test
	void everyHumAddsThreeTenthsOfAPercentUntilATaunt() {
		ChaseTempo tempo = new ChaseTempo();
		for (int hum = 1; hum <= 200; hum++) {
			tempo.dash();
			tempo.hum();
			assertEquals(1.0 + hum * 0.003, tempo.humMultiplier(), 1e-12);
		}
		tempo.taunt();
		assertEquals(1.0, tempo.humMultiplier());
		tempo.dash();
		tempo.hum();
		assertEquals(1.003, tempo.humMultiplier());
	}

	@Test
	void tauntResetsHumGrowthWithoutErasingTransportGrowth() {
		ChaseTempo tempo = new ChaseTempo();
		tempo.observe(true, false, false, 0);
		tempo.dash();
		tempo.hum();
		tempo.taunt();
		assertEquals(1.03, tempo.multiplier());
		assertEquals(1.0, tempo.humMultiplier());
	}

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
	void earlyTauntUsesReducedChanceThroughoutRandomStallWindow() {
		assertTrue(ChaseTempo.canTaunt(8999, true, 8, 0.199999, 9000, 0.2));
		assertFalse(ChaseTempo.canTaunt(8999, true, 8, 0.2, 9000, 0.2));
		assertFalse(ChaseTempo.canTaunt(9000, true, 8, 0, 9000, 0.2));
	}

	@Test
	void tauntHasStrictTimeChanceAndProximityBoundaries() {
		assertTrue(ChaseTempo.canTaunt(5999, true, 8, 0.549999));
		assertFalse(ChaseTempo.canTaunt(6000, true, 8, 0));
		assertFalse(ChaseTempo.canTaunt(100, true, 8, 0.55));
		assertFalse(ChaseTempo.canTaunt(100, false, 8, 0));
		assertFalse(ChaseTempo.canTaunt(100, true, 8.01, 0));
	}
}
