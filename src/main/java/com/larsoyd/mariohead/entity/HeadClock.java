package com.larsoyd.mariohead.entity;

// phase and cue timing on the server's game clock, so a client that starts watching late stays in step
public final class HeadClock {
	// a clip cannot start partway through, so one more than this many ticks late is left unheard
	public static final int LATE_VOICE_TICKS = 5;
	private static final double TICK_SECONDS = 0.05;

	private HeadClock() {
	}

	public static double seconds(long now, long start, float partialTick) {
		return Math.max(0.0, now - start + partialTick) * TICK_SECONDS;
	}

	public static boolean voiceOnTime(long now, long start) {
		return now - start <= LATE_VOICE_TICKS;
	}
}
