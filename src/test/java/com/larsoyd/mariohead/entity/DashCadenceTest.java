package com.larsoyd.mariohead.entity;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DashCadenceTest {
	@Test
	void eligibleDashesGrowOnEvenBeatsWithoutSkippingHums() {
		DashCadence cadence = new DashCadence();
		int revs = -1;
		for (int dash = 1; dash <= 12; dash++) {
			revs = cadence.advance(revs, true);
			assertEquals(dash / 2, revs, "speed on dash " + dash);
			assertEquals(dash - 1, cadence.beat(), "hum on dash " + dash);
			assertEquals(dash % 2 == 0, cadence.grows(), "bonus on dash " + dash);
		}
	}

	@Test
	void disablingStallsImmediatelyRestoresEveryDashWithoutBackfill() {
		for (int eligible = 1; eligible <= 6; eligible++) {
			DashCadence cadence = new DashCadence();
			int revs = -1;
			for (int i = 0; i < eligible; i++) revs = cadence.advance(revs, true);
			for (int i = 1; i <= 5; i++) {
				revs = cadence.advance(revs, false);
				assertEquals(eligible / 2 + i, revs);
				assertTrue(cadence.grows());
			}
		}
	}

	@Test
	void resetKeepsTheRunnerFloorWithoutBankingGraceDashes() {
		DashCadence cadence = new DashCadence();
		for (int i = 0; i < 50; i++) cadence.advance(i, true);
		cadence.reset();
		assertEquals(4, cadence.advance(4, true));
		assertFalse(cadence.grows());
		assertEquals(5, cadence.advance(4, true));
		assertEquals(1, cadence.beat());
	}
}
