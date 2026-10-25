package com.larsoyd.mariohead.entity;

import java.util.List;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;

public record HeadShelter(boolean sheltered, List<BlockPos> chests) {
	private static final HeadShelter NONE = new HeadShelter(false, List.of());
	private static final int RADIUS = 12;
	private static final int MAX_CELLS = 2048;

	public static HeadShelter inspect(Level level, BlockPos player, BlockPos head) {
		var pending = new ArrayDeque<BlockPos>();
		var visited = new HashSet<BlockPos>();
		var chests = new LinkedHashSet<BlockPos>();
		pending.add(player);
		visited.add(player);
		boolean bed = false;
		boolean door = false;
		while (!pending.isEmpty()) {
			BlockPos pos = pending.removeFirst();
			if (pos.equals(head) || Math.abs(pos.getX() - player.getX()) >= RADIUS
				|| Math.abs(pos.getY() - player.getY()) >= RADIUS || Math.abs(pos.getZ() - player.getZ()) >= RADIUS
				|| visited.size() > MAX_CELLS || !level.hasChunkAt(pos)) {
				return NONE;
			}
			for (Direction direction : Direction.values()) {
				BlockPos next = pos.relative(direction);
				var state = level.getBlockState(next);
				if (state.getBlock() instanceof AbstractBedBlock && next.distSqr(player) <= 36) {
					bed = true;
				}
				if (state.getBlock() instanceof ChestBlock && chests.size() < 32) {
					chests.add(next);
				}
				boolean closedDoor = state.getBlock() instanceof DoorBlock && !state.getValue(DoorBlock.OPEN);
				door |= closedDoor;
				boolean passable = state.getBlock() instanceof DoorBlock ? !closedDoor : state.getCollisionShape(level, next).isEmpty();
				if (passable && visited.add(next)) {
					pending.addLast(next);
				}
			}
		}
		return new HeadShelter(bed || door, List.copyOf(chests));
	}
}
