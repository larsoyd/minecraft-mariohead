package com.larsoyd.mariohead.entity;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PearlPursuitTest {
	@Test
	void recoversOnlyTheDistanceGainedByPearls() {
		PearlPursuit pursuit = new PearlPursuit();
		assertEquals(0.0, pursuit.boost(100));
		pursuit.teleported(12, 60);
		assertTrue(pursuit.boost(60) >= 3.0);
		assertTrue(pursuit.boost(20) > 0.0);
		assertEquals(0.0, pursuit.boost(12));
		assertEquals(0.0, pursuit.boost(60));
	}

	@Test
	void repeatedPearlsKeepTheOriginalRecoveryDistance() {
		PearlPursuit pursuit = new PearlPursuit();
		pursuit.teleported(12, 60);
		pursuit.teleported(40, 90);
		assertTrue(pursuit.boost(40) > 0.0);
		assertEquals(0.0, pursuit.boost(12));
		pursuit.teleported(12, 8);
		assertEquals(0.0, pursuit.boost(20));
	}
}
