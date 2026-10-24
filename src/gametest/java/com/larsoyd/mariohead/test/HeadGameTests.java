package com.larsoyd.mariohead.test;

import com.larsoyd.mariohead.HeadEntities;
import com.larsoyd.mariohead.entity.HeadCue;
import com.larsoyd.mariohead.entity.HeadPhase;
import com.larsoyd.mariohead.entity.HeadTuning;
import com.larsoyd.mariohead.entity.RevSpring;
import com.larsoyd.mariohead.entity.TheHead;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class HeadGameTests {
	private static final String ARENA = "mariohead-gametest:arena";

	private static ServerPlayer player(GameTestHelper helper, GameType mode, Vec3 relative, float yaw) {
		var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), HeadTestSetup.PLAYER_PREFIX + UUID.randomUUID().toString().substring(0, 12)), false);
		var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
		var connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(mode);
		Vec3 at = helper.absoluteVec(relative);
		player.teleportTo(helper.getLevel(), at.x, at.y, at.z, java.util.Set.of(), yaw, 0.0f, true);
		testInfo(helper).addListener(new GameTestListener() {
			@Override
			public void testStructureLoaded(GameTestInfo info) {
			}

			@Override
			public void testPassed(GameTestInfo info, GameTestRunner runner) {
				leave(helper, player);
			}

			@Override
			public void testFailed(GameTestInfo info, GameTestRunner runner) {
				leave(helper, player);
			}

			@Override
			public void testAddedForRerun(GameTestInfo original, GameTestInfo copy, GameTestRunner runner) {
			}
		});
		return player;
	}

	// the helper keeps its test private, and only the test can say when it is over
	private static GameTestInfo testInfo(GameTestHelper helper) {
		try {
			Field field = GameTestHelper.class.getDeclaredField("testInfo");
			field.setAccessible(true);
			return (GameTestInfo) field.get(helper);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("no test info on the helper", e);
		}
	}

	private static void leave(GameTestHelper helper, ServerPlayer player) {
		var players = helper.getLevel().getServer().getPlayerList();
		if (players.getPlayer(player.getUUID()) == player) {
			players.remove(player);
		}
	}

	// his box holds the whole visible head, so it must never meet a block
	private static TheHead head(GameTestHelper helper, Vec3 relative, ServerPlayer target) {
		TheHead head = helper.spawn(HeadEntities.THE_HEAD, relative);
		head.chase(target);
		helper.onEachTick(() -> {
			if (!head.isRemoved()) {
				AABB box = head.getBoundingBox().deflate(1e-6);
				helper.assertTrue(helper.getLevel().noCollision(head, box),
					"head clips a block at " + helper.relativeVec(head.position()) + " squeeze " + head.squeeze() + " phase " + head.phase());
			}
		});
		return head;
	}

	private static void fill(GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1, net.minecraft.world.level.block.state.BlockState state) {
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					helper.setBlock(new BlockPos(x, y, z), state);
				}
			}
		}
	}

	private static void caughtWithin(GameTestHelper helper, ServerPlayer target, TheHead head) {
		helper.succeedWhen(() -> {
			helper.assertTrue(target.isDeadOrDying(), "target alive, head " + helper.relativeVec(head.position())
				+ " squeeze " + head.squeeze() + " phase " + head.phase());
			leave(helper, target);
		});
	}

	// alone in its own dimension, so only his own ticket can keep the chunks he flies into ticking
	@GameTest(structure = ARENA, maxTicks = 2000, dimension = "minecraft:the_nether")
	public void keepsFlyingWhenThePlayerOutrunsTheLoadedWorld(GameTestHelper helper) {
		var level = helper.getLevel();
		// the test server ticks flat out while chunks load on the clock, so pace it to a fifth of real time
		helper.onEachTick(() -> {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
		fill(helper, -1, 0, 0, -1, 7, 19, Blocks.AIR.defaultBlockState());
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(2.5, 0.0, 10.5), 90.0f);
		TheHead head = head(helper, new Vec3(14.5, 0.5, 10.5), target);
		Vec3 away = helper.absoluteVec(new Vec3(-700.5, 0.0, 10.5));
		helper.runAtTickTime(90, () -> target.teleportTo(level, away.x, away.y, away.z, java.util.Set.of(), 90.0f, 0.0f, true));
		List<Double> xs = new ArrayList<>();
		int[] missed = { 0 };
		int[] lastTicks = { -1 };
		helper.onEachTick(() -> {
			if (head.isRemoved() || helper.getTick() < 100) {
				return;
			}
			if (lastTicks[0] >= 0 && head.tickCount != lastTicks[0] + 1) {
				missed[0]++;
			}
			lastTicks[0] = head.tickCount;
			if (helper.getTick() % 100 == 0) {
				xs.add(helper.relativeVec(head.position()).x);
			}
		});
		BlockPos[] last = new BlockPos[1];
		helper.runAtTickTime(1300, () -> {
			helper.assertTrue(missed[0] == 0, "he missed " + missed[0] + " ticks, x " + xs);
			for (int i = 1; i < xs.size(); i++) {
				helper.assertTrue(xs.get(i) < xs.get(i - 1) - 5.0, "stalled between samples " + (i - 1) + " and " + i + ": " + xs);
			}
			helper.assertTrue(xs.getLast() < -150.0, "only reached x " + xs.getLast());
			last[0] = head.blockPosition();
			leave(helper, target);
		});
		helper.runAtTickTime(1310, () -> helper.assertTrue(head.isRemoved(), "head stayed after the target left"));
		helper.runAtTickTime(1300 + HeadEntities.CHASE_TICKET.timeout(), () -> helper.succeedWhen(
			() -> helper.assertFalse(level.isPositionEntityTicking(last[0]), "his chunk still ticks at " + last[0])));
	}

	private static List<TheHead> summon(GameTestHelper helper, ServerPlayer player) {
		var server = helper.getLevel().getServer();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "mariohead summon " + player.getScoreboardName());
		return helper.getLevel().getEntities(HeadEntities.THE_HEAD, player.getBoundingBox().inflate(16.0), h -> h.chased() == player);
	}

	private static void summonsWhereLooking(GameTestHelper helper, float pitch, boolean open, boolean expectHead) {
		if (open) {
			fill(helper, 0, 8, 0, 19, 8, 19, Blocks.AIR.defaultBlockState());
		} else {
			fill(helper, 6, 0, 0, 6, 7, 19, Blocks.STONE.defaultBlockState());
		}
		ServerPlayer target = player(helper, GameType.CREATIVE, new Vec3(2.5, 0.0, 10.5), -90.0f);
		target.setXRot(pitch);
		helper.runAtTickTime(2, () -> {
			List<TheHead> heads = summon(helper, target);
			helper.assertTrue(heads.size() == (expectHead ? 1 : 0), "pitch " + pitch + " open " + open + " gave " + heads.size() + " heads");
			for (TheHead head : heads) {
				Vec3 to = head.centre().subtract(target.getEyePosition()).normalize();
				double angle = Math.toDegrees(Math.acos(to.dot(target.getLookAngle())));
				helper.assertTrue(angle <= 35.0, "pitch " + pitch + ": head " + angle + " degrees off the view");
			}
			helper.succeed();
		});
	}

	@GameTest(structure = ARENA, maxTicks = 20)
	public void summonsAboveWhenLookingUp(GameTestHelper helper) {
		summonsWhereLooking(helper, -75.0f, true, true);
	}

	@GameTest(structure = ARENA, maxTicks = 20)
	public void refusesWhenLookingAtTheFloor(GameTestHelper helper) {
		summonsWhereLooking(helper, 80.0f, true, false);
	}

	@GameTest(structure = ARENA, maxTicks = 20)
	public void refusesWhenAWallBlocksTheView(GameTestHelper helper) {
		summonsWhereLooking(helper, 0.0f, false, false);
	}

	enum Loss { DISCONNECT, DEATH, OTHER_DIMENSION, SPECTATOR }

	private static void lose(GameTestHelper helper, ServerPlayer target, Loss loss) {
		switch (loss) {
			case DISCONNECT -> leave(helper, target);
			case DEATH -> {
				target.setHealth(0.0f);
				target.die(target.damageSources().genericKill());
			}
			case OTHER_DIMENSION -> {
				var nether = helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
				target.teleportTo(nether, 0.5, 64.0, 0.5, java.util.Set.of(), 0.0f, 0.0f, true);
			}
			case SPECTATOR -> target.setGameMode(GameType.SPECTATOR);
		}
	}

	// before hello he turns to whoever else is near, after it the chase was theirs and it ends with them
	private static void loseTarget(GameTestHelper helper, Loss loss, int atTick, boolean introduced) {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(2.5, 0.0, 10.5), -90.0f);
		ServerPlayer other = player(helper, GameType.SURVIVAL, new Vec3(7.5, 0.0, 4.5), -90.0f);
		TheHead head = head(helper, new Vec3(10.5, 0.5, 10.5), target);
		helper.runAtTickTime(atTick, () -> {
			HeadPhase phase = head.phase();
			helper.assertTrue(introduced == (phase == HeadPhase.TALK || phase == HeadPhase.CHARGE), "phase " + phase + " at " + atTick);
			lose(helper, target, loss);
		});
		helper.runAtTickTime(Math.max(atTick + 3, 12), () -> {
			if (introduced) {
				helper.assertTrue(head.isRemoved(), loss + " in " + head.phase() + " left him in the world");
			} else {
				helper.assertTrue(!head.isRemoved() && head.chased() == other, loss + " before hello: chasing " + head.chased());
			}
			helper.succeed();
		});
	}

	@GameTest(structure = ARENA, maxTicks = 40)
	public void disconnectWhileLooking(GameTestHelper helper) {
		loseTarget(helper, Loss.DISCONNECT, 12, false);
	}

	@GameTest(structure = ARENA, maxTicks = 40)
	public void deathWhileLooking(GameTestHelper helper) {
		loseTarget(helper, Loss.DEATH, 12, false);
	}

	@GameTest(structure = ARENA, maxTicks = 40)
	public void dimensionChangeWhileLooking(GameTestHelper helper) {
		loseTarget(helper, Loss.OTHER_DIMENSION, 12, false);
	}

	@GameTest(structure = ARENA, maxTicks = 40)
	public void spectatorWhileLooking(GameTestHelper helper) {
		loseTarget(helper, Loss.SPECTATOR, 12, false);
	}

	@GameTest(structure = ARENA, maxTicks = 40)
	public void disconnectWhileSpawning(GameTestHelper helper) {
		loseTarget(helper, Loss.DISCONNECT, 4, false);
	}

	@GameTest(structure = ARENA, maxTicks = 60)
	public void disconnectWhileTalking(GameTestHelper helper) {
		loseTarget(helper, Loss.DISCONNECT, 30, true);
	}

	@GameTest(structure = ARENA, maxTicks = 60)
	public void deathWhileTalking(GameTestHelper helper) {
		loseTarget(helper, Loss.DEATH, 30, true);
	}

	@GameTest(structure = ARENA, maxTicks = 60)
	public void dimensionChangeWhileTalking(GameTestHelper helper) {
		loseTarget(helper, Loss.OTHER_DIMENSION, 30, true);
	}

	@GameTest(structure = ARENA, maxTicks = 60)
	public void spectatorWhileTalking(GameTestHelper helper) {
		loseTarget(helper, Loss.SPECTATOR, 30, true);
	}

	@GameTest(structure = ARENA, maxTicks = 100)
	public void disconnectWhileCharging(GameTestHelper helper) {
		loseTarget(helper, Loss.DISCONNECT, 80, true);
	}

	@GameTest(structure = ARENA, maxTicks = 100)
	public void deathWhileCharging(GameTestHelper helper) {
		loseTarget(helper, Loss.DEATH, 80, true);
	}

	@GameTest(structure = ARENA, maxTicks = 100)
	public void dimensionChangeWhileCharging(GameTestHelper helper) {
		loseTarget(helper, Loss.OTHER_DIMENSION, 80, true);
	}

	@GameTest(structure = ARENA, maxTicks = 100)
	public void spectatorWhileCharging(GameTestHelper helper) {
		loseTarget(helper, Loss.SPECTATOR, 80, true);
	}

	@GameTest(structure = ARENA, maxTicks = 20, required = false)
	public void failsOnPurposeSoTeardownIsChecked(GameTestHelper helper) {
		player(helper, GameType.SURVIVAL, new Vec3(2.5, 0.0, 10.5), 0.0f);
		helper.runAtTickTime(2, () -> helper.fail("this optional test fails on purpose"));
	}

	@GameTest(structure = ARENA, maxTicks = 1200)
	public void squeezesThroughAWindingCave(GameTestHelper helper) {
		var stone = Blocks.STONE.defaultBlockState();
		var air = Blocks.AIR.defaultBlockState();
		fill(helper, 4, 0, 6, 16, 4, 14, stone);
		fill(helper, 12, 0, 10, 16, 1, 10, air);
		fill(helper, 12, 0, 8, 12, 1, 10, air);
		fill(helper, 7, 0, 8, 12, 1, 8, air);
		fill(helper, 7, 0, 8, 7, 1, 12, air);
		fill(helper, 4, 0, 12, 7, 1, 12, air);
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(2.5, 0.0, 12.5), 90.0f);
		TheHead head = head(helper, new Vec3(18.5, 0.5, 10.5), target);
		float[] smallest = { 1.0f };
		helper.onEachTick(() -> smallest[0] = Math.min(smallest[0], head.squeeze()));
		helper.succeedWhen(() -> {
			helper.assertTrue(target.isDeadOrDying(), "target alive, head " + helper.relativeVec(head.position()) + " squeeze " + head.squeeze());
			helper.assertTrue(smallest[0] <= 0.34f, "never squeezed below " + smallest[0]);
			leave(helper, target);
		});
	}

	@GameTest(structure = ARENA, maxTicks = 700)
	public void ducksUnderACanopy(GameTestHelper helper) {
		var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
		fill(helper, 6, 2, 0, 12, 3, 19, leaves);
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(2.5, 0.0, 10.5), -90.0f);
		TheHead head = head(helper, new Vec3(16.5, 0.5, 10.5), target);
		caughtWithin(helper, target, head);
	}

	@GameTest(structure = ARENA, maxTicks = 700)
	public void climbsStairs(GameTestHelper helper) {
		var stone = Blocks.STONE.defaultBlockState();
		fill(helper, 0, 0, 0, 4, 2, 19, stone);
		fill(helper, 5, 0, 0, 5, 1, 19, stone);
		fill(helper, 6, 0, 0, 6, 0, 19, stone);
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(2.5, 3.0, 10.5), -90.0f);
		TheHead head = head(helper, new Vec3(16.5, 0.5, 10.5), target);
		caughtWithin(helper, target, head);
	}

	@GameTest(structure = ARENA, maxTicks = 700)
	public void climbsOntoAPillarWithNoWayUp(GameTestHelper helper) {
		fill(helper, 2, 0, 9, 4, 3, 11, Blocks.STONE.defaultBlockState());
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 4.0, 10.5), -90.0f);
		TheHead head = head(helper, new Vec3(16.5, 0.5, 10.5), target);
		caughtWithin(helper, target, head);
	}

	@GameTest(structure = ARENA, maxTicks = 700)
	public void dropsIntoAPit(GameTestHelper helper) {
		fill(helper, 0, 0, 0, 19, 4, 19, Blocks.STONE.defaultBlockState());
		fill(helper, 2, 0, 9, 4, 4, 11, Blocks.AIR.defaultBlockState());
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0.0, 10.5), -90.0f);
		TheHead head = head(helper, new Vec3(16.5, 5.5, 10.5), target);
		caughtWithin(helper, target, head);
	}

	@GameTest(structure = ARENA, maxTicks = 900)
	public void goesRoundAndUpToTheFloorAbove(GameTestHelper helper) {
		var stone = Blocks.STONE.defaultBlockState();
		fill(helper, 0, 3, 0, 12, 3, 19, stone);
		for (int step = 0; step < 4; step++) {
			fill(helper, 16 - step, 0, 0, 16 - step, step, 4, stone);
		}
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(2.5, 4.0, 10.5), -90.0f);
		TheHead head = head(helper, new Vec3(3.5, 0.5, 10.5), target);
		head.addTag("debug");
		System.out.println("[headdebug] origin=" + helper.absolutePos(BlockPos.ZERO) + " slab=" + helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(5, 3, 10))) + " exit=" + helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(13, 1, 10))) + " ramp=" + helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(13, 3, 2))));
		helper.runAtTickTime(30, () -> {
			var zombie = helper.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new Vec3(3.5, 0.0, 10.5));
			zombie.setOnGround(true);
			var zp = zombie.getNavigation().createPath(target, 0);
			StringBuilder sb = new StringBuilder("[headdebug] zombie reach=" + (zp == null ? "null" : zp.canReach()) + " ");
			for (int i = 0; zp != null && i < zp.getNodeCount(); i++) { var n = zp.getNode(i); var r = helper.absolutePos(BlockPos.ZERO); sb.append(n.x - r.getX()).append(",").append(n.y - r.getY()).append(",").append(n.z - r.getZ()).append("|"); }
			System.out.println(sb);
			zombie.discard();
		});
		helper.runAtTickTime(30, () -> {
			for (Vec3 at : new Vec3[] { new Vec3(17.5, 0.0, 10.5), new Vec3(17.5, 0.0, 2.5), new Vec3(13.5, 4.0, 2.5) }) {
				var zombie = helper.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, at);
				zombie.setOnGround(true);
				var zp = zombie.getNavigation().createPath(target, 0);
				StringBuilder sb = new StringBuilder("[headdebug] zombie from " + at + " reach=" + (zp == null ? "null" : zp.canReach()) + " ");
				for (int i = 0; zp != null && i < zp.getNodeCount(); i++) { var n = zp.getNode(i); var r = helper.absolutePos(BlockPos.ZERO); sb.append(n.x - r.getX()).append(",").append(n.y - r.getY()).append(",").append(n.z - r.getZ()).append("|"); }
				System.out.println(sb);
				zombie.discard();
			}
			var r = helper.absolutePos(BlockPos.ZERO);
			StringBuilder col = new StringBuilder("[headdebug] column x13..17 z2: ");
			for (int x = 12; x <= 17; x++) { col.append(x).append(":"); for (int y = 0; y <= 5; y++) col.append(helper.getLevel().getBlockState(r.offset(x, y, 2)).isAir() ? "." : "#"); col.append(" "); }
			System.out.println(col);
		});
		helper.runAtTickTime(30, () -> {
			for (float mult : new float[] { 1.0f, 4.0f, 16.0f }) {
				for (Vec3 at : new Vec3[] { new Vec3(17.5, 0.0, 10.5), new Vec3(3.5, 0.0, 10.5) }) {
					var zombie = helper.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, at);
					zombie.setOnGround(true);
					zombie.getNavigation().setMaxVisitedNodesMultiplier(mult);
					var zp = zombie.getNavigation().createPath(target, 0);
					System.out.println("[headdebug] zombie mult=" + mult + " from " + at + " reach=" + (zp == null ? "null" : zp.canReach() + " nodes=" + zp.getNodeCount()));
					zombie.discard();
				}
			}
		});
		helper.runAtTickTime(30, () -> {
			var r = helper.absolutePos(BlockPos.ZERO);
			for (BlockPos goal : new BlockPos[] { new BlockPos(17, 0, 2), new BlockPos(16, 1, 2), new BlockPos(13, 4, 2), new BlockPos(2, 4, 10) }) {
				var zombie = helper.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new Vec3(17.5, 0.0, 10.5));
				zombie.setOnGround(true);
				var zp = zombie.getNavigation().createPath(r.offset(goal), 0);
				StringBuilder sb = new StringBuilder("[headdebug] goal " + goal.toShortString() + " reach=" + (zp == null ? "null" : zp.canReach()) + " ");
				for (int i = 0; zp != null && i < zp.getNodeCount(); i++) { var n = zp.getNode(i); sb.append(n.x - r.getX()).append(",").append(n.y - r.getY()).append(",").append(n.z - r.getZ()).append("|"); }
				System.out.println(sb);
				zombie.discard();
			}
		});
		helper.runAtTickTime(30, () -> {
			var r = helper.absolutePos(BlockPos.ZERO);
			var allay = helper.spawn(net.minecraft.world.entity.EntityTypes.ALLAY, new Vec3(3.5, 1.0, 10.5));
			var zp = allay.getNavigation().createPath(target, 0);
			StringBuilder sb = new StringBuilder("[headdebug] allay nav=" + allay.getNavigation().getClass().getSimpleName() + " reach=" + (zp == null ? "null" : zp.canReach()) + " ");
			for (int i = 0; zp != null && i < zp.getNodeCount(); i++) { var n = zp.getNode(i); sb.append(n.x - r.getX()).append(",").append(n.y - r.getY()).append(",").append(n.z - r.getZ()).append("|"); }
			System.out.println(sb);
			allay.discard();
		});
		helper.runAtTickTime(30, () -> {
			var r = helper.absolutePos(BlockPos.ZERO);
			StringBuilder sb = new StringBuilder("[headdebug] goals ");
			for (BlockPos goal : new BlockPos[] { new BlockPos(12, 4, 2), new BlockPos(10, 4, 4), new BlockPos(8, 4, 6), new BlockPos(6, 4, 8), new BlockPos(4, 4, 10), new BlockPos(12, 4, 10), new BlockPos(8, 4, 10) }) {
				var zombie = helper.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new Vec3(17.5, 0.0, 10.5));
				zombie.setOnGround(true);
				var zp = zombie.getNavigation().createPath(r.offset(goal), 0);
				sb.append(goal.toShortString()).append("=").append(zp == null ? "null" : zp.canReach() + "/" + zp.getNodeCount()).append("  ");
				zombie.discard();
			}
			System.out.println(sb);
		});
		caughtWithin(helper, target, head);
	}

	@GameTest(structure = ARENA, maxTicks = 700)
	public void climbsAStairRamp(GameTestHelper helper) {
		var stone = Blocks.STONE.defaultBlockState();
		fill(helper, 0, 0, 0, 6, 3, 19, stone);
		for (int step = 0; step < 4; step++) {
			var stair = Blocks.STONE_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, net.minecraft.core.Direction.WEST);
			if (step > 0) {
				fill(helper, 10 - step, 0, 0, 10 - step, step - 1, 19, stone);
			}
			fill(helper, 10 - step, step, 0, 10 - step, step, 19, stair);
		}
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 4.0, 10.5), -90.0f);
		TheHead head = head(helper, new Vec3(16.5, 0.5, 10.5), target);
		caughtWithin(helper, target, head);
	}

	@GameTest(structure = ARENA, maxTicks = 700)
	public void crossesAPool(GameTestHelper helper) {
		fill(helper, 6, 0, 0, 13, 0, 19, Blocks.WATER.defaultBlockState());
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(2.5, 0.0, 10.5), -90.0f);
		TheHead head = head(helper, new Vec3(17.5, 0.5, 10.5), target);
		caughtWithin(helper, target, head);
	}

	@GameTest(structure = ARENA, maxTicks = 120)
	public void phasesFollowHisVoice(GameTestHelper helper) {
		ServerPlayer target = player(helper, GameType.CREATIVE, new Vec3(2.5, 0.0, 10.5), -90.0f);
		TheHead head = head(helper, new Vec3(17.5, 0.5, 10.5), target);
		List<String> seen = new ArrayList<>();
		helper.onEachTick(() -> {
			String now = head.phase() + "/" + head.cue();
			if (seen.isEmpty() || !seen.getLast().endsWith(now)) {
				seen.add(helper.getTick() + ":" + now);
			}
		});
		helper.runAtTickTime(5, () -> helper.assertTrue(head.phase() == HeadPhase.SPAWN, "spawn at 5, saw " + seen));
		helper.runAtTickTime(15, () -> helper.assertTrue(head.phase() == HeadPhase.LOOK, "look at 15, saw " + seen));
		helper.runAtTickTime(25, () -> helper.assertTrue(head.cue() == HeadCue.HELLO, "hello at 25, saw " + seen));
		helper.runAtTickTime(62, () -> helper.assertTrue(head.phase() == HeadPhase.TALK, "still talking at 62, saw " + seen));
		helper.runAtTickTime(68, () -> helper.assertTrue(
			head.phase() == HeadPhase.CHARGE && head.cue() == HeadCue.INHALE, "breath at 68, saw " + seen));
		helper.runAtTickTime(78, () -> {
			helper.assertTrue(head.cue() == HeadCue.MMM1 && head.revs() == 0, "first hum at 78, saw " + seen);
			leave(helper, target);
			helper.succeed();
		});
	}

	@GameTest(structure = ARENA, maxTicks = 400)
	public void catchesAStandingPlayer(GameTestHelper helper) {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(2.5, 0.0, 10.5), -90.0f);
		TheHead head = head(helper, new Vec3(14.5, 0.5, 10.5), target);
		helper.succeedWhen(() -> {
			helper.assertTrue(target.isDeadOrDying(), "target alive, head " + head.position() + " phase " + head.phase());
			helper.assertTrue(head.isRemoved(), "head still there after the catch");
			leave(helper, target);
		});
	}

	@GameTest(structure = ARENA, maxTicks = 700)
	public void findsTheDoorInAWall(GameTestHelper helper) {
		for (int z = 0; z < 20; z++) {
			if (z == 17 || z == 18) {
				continue;
			}
			for (int y = 0; y < 8; y++) {
				helper.setBlock(new BlockPos(9, y, z), Blocks.STONE);
			}
		}
		for (int y = 2; y < 8; y++) {
			helper.setBlock(new BlockPos(9, y, 17), Blocks.STONE);
			helper.setBlock(new BlockPos(9, y, 18), Blocks.STONE);
		}
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0.0, 3.5), -90.0f);
		TheHead head = head(helper, new Vec3(15.5, 0.5, 3.5), target);
		AABB wall = new AABB(helper.absoluteVec(new Vec3(9, 0, 0)), helper.absoluteVec(new Vec3(10, 8, 17)));
		helper.onEachTick(() -> helper.assertFalse(head.getBoundingBox().intersects(wall), "head inside the wall at " + head.position()));
		helper.succeedWhen(() -> {
			helper.assertTrue(target.isDeadOrDying(), "target alive, head " + helper.relativeVec(head.position()) + " phase " + head.phase());
			leave(helper, target);
		});
	}

	@GameTest(structure = ARENA, maxTicks = 60)
	public void shrugsOffAttacks(GameTestHelper helper) {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(2.5, 0.0, 10.5), -90.0f);
		TheHead head = head(helper, new Vec3(17.5, 0.5, 10.5), target);
		helper.runAtTickTime(20, () -> {
			float before = head.getHealth();
			head.hurtServer(helper.getLevel(), head.damageSources().playerAttack(target), 1000.0f);
			head.hurtServer(helper.getLevel(), head.damageSources().lava(), 1000.0f);
			helper.assertTrue(head.getHealth() == before && head.isAlive(), "hurt to " + head.getHealth());
			head.kill(helper.getLevel());
			helper.assertTrue(!head.isAlive(), "the kill command did not remove him");
			leave(helper, target);
			helper.succeed();
		});
	}

	@GameTest(structure = ARENA, maxTicks = 100)
	public void leavesWhenHisTargetDoes(GameTestHelper helper) {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(2.5, 0.0, 10.5), -90.0f);
		TheHead head = head(helper, new Vec3(17.5, 0.5, 10.5), target);
		helper.runAtTickTime(30, () -> leave(helper, target));
		helper.runAtTickTime(36, () -> {
			helper.assertTrue(head.isRemoved(), "head stayed after the target left");
			helper.succeed();
		});
	}

	@GameTest(structure = ARENA, maxTicks = 420)
	public void revsRiseAndSagButGlassStopsTheCatch(GameTestHelper helper) {
		for (int x = 1; x <= 5; x++) {
			for (int z = 8; z <= 12; z++) {
				for (int y = 0; y <= 3; y++) {
					boolean shell = x == 1 || x == 5 || z == 8 || z == 12 || y == 3;
					helper.setBlock(new BlockPos(x, y, z), shell ? Blocks.GLASS : Blocks.AIR);
				}
			}
		}
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0.0, 10.5), -90.0f);
		TheHead head = head(helper, new Vec3(16.5, 0.5, 10.5), target);
		List<double[]> humStarts = new ArrayList<>();
		double[] last = { -1.0 };
		HeadCue[] lastCue = { HeadCue.NONE };
		helper.onEachTick(() -> {
			helper.assertFalse(target.isDeadOrDying(), "caught through glass at " + helper.relativeVec(head.position()));
			HeadCue cue = head.cue();
			if (cue != lastCue[0] && cue != HeadCue.INHALE && head.phase() == HeadPhase.CHARGE) {
				humStarts.add(new double[] { head.revs(), head.chargeSpeed(), last[0] });
			}
			lastCue[0] = cue;
			last[0] = head.chargeSpeed();
		});
		helper.runAtTickTime(400, () -> {
			helper.assertTrue(humStarts.size() >= 5, "hums " + humStarts.size());
			for (int i = 0; i < humStarts.size(); i++) {
				double[] s = humStarts.get(i);
				helper.assertTrue(s[0] == i, "rev " + s[0] + " at hum " + i);
				helper.assertTrue(Math.abs(s[1] - RevSpring.speed(i, 0.0, 0.0)) < 0.02, "hum " + i + " starts at " + s[1]);
				helper.assertTrue(s[2] < s[1] * (1.0 - HeadTuning.SAG) + 1e-6, "no sag before hum " + i + ": " + s[2]);
			}
			leave(helper, target);
			helper.succeed();
		});
	}

	@GameTest(structure = ARENA, maxTicks = 40)
	public void summonsInViewOfThePlayer(GameTestHelper helper) {
		ServerPlayer target = player(helper, GameType.CREATIVE, new Vec3(2.5, 0.0, 10.5), -90.0f);
		helper.runAtTickTime(2, () -> {
			var server = helper.getLevel().getServer();
			var source = server.createCommandSourceStack();
			server.getCommands().performPrefixedCommand(source, "mariohead summon " + target.getScoreboardName());
			List<TheHead> heads = helper.getLevel().getEntities(HeadEntities.THE_HEAD, target.getBoundingBox().inflate(16.0), h -> h.chased() == target);
			helper.assertTrue(heads.size() == 1, "heads " + heads.size());
			TheHead head = heads.getFirst();
			Vec3 to = head.getBoundingBox().getCenter().subtract(target.getEyePosition());
			double distance = to.horizontalDistance();
			helper.assertTrue(distance >= HeadTuning.SPAWN_MIN - 0.01 && distance <= HeadTuning.SPAWN_MAX + 0.01, "distance " + distance);
			helper.assertTrue(to.normalize().dot(target.getLookAngle()) > 0.7, "not in view " + to);
			helper.assertTrue(head.chased() == target, "wrong target " + head.chased());
			leave(helper, target);
			helper.succeed();
		});
	}
}
