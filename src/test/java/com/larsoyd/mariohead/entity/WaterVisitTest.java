package com.larsoyd.mariohead.entity;

import static org.junit.jupiter.api.Assertions.*;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class WaterVisitTest {
	@Test
	void nearbyConnectedWaterIsOneVisitButSeparatePoolsAreNew() {
		ToIntFunction<BlockPos> pools = pos -> pos.getY() == 0 && pos.getZ() == 0 && (pos.getX() < 4 || pos.getX() > 5) && pos.getX() >= 0 && pos.getX() <= 9 ? 1 : 0;
		assertFalse(WaterVisit.different(new BlockPos(0, 0, 0), new BlockPos(3, 0, 0), pools));
		assertTrue(WaterVisit.different(new BlockPos(0, 0, 0), new BlockPos(8, 0, 0), pools));
	}

	@Test
	void SeparateDistantEntryCountsEvenWithinConnectedOcean() {
		assertTrue(WaterVisit.different(BlockPos.ZERO, new BlockPos(32, 0, 0), pos -> 1));
		assertFalse(WaterVisit.different(BlockPos.ZERO, new BlockPos(2, 0, 0), pos -> 1));
	}

	@Test
	void unknownChunksCannotProveSeparateNearbyWaterAndWorkIsBounded() {
		assertFalse(WaterVisit.different(BlockPos.ZERO, new BlockPos(5, 0, 0), pos -> pos.getX() == 0 || pos.getX() == 5 ? 1 : -1));
		int[] calls = { 0 };
		assertFalse(WaterVisit.different(BlockPos.ZERO, new BlockPos(30, 0, 0), pos -> { calls[0]++; return 1; }));
		assertTrue(calls[0] <= 50000, "water search exceeded per-entry budget: " + calls[0]);
	}
}
