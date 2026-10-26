package com.larsoyd.mariohead.entity;

public final class BehindPrank {
	public enum Stage { WAITING, APPROACH, WARNING, RESPONSE, GREETING, DASH, DONE }
	private Stage stage = Stage.WAITING;
	private boolean used;
	private long awaySince = -1;
	private long deadline;
	private boolean looked;
	private boolean punishment;

	public Stage stage() { return this.stage; }
	public boolean punishment() { return this.punishment; }
	public boolean begin(long now) {
		if (this.used || this.stage != Stage.WAITING) return false;
		this.used = true;
		this.stage = Stage.APPROACH;
		this.deadline = now + 600;
		return true;
	}

	public boolean readyToRoll(long now) {
		return !this.used && this.stage == Stage.WAITING && this.awaySince >= 0 && now - this.awaySince >= 100;
	}

	public boolean observe(long now, boolean escaped, double roll) {
		if (this.used || this.stage != Stage.WAITING) return false;
		if (!escaped) {
			this.awaySince = -1;
			return false;
		}
		if (this.awaySince < 0) this.awaySince = now;
		if (now - this.awaySince < 100) return false;
		if (roll < 0.55) return this.begin(now);
		this.used = true;
		return false;
	}

	public void warning(long now, int ticks) {
		this.stage = Stage.WARNING;
		this.deadline = now + ticks + 20;
	}

	public void sawPlayerLook() {
		this.looked = true;
	}

	public boolean answered() { return this.looked; }

	public boolean expired(long now) { return now >= this.deadline; }

	public void response() { this.stage = Stage.RESPONSE; }
	public void greeting() { this.stage = Stage.GREETING; }
	public void dash(boolean punishment) {
		this.punishment = punishment;
		this.stage = Stage.DASH;
	}
	public void finish() { this.stage = Stage.DONE; }
}
