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

	public static double speed(int revs, double sag, double pace) {
		double peak = peak(revs);
		if (revs >= PACE_FROM_REV) {
			double floor = Math.clamp(pace * PACE_EDGE, SPEED_START, SPEED_MAX);
			peak = Math.max(peak, floor);
		}
		return peak * (1.0 - SAG * sag);
	}
}
