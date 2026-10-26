package com.larsoyd.mariohead.entity;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ChaseVoicesTest {
	@Test
	void laughterUsesActualPlaybackCountsAndExactChance() {
		ChaseVoices voices = new ChaseVoices();
		voices.follow(0.1, 0.1);
		assertFalse(voices.waiting());
		voices.follow(0.1, 0.099999);
		assertTrue(voices.waiting());
		assertEquals(HeadCue.VARIETY_LAUGH1, voices.laugh(0));
		assertEquals(HeadCue.VARIETY_LAUGH1, voices.laugh(0));
		voices.played(HeadCue.VARIETY_LAUGH1);
		assertFalse(voices.waiting());
		voices.follow(1, 0.999999);
		assertEquals(HeadCue.VARIETY_LAUGH2, voices.laugh(0));
	}

	@Test
	void waterLaughUsesSixtyPercentAndDrowningAlwaysLaughs() {
		ChaseVoices voices = new ChaseVoices();
		voices.followWater(false, 0.599999);
		assertTrue(voices.waiting());
		voices.followWater(false, 0.6);
		assertFalse(voices.waiting());
		voices.followWater(true, 0.999999);
		assertTrue(voices.waiting());
	}

	@Test
	void stallAndTauntShareRecordingHistoryEvenWithLaughterBetweenThem() {
		ChaseVoices voices = new ChaseVoices();
		voices.played(HeadCue.TAUNT_100);
		voices.played(HeadCue.VARIETY_LAUGH1);
		for (int i = 0; i < 100; i++) {
			assertNotEquals(HeadCue.TAUNT_100, voices.choose(ChaseVoices.STALLS, i / 100.0));
		}
		voices.played(HeadCue.TAUNT_078);
		for (int i = 0; i < 100; i++) {
			assertNotEquals(HeadCue.TAUNT_078, voices.choose(ChaseVoices.TAUNTS, i / 100.0));
		}
	}

	@Test
	void poolsContainEveryRequestedLineOnce() {
		assertEquals(16, java.util.Set.of(ChaseVoices.STALLS).size());
		assertEquals(9, java.util.Set.of(ChaseVoices.CUTOFF).size());
		assertEquals(19, java.util.Set.of(ChaseVoices.TAUNTS).size());
		assertEquals(java.util.Set.of(HeadCue.VARIETY_071, HeadCue.VARIETY_085), java.util.Set.of(ChaseVoices.WATER));
	}

	@Test
	void unrelatedDialogueCancelsAStaleLaughButHumsDoNotEraseHistory() {
		ChaseVoices voices = new ChaseVoices();
		voices.played(HeadCue.TAUNT_100);
		voices.follow(1, 0);
		voices.played(HeadCue.HOME_NICE);
		assertFalse(voices.waiting());
		voices.played(HeadCue.TAUNT_100);
		voices.played(HeadCue.MMM1);
		for (int i = 0; i < 100; i++) {
			assertNotEquals(HeadCue.TAUNT_100, voices.choose(ChaseVoices.STALLS, i / 100.0));
		}
	}
}
