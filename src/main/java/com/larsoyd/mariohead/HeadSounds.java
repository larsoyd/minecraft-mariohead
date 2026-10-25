package com.larsoyd.mariohead;

import com.larsoyd.mariohead.entity.HeadCue;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

public final class HeadSounds {
	public static final SoundEvent APPEAR = register("entity.the_head.appear");
	public static final SoundEvent HELLO = register("entity.the_head.hello");
	public static final SoundEvent MMM1 = register("entity.the_head.mmm1");
	public static final SoundEvent MMM2 = register("entity.the_head.mmm2");
	public static final SoundEvent MMM3 = register("entity.the_head.mmm3");
	public static final SoundEvent UNDERGROUND = register("entity.the_head.underground");

	private HeadSounds() {
	}

	public static SoundEvent of(HeadCue cue) {
		return switch (cue) {
			case HELLO -> HELLO;
			case MMM1 -> MMM1;
			case MMM2 -> MMM2;
			case MMM3 -> MMM3;
			case UNDERGROUND -> UNDERGROUND;
			default -> throw new IllegalArgumentException("no clip for cue " + cue);
		};
	}

	private static SoundEvent register(String name) {
		var id = MarioHead.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	static void init() {
	}
}
