package com.larsoyd.mariohead.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HeadSpeechTest {
	@Test
	void aGulpCutsThroughAHumAndYummyFollowsItsPause() {
		HeadSpeech speech = new HeadSpeech();
		speech.started(HeadCue.MMM1, 100);
		assertFalse(speech.canStart(HeadCue.MMM2, 110));
		assertTrue(speech.canStart(HeadCue.GULP, 110));
		speech.started(HeadCue.GULP, 110);
		assertTrue(speech.canStart(HeadCue.YUMMY, 128));
		assertFalse(speech.canStart(HeadCue.MMM2, 128));
	}

	@Test
	void homeDialogueUsesHalfSecondThenPointTwoSecondPause() {
		HeadSpeech speech = new HeadSpeech();
		speech.started(HeadCue.HOME_NICE, 100);
		long ask = 100 + (HeadCue.HOME_NICE.clip().millis() + 49) / 50 + 10;
		assertFalse(speech.canStart(HeadCue.HOME_ASK, ask - 1));
		assertTrue(speech.canStart(HeadCue.HOME_ASK, ask));
		speech.started(HeadCue.HOME_ASK, ask);
		long hum = ask + (HeadCue.HOME_ASK.clip().millis() + 49) / 50 + 4;
		assertFalse(speech.canStart(HeadCue.DIG_MMM1, hum - 1));
		assertTrue(speech.canStart(HeadCue.DIG_MMM1, hum));
	}

	@Test
	void diggingHumsFinishBeforeTheNextHumOrDialogue() {
		HeadSpeech speech = new HeadSpeech();
		speech.started(HeadCue.DIG_MMM1, 100);
		assertFalse(speech.canStart(HeadCue.DIG_MMM2, 135));
		assertTrue(speech.canStart(HeadCue.DIG_MMM2, 136));
		assertFalse(speech.canStart(HeadCue.DASH_067, 145));
		assertTrue(speech.canStart(HeadCue.DASH_067, 146));
		for (int index = 0; index < DashVoice.CLIPS.length; index++) {
			HeadCue line = HeadCue.chatter(index);
			speech.started(line, 200);
			long ready = 200 + (line.clip().millis() + 49) / 50 + 15;
			assertFalse(speech.canStart(HeadCue.DIG_MMM1, ready - 1));
			assertTrue(speech.canStart(HeadCue.DIG_MMM1, ready));
		}
	}

	@Test
	void everyTauntGetsBreathingRoomOnBothSides() {
		for (int index = 0; index < TauntVoice.CLIPS.length; index++) {
			HeadSpeech speech = new HeadSpeech();
			HeadCue taunt = HeadCue.taunt(index);
			speech.started(HeadCue.MMM1, 100);
			assertFalse(speech.canStart(taunt, 145));
			assertTrue(speech.canStart(taunt, 146));
			speech.started(taunt, 146);
			long next = 146 + (taunt.clip().millis() + 49) / 50 + 15;
			assertFalse(speech.canStart(HeadCue.MMM1, next - 1));
			assertTrue(speech.canStart(HeadCue.MMM1, next));
		}
	}

	@Test
	void announcementWaitsForHumAndHalfASecondOfSilence() {
		HeadSpeech speech = new HeadSpeech();
		speech.started(HeadCue.MMM1, 100);
		assertFalse(speech.canStart(HeadCue.UNDERGROUND, 145));
		assertTrue(speech.canStart(HeadCue.UNDERGROUND, 146));
	}

	@Test
	void humWaitsForAnnouncementAndHalfASecondOfSilence() {
		HeadSpeech speech = new HeadSpeech();
		speech.started(HeadCue.UNDERGROUND, 100);
		assertFalse(speech.canStart(HeadCue.MMM1, 167));
		assertTrue(speech.canStart(HeadCue.MMM1, 168));
	}

	@Test
	void startupAndHumsNeedNoExtraBreathingRoom() {
		HeadSpeech speech = new HeadSpeech();
		assertTrue(speech.canStart(HeadCue.HELLO, 0));
		speech.started(HeadCue.HELLO, 0);
		assertFalse(speech.canStart(HeadCue.MMM1, 48));
		assertTrue(speech.canStart(HeadCue.MMM1, 49));
		speech.started(HeadCue.MMM1, 49);
		assertFalse(speech.canStart(HeadCue.MMM2, 84));
		assertTrue(speech.canStart(HeadCue.MMM2, 85));
	}
}
