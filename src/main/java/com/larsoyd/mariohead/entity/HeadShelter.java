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

public record HeadShelter(boolean sheltered, List<BlockPos> chests, List<Opening> openings) {
	private static final HeadShelter NONE = new HeadShelter(false, List.of(), List.of());
	private static final int RADIUS = 12;
	private static final int MAX_CELLS = 2048;

	public record Opening(BlockPos wall, Direction outward) {
	}

	public static HeadShelter inspect(Level level, BlockPos player, BlockPos head) {
		var pending = new ArrayDeque<BlockPos>();
		var visited = new HashSet<BlockPos>();
		var chests = new LinkedHashSet<BlockPos>();
		var openings = new LinkedHashSet<Opening>();
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
				boolean window = state.is(net.minecraft.world.level.block.Blocks.GLASS)
					|| state.is(net.minecraft.world.level.block.Blocks.GLASS_PANE)
					|| state.getBlock() instanceof net.minecraft.world.level.block.StainedGlassBlock
					|| state.getBlock() instanceof net.minecraft.world.level.block.StainedGlassPaneBlock;
				boolean glazedDoor = state.is(net.minecraft.world.level.block.Blocks.OAK_DOOR)
					|| state.is(net.minecraft.world.level.block.Blocks.ACACIA_DOOR)
					|| state.is(net.minecraft.world.level.block.Blocks.MANGROVE_DOOR)
					|| state.getBlock() instanceof DoorBlock glazed && (glazed.type() == net.minecraft.world.level.block.state.properties.BlockSetType.IRON
						|| glazed.type() == net.minecraft.world.level.block.state.properties.BlockSetType.COPPER);
				if (direction.getAxis().isHorizontal() && (window || glazedDoor && closedDoor)) {
					openings.add(new Opening(next.immutable(), direction));
				}
				boolean passable = state.getBlock() instanceof DoorBlock ? !closedDoor : state.getCollisionShape(level, next).isEmpty();
				if (passable && visited.add(next)) {
					pending.addLast(next);
				}
			}
		}
		return new HeadShelter(bed || door, List.copyOf(chests), List.copyOf(openings));
	}
}
