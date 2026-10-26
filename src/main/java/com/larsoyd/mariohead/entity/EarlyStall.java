package com.larsoyd.mariohead.entity;

public final class EarlyStall {
	private final int cutoff;
	private boolean disabled;
	private boolean rolled;
	private boolean started;
	private long readyAt;

	public EarlyStall(double random) {
		this.cutoff = 6000 + (int) (random * 18001);
	}

	public int cutoff() {
		return this.cutoff;
	}

	public boolean active(long chaseTick) {
		return !this.disabled && chaseTick < this.cutoff;
	}

	public boolean expire(long chaseTick, boolean escalated, boolean audible) {
		if (this.disabled || !escalated && chaseTick < this.cutoff) return false;
		this.disabled = true;
		return !escalated && audible;
	}

	public void beginDash(long chaseTick) {
		this.rolled = chaseTick < this.readyAt;
	}

	public boolean canRoll(long chaseTick) {
		return this.active(chaseTick) && !this.rolled && chaseTick >= this.readyAt;
	}

	public boolean guaranteed(long chaseTick) {
		return !this.started && chaseTick < 6000;
	}

	public double probability(long chaseTick) {
		return this.active(chaseTick) ? 0.70 - 0.60 * Math.max(0, chaseTick) / this.cutoff : 0;
	}

	public boolean tryStart(long chaseTick, boolean imminent, double chance) {
		if (!this.canRoll(chaseTick) || !imminent) return false;
		this.rolled = true;
		boolean selected = this.guaranteed(chaseTick) || chance < this.probability(chaseTick);
		this.started |= selected;
		return selected;
	}

	public void finished(long chaseTick) {
		this.readyAt = chaseTick + 180;
	}
}
