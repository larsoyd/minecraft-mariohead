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
	TAUNT_107,
	DASH_067,
	DASH_073,
	DASH_086,
	DIG_MMM1,
	DIG_MMM2,
	DIG_MMM3;

	private static final HeadCue[] BY_ID = values();
	private static final HeadCue[] HUMS = { MMM1, MMM2, MMM3 };
	private static final HeadVoice.Clip[] DIG_CLIPS = {
		new HeadVoice.Clip("dig_mmm1", 500, new int[15]),
		new HeadVoice.Clip("dig_mmm2", 500, new int[15]),
		new HeadVoice.Clip("dig_mmm3", 500, new int[15])
	};
	private static final int CUE_BITS = 5;

	public HeadVoice.@Nullable Clip clip() {
		return switch (this) {
			case HELLO -> HeadVoice.HELLO;
			case MMM1 -> HeadVoice.MMM1;
			case MMM2 -> HeadVoice.MMM2;
			case MMM3 -> HeadVoice.MMM3;
			case UNDERGROUND -> HeadVoice.UNDERGROUND;
			case DASH_067, DASH_073, DASH_086 -> DashVoice.CLIPS[this.ordinal() - DASH_067.ordinal()];
			case DIG_MMM1, DIG_MMM2, DIG_MMM3 -> DIG_CLIPS[this.ordinal() - DIG_MMM1.ordinal()];
			default -> this.taunt() ? TauntVoice.CLIPS[this.ordinal() - TAUNT_006.ordinal()] : null;
		};
	}

	// words move his mouth, a hum keeps it shut
	public boolean spoken() {
		return this == HELLO || this == UNDERGROUND || this.taunt() || this.chatter();
	}

	public boolean taunt() {
		return this.ordinal() >= TAUNT_006.ordinal() && this.ordinal() <= TAUNT_107.ordinal();
	}

	public boolean chatter() {
		return this.ordinal() >= DASH_067.ordinal() && this.ordinal() <= DASH_086.ordinal();
	}

	public boolean digHum() {
		return this.ordinal() >= DIG_MMM1.ordinal();
	}

	public static HeadCue digHum(int rev) {
		return BY_ID[DIG_MMM1.ordinal() + Math.floorMod(rev, 3)];
	}

	public static HeadCue chatter(int index) {
		return BY_ID[DASH_067.ordinal() + index];
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
