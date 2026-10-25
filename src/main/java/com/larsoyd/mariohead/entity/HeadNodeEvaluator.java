package com.larsoyd.mariohead.entity;

import java.util.Set;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

// a walker whose box sits centred on its node, the stock one hangs wide mobs off the node corner
final class HeadNodeEvaluator extends WalkNodeEvaluator {
	@Override
	public Set<PathType> getPathTypeWithinMobBB(PathfindingContext context, int x, int y, int z) {
		int low = lowCell(this.entityWidth);
		return super.getPathTypeWithinMobBB(context, x + low, y, z + low);
	}

	static int lowCell(int cells) {
		return -((cells - 1) / 2);
	}

	// where the middle of a box this many cells wide is, from its node corner
	static double centre(int cells) {
		return lowCell(cells) + cells / 2.0;
	}
}
