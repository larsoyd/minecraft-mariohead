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
	DASH_075,
	DIG_MMM1,
	DIG_MMM2,
	DIG_MMM3,
	HOME_NICE,
	HOME_ASK,
	HOME_HUNGRY,
	HOME_THIRSTY,
	HOME_RETURN,
	PRANK_WARNING,
	PRANK_RESPONSE,
	FALL_019,
	VARIETY_017,
	VARIETY_036,
	VARIETY_037,
	VARIETY_043,
	VARIETY_045,
	VARIETY_049,
	VARIETY_050,
	VARIETY_051,
	VARIETY_065,
	VARIETY_071,
	VARIETY_074,
	VARIETY_081,
	VARIETY_084,
	VARIETY_085,
	VARIETY_101,
	VARIETY_102,
	VARIETY_WEEE2,
	VARIETY_LAUGH1,
	VARIETY_LAUGH2,
	VARIETY_LAUGH3,
	VARIETY_LAUGH4,
	VARIETY_LAUGH5,
	VARIETY_DROWNING;

	private static final HeadCue[] BY_ID = values();
	private static final HeadCue[] HUMS = { MMM1, MMM2, MMM3 };
	private static final HeadVoice.Clip[] DIG_CLIPS = {
		new HeadVoice.Clip("dig_mmm1", 1550, new int[47]),
		new HeadVoice.Clip("dig_mmm2", 2200, new int[66]),
		new HeadVoice.Clip("dig_mmm3", 1940, new int[59])
	};
	private static final int CUE_BITS = 7;

	public HeadVoice.@Nullable Clip clip() {
		if (this.variety()) return VarietyVoice.CLIPS[this.ordinal() - VARIETY_017.ordinal()];
		return switch (this) {
			case FALL_019 -> FallVoice.CLIPS[0];
			case PRANK_WARNING, PRANK_RESPONSE -> PrankVoice.CLIPS[this.ordinal() - PRANK_WARNING.ordinal()];
			case HOME_NICE, HOME_ASK, HOME_HUNGRY, HOME_THIRSTY, HOME_RETURN -> HomeVoice.CLIPS[this.ordinal() - HOME_NICE.ordinal()];
			case HELLO -> HeadVoice.HELLO;
			case MMM1 -> HeadVoice.MMM1;
			case MMM2 -> HeadVoice.MMM2;
			case MMM3 -> HeadVoice.MMM3;
			case UNDERGROUND -> HeadVoice.UNDERGROUND;
			case DASH_067, DASH_073, DASH_086, DASH_075 -> DashVoice.CLIPS[this.ordinal() - DASH_067.ordinal()];
			case DIG_MMM1, DIG_MMM2, DIG_MMM3 -> DIG_CLIPS[this.ordinal() - DIG_MMM1.ordinal()];
			default -> this.taunt() ? TauntVoice.CLIPS[this.ordinal() - TAUNT_006.ordinal()] : null;
		};
	}

	// words move his mouth, a hum keeps it shut
	public boolean spoken() {
		return this == HELLO || this == UNDERGROUND || this.taunt() || this.chatter() || this.home() || this.prank() || this.variety();
	}

	public boolean variety() {
		return this.ordinal() >= VARIETY_017.ordinal();
	}

	public boolean laugh() {
		return this.ordinal() >= VARIETY_LAUGH1.ordinal() && this.ordinal() <= VARIETY_LAUGH5.ordinal();
	}

	public boolean taunt() {
		return this == HOME_RETURN || this == FALL_019 || this == VARIETY_071 || this == VARIETY_085 || this == VARIETY_DROWNING
			|| this.ordinal() >= TAUNT_006.ordinal() && this.ordinal() <= TAUNT_107.ordinal();
	}

	public boolean chatter() {
		return this.ordinal() >= DASH_067.ordinal() && this.ordinal() <= DASH_075.ordinal();
	}

	public boolean home() {
		return this.ordinal() >= HOME_NICE.ordinal() && this.ordinal() <= HOME_RETURN.ordinal();
	}

	public boolean prank() {
		return this == PRANK_WARNING || this == PRANK_RESPONSE;
	}

	public boolean digHum() {
		return this.ordinal() >= DIG_MMM1.ordinal() && this.ordinal() <= DIG_MMM3.ordinal();
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
