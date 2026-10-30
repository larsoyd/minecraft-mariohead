package com.larsoyd.mariohead.entity;

import com.larsoyd.mariohead.mixin.CubeMoveAccess;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

// every mob near him forgets what it was doing and runs, goals and brains alike
public final class HeadDread {
	private static final double REACH = 16.0;
	private static final double REACH_UP = 8.0;
	private static final int FLEE_BLOCKS = 16;
	private static final int FLEE_RISE = 7;
	private static final int RETHINK_TICKS = 10;
	private static final int REPLAN_TICKS = 40;
	private static final float FLEE_SPEED = 1.4f;
	private static final float BRAIN_FLEE_SPEED = 1.0f;

	private HeadDread() {
	}

	private static boolean brave(Mob mob) {
		return mob instanceof TheHead || mob instanceof IronGolem;
	}

	public static boolean near(Mob mob) {
		return !brave(mob) && !mob.isNoAi() && !mob.level().isClientSide() && !mob.level().getEntitiesOfClass(TheHead.class, mob.getBoundingBox().inflate(REACH, REACH_UP, REACH), head -> true).isEmpty();
	}

	static void spread(ServerLevel level, TheHead head) {
		Vec3 from = head.centre();
		List<Villager> scared = new ArrayList<>();
		for (Mob mob : level.getEntitiesOfClass(Mob.class, head.getBoundingBox().inflate(REACH, REACH_UP, REACH), mob -> !brave(mob) && !mob.isNoAi())) {
			calm(mob);
			if (mob instanceof Villager villager) {
				if (!villager.getBrain().hasMemoryValue(MemoryModuleType.HEARD_BELL_TIME)) scared.add(villager);
				if (head.alarm().ringing(villager) || villager.getBrain().hasMemoryValue(MemoryModuleType.HIDING_PLACE)) continue;
			}
			int tick = mob.tickCount + mob.getId();
			if (mob.hasPassenger(rider -> rider instanceof Player)) mob.getNavigation().stop();
			else if (!(mob instanceof PathfinderMob) || mob instanceof AbstractCubeMob || tick % RETHINK_TICKS == 0 && !fleeing(mob, from) || tick % REPLAN_TICKS == 0) flee(mob, from);
		}
		head.alarm().tick(level, scared);
	}

	// a path that leads away is left alone, so a herd does not replan all at once
	private static boolean fleeing(Mob mob, Vec3 from) {
		BlockPos to = mob.getNavigation().getTargetPos();
		return !mob.getNavigation().isDone() && to != null && to.distToCenterSqr(from) > mob.distanceToSqr(from);
	}

	private static void calm(Mob mob) {
		mob.setTarget(null);
		mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
		if (mob instanceof NeutralMob neutral) neutral.stopBeingAngry();
		if (mob instanceof Creeper creeper && !creeper.isIgnited()) creeper.setSwellDir(-1);
		if (mob instanceof WitherBoss wither) {
			for (int head = 0; head < 3; head++) wither.setAlternativeTarget(head, 0);
		}
		if (mob.isSleeping()) mob.stopSleeping();
	}

	private static void flee(Mob mob, Vec3 from) {
		Vec3 flat = mob.position().subtract(from).multiply(1, 0, 1).normalize();
		Vec3 away = mob instanceof PathfinderMob walker ? DefaultRandomPos.getPosAway(walker, FLEE_BLOCKS, FLEE_RISE, from) : null;
		if (away == null) away = mob.position().add(flat.scale(FLEE_BLOCKS));
		if (mob.getMoveControl() instanceof CubeMoveAccess cube) {
			cube.mariohead$setDirection((float) Math.toDegrees(Math.atan2(flat.z, flat.x)) - 90.0f, false);
			cube.mariohead$setWantedMovement(FLEE_SPEED);
		} else if (mob.getBrain().checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)) {
			mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(away, BRAIN_FLEE_SPEED, 0));
		} else if (mob instanceof PathfinderMob) {
			mob.getNavigation().moveTo(away.x, away.y, away.z, FLEE_SPEED);
		} else {
			mob.getMoveControl().setWantedPosition(away.x, mob.getY(), away.z, FLEE_SPEED);
		}
	}
}
