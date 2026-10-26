package com.larsoyd.mariohead.entity;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class VoiceSelectionTest {
	@Test
	void selectsLeastPlayedWithoutConsecutiveRepeats() {
		for (int seed = 0; seed < 100; seed++) {
			var random = new java.util.Random(seed);
			VoiceSelection selection = new VoiceSelection(5);
			int[] counts = new int[5];
			int last = -1;
			for (int play = 0; play < 500; play++) {
				int next = selection.choose(random.nextDouble());
				assertTrue(next >= 0 && next < counts.length, "no eligible recording selected");
				assertNotEquals(last, next);
				assertEquals(java.util.Arrays.stream(counts).min().orElseThrow(), counts[next]);
				counts[next]++;
				selection.played(next);
				last = next;
			}
		}
	}

	@Test
	void equalCountsUseRandomChoiceAndUnplayedQueuesDoNotCount() {
		VoiceSelection selection = new VoiceSelection(5);
		for (int index = 0; index < 5; index++) {
			assertEquals(index, selection.choose((index + 0.5) / 5));
		}
		selection.played(2);
		assertEquals(0, selection.choose(0));
		assertEquals(4, selection.choose(0.999999));
	}

	@Test
	void sharedHistoryExcludesLastLineAcrossDifferentPools() {
		VoiceSelection selection = new VoiceSelection(6);
		selection.played(3);
		assertEquals(4, selection.choose(new int[] {3, 4}, 0));
		selection.played(4);
		assertEquals(3, selection.choose(new int[] {3, 4}, 0));
		assertEquals(0, selection.choose(new int[] {0, 3, 4}, 0.999));
	}
}
