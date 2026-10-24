package com.larsoyd.mariohead.entity;

import com.larsoyd.mariohead.HeadEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

// he turns up in plain view, so the player watches him arrive and say hello
public final class HeadSpawner {
	private static final int TRIES = 48;
	private static final float VIEW_HALF_ANGLE = 35.0f;
	private static final double FLOOR_DROP = 8.0;

	private HeadSpawner() {
	}

	public static @Nullable TheHead summonFor(ServerPlayer player) {
		ServerLevel level = player.level();
		RandomSource random = player.getRandom();
		Vec3 eye = player.getEyePosition();
		for (int i = 0; i < TRIES; i++) {
			float yaw = player.getYRot() + (random.nextFloat() * 2.0f - 1.0f) * VIEW_HALF_ANGLE;
			double distance = Mth.lerp(random.nextDouble(), HeadTuning.SPAWN_MIN, HeadTuning.SPAWN_MAX);
			double x = eye.x - Mth.sin(yaw * Mth.DEG_TO_RAD) * distance;
			double z = eye.z + Mth.cos(yaw * Mth.DEG_TO_RAD) * distance;
			Vec3 centre = spot(level, player, x, z);
			if (centre != null && clear(level, player, eye, centre)) {
				TheHead head = HeadEntities.THE_HEAD.create(level, EntitySpawnReason.COMMAND);
				if (head == null) {
					return null;
				}
				head.snapTo(centre.x, centre.y - TheHead.HEIGHT / 2.0, centre.z, yaw + 180.0f, 0.0f);
				head.chase(player);
				level.addFreshEntity(head);
				return head;
			}
		}
		return null;
	}

	private static @Nullable Vec3 spot(ServerLevel level, ServerPlayer player, double x, double z) {
		Vec3 top = new Vec3(x, player.getEyeY() + 1.0, z);
		HitResult floor = level.clip(new ClipContext(top, top.add(0.0, -FLOOR_DROP, 0.0),
			ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (floor.getType() == HitResult.Type.MISS) {
			return null;
		}
		return new Vec3(x, floor.getLocation().y + HeadTuning.HOVER, z);
	}

	private static boolean clear(ServerLevel level, ServerPlayer player, Vec3 eye, Vec3 centre) {
		AABB box = HeadRoom.box(centre.x, centre.y, centre.z, 1.0f);
		return level.noCollision(box) && level.clip(new ClipContext(eye, centre,
			ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
	}
}
