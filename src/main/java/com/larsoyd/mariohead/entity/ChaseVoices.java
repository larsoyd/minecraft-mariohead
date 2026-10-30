package com.larsoyd.mariohead.entity;

public final class ChaseVoices {
	public static final HeadCue[] TAUNTS = java.util.stream.IntStream.range(0, TauntVoice.CLIPS.length)
		.mapToObj(HeadCue::taunt).toArray(HeadCue[]::new);
	public static final HeadCue[] STALLS = {
		HeadCue.VARIETY_043, HeadCue.VARIETY_045, HeadCue.TAUNT_100, HeadCue.VARIETY_102,
		HeadCue.TAUNT_099, HeadCue.VARIETY_101, HeadCue.TAUNT_107, HeadCue.TAUNT_078,
		HeadCue.VARIETY_017, HeadCue.VARIETY_065, HeadCue.VARIETY_WEEE2, HeadCue.TAUNT_098,
		HeadCue.TAUNT_091, HeadCue.VARIETY_049, HeadCue.VARIETY_050, HeadCue.VARIETY_051
	};
	public static final HeadCue[] CUTOFF = {
		HeadCue.TAUNT_082, HeadCue.VARIETY_084, HeadCue.VARIETY_037, HeadCue.TAUNT_010,
		HeadCue.VARIETY_036, HeadCue.TAUNT_106, HeadCue.TAUNT_105, HeadCue.VARIETY_081, HeadCue.VARIETY_074
	};
	public static final HeadCue[] WATER = { HeadCue.VARIETY_071, HeadCue.VARIETY_085 };
	private static final HeadCue[] AFTER_MEAL = { HeadCue.YUMMY, HeadCue.LASAGNA };
	private static final HeadCue[] CUES = HeadCue.values();
	private final VoiceSelection lines = new VoiceSelection(CUES.length);
	private final VoiceSelection laughs = new VoiceSelection(5);
	private boolean waiting;
	private boolean yummySaid;

	public HeadCue choose(HeadCue[] pool, double random) {
		int id = this.lines.choose(java.util.Arrays.stream(pool).mapToInt(HeadCue::ordinal).toArray(), random);
		return id < 0 ? HeadCue.NONE : CUES[id];
	}

	public HeadCue afterMeal(double random) {
		return this.yummySaid ? this.choose(AFTER_MEAL, random) : HeadCue.YUMMY;
	}

	public void follow(double chance, double roll) {
		this.waiting = roll < chance;
	}

	public void followWater(boolean drowning, double roll) {
		this.follow(drowning ? 1 : 0.6, roll);
	}

	public boolean waiting() {
		return this.waiting;
	}

	public HeadCue laugh(double random) {
		return CUES[HeadCue.VARIETY_LAUGH1.ordinal() + this.laughs.choose(random)];
	}

	public void played(HeadCue cue) {
		this.yummySaid |= cue == HeadCue.YUMMY;
		if (cue.laugh()) {
			this.laughs.played(cue.ordinal() - HeadCue.VARIETY_LAUGH1.ordinal());
			this.waiting = false;
		} else if (cue.spoken()) {
			this.waiting = false;
			this.lines.played(cue.ordinal());
		}
	}
}
