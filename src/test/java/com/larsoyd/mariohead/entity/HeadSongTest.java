package com.larsoyd.mariohead.entity;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class HeadSongTest {
	private static HeadSong chosen(long now) {
		HeadSong song = new HeadSong();
		song.stallStarted(now, 0.0, 0.0);
		return song;
	}

	@Test
	void aQuarterOfHeadsAreChosenAndTheRollIsNeverRepeated() {
		HeadSong lucky = new HeadSong();
		lucky.stallStarted(0, 0.249999, 0);
		assertTrue(lucky.approaching(100, true));
		HeadSong unlucky = new HeadSong();
		unlucky.stallStarted(0, 0.25, 0);
		assertFalse(unlucky.approaching(100_000, true));
		unlucky.stallStarted(200_000, 0.0, 0);
		assertFalse(unlucky.approaching(300_000, true), "a second stall rolled again");
	}

	@Test
	void waitsInsideTheStallBeforeItComesIn() {
		HeadSong early = chosen(1000);
		assertFalse(early.approaching(1099, true));
		assertTrue(early.approaching(1100, true));
		HeadSong late = new HeadSong();
		late.stallStarted(1000, 0, 0.999999);
		assertFalse(late.approaching(1698, true));
		assertTrue(late.approaching(1699, true));
	}

	@Test
	void neverComesInOutsideTheStall() {
		HeadSong song = chosen(0);
		assertFalse(song.approaching(100, false));
		assertSame(HeadSong.Stage.WAITING, song.stage());
	}

	@Test
	void anApproachThatCannotFinishWaitsForTheNextStall() {
		HeadSong dropped = chosen(0);
		assertTrue(dropped.approaching(100, true));
		assertSame(HeadSong.Stage.APPROACH, dropped.stage());
		assertFalse(dropped.approaching(101, false));
		assertSame(HeadSong.Stage.WAITING, dropped.stage());
		assertFalse(dropped.approaching(102, true), "came back in the same stall");
		dropped.stallStarted(5000, 0.9, 0);
		assertTrue(dropped.approaching(5100, true), "the chosen song was lost");

		HeadSong slow = chosen(0);
		assertTrue(slow.approaching(100, true));
		assertTrue(slow.approaching(499, true));
		assertFalse(slow.approaching(500, true));
		assertSame(HeadSong.Stage.WAITING, slow.stage());
	}

	@Test
	void linesFallOnTheirOwnTicks() {
		HeadSong song = chosen(0);
		assertTrue(song.approaching(100, true));
		assertFalse(song.singing());
		assertTrue(song.busy());
		song.begin(200);
		assertTrue(song.singing());
		long verse1 = 200 + HeadSong.VERSE1_TICKS;
		long verse2 = verse1 + HeadSong.VERSE2_TICKS;
		long outro = verse2 + HeadSong.OUTRO_TICKS;
		long over = outro + (HeadCue.SONG_OUTRO.clip().millis() + 49) / 50;
		assertSame(HeadCue.NONE, song.next(verse1 - 1));
		assertSame(HeadCue.SONG_VERSE1, song.next(verse1));
		assertSame(HeadCue.NONE, song.next(verse1));
		assertSame(HeadCue.NONE, song.next(verse2 - 1));
		assertSame(HeadCue.SONG_VERSE2, song.next(verse2));
		assertSame(HeadCue.NONE, song.next(outro - 1));
		assertSame(HeadCue.SONG_OUTRO, song.next(outro));
		assertSame(HeadCue.NONE, song.next(over - 1));
		assertTrue(song.singing());
		assertSame(HeadCue.NONE, song.next(over));
		assertFalse(song.singing());
		assertFalse(song.busy());
		assertSame(HeadSong.Stage.OVER, song.stage());
	}

	@Test
	void pausesAreAtLeastTheFileEndsApart() {
		assertTrue(HeadSong.VERSE1_TICKS * 50 > HeadCue.SONG_ASK.clip().millis());
		assertTrue(HeadSong.VERSE2_TICKS * 50 > HeadCue.SONG_VERSE1.clip().millis());
		assertTrue(HeadSong.OUTRO_TICKS * 50 > HeadCue.SONG_VERSE2.clip().millis());
	}

	@Test
	void singsOnlyOnce() {
		HeadSong song = chosen(0);
		song.approaching(100, true);
		song.begin(100);
		for (long tick = 100; tick < 1000; tick++) song.next(tick);
		assertSame(HeadSong.Stage.OVER, song.stage());
		song.stallStarted(2000, 0, 0);
		assertFalse(song.approaching(3000, true));
		assertSame(HeadSong.Stage.OVER, song.stage());
	}

	@Test
	void comingForHimOnlyBacksHimUpWhileSinging() {
		HeadSong song = chosen(0);
		song.cameFor();
		assertFalse(song.backing(), "counted before the song began");
		song.approaching(100, true);
		song.begin(100);
		assertFalse(song.backing());
		song.cameFor();
		assertTrue(song.backing());
		for (long tick = 100; tick < 1000; tick++) song.next(tick);
		assertFalse(song.backing());
		assertTrue(song.approachedDuring());
	}

	@Test
	void holdsTheSingingGap() {
		assertEquals(0.45, HeadSong.speed(20, 0.28), 1e-9);
		assertEquals(0.0, HeadSong.speed(5, 0.0), 1e-9);
		assertEquals(0.0, HeadSong.speed(HeadSong.GAP, 0.28), 1e-9);
		assertEquals(0.0, HeadSong.speed(HeadSong.GAP - 1, 0.28), 1e-9);
		assertEquals(0.28 * 0.97 + 0.012, HeadSong.speed(HeadSong.GAP + 0.1, 0.28), 1e-9);
		assertEquals(0.5 * 1.15, HeadSong.speed(30, 0.5), 1e-9);
	}
}
