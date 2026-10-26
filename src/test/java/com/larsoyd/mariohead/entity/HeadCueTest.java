package com.larsoyd.mariohead.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HeadCueTest {
	@Test
	void packRoundTrips() {
		for (HeadCue cue : HeadCue.values()) {
			for (long time : new long[] { 0L, 1L, 7L, 24000L, 1L << 40, (1L << 57) - 1 }) {
				long packed = HeadCue.pack(cue, time);
				assertSame(cue, HeadCue.cueOf(packed));
				assertEquals(time, HeadCue.timeOf(packed));
			}
		}
	}

	@Test
	void varietyClipsAreSpeechAndHaveDistinctSynchronizedIds() {
		int count = 0;
		for (HeadCue cue : HeadCue.values()) {
			if (cue.name().startsWith("VARIETY_")) {
				assertTrue(cue.spoken());
				assertEquals(cue.name().toLowerCase(java.util.Locale.ROOT), cue.clip().name());
				count++;
			}
		}
		assertEquals(23, count);
	}

	@Test
	void phasesPackWithTheirStart() {
		for (HeadPhase phase : HeadPhase.values()) {
			long packed = HeadPhase.pack(phase, 123456789L);
			assertSame(phase, HeadPhase.phaseOf(packed));
			assertEquals(123456789L, HeadPhase.timeOf(packed));
		}
	}

	@Test
	void humsCycleThroughTheThreeClips() {
		assertSame(HeadCue.MMM1, HeadCue.hum(0));
		assertSame(HeadCue.MMM2, HeadCue.hum(1));
		assertSame(HeadCue.MMM3, HeadCue.hum(2));
		assertSame(HeadCue.MMM1, HeadCue.hum(3));
		assertSame(HeadCue.MMM3, HeadCue.hum(11));
	}

	@Test
	void clips() {
		assertSame(HeadVoice.HELLO, HeadCue.HELLO.clip());
		assertSame(HeadVoice.MMM2, HeadCue.MMM2.clip());
		assertNull(HeadCue.INHALE.clip());
		assertNull(HeadCue.NONE.clip());
	}

	@Test
	void phaseIds() {
		for (HeadPhase phase : HeadPhase.values()) {
			assertSame(phase, HeadPhase.byId(phase.ordinal()));
		}
	}
}
