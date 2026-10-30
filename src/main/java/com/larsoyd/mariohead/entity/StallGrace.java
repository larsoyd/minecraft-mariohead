package com.larsoyd.mariohead.entity;

public final class StallGrace {
	public enum Stage { NONE, FOLLOW, WITHDRAW, STALK, ENDING }
	private Stage stage = Stage.NONE;
	private long began;
	private long until;
	private long withdrawAt;
	private double personality;
	private double closed;
	private boolean retreat;
	private int starts;
	private boolean completed;

	public Stage stage() { return this.stage; }
	public boolean completed() { return this.completed; }
	public long duration() { return this.until - this.began; }

	public void start(long now, double personality, double stalkChance, double delay) {
		this.stage = Stage.FOLLOW;
		this.began = now;
		this.starts = Math.min(3, this.starts + 1);
		int duration = this.starts == 1 ? 1800 : this.starts == 2 ? 1200 : 900;
		this.until = now + duration;
		this.withdrawAt = stalkChance < 0.1 ? now + 400 + (int) (delay * (duration - 899)) : Long.MAX_VALUE;
		this.personality = personality;
		this.retreat = false;
		this.closed = 0;
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
		this.completed |= this.stage != Stage.NONE;
		this.stage = Stage.NONE;
		this.retreat = false;
	}

	// net blocks walked at him, so a look back while running never counts as coming for him
	public boolean approached(double gap, double towardSpeed) {
		this.closed = gap < 24 ? Math.max(0, this.closed * 0.99 + towardSpeed) : 0;
		return (this.stage == Stage.FOLLOW || this.stage == Stage.WITHDRAW) && this.closed >= 4;
	}

	public double gap(long now) {
		return 15 + this.personality * 3 + Math.sin((now - this.began) / 180.0 + this.personality * Math.PI * 2) * 3;
	}

	public double speed(long now, double distance, double pace) {
		double cruise = Math.min(0.36, Math.max(0.16, pace * 0.99));
		return Math.clamp(Math.min(pace, 0.36) * 0.97 + (distance - this.gap(now)) * 0.035, 0, cruise);
	}
}
