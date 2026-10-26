package com.larsoyd.mariohead.entity;

public final class DashCadence {
	private int beat = -1;
	private boolean grows = true;

	public int advance(int revs, boolean stallsPossible) {
		this.beat++;
		this.grows = !stallsPossible || this.beat % 2 == 1;
		return Math.max(0, revs + (this.grows ? 1 : 0));
	}

	public int beat() { return this.beat; }
	public boolean grows() { return this.grows; }

	public void reset() {
		this.beat = -1;
		this.grows = false;
	}
}
