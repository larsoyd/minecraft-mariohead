package com.larsoyd.mariohead.entity;

public final class HeadSpeech {
	private long end = -10;
	private long ready;

	public boolean canStart(HeadCue cue, long tick) {
		return tick >= Math.max(this.ready, this.end + (breathes(cue) ? 10 : 0));
	}

	public void started(HeadCue cue, long tick) {
		HeadVoice.Clip clip = cue.clip();
		if (clip != null) {
			// allow a late observer to finish the whole recording before the next one
			this.end = tick + (clip.millis() + 49) / 50 + (cue.digHum() ? 0 : HeadClock.LATE_VOICE_TICKS);
			this.ready = this.end + (breathes(cue) ? 10 : 0);
		}
	}

	private static boolean breathes(HeadCue cue) {
		return cue.spoken() && cue != HeadCue.HELLO;
	}
}
