package com.larsoyd.mariohead.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

// a late observer sees the mouth where the server is, and only hears a clip it was there to hear start
class HeadClockTest {
	@Test
	void secondsFollowTheServerClock() {
		assertEquals(0.0, HeadClock.seconds(1000, 1000, 0.0f), 1e-9);
		assertEquals(0.025, HeadClock.seconds(1000, 1000, 0.5f), 1e-9);
		assertEquals(1.0, HeadClock.seconds(1020, 1000, 0.0f), 1e-9);
		assertEquals(1.55, HeadClock.seconds(1030, 999, 0.0f), 1e-9);
	}

	@Test
	void aClockBehindTheStartIsZero() {
		assertEquals(0.0, HeadClock.seconds(995, 1000, 0.5f), 1e-9);
	}

	@Test
	void voiceOnlyWhenHeardFromTheStart() {
		assertTrue(HeadClock.voiceOnTime(1000, 1000));
		assertTrue(HeadClock.voiceOnTime(1004, 1000));
		assertTrue(HeadClock.voiceOnTime(1000 + HeadClock.LATE_VOICE_TICKS, 1000));
		assertFalse(HeadClock.voiceOnTime(1001 + HeadClock.LATE_VOICE_TICKS, 1000));
		assertFalse(HeadClock.voiceOnTime(1400, 1000));
		assertEquals(5, HeadClock.LATE_VOICE_TICKS);
	}
}
