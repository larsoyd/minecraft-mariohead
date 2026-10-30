package com.larsoyd.mariohead.entity;

import net.minecraft.world.phys.Vec3;

// what he may eat on his way to the player, he never turns aside or goes past them for it
public final class HeadMeal {
	public static final double REACH = 16.0;
	public static final double BITE = 0.4;
	public static final int COOLDOWN_TICKS = 60;
	// he eats at the stall gap, past a normal cry's 16 blocks
	public static final float DEATH_VOLUME = 3.0f;
	private static final double NEAREST = 3.0;
	private static final double CONE_COSINE = Math.cos(Math.toRadians(30.0));
	private static final double RISE = 3.0;
	private static final double CLEARANCE = 6.0;
	private static final double DASH_SPEED = 0.8;
	private static final double DASH_ACCEL = 0.12;
	private static final double YUMMY_CHANCE = 0.4;
	private static final int YUMMY_PAUSE_MILLIS = 300;

	private HeadMeal() {
	}

	public static boolean inLine(Vec3 head, Vec3 player, Vec3 mob) {
		Vec3 way = player.subtract(head).multiply(1, 0, 1);
		Vec3 to = mob.subtract(head).multiply(1, 0, 1);
		double along = to.dot(way.normalize());
		return to.length() >= NEAREST && to.length() <= REACH && along >= to.length() * CONE_COSINE
			&& along <= way.length() - CLEARANCE && mob.subtract(player).horizontalDistance() >= CLEARANCE
			&& Math.abs(mob.y - head.y) <= RISE;
	}

	public static boolean closes(Vec3 head, Vec3 player, Vec3 mob) {
		return mob.subtract(player).horizontalDistance() < head.subtract(player).horizontalDistance();
	}

	public static boolean clearOfPlayer(double gap) {
		return gap >= CLEARANCE;
	}

	public static boolean yummy(double roll) {
		return roll < YUMMY_CHANCE;
	}

	public static int yummyDelay(HeadVoice.Clip gulp) {
		return (gulp.millis() + YUMMY_PAUSE_MILLIS + 25) / 50;
	}

	public static double dashSpeed(double from) {
		return Math.min(DASH_SPEED, from + DASH_ACCEL);
	}
}
