package com.larsoyd.mariohead.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// the floor, the ceiling and how big he may be at a spot, his box holding all of him
final class HeadRoom {
	private static final double GAP = 1e-3;

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

	// his centre at a squeeze, as close to want as the floor and ceiling around probe allow
	static double settle(Entity head, Vec3 probe, double want, float squeeze) {
		double half = TheHead.HEIGHT * squeeze / 2.0 + GAP;
		double floor = surface(head, probe, -HeadTuning.FLOOR_SEARCH);
		double ceiling = surface(head, probe, HeadTuning.FLOOR_SEARCH);
		double lo = Double.isNaN(floor) ? -Double.MAX_VALUE : floor + half;
		double hi = Double.isNaN(ceiling) ? Double.MAX_VALUE : ceiling - half;
		return lo > hi ? Double.NaN : Mth.clamp(want, lo, hi);
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

	static boolean clearLine(Entity head, Level level, Vec3 from, Vec3 to) {
		return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, head)).getType()
			== HitResult.Type.MISS;
	}
}
