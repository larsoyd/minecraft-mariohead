package com.larsoyd.mariohead.entity;

public final class ChaseTempo {
	private double multiplier = 1.0;
	private boolean flown;
	private boolean accelerating;
	private int footTicks;
	private double footDistance;

	public void observe(boolean boat, boolean flying, boolean onFoot, double step) {
		this.flown |= flying;
		this.accelerating |= boat || this.flown;
		if (this.accelerating && !this.flown && !boat && onFoot) {
			this.footTicks++;
			this.footDistance += step >= 0.0 && step < HeadTuning.PACE_TELEPORT ? step : 0.0;
			if (this.footTicks >= 100 && this.footDistance >= 8.0) {
				this.multiplier = 1.0;
				this.accelerating = false;
			}
		} else {
			this.footTicks = 0;
			this.footDistance = 0.0;
		}
	}

	public void dash() {
		if (this.accelerating) {
			this.multiplier *= 1.03;
		}
	}

	public double multiplier() {
		return this.multiplier;
	}

	public static boolean canTaunt(int chaseTicks, boolean onLand, double gap, double chance) {
		return chaseTicks < 6000 && onLand && gap <= 8.0 && chance < 0.55;
	}
}
