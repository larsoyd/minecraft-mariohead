package com.larsoyd.mariohead.client;

import com.larsoyd.mariohead.HeadSounds;
import com.larsoyd.mariohead.MarioHead;
import com.larsoyd.mariohead.entity.HeadCue;
import com.larsoyd.mariohead.entity.TheHead;
import com.larsoyd.mariohead.entity.VoiceCurve;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

// his voice follows him, as loud as his distance to whoever is listening says
public final class HeadVoiceSound extends AbstractTickableSoundInstance {
	// his words are mixed quieter than his hums, so they get twice the curve
	private static final float WORDS_GAIN = 2.0f;
	// the underground line is an announcement, everyone around hears it
	private static final float ANNOUNCE_FLOOR = 0.6f;
	private static final AtomicInteger STARTED = new AtomicInteger();
	private static final AtomicInteger LOUD = new AtomicInteger();
	private final TheHead head;
	private final float gain;
	private final float floor;

	private HeadVoiceSound(TheHead head, HeadCue cue, boolean loud) {
		super(loud ? HeadSounds.loud(cue) : HeadSounds.of(cue), SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
		this.head = head;
		this.gain = cue.spoken() ? WORDS_GAIN : 1.0f;
		// digging, his hums come through the rock at full blast wherever you are
		this.floor = loud ? 1.0f : cue == HeadCue.UNDERGROUND ? ANNOUNCE_FLOOR : 0.0f;
		this.attenuation = SoundInstance.Attenuation.NONE;
		this.looping = false;
		this.delay = 0;
		this.follow();
	}

	public static void play(TheHead head, HeadCue cue) {
		Minecraft minecraft = Minecraft.getInstance();
		boolean loud = head.digging() && !cue.spoken();
		minecraft.getSoundManager().play(new HeadVoiceSound(head, cue, loud));
		if (loud) {
			LOUD.incrementAndGet();
		}
		MarioHead.LOG.debug("[head] voice cue={} head={} started={}", cue, head.getId(), STARTED.incrementAndGet());
	}

	public static int started() {
		return STARTED.get();
	}

	public static int loudStarted() {
		return LOUD.get();
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}

	@Override
	public void tick() {
		if (this.head.isRemoved()) {
			this.stop();
			return;
		}
		this.follow();
	}

	private void follow() {
		Vec3 c = this.head.centre();
		this.x = c.x;
		this.y = c.y;
		this.z = c.z;
		Player listener = Minecraft.getInstance().player;
		double distance = listener == null ? Double.MAX_VALUE : listener.getEyePosition().distanceTo(c);
		this.volume = Math.max(this.floor, Math.min(1.0f, VoiceCurve.volume(distance) * this.gain));
	}
}
