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
	UNDERGROUND,
	TAUNT_006,
	TAUNT_008,
	TAUNT_009,
	TAUNT_010,
	TAUNT_016,
	TAUNT_023,
	TAUNT_026,
	TAUNT_078,
	TAUNT_082,
	TAUNT_088,
	TAUNT_089,
	TAUNT_090,
	TAUNT_091,
	TAUNT_098,
	TAUNT_099,
	TAUNT_100,
	TAUNT_105,
	TAUNT_106,
	TAUNT_107;

	private static final HeadCue[] BY_ID = values();
	private static final HeadCue[] HUMS = { MMM1, MMM2, MMM3 };
	private static final int CUE_BITS = 5;

	public HeadVoice.@Nullable Clip clip() {
		return switch (this) {
			case HELLO -> HeadVoice.HELLO;
			case MMM1 -> HeadVoice.MMM1;
			case MMM2 -> HeadVoice.MMM2;
			case MMM3 -> HeadVoice.MMM3;
			case UNDERGROUND -> HeadVoice.UNDERGROUND;
			default -> this.taunt() ? TauntVoice.CLIPS[this.ordinal() - TAUNT_006.ordinal()] : null;
		};
	}

	// words move his mouth, a hum keeps it shut
	public boolean spoken() {
		return this == HELLO || this == UNDERGROUND || this.taunt();
	}

	public boolean taunt() {
		return this.ordinal() >= TAUNT_006.ordinal();
	}

	public static HeadCue taunt(int index) {
		return BY_ID[TAUNT_006.ordinal() + Math.floorMod(index, TauntVoice.CLIPS.length)];
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
