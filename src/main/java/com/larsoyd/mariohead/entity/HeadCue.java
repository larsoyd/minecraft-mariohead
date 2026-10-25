package com.larsoyd.mariohead.entity;

import org.jspecify.annotations.Nullable;

// what his voice is doing
public enum HeadCue {
	NONE,
	HELLO,
	MMM1,
	MMM2,
	MMM3,
	INHALE,
	UNDERGROUND;

	private static final HeadCue[] BY_ID = values();
	private static final HeadCue[] HUMS = { MMM1, MMM2, MMM3 };
	private static final int CUE_BITS = 3;

	public HeadVoice.@Nullable Clip clip() {
		return switch (this) {
			case HELLO -> HeadVoice.HELLO;
			case MMM1 -> HeadVoice.MMM1;
			case MMM2 -> HeadVoice.MMM2;
			case MMM3 -> HeadVoice.MMM3;
			case UNDERGROUND -> HeadVoice.UNDERGROUND;
			default -> null;
		};
	}

	// words move his mouth, a hum keeps it shut
	public boolean spoken() {
		return this == HELLO || this == UNDERGROUND;
	}

	public static HeadCue hum(int rev) {
		return HUMS[Math.floorMod(rev, HUMS.length)];
	}

	public static long pack(HeadCue cue, long time) {
		return time << CUE_BITS | cue.ordinal();
	}

	public static HeadCue cueOf(long packed) {
		return BY_ID[(int) (packed & (1 << CUE_BITS) - 1) % BY_ID.length];
	}

	public static long timeOf(long packed) {
		return packed >>> CUE_BITS;
	}
}
