package com.larsoyd.mariohead.entity;

import org.jspecify.annotations.Nullable;

// what his voice is doing, with a counter so the same cue twice still reads as new
public enum HeadCue {
	NONE,
	HELLO,
	MMM1,
	MMM2,
	MMM3,
	INHALE;

	private static final HeadCue[] BY_ID = values();
	private static final HeadCue[] HUMS = { MMM1, MMM2, MMM3 };
	private static final int CUE_BITS = 3;

	public HeadVoice.@Nullable Clip clip() {
		return switch (this) {
			case HELLO -> HeadVoice.HELLO;
			case MMM1 -> HeadVoice.MMM1;
			case MMM2 -> HeadVoice.MMM2;
			case MMM3 -> HeadVoice.MMM3;
			default -> null;
		};
	}

	public static HeadCue hum(int rev) {
		return HUMS[Math.floorMod(rev, HUMS.length)];
	}

	public static int pack(HeadCue cue, int seq) {
		return seq << CUE_BITS | cue.ordinal();
	}

	public static HeadCue cueOf(int packed) {
		return BY_ID[(packed & (1 << CUE_BITS) - 1) % BY_ID.length];
	}

	public static int seqOf(int packed) {
		return packed >>> CUE_BITS;
	}
}
