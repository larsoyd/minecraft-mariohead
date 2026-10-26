package com.larsoyd.mariohead.entity;

public final class EarlyStall {
	private final int cutoff;
	private boolean disabled;
	private boolean rolled;
	private long readyAt;

	public EarlyStall(double random) {
		this.cutoff = 6000 + (int) (random * 6001);
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

	public boolean tryStart(long chaseTick, boolean imminent, double chance) {
		if (!this.active(chaseTick) || this.rolled || chaseTick < this.readyAt || !imminent) return false;
		this.rolled = true;
		return chance < 0.15;
	}

	public void finished(long chaseTick) {
		this.readyAt = chaseTick + 180;
	}
}
