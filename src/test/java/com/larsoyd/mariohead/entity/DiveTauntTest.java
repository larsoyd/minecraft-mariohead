package com.larsoyd.mariohead.entity;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DiveTauntTest {
	@Test
	void requiresFortyActiveTicksAndExactlyFifteenPercent() {
		DiveTaunt dive = new DiveTaunt();
		for (int i = 0; i < 39; i++) dive.observe(true);
		assertFalse(dive.tryStart(100, true, 0));
		dive.observe(true);
		assertFalse(dive.tryStart(100, false, 0));
		assertTrue(dive.tryStart(100, true, 0.149999));
		assertFalse(dive.tryStart(101, true, 0));
		DiveTaunt losing = new DiveTaunt();
		for (int i = 0; i < 40; i++) losing.observe(true);
		assertFalse(losing.tryStart(100, true, 0.15));
		assertFalse(losing.tryStart(2000, true, 0));
	}

	@Test
	void onlyNewWaterVisitAndElapsedCooldownAllowAnotherRoll() {
		DiveTaunt dive = new DiveTaunt();
		for (int i = 0; i < 40; i++) dive.observe(true);
		assertTrue(dive.tryStart(100, true, 0));
		dive.played(100);
		dive.enter(false);
		for (int i = 0; i < 40; i++) dive.observe(true);
		assertFalse(dive.tryStart(2000, true, 0));
		dive.enter(true);
		for (int i = 0; i < 40; i++) dive.observe(true);
		assertFalse(dive.tryStart(1299, true, 0));
		assertTrue(dive.tryStart(1300, true, 0));
	}

	@Test
	void InterruptedSwimmingRestartsTwoSecondRequirement() {
		DiveTaunt dive = new DiveTaunt();
		for (int i = 0; i < 39; i++) dive.observe(true);
		dive.observe(false);
		for (int i = 0; i < 39; i++) dive.observe(true);
		assertFalse(dive.tryStart(200, true, 0));
		dive.observe(true);
		assertTrue(dive.tryStart(201, true, 0));
	}
}
