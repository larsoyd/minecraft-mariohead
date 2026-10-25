package com.larsoyd.mariohead.entity;

import static com.larsoyd.mariohead.entity.HeadTuning.PACE_EDGE;
import static com.larsoyd.mariohead.entity.HeadTuning.PACE_FROM_REV;
import static com.larsoyd.mariohead.entity.HeadTuning.SAG;
import static com.larsoyd.mariohead.entity.HeadTuning.SPEED_MAX;
import static com.larsoyd.mariohead.entity.HeadTuning.SPEED_START;
import static com.larsoyd.mariohead.entity.HeadTuning.SPEED_STEP;

// each hum snaps his speed up, a little higher every rev, and it sags until the next one
public final class RevSpring {
	private RevSpring() {
	}

	public static double peak(int revs) {
		return Math.min(SPEED_START + SPEED_STEP * revs, SPEED_MAX);
	}

	// from the fifth rev even his sag stays over a runner's pace, so a straight run only delays him
	public static double speed(int revs, double sag, double pace) {
		double peak = peak(revs);
		if (revs < PACE_FROM_REV) {
			return peak * (1.0 - SAG * sag);
		}
		peak = Math.max(peak, Math.clamp(pace * PACE_EDGE, SPEED_START, SPEED_MAX));
		return Math.max(peak * (1.0 - SAG * sag), Math.min(pace * PACE_EDGE, SPEED_MAX));
	}
}
