package com.larsoyd.mariohead.entity;

public final class DiveTaunt {
	private int swimmingTicks;
	private boolean rolled;
	private long readyAt;

	public void enter(boolean different) {
		this.swimmingTicks = 0;
		if (different) this.rolled = false;
	}

	public void observe(boolean swimming) {
		this.swimmingTicks = swimming ? Math.min(40, this.swimmingTicks + 1) : 0;
	}

	public boolean tryStart(long now, boolean eligible, double chance) {
		if (!eligible || this.rolled || this.swimmingTicks < 40 || now < this.readyAt) return false;
		this.rolled = true;
		return chance < 0.15;
	}

	public void played(long now) {
		this.readyAt = now + 1200;
	}
}
