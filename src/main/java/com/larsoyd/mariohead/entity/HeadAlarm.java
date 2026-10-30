package com.larsoyd.mariohead.entity;

import com.larsoyd.mariohead.MarioHead;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

// one villager runs for the bell and the ring sends the village indoors, as a raid does
final class HeadAlarm {
	private static final double RING_REACH = 3.0;
	private static final double BELL_SEARCH = 32.0;
	private static final int RINGER_PATIENCE = 200;
	private static final int WALK_REFRESH = 20;
	private static final float RUN_SPEED = 0.7f;

	private @Nullable Villager ringer;
	private @Nullable BlockPos bell;
	private long giveUpAt;

	boolean ringing(Villager villager) {
		return villager == this.ringer;
	}

	void tick(ServerLevel level, List<Villager> scared) {
		if (scared.isEmpty() && this.ringer == null) return;
		long now = level.getGameTime();
		if (this.ringer == null || !this.ringer.isAlive()) {
			this.ringer = scared.stream().filter(villager -> bellOf(level, villager) != null)
				.min(Comparator.comparingDouble(villager -> villager.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(bellOf(level, villager))))).orElse(null);
			if (this.ringer == null) {
				alert(level, scared);
				return;
			}
			this.bell = bellOf(level, this.ringer);
			this.giveUpAt = now + RINGER_PATIENCE;
			MarioHead.LOG.debug("[head] alarm ringer={} bell={}", this.ringer.getId(), this.bell.toShortString());
		}
		boolean arrived = this.bell.closerToCenterThan(this.ringer.position(), RING_REACH);
		if (arrived && level.getBlockState(this.bell).getBlock() instanceof BellBlock block) block.attemptToRing(this.ringer, level, this.bell, null);
		if (arrived || now >= this.giveUpAt) {
			MarioHead.LOG.debug("[head] alarm_over rang={} scared={}", arrived, scared.size());
			this.ringer.getBrain().setMemory(MemoryModuleType.HEARD_BELL_TIME, now);
			this.ringer = null;
			alert(level, scared);
		} else if ((now - this.giveUpAt) % WALK_REFRESH == 0) {
			this.ringer.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(this.bell, RUN_SPEED, 1));
		}
	}

	private static @Nullable BlockPos bellOf(ServerLevel level, Villager villager) {
		GlobalPos point = villager.getBrain().getMemory(MemoryModuleType.MEETING_POINT).orElse(null);
		return point != null && point.dimension() == level.dimension() && level.getBlockState(point.pos()).is(Blocks.BELL)
			&& point.pos().closerToCenterThan(villager.position(), BELL_SEARCH) ? point.pos() : null;
	}

	// anyone the ring did not reach, or who has no bell at all, still knows to hide
	private static void alert(ServerLevel level, List<Villager> scared) {
		for (Villager villager : scared) villager.getBrain().setMemory(MemoryModuleType.HEARD_BELL_TIME, level.getGameTime());
	}
}
