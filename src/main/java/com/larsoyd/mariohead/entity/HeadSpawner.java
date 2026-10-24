package com.larsoyd.mariohead.entity;

import com.larsoyd.mariohead.HeadEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

// he turns up in plain view, so the player watches him arrive and say hello
public final class HeadSpawner {
	private static final int TRIES = 64;
	private static final double VIEW_HALF_ANGLE = 30.0;

	private HeadSpawner() {
	}

	public static @Nullable TheHead summonFor(ServerPlayer player) {
		ServerLevel level = player.level();
		RandomSource random = player.getRandom();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		double cos = Math.cos(Math.toRadians(VIEW_HALF_ANGLE));
		for (int i = 0; i < TRIES; i++) {
			float yaw = player.getYRot() + (float) ((random.nextDouble() * 2.0 - 1.0) * VIEW_HALF_ANGLE);
			float pitch = Mth.clamp(player.getXRot() + (float) ((random.nextDouble() * 2.0 - 1.0) * VIEW_HALF_ANGLE), -90.0f, 90.0f);
			Vec3 dir = Vec3.directionFromRotation(pitch, yaw);
			if (dir.dot(look) < cos) {
				continue;
			}
			Vec3 centre = eye.add(dir.scale(Mth.lerp(random.nextDouble(), HeadTuning.SPAWN_MIN, HeadTuning.SPAWN_MAX)));
			if (open(level, player, eye, centre)) {
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

	private static boolean open(ServerLevel level, ServerPlayer player, Vec3 eye, Vec3 centre) {
		return level.noCollision(HeadRoom.box(centre.x, centre.y, centre.z, 1.0f)) && level.clip(new ClipContext(eye, centre,
			ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
	}
}
