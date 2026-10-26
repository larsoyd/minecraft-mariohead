package com.larsoyd.mariohead.playtest;

import java.util.List;

public final class EscapePolicy {
	public record Route(double yaw, double clearance, double danger, double gain, boolean jump) {}

	public static Route choose(List<Route> routes, double heading) {
		Route best = null;
		double score = -Double.MAX_VALUE;
		for (Route route : routes) {
			if (route.clearance() < 0.75 || route.danger() >= 100) continue;
			double turn = Math.abs(((route.yaw() - heading) % 360 + 540) % 360 - 180);
			double value = route.gain() * 3 + route.clearance() - route.danger() * 4 - turn / 180;
			if (value > score) {
				score = value;
				best = route;
			}
		}
		return best;
	}
}
