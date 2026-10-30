package com.larsoyd.mariohead.showcase;

public final class MotionCurve {
	private MotionCurve() {}

	public static double progress(double time) {
		double t = Math.clamp(time, 0, 1);
		return t * t * t * (t * (t * 6 - 15) + 10);
	}

	public static double angle(double from, double to, double time) {
		double delta = ((to - from) % 360 + 540) % 360 - 180;
		return from + delta * progress(time);
	}
}
