package com.larsoyd.mariohead.entity;

import static com.larsoyd.mariohead.entity.HeadTuning.INHALE_SECONDS;
import static com.larsoyd.mariohead.entity.HeadTuning.MOUTH_OPEN_AT;

// the face he pulls, from where his phase and his voice are
public final class HeadPose {
	private HeadPose() {
	}

	public static boolean mouthOpen(HeadPhase phase, HeadCue cue, double cueSeconds, double phaseSeconds) {
		return switch (phase) {
			case SPAWN, LOOK -> true;
			case TALK, CHARGE -> opening(cue, cueSeconds) >= MOUTH_OPEN_AT;
		};
	}

	private static int opening(HeadCue cue, double seconds) {
		if (cue == HeadCue.INHALE) {
			double left = Math.max(0.0, INHALE_SECONDS - seconds) / INHALE_SECONDS;
			return (int) (255.0 - 255.0 * Math.abs(left * 2.0 - 1.0));
		}
		if (cue != HeadCue.HELLO) {
			return 0;
		}
		int[] mouth = HeadVoice.HELLO.mouth();
		int frame = (int) Math.floor(seconds * HeadVoice.MOUTH_FPS);
		return frame >= 0 && frame < mouth.length ? mouth[frame] : 0;
	}

	public static float browDegrees(HeadPhase phase) {
		return phase == HeadPhase.CHARGE ? HeadTuning.BROW_ANGRY_DEGREES : 0.0f;
	}

	public static float eyeDegrees(HeadPhase phase, double phaseSeconds) {
		if (phase != HeadPhase.LOOK) {
			return 0.0f;
		}
		return (float) (Math.sin(phaseSeconds * HeadTuning.EYE_PAN_RATE) * HeadTuning.EYE_PAN_DEGREES);
	}

	public static float spawnScale(HeadPhase phase, double phaseSeconds) {
		if (phase != HeadPhase.SPAWN || phaseSeconds >= HeadTuning.SPAWN_SECONDS) {
			return 1.0f;
		}
		return (float) (HeadTuning.SPAWN_SCALE + (1.0 - HeadTuning.SPAWN_SCALE) * phaseSeconds / HeadTuning.SPAWN_SECONDS);
	}
}
