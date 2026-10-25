package com.larsoyd.mariohead.entity;

public final class PearlPursuit {
	private double recoverGap = Double.NaN;

	public void teleported(double before, double after) {
		if (after > before + 1.0) {
			this.recoverGap = Double.isNaN(this.recoverGap) ? before : Math.min(before, this.recoverGap);
		}
	}

	public double boost(double gap) {
		if (Double.isNaN(this.recoverGap)) {
			return 0.0;
		}
		if (gap <= this.recoverGap) {
			this.recoverGap = Double.NaN;
			return 0.0;
		}
		return Math.min(4.0, 0.4 + (gap - this.recoverGap) / 10.0);
	}
}
