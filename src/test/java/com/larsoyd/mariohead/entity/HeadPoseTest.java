package com.larsoyd.mariohead.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HeadPoseTest {
	private static double frame(double n) {
		return n / 30.0;
	}

	@Test
	void helloFollowsTheEnvelope() {
		for (int f = 0; f < HeadVoice.HELLO.frames(); f++) {
			boolean want = HeadVoice.HELLO.mouth()[f] >= 70;
			assertEquals(want, HeadPose.mouthOpen(HeadPhase.TALK, HeadCue.HELLO, frame(f + 0.5), 1.0), "frame " + f);
		}
		assertFalse(HeadPose.mouthOpen(HeadPhase.TALK, HeadCue.HELLO, 10.0, 1.0));
	}

	@Test
	void theUndergroundLineIsSpokenInTheCharge() {
		for (int f = 0; f < HeadVoice.UNDERGROUND.frames(); f++) {
			boolean want = HeadVoice.UNDERGROUND.mouth()[f] >= 70;
			assertEquals(want, HeadPose.mouthOpen(HeadPhase.CHARGE, HeadCue.UNDERGROUND, frame(f + 0.5), 1.0), "frame " + f);
		}
	}

	@Test
	void humsAreShut() {
		for (HeadCue hum : new HeadCue[] { HeadCue.MMM1, HeadCue.MMM2, HeadCue.MMM3 }) {
			for (double t = 0.0; t < 2.5; t += 0.01) {
				assertFalse(HeadPose.mouthOpen(HeadPhase.CHARGE, hum, t, 5.0), hum + " at " + t);
			}
		}
	}

	@Test
	void breathOpensInTheMiddle() {
		assertFalse(HeadPose.mouthOpen(HeadPhase.CHARGE, HeadCue.INHALE, frame(0.0), 5.0));
		assertTrue(HeadPose.mouthOpen(HeadPhase.CHARGE, HeadCue.INHALE, frame(6.5), 5.0));
		assertFalse(HeadPose.mouthOpen(HeadPhase.CHARGE, HeadCue.INHALE, frame(13.0), 5.0));
		assertFalse(HeadPose.mouthOpen(HeadPhase.CHARGE, HeadCue.INHALE, frame(1.5), 5.0));
		assertTrue(HeadPose.mouthOpen(HeadPhase.CHARGE, HeadCue.INHALE, frame(2.5), 5.0));
	}

	@Test
	void openWhileArrivingAndLooking() {
		assertTrue(HeadPose.mouthOpen(HeadPhase.SPAWN, HeadCue.NONE, 0.0, 0.1));
		for (double t = 0.0; t < 3.0; t += 0.05) {
			assertTrue(HeadPose.mouthOpen(HeadPhase.LOOK, HeadCue.NONE, 0.0, t), "look at " + t);
		}
	}

	@Test
	void browsLowerOnlyForTheCharge() {
		assertEquals(5.5f, HeadPose.browDegrees(HeadPhase.CHARGE), 1e-6);
		assertEquals(0.0f, HeadPose.browDegrees(HeadPhase.TALK), 1e-6);
		assertEquals(0.0f, HeadPose.browDegrees(HeadPhase.LOOK), 1e-6);
	}

	@Test
	void irisesPanWhileLooking() {
		assertEquals(0.0f, HeadPose.eyeDegrees(HeadPhase.LOOK, 0.0), 0.05);
		assertEquals(6.5f, HeadPose.eyeDegrees(HeadPhase.LOOK, frame(6.4)), 0.01);
		assertEquals(-6.5f, HeadPose.eyeDegrees(HeadPhase.LOOK, frame(19.2)), 0.01);
		assertEquals(0.0f, HeadPose.eyeDegrees(HeadPhase.CHARGE, frame(6.4)), 1e-6);
	}

	@Test
	void swellsIntoBeing() {
		assertEquals(0.15f, HeadPose.spawnScale(HeadPhase.SPAWN, 0.0), 1e-6);
		assertEquals(0.575f, HeadPose.spawnScale(HeadPhase.SPAWN, 0.25), 1e-6);
		assertEquals(1.0f, HeadPose.spawnScale(HeadPhase.SPAWN, 0.5), 1e-6);
		assertEquals(1.0f, HeadPose.spawnScale(HeadPhase.SPAWN, 2.0), 1e-6);
		assertEquals(1.0f, HeadPose.spawnScale(HeadPhase.CHARGE, 0.0), 1e-6);
	}
}
