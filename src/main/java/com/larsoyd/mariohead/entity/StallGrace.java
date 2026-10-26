package com.larsoyd.mariohead.entity;

public final class StallGrace {
	public enum Stage { NONE, FOLLOW, WITHDRAW, STALK, ENDING }
	private Stage stage = Stage.NONE;
	private long began;
	private long until;
	private long withdrawAt;
	private double personality;
	private boolean retreat;

	public Stage stage() { return this.stage; }

	public void start(long now, double personality, double stalkChance, double delay) {
		this.stage = Stage.FOLLOW;
		this.began = now;
		this.until = now + 1800;
		this.withdrawAt = stalkChance < 0.1 ? now + 400 + (int) (delay * 901) : Long.MAX_VALUE;
		this.personality = personality;
		this.retreat = false;
	}

	public boolean tick(long now) {
		if (this.stage != Stage.FOLLOW && this.stage != Stage.WITHDRAW) return false;
		if (now >= this.until) {
			this.finish();
			return true;
		}
		if (now >= this.withdrawAt) this.stage = Stage.WITHDRAW;
		return false;
	}

	public void end(boolean retreat) {
		this.stage = Stage.ENDING;
		this.retreat |= retreat;
	}

	public boolean retreating() { return this.retreat; }
	public void stalk() { this.stage = Stage.STALK; }

	public void finish() {
		this.stage = Stage.NONE;
		this.retreat = false;
	}

	public boolean approached(double gap, double towardSpeed) {
		return (this.stage == Stage.FOLLOW || this.stage == Stage.WITHDRAW) && gap < 24 && towardSpeed > 0.06;
	}

	public double gap(long now) {
		return 15 + this.personality * 3 + Math.sin((now - this.began) / 180.0 + this.personality * Math.PI * 2) * 3;
	}

	public double speed(long now, double distance, double pace) {
		double cruise = Math.min(0.36, Math.max(0.16, pace * 0.99));
		return Math.clamp(Math.min(pace, 0.36) * 0.97 + (distance - this.gap(now)) * 0.035, 0, cruise);
	}
}
