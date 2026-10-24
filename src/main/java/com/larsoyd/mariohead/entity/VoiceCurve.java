package com.larsoyd.mariohead.entity;

// loudness by his distance to the listener: a jumpscare beside you, a fade down a corridor
public final class VoiceCurve {
	private static final double LOUD_WITHIN = 2.5;
	private static final double SCARE_FROM = 7.0;
	private static final double BODY = 0.5;
	private static final double SILENT_AT = 50.0;

	private VoiceCurve() {
	}

	public static float volume(double distance) {
		if (distance >= SILENT_AT) {
			return 0.0f;
		}
		double t = Math.min(1.0, (SILENT_AT - distance) / (SILENT_AT - LOUD_WITHIN));
		double swell = Math.clamp((SCARE_FROM - distance) / (SCARE_FROM - LOUD_WITHIN), 0.0, 1.0);
		swell = swell * swell * (3.0 - 2.0 * swell);
		return (float) (BODY * t * t + (1.0 - BODY) * swell);
	}
}
