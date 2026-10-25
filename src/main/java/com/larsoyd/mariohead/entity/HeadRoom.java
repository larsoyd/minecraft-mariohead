package com.larsoyd.mariohead.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

// the floor, the ceiling and how big he may be at a spot, his box holding all of him
final class HeadRoom {
	private static final double GAP = 1e-3;
	private static final double ESCAPE_STEP = 0.25;
	private static final double SWEEP_STEP = 0.5;
	private static final double FOOTPRINT_INSET = 1e-3;
	private static final double[] SIDE_STEPS = { 0.0, 0.5, -0.5, 1.0, -1.0 };
	private static final double[] ON_THE_SPOT = { 0.0 };
	private static final double FIT_REACH = 1.5;
	private static final double FIT_STEP = 0.25;
	private static final int FIT_WAYS = 8;
	private static final double RISE = 2.0;
	private static final double RISE_STEP = 0.5;

	private HeadRoom() {
	}

	static AABB box(double x, double centreY, double z, float squeeze) {
		double w = TheHead.WIDTH * squeeze / 2.0;
		double h = TheHead.HEIGHT * squeeze / 2.0;
		return new AABB(x - w, centreY - h, z - w, x + w, centreY + h, z + w);
	}

	static boolean fits(Entity head, double x, double centreY, double z, float squeeze) {
		return head.level().noCollision(head, box(x, centreY, z, squeeze));
	}

	// the height of the first collider hit from a point straight up or down, NaN when there is none
	static double surface(Entity head, Vec3 from, double reach) {
		HitResult hit = head.level().clip(new ClipContext(from, from.add(0.0, reach, 0.0),
			ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, head));
		return hit.getType() == HitResult.Type.MISS ? Double.NaN : hit.getLocation().y;
	}

	// nearest surface up or down from anywhere under a box that wide, so he clears a whole stair
	static double surface(Entity head, Vec3 from, double reach, float squeeze) {
		double half = TheHead.WIDTH * squeeze / 2.0 - FOOTPRINT_INSET;
		double best = surface(head, from, reach);
		for (int corner = 0; corner < 4; corner++) {
			double y = surface(head, from.add((corner & 1) == 0 ? -half : half, 0.0, (corner & 2) == 0 ? -half : half), reach);
			if (!Double.isNaN(y)) {
				best = Double.isNaN(best) ? y : reach < 0.0 ? Math.max(best, y) : Math.min(best, y);
			}
		}
		return best;
	}

	// his centre at a squeeze, as close to want as the floor and ceiling around probe allow
	static double settle(Entity head, Vec3 probe, double want, float squeeze) {
		double half = TheHead.HEIGHT * squeeze / 2.0 + GAP;
		double floor = surface(head, probe, -HeadTuning.FLOOR_SEARCH, squeeze);
		double ceiling = surface(head, probe, HeadTuning.FLOOR_SEARCH, squeeze);
		double lo = Double.isNaN(floor) ? -Double.MAX_VALUE : floor + half;
		double hi = Double.isNaN(ceiling) ? Double.MAX_VALUE : ceiling - half;
		return lo > hi ? Double.NaN : Mth.clamp(want, lo, hi);
	}

	// whether his box fits close to a spot, settled there, risen up to two blocks or shifted a block aside
	static boolean fitsNear(Entity head, double x, double z, double centreY, float squeeze, float yaw, boolean onTheSpot) {
		double sideX = Math.cos(Math.toRadians(yaw));
		double sideZ = Math.sin(Math.toRadians(yaw));
		for (double side : onTheSpot ? ON_THE_SPOT : SIDE_STEPS) {
			double px = x + sideX * side;
			double pz = z + sideZ * side;
			double y = settle(head, new Vec3(px, centreY, pz), centreY, squeeze);
			for (double up = 0.0; !Double.isNaN(y) && up <= RISE + 1e-9; up += RISE_STEP) {
				if (fits(head, px, y + up, pz, squeeze)) {
					return true;
				}
			}
		}
		return false;
	}

	// nearest spot to x z where his box settles near wantY, in rings, the spot itself if none fits
	static Vec3 nearestFit(Entity head, double x, double z, double wantY, float squeeze) {
		for (double r = 0.0; r <= FIT_REACH + 1e-9; r += FIT_STEP) {
			int ways = r == 0.0 ? 1 : FIT_WAYS;
			for (int i = 0; i < ways; i++) {
				double a = 2.0 * Math.PI * i / ways;
				double px = x + Math.cos(a) * r;
				double pz = z + Math.sin(a) * r;
				double y = settle(head, new Vec3(px, wantY, pz), wantY, squeeze);
				if (!Double.isNaN(y) && fits(head, px, y, pz, squeeze)) {
					return new Vec3(px, y, pz);
				}
			}
		}
		return new Vec3(x, wantY, z);
	}

	// the largest squeeze that fits at x z near centreY, 0 when not even the smallest does
	static float at(Entity head, double x, double z, double centreY) {
		Vec3 probe = new Vec3(x, centreY, z);
		for (float s = 1.0f; s >= HeadTuning.MIN_SQUEEZE - 1e-4f; s -= HeadTuning.SQUEEZE_STEP) {
			double y = settle(head, probe, centreY, s);
			if (!Double.isNaN(y) && fits(head, x, y, z, s)) {
				return s;
			}
		}
		return 0.0f;
	}

	// the closest centre within reach where his box at that size fits, never on the side toward away from
	static @Nullable Vec3 nearestRoom(Entity head, Vec3 from, float squeeze, Vec3 toward) {
		for (double r = ESCAPE_STEP; r <= HeadTuning.ESCAPE_REACH + 1e-9; r += ESCAPE_STEP) {
			Vec3 best = null;
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx == 0 && dy == 0 && dz == 0) {
							continue;
						}
						Vec3 dir = new Vec3(dx, dy, dz).normalize();
						if (dir.dot(toward) > 1e-6) {
							continue;
						}
						Vec3 p = from.add(dir.scale(r));
						if (fits(head, p.x, p.y, p.z, squeeze) && (best == null || p.distanceToSqr(from) < best.distanceToSqr(from))) {
							best = p;
						}
					}
				}
			}
			if (best != null) {
				return best;
			}
		}
		return null;
	}

	// whether his box at a squeeze can fly the straight line between two centres
	static boolean sweep(Entity head, Vec3 from, Vec3 to, float squeeze) {
		int steps = Math.max(1, Mth.ceil(from.distanceTo(to) / SWEEP_STEP));
		for (int i = 1; i <= steps; i++) {
			Vec3 p = from.lerp(to, (double) i / steps);
			if (!fits(head, p.x, p.y, p.z, squeeze)) {
				return false;
			}
		}
		return true;
	}

	static boolean clearLine(Entity head, Level level, Vec3 from, Vec3 to) {
		return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, head)).getType()
			== HitResult.Type.MISS;
	}
}
