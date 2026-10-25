package com.larsoyd.mariohead.entity;

public final class HeadSpeech {
	private long end = -10;
	private long ready;
	private boolean homeGap;

	public boolean canStart(HeadCue cue, long tick) {
		return tick >= (this.homeGap ? this.ready : Math.max(this.ready, this.end + (breathes(cue) ? 10 : 0)));
	}

	public void started(HeadCue cue, long tick) {
		HeadVoice.Clip clip = cue.clip();
		if (clip != null) {
			this.homeGap = cue == HeadCue.HOME_NICE || cue == HeadCue.HOME_ASK;
			if (this.homeGap) {
				this.end = tick + (clip.millis() + 49) / 50;
				this.ready = this.end + (cue == HeadCue.HOME_ASK ? 4 : 10);
				return;
			}
			// allow a late observer to finish the whole recording before the next one
			this.end = tick + (clip.millis() + 49) / 50 + HeadClock.LATE_VOICE_TICKS;
			this.ready = this.end + (breathes(cue) ? 10 : 0);
		}
	}

	private static boolean breathes(HeadCue cue) {
		return cue.spoken() && cue != HeadCue.HELLO;
	}
}
