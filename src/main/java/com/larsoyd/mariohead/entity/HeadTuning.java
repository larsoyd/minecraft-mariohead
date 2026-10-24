package com.larsoyd.mariohead.entity;

// speeds are blocks per tick, distances blocks, times seconds
public final class HeadTuning {
	// a sprinting player on flat ground, which the rev speeds are measured against
	public static final double SPRINT_SPEED = 0.2806;
	public static final double SPEED_START = SPRINT_SPEED * 0.75;
	public static final double SPEED_STEP = SPRINT_SPEED * 0.078125;
	public static final double SPEED_MAX = SPRINT_SPEED * 1.625;
	public static final double SAG = 0.45;
	public static final int PACE_FROM_REV = 4;
	public static final double PACE_EDGE = 1.02;
	public static final double PACE_TICKS = 20.0;
	public static final double PACE_TELEPORT = 4.0;
	public static final double ACCEL = 0.0197;
	public static final double DECEL = 0.0395;
	public static final float TURN_DEGREES = 25.3125f;
	public static final float GRAB_TURN_DEGREES = 33.75f;
	public static final float SLOW_TURN_DEGREES = 45.0f;
	public static final double MIN_TURN_SPEED = 0.3;

	public static final double SPAWN_SECONDS = 0.5;
	public static final double LOOK_SECONDS = 0.5;
	public static final double INHALE_SECONDS = 13.0 / HeadVoice.MOUTH_FPS;
	public static final float SPAWN_SCALE = 0.15f;
	public static final int MOUTH_OPEN_AT = 70;
	public static final float BROW_ANGRY_DEGREES = 5.5f;
	public static final float EYE_PAN_DEGREES = 6.5f;
	// radians per second of the glance about, as a 30 Hz wave
	public static final double EYE_PAN_RATE = 0x0A00 / 65536.0 * 2.0 * Math.PI * HeadVoice.MOUTH_FPS;
	public static final double SWAY_RATE = 0x0700 / 65536.0 * 2.0 * Math.PI * HeadVoice.MOUTH_FPS;
	public static final float SWAY_DEGREES_PER_TICK = 0.7416f;

	public static final double SPAWN_MIN = 7.0;
	public static final double SPAWN_MAX = 12.0;
	public static final double ACQUIRE_RANGE = 32.0;
	public static final double HOVER = 2.0;
	public static final double CLEARANCE = 1.0;
	public static final double CLIMB = 0.21;
	public static final double FLOOR_SEARCH = 12.0;
	public static final double GRAB_DISTANCE = 4.0;
	public static final double PATH_RANGE = 48.0;
	public static final int REPLAN_TICKS = 7;
	public static final double WAYPOINT_REACH = 0.9;
	public static final double CONTACT = 1.7;
	public static final double CONTACT_Y = 2.2;

	public static final double BOB_BLOCKS = 0.14;
	public static final double BOB_RATE = 0x0320 / 65536.0 * 2.0 * Math.PI * HeadVoice.MOUTH_FPS;
	public static final float ROLL_PER_TURN = 3.2f;
	public static final float ROLL_MAX = 22.5f;
	public static final float ROLL_RATE = 3.164f;
	public static final float PITCH_PER_BLOCK = 0.4944f;
	public static final float PITCH_MAX = 25.3f;
	public static final float PITCH_RATE = 2.637f;
	public static final double LOD_DISTANCE = 24.0;

	private HeadTuning() {
	}
}
