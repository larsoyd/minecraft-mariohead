package com.larsoyd.mariohead.entity;

public final class DashChatter {
	private static final int LINES = 4;
	private static final int ALL = (1 << LINES) - 1;
	private int used;
	private int last = -1;
	private long readyAt;

	public int choose(long tick, double chance, int selection) {
		if (tick < this.readyAt || chance >= 0.2) {
			return -1;
		}
		if (this.used == ALL) {
			this.used = 0;
		}
		int available = ALL & ~this.used & ~(this.last < 0 ? 0 : 1 << this.last);
		int slot = Math.floorMod(selection, Integer.bitCount(available));
		for (int index = 0; index < LINES; index++) {
			if ((available & 1 << index) != 0 && slot-- == 0) {
				this.used |= 1 << index;
				this.last = index;
				return index;
			}
		}
		throw new IllegalStateException("no unused dash line");
	}

	public void finishedAt(long tick) {
		this.readyAt = tick + 100;
	}
}
