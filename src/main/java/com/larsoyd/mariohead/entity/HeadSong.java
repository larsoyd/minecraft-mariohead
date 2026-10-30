package com.larsoyd.mariohead.entity;

// his one serenade, ticks run start to start so the pauses fall between the words
public final class HeadSong {
	public enum Stage { WAITING, APPROACH, ASK, VERSE1, VERSE2, OUTRO, OVER }
	public static final double GAP = 8;
	public static final double REACH = 9;
	public static final int VERSE1_TICKS = 45;
	public static final int VERSE2_TICKS = 122;
	public static final int OUTRO_TICKS = 137;
	private static final double CHANCE = 0.25;
	private static final int EARLIEST_TICKS = 100;
	private static final int WINDOW_TICKS = 600;
	private static final int APPROACH_TICKS = 400;
	private Stage stage = Stage.WAITING;
	private boolean rolled;
	private boolean chosen;
	private boolean cameFor;
	private long startAt = Long.MAX_VALUE;
	private long nextAt;

	public Stage stage() { return this.stage; }
	public boolean busy() { return this.stage != Stage.WAITING && this.stage != Stage.OVER; }
	public boolean singing() { return this.busy() && this.stage != Stage.APPROACH; }
	public boolean backing() { return this.singing() && this.cameFor; }
	public boolean approachedDuring() { return this.cameFor; }

	public void stallStarted(long now, double chance, double delay) {
		if (!this.rolled) this.chosen = chance < CHANCE;
		this.rolled = true;
		if (this.chosen) this.startAt = now + EARLIEST_TICKS + (int) (delay * WINDOW_TICKS);
	}

	// a stall that ends first keeps the song for the next one
	public boolean approaching(long now, boolean open) {
		if (this.stage == Stage.WAITING && open && now >= this.startAt) {
			this.stage = Stage.APPROACH;
			this.nextAt = now + APPROACH_TICKS;
		} else if (this.stage == Stage.APPROACH && (!open || now >= this.nextAt)) {
			this.stage = Stage.WAITING;
			this.startAt = Long.MAX_VALUE;
		}
		return this.stage == Stage.APPROACH;
	}

	public void begin(long now) {
		this.stage = Stage.ASK;
		this.nextAt = now + VERSE1_TICKS;
	}

	public void cameFor() {
		this.cameFor |= this.singing();
	}

	public HeadCue next(long now) {
		if (now < this.nextAt) return HeadCue.NONE;
		switch (this.stage) {
			case ASK -> {
				this.stage = Stage.VERSE1;
				this.nextAt = now + VERSE2_TICKS;
				return HeadCue.SONG_VERSE1;
			}
			case VERSE1 -> {
				this.stage = Stage.VERSE2;
				this.nextAt = now + OUTRO_TICKS;
				return HeadCue.SONG_VERSE2;
			}
			case VERSE2 -> {
				this.stage = Stage.OUTRO;
				this.nextAt = now + (HeadCue.SONG_OUTRO.clip().millis() + 49) / 50;
				return HeadCue.SONG_OUTRO;
			}
			default -> {
				this.stage = Stage.OVER;
				return HeadCue.NONE;
			}
		}
	}

	public static double speed(double gap, double pace) {
		return gap <= GAP ? 0 : Math.min(pace * 0.97 + (gap - GAP) * 0.12, Math.max(0.45, pace * 1.15));
	}
}
