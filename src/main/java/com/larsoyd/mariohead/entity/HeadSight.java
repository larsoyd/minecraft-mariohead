package com.larsoyd.mariohead.entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// could anyone be looking at him, from their eyes or from the third person camera they say they use
public final class HeadSight {
	public enum View { FIRST, BACK, FRONT }

	private static final Map<UUID, View> VIEWS = new ConcurrentHashMap<>();
	private static final double RANGE = 128.0;
	private static final double HALF_WIDE = 80.0;
	private static final double HALF_TALL = 55.0;
	private static final double CAMERA_BACK = 4.0;
	private static final double HULL_INSET = 0.8;

	private HeadSight() {
	}

	public static void setView(UUID player, View view) {
		if (view == View.FIRST) {
			VIEWS.remove(player);
		} else {
			VIEWS.put(player, view);
		}
	}

	public static View view(UUID player) {
		return VIEWS.getOrDefault(player, View.FIRST);
	}

	public static boolean seen(ServerLevel level, AABB box) {
		Vec3 c = box.getCenter();
		double radius = Math.sqrt(box.getXsize() * box.getXsize() + box.getYsize() * box.getYsize() + box.getZsize() * box.getZsize()) / 2.0;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || player.distanceToSqr(c) > RANGE * RANGE) {
				continue;
			}
			Vec3 eye = player.getEyePosition();
			Vec3 look = player.getLookAngle();
			View view = view(player.getUUID());
			boolean framed = eye.distanceTo(c) <= radius || inView(eye, look, c, radius)
				|| view == View.BACK && inView(camera(level, player, eye, look.scale(-1.0)), look, c, radius)
				|| view == View.FRONT && inView(camera(level, player, eye, look), look.scale(-1.0), c, radius);
			if (framed && sightLine(level, player, eye, box)) {
				return true;
			}
		}
		return false;
	}

	// a third person camera out along a direction, pulled in by whatever is in the way like the real one
	private static Vec3 camera(ServerLevel level, ServerPlayer player, Vec3 eye, Vec3 out) {
		Vec3 at = eye.add(out.scale(CAMERA_BACK));
		HitResult hit = level.clip(new ClipContext(eye, at, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS ? at : hit.getLocation();
	}

	// a screen is wider than it is tall, so the frustum gets its own half angle on each axis
	private static boolean inView(Vec3 from, Vec3 look, Vec3 c, double radius) {
		Vec3 to = c.subtract(from);
		double distance = to.length();
		double ahead = to.dot(look);
		if (distance <= radius || ahead <= 0.0) {
			return false;
		}
		Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
		right = right.lengthSqr() < 1e-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
		Vec3 up = right.cross(look);
		double slack = Math.toDegrees(Math.asin(radius / distance));
		double across = Math.toDegrees(Math.atan2(Math.abs(to.dot(right)), ahead));
		double tall = Math.toDegrees(Math.atan2(Math.abs(to.dot(up)), ahead));
		return across <= HALF_WIDE + slack && tall <= HALF_TALL + slack;
	}

	// his middle first, then the corners of his box pulled in a little
	private static boolean sightLine(ServerLevel level, ServerPlayer player, Vec3 eye, AABB box) {
		Vec3 c = box.getCenter();
		double hx = box.getXsize() / 2.0 * HULL_INSET;
		double hy = box.getYsize() / 2.0 * HULL_INSET;
		double hz = box.getZsize() / 2.0 * HULL_INSET;
		for (int i = 0; i < 9; i++) {
			int corner = i - 1;
			Vec3 point = i == 0 ? c : c.add((corner & 1) == 0 ? -hx : hx, (corner & 2) == 0 ? -hy : hy, (corner & 4) == 0 ? -hz : hz);
			if (level.clip(new ClipContext(eye, point, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS) {
				return true;
			}
		}
		return false;
	}
}
