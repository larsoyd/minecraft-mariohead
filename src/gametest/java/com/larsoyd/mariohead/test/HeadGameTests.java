package com.larsoyd.mariohead.test;

import com.larsoyd.mariohead.HeadEntities;
import com.larsoyd.mariohead.entity.HeadCue;
import com.larsoyd.mariohead.entity.HeadPhase;
import com.larsoyd.mariohead.entity.HeadSight;
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
	private static final java.util.Map<ServerPlayer, EmbeddedChannel> CHANNELS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

	private static ServerPlayer player(GameTestHelper helper, GameType mode, Vec3 relative, float yaw) {
		var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), HeadTestSetup.PLAYER_PREFIX + UUID.randomUUID().toString().substring(0, 12)), false);
		var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
		var connection = new Connection(PacketFlow.SERVERBOUND);
		CHANNELS.put(player, new EmbeddedChannel(connection));
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
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(2.5, 0.0, 10.5), -90.0f);
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

	// he would only drop a creative or spectating player again, so the command says so instead
	@GameTest(structure = ARENA, maxTicks = 20)
	public void refusesToSummonForCreativeOrSpectator(GameTestHelper helper) {
		ServerPlayer creative = player(helper, GameType.CREATIVE, new Vec3(2.5, 0.0, 6.5), -90.0f);
		ServerPlayer spectator = player(helper, GameType.SPECTATOR, new Vec3(2.5, 0.0, 14.5), -90.0f);
		helper.runAtTickTime(2, () -> {
			helper.assertTrue(summon(helper, creative).isEmpty(), "summoned for a creative player");
			helper.assertTrue(summon(helper, spectator).isEmpty(), "summoned for a spectator");
			helper.succeed();
		});
	}

	@GameTest(structure = ARENA, maxTicks = 40)
	public void eggHeadPicksTheSurvivalPlayer(GameTestHelper helper) {
		var egg = new net.minecraft.world.item.ItemStack(com.larsoyd.mariohead.HeadItems.SPAWN_EGG);
		helper.assertTrue(net.minecraft.world.item.SpawnEggItem.getType(egg) == HeadEntities.THE_HEAD, "egg spawns " + net.minecraft.world.item.SpawnEggItem.getType(egg));
		ServerPlayer creative = player(helper, GameType.CREATIVE, new Vec3(12.5, 0.0, 10.5), 90.0f);
		ServerPlayer survivor = player(helper, GameType.SURVIVAL, new Vec3(4.5, 0.0, 10.5), -90.0f);
		TheHead head = HeadEntities.THE_HEAD.spawn(helper.getLevel(), helper.absolutePos(new BlockPos(14, 0, 10)), net.minecraft.world.entity.EntitySpawnReason.SPAWN_ITEM_USE);
		helper.assertTrue(head != null && head.chased() == null, "egg head starts with a target");
		helper.runAtTickTime(15, () -> {
			helper.assertTrue(head.chased() == survivor, "egg head chases " + head.chased() + " not the survivor, creative was " + creative.getScoreboardName());
			helper.succeed();
		});
	}

	enum Loss { DISCONNECT, DEATH, OTHER_DIMENSION, SPECTATOR, CREATIVE }

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
			case CREATIVE -> target.setGameMode(GameType.CREATIVE);
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

	@GameTest(structure = ARENA, maxTicks = 40)
	public void creativeWhileLooking(GameTestHelper helper) {
		loseTarget(helper, Loss.CREATIVE, 12, false);
	}

	@GameTest(structure = ARENA, maxTicks = 60)
	public void creativeWhileTalking(GameTestHelper helper) {
		loseTarget(helper, Loss.CREATIVE, 30, true);
	}

	@GameTest(structure = ARENA, maxTicks = 100)
	public void creativeWhileCharging(GameTestHelper helper) {
		loseTarget(helper, Loss.CREATIVE, 80, true);
	}

	// a catch is a death that only a totem can turn away, and he is gone either way
	private static void caughtThrough(GameTestHelper helper, java.util.function.Consumer<ServerPlayer> guard) {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(2.5, 0.0, 10.5), -90.0f);
		guard.accept(target);
		TheHead head = head(helper, new Vec3(12.5, 0.5, 10.5), target);
		helper.succeedWhen(() -> {
			helper.assertTrue(target.isDeadOrDying(), "target alive at " + target.getHealth() + " health, head removed " + head.isRemoved());
			helper.assertTrue(head.isRemoved(), "head stayed after the catch");
			leave(helper, target);
		});
	}

	@GameTest(structure = ARENA, maxTicks = 400)
	public void catchGoesThroughARaisedShield(GameTestHelper helper) {
		caughtThrough(helper, p -> {
			p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHIELD));
			p.startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);
			helper.onEachTick(() -> {
				if (!p.isDeadOrDying()) {
					p.setYHeadRot(p.getYRot());
					p.doTick();
				}
			});
			helper.runAtTickTime(40, () -> helper.assertTrue(p.isBlocking(), "shield not raised"));
		});
	}

	@GameTest(structure = ARENA, maxTicks = 400)
	public void catchGoesThroughResistance(GameTestHelper helper) {
		caughtThrough(helper, p -> p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, 2000, 4)));
	}

	@GameTest(structure = ARENA, maxTicks = 400)
	public void catchGoesThroughArmourAndAbsorption(GameTestHelper helper) {
		caughtThrough(helper, p -> {
			p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_HELMET));
			p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_CHESTPLATE));
			p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_LEGGINGS));
			p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_BOOTS));
			p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.ABSORPTION, 2000, 20));
		});
	}

	@GameTest(structure = ARENA, maxTicks = 400)
	public void totemSavesThePlayerOnceAndHeIsGone(GameTestHelper helper) {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(2.5, 0.0, 10.5), -90.0f);
		target.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING));
		TheHead head = head(helper, new Vec3(12.5, 0.5, 10.5), target);
		helper.succeedWhen(() -> {
			helper.assertTrue(head.isRemoved(), "head not gone yet, at " + helper.relativeVec(head.position()));
			helper.assertFalse(target.isDeadOrDying(), "totem did not save the player");
			helper.assertTrue(target.getMainHandItem().isEmpty(), "totem not used up: " + target.getMainHandItem());
			leave(helper, target);
		});
	}

	// a path across open ground is a staircase of grid nodes, he flies it as one line instead of weaving
	@GameTest(structure = ARENA, maxTicks = 400)
	public void fliesAStraightLineAcrossOpenGround(GameTestHelper helper) {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(1.5, 0.0, 1.5), -90.0f);
		TheHead head = head(helper, new Vec3(18.5, 0.5, 12.5), target);
		double[] turned = { 0.0 };
		float[] first = { Float.NaN };
		float[] last = { 0.0f };
		helper.onEachTick(() -> {
			if (head.isRemoved() || head.phase() != HeadPhase.CHARGE || head.centre().distanceTo(target.position()) < HeadTuning.GRAB_DISTANCE + 1.0) {
				return;
			}
			if (Float.isNaN(first[0])) {
				first[0] = head.getYRot();
			} else {
				turned[0] += Math.abs(net.minecraft.util.Mth.wrapDegrees(head.getYRot() - last[0]));
			}
			last[0] = head.getYRot();
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(target.isDeadOrDying(), "target alive, head " + helper.relativeVec(head.position()));
			double overall = Math.abs(net.minecraft.util.Mth.wrapDegrees(last[0] - first[0]));
			helper.assertTrue(turned[0] - overall < 8.0, "weaved " + String.format("%.1f", turned[0] - overall) + " degrees beyond a net turn of " + String.format("%.1f", overall));
			leave(helper, target);
		});
	}

	// run alone with the test filter, it logs the server tick time with this many heads chasing
	private static void tickTimeWith(GameTestHelper helper, int heads) {
		fill(helper, 9, 0, 0, 9, 7, 16, Blocks.STONE.defaultBlockState());
		for (int x = 1; x <= 4; x++) {
			for (int z = 9; z <= 12; z++) {
				for (int y = 0; y <= 2; y++) {
					helper.setBlock(new BlockPos(x, y, z), x == 1 || x == 4 || z == 9 || z == 12 ? Blocks.GLASS : Blocks.AIR);
				}
			}
		}
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.0, 0.0, 11.0), -90.0f);
		for (int i = 0; i < heads; i++) {
			head(helper, new Vec3(13.5 + (i % 3) * 2.0, 0.5, 2.5 + i * 3.5), target);
		}
		helper.runAtTickTime(270, () -> {
			long[] times = helper.getLevel().getServer().getTickTimesNanos();
			double sum = 0.0;
			double worst = 0.0;
			for (long t : times) {
				sum += t / 1e6;
				worst = Math.max(worst, t / 1e6);
			}
			com.larsoyd.mariohead.MarioHead.LOG.info("[perf] heads={} ticks={} avg_ms={} max_ms={} target_alive={}", heads, times.length,
				String.format("%.2f", sum / times.length), String.format("%.2f", worst), !target.isDeadOrDying());
			leave(helper, target);
			helper.succeed();
		});
	}

	@GameTest(structure = ARENA, maxTicks = 300, required = false)
	public void tickTimeWithFiveHeads(GameTestHelper helper) {
		tickTimeWith(helper, 5);
	}

	@GameTest(structure = ARENA, maxTicks = 300, required = false)
	public void tickTimeWithNoHeads(GameTestHelper helper) {
		tickTimeWith(helper, 0);
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

	private static void neverShrinks(GameTestHelper helper, TheHead head) {
		helper.onEachTick(() -> helper.assertTrue(head.isRemoved() || head.squeeze() == 1.0f,
			"shrank to " + head.squeeze() + " at " + helper.relativeVec(head.position())));
	}

	// leaves and grass are nothing to him, he flies through them at full size
	@GameTest(structure = ARENA, maxTicks = 700)
	public void fliesThroughACanopy(GameTestHelper helper) {
		var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
		fill(helper, 6, 2, 0, 12, 3, 19, leaves);
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(2.5, 0.0, 10.5), -90.0f);
		TheHead head = head(helper, new Vec3(16.5, 0.5, 10.5), target);
		neverShrinks(helper, head);
		caughtWithin(helper, target, head);
	}

	@GameTest(structure = ARENA, maxTicks = 700)
	public void fliesThroughALeafWallAndGrass(GameTestHelper helper) {
		var leaves = Blocks.BIRCH_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
		fill(helper, 9, 0, 0, 9, 7, 19, leaves);
		fill(helper, 5, -1, 0, 13, -1, 19, Blocks.GRASS_BLOCK.defaultBlockState());
		fill(helper, 5, 0, 0, 7, 0, 19, Blocks.SHORT_GRASS.defaultBlockState());
		fill(helper, 11, 0, 0, 13, 0, 19, Blocks.FERN.defaultBlockState());
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0.0, 10.5), -90.0f);
		TheHead head = head(helper, new Vec3(16.5, 0.5, 10.5), target);
		neverShrinks(helper, head);
		boolean[] inside = { false };
		AABB wall = new AABB(helper.absoluteVec(new Vec3(9, 0, 0)), helper.absoluteVec(new Vec3(10, 8, 20)));
		helper.onEachTick(() -> inside[0] |= head.getBoundingBox().intersects(wall));
		helper.runAtTickTime(40, () -> helper.assertBlockPresent(Blocks.SHORT_GRASS, new BlockPos(6, 0, 10)));
		helper.succeedWhen(() -> {
			helper.assertTrue(target.isDeadOrDying(), "target alive, head " + helper.relativeVec(head.position()) + " phase " + head.phase());
			helper.assertTrue(inside[0], "never flew into the leaves");
			leave(helper, target);
		});
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
		target.setXRot(80.0f);
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

	@GameTest(structure = ARENA, maxTicks = 900)
	public void squeezesThroughAHoleInAWall(GameTestHelper helper) {
		fill(helper, 9, 0, 0, 9, 7, 19, Blocks.STONE.defaultBlockState());
		helper.setBlock(new BlockPos(9, 0, 10), Blocks.AIR);
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0.0, 10.5), 90.0f);
		TheHead head = head(helper, new Vec3(16.5, 0.5, 10.5), target);
		float[] smallest = { 1.0f };
		helper.onEachTick(() -> smallest[0] = Math.min(smallest[0], head.squeeze()));
		helper.succeedWhen(() -> {
			helper.assertTrue(target.isDeadOrDying(), "target alive, head " + helper.relativeVec(head.position()) + " squeeze " + head.squeeze());
			helper.assertTrue(smallest[0] <= 0.34f, "never squeezed below " + smallest[0]);
		});
	}

	@GameTest(structure = ARENA, maxTicks = 900)
	public void fitsUnderASlabCeiling(GameTestHelper helper) {
		var stone = Blocks.STONE.defaultBlockState();
		var slab = Blocks.STONE_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP);
		fill(helper, 9, 0, 0, 9, 7, 19, stone);
		fill(helper, 7, 0, 8, 11, 1, 8, stone);
		fill(helper, 7, 0, 12, 11, 1, 12, stone);
		fill(helper, 7, 1, 9, 11, 1, 11, slab);
		fill(helper, 7, 2, 8, 11, 7, 12, stone);
		fill(helper, 9, 0, 9, 9, 0, 11, Blocks.AIR.defaultBlockState());
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0.0, 10.5), 90.0f);
		TheHead head = head(helper, new Vec3(16.5, 0.5, 10.5), target);
		caughtWithin(helper, target, head);
	}

	// glass shows all of him, and the one way through is a hole at the floor
	private static void windowWithAHole(GameTestHelper helper) {
		fill(helper, 9, 0, 0, 9, 7, 19, Blocks.STONE.defaultBlockState());
		fill(helper, 9, 1, 2, 9, 6, 17, Blocks.GLASS.defaultBlockState());
		helper.setBlock(new BlockPos(9, 0, 10), Blocks.AIR);
	}

	// watched he keeps his size, so the gap holds him until the player looks away
	@GameTest(structure = ARENA, maxTicks = 900)
	public void waitsAtAGapWhileWatchedAndSqueezesWhenNot(GameTestHelper helper) {
		windowWithAHole(helper);
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0.0, 10.5), -90.0f);
		TheHead head = head(helper, new Vec3(16.5, 0.5, 10.5), target);
		helper.onEachTick(() -> {
			if (helper.getTick() <= 300) {
				helper.assertTrue(head.squeeze() == 1.0f, "shrank to " + head.squeeze() + " in plain view at " + helper.relativeVec(head.position()));
			}
		});
		helper.runAtTickTime(300, () -> {
			helper.assertFalse(target.isDeadOrDying(), "caught while watched");
			helper.assertTrue(helper.relativeVec(head.position()).x < 13.0, "never came up to the wall, at " + helper.relativeVec(head.position()));
			target.setYRot(90.0f);
			target.setYHeadRot(90.0f);
		});
		helper.runAfterDelay(301, () -> caughtWithin(helper, target, head));
	}

	// a way round he fits through beats the hole beside the player, which he would have to shrink for
	@GameTest(structure = ARENA, maxTicks = 900)
	public void takesTheWideWayRoundOverAHole(GameTestHelper helper) {
		windowWithAHole(helper);
		fill(helper, 9, 0, 15, 9, 7, 19, Blocks.AIR.defaultBlockState());
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0.0, 10.5), -90.0f);
		TheHead head = head(helper, new Vec3(16.5, 0.5, 10.5), target);
		helper.onEachTick(() -> {
			if (!head.isRemoved() && !target.isDeadOrDying()) {
				net.minecraft.world.phys.Vec3 to = head.centre().subtract(target.getEyePosition());
				target.setYRot((float) (Math.toDegrees(Math.atan2(to.z, to.x)) - 90.0));
				target.setYHeadRot(target.getYRot());
			}
		});
		neverShrinks(helper, head);
		caughtWithin(helper, target, head);
	}

	// a player running across his path is met ahead of where they are, not trailed
	@GameTest(structure = ARENA, maxTicks = 300)
	public void headsOffAPlayerRunningAcross(GameTestHelper helper) {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0.0, 2.5), 0.0f);
		TheHead head = head(helper, new Vec3(16.5, 0.5, 4.5), target);
		double[] lead = { 0.0, 0.0 };
		helper.onEachTick(() -> {
			long t = helper.getTick();
			if (t >= 60 && t < 160 && !target.isDeadOrDying()) {
				Vec3 at = helper.absoluteVec(new Vec3(3.5, 0.0, 2.5 + (t - 59) * 0.14));
				target.teleportTo(helper.getLevel(), at.x, at.y, at.z, java.util.Set.of(), 0.0f, 0.0f, true);
			}
			Vec3 v = head.getDeltaMovement().multiply(1.0, 0.0, 1.0);
			Vec3 line = target.position().subtract(head.centre()).multiply(1.0, 0.0, 1.0);
			if (t >= 80 && t < 150 && !head.isRemoved() && v.length() > 0.05 && line.length() > HeadTuning.GRAB_DISTANCE + 1.0) {
				Vec3 run = helper.absoluteVec(new Vec3(0.0, 0.0, 1.0)).subtract(helper.absoluteVec(Vec3.ZERO)).normalize();
				lead[0] += v.normalize().dot(run) - line.normalize().dot(run);
				lead[1]++;
			}
		});
		helper.runAtTickTime(150, () -> {
			double mean = lead[0] / Math.max(1.0, lead[1]);
			helper.assertTrue(lead[1] > 20 && mean > 0.1, "heading leads the runner by " + String.format("%.3f", mean) + " over " + (int) lead[1] + " ticks");
			leave(helper, target);
			helper.succeed();
		});
	}

	// walled in under rock, he announces once after five seconds and blasts his way in at full size
	@GameTest(structure = ARENA, maxTicks = 700)
	public void announcesAndDigsToASealedPlayer(GameTestHelper helper) {
		fill(helper, 1, 0, 8, 5, 4, 12, Blocks.STONE.defaultBlockState());
		fill(helper, 3, 1, 10, 3, 2, 10, Blocks.AIR.defaultBlockState());
		fill(helper, 0, -4, 0, 19, -2, 19, Blocks.STONE.defaultBlockState());
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 1.0, 10.5), -90.0f);
		TheHead head = helper.spawn(HeadEntities.THE_HEAD, new Vec3(16.5, 0.5, 10.5));
		head.chase(target);
		int[] charged = { -1 };
		List<Integer> announced = new ArrayList<>();
		HeadCue[] last = { HeadCue.NONE };
		helper.onEachTick(() -> {
			if (head.isRemoved()) {
				return;
			}
			int t = (int) helper.getTick();
			if (charged[0] < 0 && head.phase() == HeadPhase.CHARGE) {
				charged[0] = t;
			}
			if (head.cue() == HeadCue.UNDERGROUND && last[0] != HeadCue.UNDERGROUND) {
				announced.add(t);
			}
			last[0] = head.cue();
			helper.assertTrue(helper.getLevel().noCollision(head, head.getBoundingBox().deflate(1e-6)),
				"inside the rock at " + helper.relativeVec(head.position()) + " announced " + announced);
		});
		neverShrinks(helper, head);
		helper.succeedWhen(() -> {
			helper.assertTrue(target.isDeadOrDying(), "sealed player alive, head " + helper.relativeVec(head.position()) + " announced " + announced);
			helper.assertTrue(stoneLeft(helper, 1, 0, 8, 5, 4, 12) < 120, "no rock blasted, " + stoneLeft(helper, 1, 0, 8, 5, 4, 12) + " stone left");
			// his box is under three blocks wide, so his tunnel can touch four rows of the block at most
			int rows = 0;
			for (int z = 8; z <= 12; z++) {
				rows += stoneLeft(helper, 1, 0, z, 5, 4, z) < (z == 10 ? 23 : 25) ? 1 : 0;
			}
			helper.assertTrue(rows <= 4, "the blast cut into " + rows + " rows, wider than his tunnel");
			helper.assertTrue(announced.size() == 1, "announced at " + announced);
			int wait = announced.getFirst() - charged[0];
			helper.assertTrue(wait >= 100 && wait <= 140, "announced " + wait + " ticks into the charge");
			leave(helper, target);
		});
	}

	// a tunnel too small for him at the foot of a deep shaft, so he digs the rest at his own size
	@GameTest(structure = ARENA, maxTicks = 900)
	public void digsToAPlayerInATunnel(GameTestHelper helper) {
		fill(helper, 0, 8, 0, 19, 8, 19, Blocks.AIR.defaultBlockState());
		fill(helper, 0, 0, 0, 19, 6, 19, Blocks.STONE.defaultBlockState());
		fill(helper, 12, 1, 10, 12, 6, 10, Blocks.AIR.defaultBlockState());
		fill(helper, 4, 1, 10, 11, 1, 10, Blocks.AIR.defaultBlockState());
		fill(helper, 3, 1, 10, 3, 2, 10, Blocks.AIR.defaultBlockState());
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 1.0, 10.5), -90.0f);
		TheHead head = head(helper, new Vec3(16.5, 7.5, 10.5), target);
		neverShrinks(helper, head);
		caughtWithin(helper, target, head);
	}

	// his hello is harmless, walking into him only counts once the chase is on
	@GameTest(structure = ARENA, maxTicks = 300)
	public void touchingHimBeforeTheChargeIsSafe(GameTestHelper helper) {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(2.5, 0.0, 10.5), -90.0f);
		TheHead head = head(helper, new Vec3(12.5, 0.5, 10.5), target);
		helper.runAtTickTime(30, () -> {
			Vec3 c = head.centre();
			target.teleportTo(helper.getLevel(), c.x, c.y - 0.9, c.z, java.util.Set.of(), -90.0f, 0.0f, true);
		});
		helper.runAtTickTime(60, () -> helper.assertTrue(!target.isDeadOrDying() && head.phase() != HeadPhase.CHARGE,
			"phase " + head.phase() + " target dead " + target.isDeadOrDying()));
		caughtWithin(helper, target, head);
	}

	// dug in under the floor he floats over, a route ending right above the player is still no way in
	@GameTest(structure = ARENA, maxTicks = 700)
	public void announcesToAPlayerDugInUnderHim(GameTestHelper helper) {
		fill(helper, 0, 0, 0, 19, 3, 19, Blocks.STONE.defaultBlockState());
		fill(helper, 0, -4, 0, 19, -2, 19, Blocks.STONE.defaultBlockState());
		fill(helper, 10, 1, 10, 10, 2, 10, Blocks.AIR.defaultBlockState());
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(10.5, 1.0, 10.5), -90.0f);
		TheHead head = helper.spawn(HeadEntities.THE_HEAD, new Vec3(16.5, 4.5, 10.5));
		head.chase(target);
		neverShrinks(helper, head);
		boolean[] announced = { false };
		helper.onEachTick(() -> announced[0] |= !head.isRemoved() && head.cue() == HeadCue.UNDERGROUND);
		helper.succeedWhen(() -> {
			helper.assertTrue(target.isDeadOrDying() && announced[0], "dead " + target.isDeadOrDying() + " announced " + announced[0] + " head " + helper.relativeVec(head.position()));
			leave(helper, target);
		});
	}

	// a dugout whose roof broke open at one corner too small for him, so he blasts it wider
	@GameTest(structure = ARENA, maxTicks = 900)
	public void dropsThroughABrokenCornerOfADugout(GameTestHelper helper) {
		fill(helper, 0, 0, 0, 19, 2, 19, Blocks.STONE.defaultBlockState());
		fill(helper, 0, -4, 0, 19, -2, 19, Blocks.STONE.defaultBlockState());
		fill(helper, 8, 0, 8, 12, 1, 12, Blocks.AIR.defaultBlockState());
		fill(helper, 11, 2, 8, 12, 2, 8, Blocks.AIR.defaultBlockState());
		helper.setBlock(new BlockPos(12, 2, 9), Blocks.AIR);
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(10.5, 0.0, 10.5), 0.0f);
		TheHead head = head(helper, new Vec3(14.5, 3.5, 15.5), target);
		neverShrinks(helper, head);
		caughtWithin(helper, target, head);
	}

	// a pane wall with one opening at the far end, he goes round and never through the panes
	@GameTest(structure = ARENA, maxTicks = 900)
	public void goesRoundAWallOfPanes(GameTestHelper helper) {
		fill(helper, 9, 0, 0, 9, 7, 15, Blocks.GLASS_PANE.defaultBlockState());
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0.0, 6.5), 90.0f);
		TheHead head = head(helper, new Vec3(15.5, 0.5, 6.5), target);
		double[] lastX = { 15.5 };
		helper.onEachTick(() -> {
			if (head.isRemoved()) {
				return;
			}
			Vec3 at = helper.relativeVec(head.centre());
			helper.assertFalse(lastX[0] >= 9.5 && at.x < 9.5 && at.z < 16.0, "crossed the panes at " + at);
			lastX[0] = at.x;
		});
		caughtWithin(helper, target, head);
	}

	// a pit shut with a trapdoor lid in a stone roof, the player is under a roof he does not fit through
	@GameTest(structure = ARENA, maxTicks = 700)
	public void digsPastATrapdoorLid(GameTestHelper helper) {
		fill(helper, 0, 0, 0, 19, 3, 19, Blocks.STONE.defaultBlockState());
		fill(helper, 0, -4, 0, 19, -2, 19, Blocks.STONE.defaultBlockState());
		fill(helper, 10, 0, 10, 10, 2, 10, Blocks.AIR.defaultBlockState());
		helper.setBlock(new BlockPos(10, 3, 10), Blocks.OAK_TRAPDOOR.defaultBlockState());
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(10.5, 0.0, 10.5), 90.0f);
		TheHead head = head(helper, new Vec3(15.5, 4.5, 10.5), target);
		neverShrinks(helper, head);
		caughtWithin(helper, target, head);
	}

	// a piston shoves a block into him while he says hello, he works free and still gets his player
	@GameTest(structure = ARENA, maxTicks = 600)
	public void aPistonShovesABlockIntoHim(GameTestHelper helper) {
		helper.setBlock(new BlockPos(12, 1, 6), Blocks.PISTON.defaultBlockState().setValue(net.minecraft.world.level.block.piston.PistonBaseBlock.FACING, net.minecraft.core.Direction.SOUTH));
		helper.setBlock(new BlockPos(12, 1, 7), Blocks.STONE);
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(2.5, 0.0, 10.5), 90.0f);
		TheHead head = helper.spawn(HeadEntities.THE_HEAD, new Vec3(12.5, 0.5, 9.5));
		head.chase(target);
		helper.runAtTickTime(20, () -> helper.setBlock(new BlockPos(12, 1, 5), Blocks.REDSTONE_BLOCK));
		helper.runAtTickTime(30, () -> helper.assertBlockPresent(Blocks.STONE, new BlockPos(12, 1, 8)));
		caughtWithin(helper, target, head);
	}

	// at the foot of a ladder shaft open to the sky, he finds the shaft and comes down it
	@GameTest(structure = ARENA, maxTicks = 900)
	public void comesDownALadderShaft(GameTestHelper helper) {
		fill(helper, 0, 8, 0, 19, 8, 19, Blocks.AIR.defaultBlockState());
		fill(helper, 0, 0, 0, 19, 5, 19, Blocks.STONE.defaultBlockState());
		fill(helper, 8, 1, 10, 8, 5, 10, Blocks.AIR.defaultBlockState());
		fill(helper, 8, 1, 10, 8, 5, 10, Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, net.minecraft.core.Direction.SOUTH));
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(8.5, 1.0, 10.5), 0.0f);
		TheHead head = head(helper, new Vec3(15.5, 6.5, 10.5), target);
		caughtWithin(helper, target, head);
	}

	// under an overhang high enough for him the roof does not matter, he comes in at full size
	@GameTest(structure = ARENA, maxTicks = 700)
	public void comesUnderAnOverhang(GameTestHelper helper) {
		fill(helper, 0, 4, 0, 6, 4, 19, Blocks.STONE.defaultBlockState());
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(2.5, 0.0, 10.5), -90.0f);
		TheHead head = head(helper, new Vec3(15.5, 0.5, 10.5), target);
		neverShrinks(helper, head);
		boolean[] dug = { false };
		helper.onEachTick(() -> dug[0] |= !head.isRemoved() && head.digging());
		helper.succeedWhen(() -> {
			helper.assertTrue(target.isDeadOrDying(), "target alive, head " + helper.relativeVec(head.position()));
			helper.assertFalse(dug[0], "dug under an overhang he fits under");
			leave(helper, target);
		});
	}

	// pearl hops of ten blocks are not running, so they never speed him up
	@GameTest(structure = ARENA, maxTicks = 400)
	public void pearlHopsDoNotSpeedHimUp(GameTestHelper helper) {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(2.5, 0.0, 2.5), -90.0f);
		TheHead head = head(helper, new Vec3(16.5, 0.5, 16.5), target);
		helper.onEachTick(() -> {
			long t = helper.getTick();
			if (t > 60 && t % 20 == 0 && !target.isDeadOrDying()) {
				Vec3 to = helper.absoluteVec(new Vec3((t / 20) % 2 == 0 ? 2.5 : 16.5, 0.0, 2.5));
				target.teleportTo(helper.getLevel(), to.x, to.y, to.z, java.util.Set.of(), -90.0f, 0.0f, true);
			}
			if (!head.isRemoved() && head.phase() == HeadPhase.CHARGE) {
				helper.assertTrue(head.chargeSpeed() <= RevSpring.peak(head.revs()) + 1e-9,
					"speed " + head.chargeSpeed() + " over the rev " + head.revs() + " peak after a hop");
			}
		});
		helper.runAtTickTime(390, () -> {
			if (!target.isDeadOrDying()) {
				leave(helper, target);
			}
			helper.succeed();
		});
	}

	// riding a boat changes nothing, the player in it is caught
	@GameTest(structure = ARENA, maxTicks = 500)
	public void catchesAPlayerInABoat(GameTestHelper helper) {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(2.5, 0.0, 10.5), -90.0f);
		var boat = helper.spawn(net.minecraft.world.entity.EntityTypes.OAK_BOAT, new Vec3(2.5, 0.0, 10.5));
		helper.assertTrue(target.startRiding(boat, true, false), "the player did not get in the boat");
		TheHead head = head(helper, new Vec3(14.5, 0.5, 10.5), target);
		caughtWithin(helper, target, head);
	}

	// three heads on one player: one catch, and the other two are gone with the chase
	@GameTest(structure = ARENA, maxTicks = 500)
	public void threeHeadsMakeOneCatch(GameTestHelper helper) {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(2.5, 0.0, 10.5), -90.0f);
		List<TheHead> heads = List.of(head(helper, new Vec3(14.5, 0.5, 4.5), target), head(helper, new Vec3(14.5, 0.5, 10.5), target),
			head(helper, new Vec3(14.5, 0.5, 16.5), target));
		int[] deaths = { 0 };
		helper.onEachTick(() -> deaths[0] = target.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DEATHS)));
		helper.succeedWhen(() -> {
			helper.assertTrue(target.isDeadOrDying(), "target alive");
			helper.assertTrue(heads.stream().allMatch(TheHead::isRemoved), "heads left after the catch: " + heads.stream().filter(h -> !h.isRemoved()).count());
			helper.assertTrue(deaths[0] == 1, "died " + deaths[0] + " times");
			leave(helper, target);
		});
	}

	// a trench his width with the player against one side, straight down he would sit on the rim
	@GameTest(structure = ARENA, maxTicks = 700)
	public void dropsIntoATrenchBesideThePlayer(GameTestHelper helper) {
		fill(helper, 0, 0, 0, 19, 3, 19, Blocks.STONE.defaultBlockState());
		fill(helper, 3, 0, 0, 5, 3, 19, Blocks.AIR.defaultBlockState());
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.4, 0.0, 10.5), 0.0f);
		target.setXRot(80.0f);
		TheHead head = head(helper, new Vec3(14.5, 4.5, 10.5), target);
		caughtWithin(helper, target, head);
	}

	// a gully narrower than him in open ground, nobody watching, so he shrinks once he is over the player
	@GameTest(structure = ARENA, maxTicks = 700)
	public void shrinksIntoANarrowGully(GameTestHelper helper) {
		fill(helper, 0, 0, 0, 19, 3, 19, Blocks.STONE.defaultBlockState());
		fill(helper, 3, 0, 0, 4, 3, 19, Blocks.AIR.defaultBlockState());
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0.0, 10.5), 0.0f);
		target.setXRot(80.0f);
		TheHead head = head(helper, new Vec3(14.5, 4.5, 10.5), target);
		caughtWithin(helper, target, head);
	}

	// right behind a player he is out of sight in first person and in plain view of a third person camera
	@GameTest(structure = ARENA, maxTicks = 20)
	public void onlyAThirdPersonCameraSeesBehindThePlayer(GameTestHelper helper) {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(10.5, 0.0, 6.5), 180.0f);
		Vec3 behind = helper.absoluteVec(new Vec3(10.5, 4.0, 8.5));
		AABB box = new AABB(behind.x - 1.5, behind.y - 1.2, behind.z - 1.5, behind.x + 1.5, behind.y + 1.2, behind.z + 1.5);
		helper.runAtTickTime(2, () -> {
			helper.assertFalse(HeadSight.seen(helper.getLevel(), box), "seen behind a first person player");
			HeadSight.setView(target.getUUID(), HeadSight.View.BACK);
			helper.assertTrue(HeadSight.seen(helper.getLevel(), box), "unseen by a third person camera right behind the player");
			HeadSight.setView(target.getUUID(), HeadSight.View.FRONT);
			helper.assertTrue(HeadSight.seen(helper.getLevel(), box), "unseen by the front camera that looks back past the player");
			HeadSight.setView(target.getUUID(), HeadSight.View.FIRST);
			leave(helper, target);
			helper.succeed();
		});
	}

	// a player treading deep water is no safer, he comes over the water and down to them
	@GameTest(structure = ARENA, maxTicks = 700)
	public void catchesASwimmer(GameTestHelper helper) {
		fill(helper, 0, 0, 0, 19, 3, 19, Blocks.STONE.defaultBlockState());
		fill(helper, 1, 0, 6, 9, 3, 14, Blocks.WATER.defaultBlockState());
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(4.5, 1.5, 10.5), 90.0f);
		TheHead head = head(helper, new Vec3(15.5, 4.5, 10.5), target);
		caughtWithin(helper, target, head);
	}

	// a player the server does not track him for, as at a low render distance, still hears his hello
	@GameTest(structure = ARENA, maxTicks = 80)
	public void untrackedPlayersHearHisVoice(GameTestHelper helper) {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(14.5, 0.0, 10.5 - 44.0), 0.0f);
		TheHead head = head(helper, new Vec3(14.5, 0.5, 10.5), target);
		List<String> heard = new ArrayList<>();
		helper.onEachTick(() -> drainSounds(channel(target), heard));
		helper.runAtTickTime(60, () -> {
			helper.assertTrue(head.cue() == HeadCue.HELLO, "cue " + head.cue());
			helper.assertTrue(heard.contains("entity.the_head.hello"), "heard " + heard);
			leave(helper, target);
			helper.succeed();
		});
	}

	private static void drainSounds(EmbeddedChannel channel, List<String> heard) {
		for (Object out; (out = channel.readOutbound()) != null; ) {
			if (out instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket sound) {
				heard.add(sound.getSound().value().location().getPath());
			}
		}
	}

	private static EmbeddedChannel channel(ServerPlayer player) {
		return CHANNELS.get(player);
	}

	// a fence ring keeps walkers out, he floats over it
	@GameTest(structure = ARENA, maxTicks = 700)
	public void floatsOverAFenceRing(GameTestHelper helper) {
		var fence = Blocks.OAK_FENCE.defaultBlockState();
		for (int x = 1; x <= 5; x++) {
			for (int z = 8; z <= 12; z++) {
				if (x == 1 || x == 5 || z == 8 || z == 12) {
					helper.setBlock(new BlockPos(x, 0, z), fence);
				}
			}
		}
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0.0, 10.5), 90.0f);
		TheHead head = head(helper, new Vec3(16.5, 0.5, 10.5), target);
		caughtWithin(helper, target, head);
	}

	// a shut door he cannot open makes a sealed room, so he says so and blasts his way in
	@GameTest(structure = ARENA, maxTicks = 700)
	public void aShutDoorSealsTheRoom(GameTestHelper helper) {
		fill(helper, 1, 0, 8, 5, 3, 12, Blocks.STONE.defaultBlockState());
		fill(helper, 2, 0, 9, 4, 2, 11, Blocks.AIR.defaultBlockState());
		fill(helper, 0, -4, 0, 19, -2, 19, Blocks.STONE.defaultBlockState());
		var door = Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.WEST);
		helper.setBlock(new BlockPos(5, 0, 10), door.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER));
		helper.setBlock(new BlockPos(5, 1, 10), door.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0.0, 10.5), 90.0f);
		TheHead head = helper.spawn(HeadEntities.THE_HEAD, new Vec3(16.5, 0.5, 10.5));
		head.chase(target);
		neverShrinks(helper, head);
		boolean[] announced = { false };
		helper.onEachTick(() -> announced[0] |= !head.isRemoved() && head.cue() == HeadCue.UNDERGROUND);
		helper.succeedWhen(() -> {
			helper.assertTrue(target.isDeadOrDying() && announced[0], "dead " + target.isDeadOrDying() + " announced " + announced[0] + " head " + helper.relativeVec(head.position()));
			leave(helper, target);
		});
	}

	// boxed in with the player in the open, he slips through the ground and leaves the box whole
	@GameTest(structure = ARENA, maxTicks = 700)
	public void boxedInHeComesOutUnderground(GameTestHelper helper) {
		fill(helper, 12, 0, 7, 18, 5, 13, Blocks.STONE.defaultBlockState());
		fill(helper, 13, 0, 8, 17, 4, 12, Blocks.AIR.defaultBlockState());
		fill(helper, 0, -4, 0, 19, -2, 19, Blocks.STONE.defaultBlockState());
		int box = stoneLeft(helper, 12, 0, 7, 18, 5, 13);
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0.0, 10.5), 90.0f);
		TheHead head = helper.spawn(HeadEntities.THE_HEAD, new Vec3(15.5, 0.5, 10.5));
		head.chase(target);
		helper.succeedWhen(() -> {
			helper.assertTrue(target.isDeadOrDying(), "target alive, head " + helper.relativeVec(head.position()));
			helper.assertTrue(stoneLeft(helper, 12, 0, 7, 18, 5, 13) == box, "the box was blasted");
			leave(helper, target);
		});
	}

	private static int stoneLeft(GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1) {
		int count = 0;
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					count += helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.STONE) ? 1 : 0;
				}
			}
		}
		return count;
	}

	// a U of wall opening away from him, so the way in starts by going round the far side
	@GameTest(structure = ARENA, maxTicks = 900)
	public void goesRoundAWallOpeningAway(GameTestHelper helper) {
		var stone = Blocks.STONE.defaultBlockState();
		fill(helper, 8, 0, 3, 8, 7, 17, stone);
		fill(helper, 1, 0, 3, 7, 7, 3, stone);
		fill(helper, 1, 0, 17, 7, 7, 17, stone);
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(5.5, 0.0, 10.5), -90.0f);
		TheHead head = head(helper, new Vec3(15.5, 0.5, 10.5), target);
		caughtWithin(helper, target, head);
	}

	// the chase is his player's, anyone else in the way is flown through unharmed
	@GameTest(structure = ARENA, maxTicks = 400)
	public void passesOtherPlayersUnharmed(GameTestHelper helper) {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(2.5, 0.0, 10.5), -90.0f);
		ServerPlayer bystander = player(helper, GameType.SURVIVAL, new Vec3(7.5, 0.0, 10.5), 90.0f);
		TheHead head = head(helper, new Vec3(14.5, 0.5, 10.5), target);
		helper.succeedWhen(() -> {
			helper.assertTrue(target.isDeadOrDying(), "target alive, head " + helper.relativeVec(head.position()));
			helper.assertFalse(bystander.isDeadOrDying(), "the bystander died");
			leave(helper, target);
			leave(helper, bystander);
		});
	}

	// buried by a player he squirms out to the nearest open air, never on through a wall to his target
	@GameTest(structure = ARENA, maxTicks = 300)
	public void escapesBeingBuriedButNotThroughAWall(GameTestHelper helper) {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0.0, 10.5), -90.0f);
		fill(helper, 9, 0, 0, 9, 7, 16, Blocks.GLASS.defaultBlockState());
		AABB glass = new AABB(helper.absoluteVec(new Vec3(9, 0, 0)), helper.absoluteVec(new Vec3(10, 8, 17)));
		fill(helper, 0, -4, 0, 19, -2, 19, Blocks.STONE.defaultBlockState());
		TheHead head = helper.spawn(HeadEntities.THE_HEAD, new Vec3(14.5, 0.5, 10.5));
		head.chase(target);
		boolean[] buried = { false };
		int[] freeAt = { -1 };
		helper.runAtTickTime(80, () -> {
			fill(helper, 10, 0, 7, 18, 5, 14, Blocks.STONE.defaultBlockState());
			buried[0] = true;
		});
		helper.onEachTick(() -> {
			helper.assertFalse(!head.isRemoved() && head.getBoundingBox().intersects(glass), "into the glass at " + helper.relativeVec(head.position()));
			boolean clear = helper.getLevel().noCollision(head, head.getBoundingBox().deflate(1e-6));
			if (buried[0] && freeAt[0] < 0 && clear) {
				freeAt[0] = (int) helper.getTick();
			}
			if (freeAt[0] >= 0) {
				helper.assertTrue(clear, "clips again at " + helper.relativeVec(head.position()) + " after getting free at " + freeAt[0]);
			}
		});
		helper.runAtTickTime(290, () -> {
			helper.assertTrue(freeAt[0] >= 0 && freeAt[0] < 140, "free at " + freeAt[0] + ", head " + helper.relativeVec(head.position()));
			leave(helper, target);
			helper.succeed();
		});
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
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(2.5, 0.0, 10.5), -90.0f);
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
		// a roofless pen too small for him, and the player keeps an eye on him so he cannot shrink into it
		for (int x = 1; x <= 4; x++) {
			for (int z = 9; z <= 12; z++) {
				for (int y = 0; y <= 2; y++) {
					boolean shell = x == 1 || x == 4 || z == 9 || z == 12;
					helper.setBlock(new BlockPos(x, y, z), shell ? Blocks.GLASS : Blocks.AIR);
				}
			}
		}
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.0, 0.0, 11.0), -90.0f);
		TheHead head = head(helper, new Vec3(16.5, 0.5, 10.5), target);
		helper.onEachTick(() -> {
			if (!head.isRemoved()) {
				Vec3 to = head.centre().subtract(target.getEyePosition());
				target.setYRot((float) (Math.toDegrees(Math.atan2(to.z, to.x)) - 90.0));
				target.setXRot((float) -Math.toDegrees(Math.atan2(to.y, to.horizontalDistance())));
			}
		});
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
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(2.5, 0.0, 10.5), -90.0f);
		helper.runAtTickTime(2, () -> {
			var server = helper.getLevel().getServer();
			var source = server.createCommandSourceStack();
			server.getCommands().performPrefixedCommand(source, "mariohead summon " + target.getScoreboardName());
			List<TheHead> heads = helper.getLevel().getEntities(HeadEntities.THE_HEAD, target.getBoundingBox().inflate(16.0), h -> h.chased() == target);
			helper.assertTrue(heads.size() == 1, "heads " + heads.size());
			TheHead head = heads.getFirst();
			Vec3 to = head.getBoundingBox().getCenter().subtract(target.getEyePosition());
			double distance = to.length();
			helper.assertTrue(distance >= HeadTuning.SPAWN_MIN - 0.01 && distance <= HeadTuning.SPAWN_MAX + 0.01, "distance " + distance);
			helper.assertTrue(to.normalize().dot(target.getLookAngle()) > 0.7, "not in view " + to);
			helper.assertTrue(head.chased() == target, "wrong target " + head.chased());
			leave(helper, target);
			helper.succeed();
		});
	}
}
