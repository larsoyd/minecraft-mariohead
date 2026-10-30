package com.larsoyd.mariohead.showcase;

public final class Walking {
	private Walking() {}
	public static double wrap(double angle) { return ((angle % 360) + 540) % 360 - 180; }
	public static int direction(double camera, double travel) {
		return Math.floorMod((int) Math.round(wrap(travel - camera) / 45), 8);
	}
	public static double follow(double current, double target, double dt, double response) {
		return current + wrap(target-current) * (1-Math.exp(-response*Math.clamp(dt,0,.05)));
	}
	public static double rate(double velocity, double current, double target, double dt, double response, double speed, double acceleration) {
		double desired=Math.clamp(wrap(target-current)*response,-speed,speed);
		double change=acceleration*Math.clamp(dt,0,.05);
		return velocity+Math.clamp(desired-velocity,-change,change);
	}
}
