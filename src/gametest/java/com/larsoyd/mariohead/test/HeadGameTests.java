package com.larsoyd.mariohead.test;

import com.larsoyd.mariohead.HeadEntities;
import com.larsoyd.mariohead.entity.ChaseTempo;
import com.larsoyd.mariohead.entity.HeadClock;
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
import net.minecraft.server.level.ServerLevel;
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

	@GameTest(structure = ARENA, maxTicks = 800)
	public void entersRealVillageSmallHouse(GameTestHelper helper) {
		villageHouseScenario(helper, "plains_small_house_1", false);
	}

	@GameTest(structure = ARENA, maxTicks = 800)
	public void raidsChestInsideRealVillageSmallHouse(GameTestHelper helper) {
		villageHouseScenario(helper, "plains_small_house_1", true);
	}

	@GameTest(structure = ARENA, maxTicks = 800)
	public void raidsChestInsideRealVillageMediumHouse(GameTestHelper helper) {
		villageHouseScenario(helper, "plains_medium_house_1", true);
	}

	@GameTest(structure = ARENA, maxTicks = 800)
	public void raidsChestInsideRealDesertHouse(GameTestHelper helper) {
		villageHouseScenario(helper, "desert/houses/desert_small_house_1", true);
	}

	@GameTest(structure = ARENA, maxTicks = 800)
	public void raidsChestInsideRealSavannaHouse(GameTestHelper helper) {
		villageHouseScenario(helper, "savanna/houses/savanna_small_house_1", true);
	}

	@GameTest(structure = ARENA, maxTicks = 800)
	public void raidsChestInsideRealTaigaHouse(GameTestHelper helper) {
		villageHouseScenario(helper, "taiga/houses/taiga_small_house_1", true);
	}

	@GameTest(structure = ARENA, maxTicks = 800)
	public void raidsChestInsideRealSnowyHouse(GameTestHelper helper) {
		villageHouseScenario(helper, "snowy/houses/snowy_small_house_1", true);
	}

	@GameTest(structure = ARENA, maxTicks = 800)
	public void raidsVillageHouseFromBelowFoundation(GameTestHelper helper) {
		villageHouseScenario(helper, "plains_small_house_1", true, true);
	}

	@GameTest(structure = ARENA, maxTicks = 850)
	public void raidsChestInsideTaigaArmorerHouse(GameTestHelper helper) {
		villageHouseScenario(helper, "taiga/houses/taiga_armorer_house_1", true);
	}

	private void villageHouseScenario(GameTestHelper helper, String name, boolean withChest) {
		villageHouseScenario(helper, name, withChest, false);
	}

	private void villageHouseScenario(GameTestHelper helper, String name, boolean withChest, boolean raised) {
		fill(helper, 0, 0, 0, 23, 20, 23, Blocks.AIR.defaultBlockState());
		fill(helper, 0, -1, 0, 23, -1, 23, Blocks.STONE.defaultBlockState());
		var level = helper.getLevel();
		var template = level.getStructureTemplateManager().get(net.minecraft.resources.Identifier.withDefaultNamespace("village/" + (name.contains("/") ? name : "plains/houses/" + name))).orElseThrow();
		var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings()
			.setIgnoreEntities(true)
			.addProcessor(net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor.INSTANCE)
			.addProcessor(net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor.STRUCTURE_BLOCK);
		BlockPos origin = helper.absolutePos(new BlockPos(5, raised ? 4 : 0, 3));
		if (raised) fill(helper, 5, 0, 3, 4 + template.getSize().getX(), 3, 2 + template.getSize().getZ(), Blocks.COBBLESTONE.defaultBlockState());
		helper.assertTrue(template.placeInWorld(level, origin, origin, settings, net.minecraft.util.RandomSource.create(4321), 3), "could not place vanilla house " + name);
		List<BlockPos> standing = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(template.getSize()))) {
			if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
				&& !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()
				&& com.larsoyd.mariohead.entity.HeadShelter.inspect(level, pos, origin.offset(-4, 2, 0)).sheltered()) {
				standing.add(pos.immutable());
			}
		}
		helper.assertTrue(standing.size() >= 2, "vanilla house has no detected interior: " + name);
		BlockPos playerPos = standing.getLast();
		BlockPos chestPos = standing.stream().filter(pos -> !pos.equals(playerPos) && pos.getY() == playerPos.getY()).findFirst().orElseThrow();
		if (withChest) {
			level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
			((net.minecraft.world.Container) level.getBlockEntity(chestPos))
				.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 17));
		}
		ServerPlayer target = player(helper, GameType.SURVIVAL, helper.relativeVec(Vec3.atBottomCenterOf(playerPos)), 90);
		TheHead head = helper.spawn(HeadEntities.THE_HEAD, helper.relativeVec(new Vec3(origin.getX() - 3.5, raised ? helper.absoluteVec(new Vec3(0, 0.5, 0)).y : playerPos.getY(), playerPos.getZ() + 0.5)));
		head.chase(target);
		Vec3 ownerEyes = target.getEyePosition();
		var openings = com.larsoyd.mariohead.entity.HeadShelter.inspect(level, playerPos, head.blockPosition()).openings();
		boolean[] home = { false };
		boolean[] food = { false };
		boolean[] entered = { false };
		boolean[] returned = { false };
		helper.onEachTick(() -> {
			if (name.contains("armorer") && !home[0] && head.cue() == HeadCue.HOME_NICE) {
				helper.assertTrue(openings.stream().anyMatch(opening -> level.clip(new net.minecraft.world.level.ClipContext(
					ownerEyes, head.centre(), net.minecraft.world.level.ClipContext.Block.COLLIDER,
					net.minecraft.world.level.ClipContext.Fluid.NONE, head)).getBlockPos().equals(opening.wall())),
					"armorer greeting not visible through window at " + helper.relativeVec(head.centre()));
			}
			home[0] |= head.cue() == HeadCue.HOME_NICE;
			food[0] |= head.cue() == HeadCue.HOME_HUNGRY || head.cue() == HeadCue.HOME_THIRSTY;
			returned[0] |= head.cue() == HeadCue.HOME_RETURN;
			Vec3 c = head.centre();

			entered[0] |= c.x > origin.getX() + 1 && c.x < origin.getX() + template.getSize().getX() - 1
				&& c.z > origin.getZ() + 1 && c.z < origin.getZ() + template.getSize().getZ() - 1
				&& c.distanceTo(ownerEyes) < 3 && level.noCollision(head, head.getBoundingBox().deflate(1e-5));
			if (withChest && home[0] && head.cue().digHum()) target.setPos(helper.absoluteVec(new Vec3(25.5, playerPos.getY() - helper.absolutePos(BlockPos.ZERO).getY(), 9.5)));
			if (withChest && food[0]) helper.assertTrue(entered[0], "food began before entering native house " + name + " at " + helper.relativeVec(c) + " owner " + helper.relativeVec(ownerEyes));
			if (home[0] && entered[0] && (!withChest || food[0] && returned[0] && level.getBlockState(chestPos).isAir())) {
				com.larsoyd.mariohead.MarioHead.LOG.info("[home-test] house={} entered={} food={} returned={} tick={}", name, entered[0], food[0], returned[0], helper.getTick());
				helper.succeed();
			}
			if (helper.getTick() > 750) helper.assertTrue(false, "village entry failed house=" + name + " home=" + home[0]
				+ " food=" + food[0] + " entered=" + entered[0] + " returned=" + returned[0] + " pos=" + helper.relativeVec(c) + " cue=" + head.cue());
		});
	}

	@GameTest(structure = ARENA, maxTicks = 650)
	public void singleChestGetsFullHomeRaid(GameTestHelper helper) {
		homeRaidScenario(helper, 0);
	}

	@GameTest(structure = ARENA, maxTicks = 650)
	public void trappedChestGetsFullHomeRaid(GameTestHelper helper) {
		homeRaidScenario(helper, 1);
	}

	@GameTest(structure = ARENA, maxTicks = 650)
	public void doubleChestSurvivesIgnitionUntilFoodLine(GameTestHelper helper) {
		homeRaidScenario(helper, 2);
	}

	@GameTest(structure = ARENA, maxTicks = 650)
	public void chestPlacedDuringHomeDialogueGetsRaided(GameTestHelper helper) {
		homeRaidScenario(helper, 3);
	}

	@GameTest(structure = ARENA, maxTicks = 650)
	public void openingHouseAfterGreetingKeepsChestTargets(GameTestHelper helper) {
		homeRaidScenario(helper, 4);
	}

	@GameTest(structure = ARENA, maxTicks = 650)
	public void existingDiggingStillRaidsHouseChest(GameTestHelper helper) {
		homeRaidScenario(helper, 5);
	}

	@GameTest(structure = ARENA, maxTicks = 650)
	public void removedChestCancelsRaidBeforeAnotherDash(GameTestHelper helper) {
		homeRaidScenario(helper, 6);
	}

	@GameTest(structure = ARENA, maxTicks = 650)
	public void doorHouseWithoutBedRaidsSingleChest(GameTestHelper helper) {
		homeRaidScenario(helper, 7);
	}

	@GameTest(structure = ARENA, maxTicks = 650)
	public void raisedChestGetsRaided(GameTestHelper helper) {
		homeRaidScenario(helper, 8);
	}

	@GameTest(structure = ARENA, maxTicks = 650)
	public void lowCeilingHouseStillRaidsChest(GameTestHelper helper) {
		homeRaidScenario(helper, 9);
	}

	@GameTest(structure = ARENA, maxTicks = 650)
	public void houseIgnitionDashKeepsMoving(GameTestHelper helper) {
		homeRaidScenario(helper, 10);
	}

	@GameTest(structure = ARENA, maxTicks = 5)
	public void hiddenHouseApproachSkipsLongExteriorRoute(GameTestHelper helper) throws ReflectiveOperationException {
		windowVisibility(helper, 0);
	}

	@GameTest(structure = ARENA, maxTicks = 5)
	public void watchedHouseApproachNeverTeleports(GameTestHelper helper) throws ReflectiveOperationException {
		windowVisibility(helper, 1);
	}

	@GameTest(structure = ARENA, maxTicks = 5)
	public void visibleHouseDestinationNeverTeleports(GameTestHelper helper) throws ReflectiveOperationException {
		windowVisibility(helper, 2);
	}

	@GameTest(structure = ARENA, maxTicks = 5)
	public void blockedHouseDestinationNeverTeleports(GameTestHelper helper) throws ReflectiveOperationException {
		windowVisibility(helper, 3);
	}

	@GameTest(structure = ARENA, maxTicks = 5)
	public void thirdPersonHouseApproachNeverTeleports(GameTestHelper helper) throws ReflectiveOperationException {
		windowVisibility(helper, 4);
	}

	@GameTest(structure = ARENA, maxTicks = 5)
	public void otherObserverPreventsHouseTeleport(GameTestHelper helper) throws ReflectiveOperationException {
		windowVisibility(helper, 5);
	}

	@GameTest(structure = ARENA, maxTicks = 5)
	public void distantHouseApproachCancelsBeforeGreeting(GameTestHelper helper) throws ReflectiveOperationException {
		windowVisibility(helper, 6);
	}

	@GameTest(structure = ARENA, maxTicks = 5)
	public void longHouseShortcutDoesNotTeleport(GameTestHelper helper) throws ReflectiveOperationException {
		windowVisibility(helper, 7);
	}

	@GameTest(structure = ARENA, maxTicks = 5)
	public void nearbyHouseApproachDoesNotTeleportFromTwentyBlocks(GameTestHelper helper) throws ReflectiveOperationException {
		windowVisibility(helper, 8);
	}

	@GameTest(structure = ARENA, maxTicks = 5)
	public void distantHouseGreetingIsCancelledBeforeVoice(GameTestHelper helper) throws ReflectiveOperationException {
		windowVisibility(helper, 9);
	}

	private void windowVisibility(GameTestHelper helper, int scenario) throws ReflectiveOperationException {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(10.5, 0, 17.5), 0);
		TheHead head = helper.spawn(HeadEntities.THE_HEAD, new Vec3(5.5, 0.5, 5.5));
		head.chase(target);
		head.setNoAi(true);
		Vec3 destination = helper.absoluteVec(new Vec3(scenario == 7 ? 30.5 : 15.5, 2.0, 5.5));
		if (scenario == 6 || scenario == 9) target.setPos(helper.absoluteVec(new Vec3(10.5, 0, 40.5)));
		if (scenario == 7) target.setPos(helper.absoluteVec(new Vec3(17.5, 0, 10.5)));
		if (scenario == 8) target.setPos(helper.absoluteVec(new Vec3(10.5, 0, 25.5)));
		Field stage = TheHead.class.getDeclaredField("homeStage");
		Field route = TheHead.class.getDeclaredField("homeApproach");
		Field until = TheHead.class.getDeclaredField("homeApproachUntil");
		stage.setAccessible(true);
		route.setAccessible(true);
		until.setAccessible(true);
		stage.set(head, java.util.Arrays.stream(stage.getType().getEnumConstants()).filter(value -> value.toString().equals(scenario == 9 ? "NICE" : "APPROACH")).findFirst().orElseThrow());
		route.set(head, List.of(head.centre().add(0, 2, 0), destination));
		until.setLong(head, helper.getLevel().getGameTime() + 300);
		if (scenario == 1 || scenario == 2) target.setYRot(180);
		if (scenario == 2) fill(helper, 0, 0, 10, 10, 5, 10, Blocks.STONE.defaultBlockState());
		if (scenario == 3) helper.setBlock(new BlockPos(15, 2, 5), Blocks.STONE.defaultBlockState());
		if (scenario == 4) HeadSight.setView(target.getUUID(), HeadSight.View.FRONT);
		if (scenario == 5) player(helper, GameType.SURVIVAL, new Vec3(10.5, 0, 17.5), 180);
		Vec3 before = head.centre();
		var arrival = head.getBoundingBox().move(destination.subtract(before));
		helper.assertTrue(HeadSight.seen(helper.getLevel(), head.getBoundingBox()) == (scenario == 1 || scenario == 4 || scenario == 5), "unexpected source visibility: " + scenario);
		helper.assertTrue(HeadSight.seen(helper.getLevel(), arrival) == (scenario == 1 || scenario == 2 || scenario == 4 || scenario == 5), "unexpected destination visibility: " + scenario);
		var tick = TheHead.class.getDeclaredMethod("tickHome", ServerLevel.class, net.minecraft.world.entity.player.Player.class);
		tick.setAccessible(true);
		tick.invoke(head, helper.getLevel(), target);
		HeadSight.setView(target.getUUID(), HeadSight.View.FIRST);
		if (scenario == 0) {
			helper.assertTrue(head.centre().distanceTo(destination) < 0.01, "unseen approach still walks whole route: " + helper.relativeVec(head.centre()));
			helper.assertTrue(stage.get(head).toString().equals("NICE"), "hidden arrival did not advance greeting");
			for (Object packet; (packet = CHANNELS.get(target).readOutbound()) != null;) {
				helper.assertFalse(packet instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket, "hidden arrival emitted a sound");
			}
		} else {
			helper.assertTrue(head.centre().distanceTo(before) < 0.3, "visible, distant or blocked approach teleported: " + scenario);
			helper.assertTrue(stage.get(head).toString().equals(scenario == 6 || scenario == 9 ? "NONE" : "APPROACH"), "unsafe arrival advanced greeting");
			helper.assertTrue(head.cue() == HeadCue.NONE, "distant approach emitted greeting");
		}
		helper.succeed();
	}

	@GameTest(structure = ARENA, maxTicks = 850)
	public void houseApproachesWindowBeforeBreakingWall(GameTestHelper helper) {
		homeRaidScenario(helper, 11);
	}

	@GameTest(structure = ARENA, maxTicks = 850)
	public void houseReturnWaitsForWholeThreatWithStationaryOwner(GameTestHelper helper) {
		homeRaidScenario(helper, 12);
	}

	@GameTest(structure = ARENA, maxTicks = 850)
	public void houseReturnKeepsUpWithMovingOwnerDuringThreat(GameTestHelper helper) {
		homeRaidScenario(helper, 13);
	}

	@GameTest(structure = ARENA, maxTicks = 850)
	public void houseRaidsChestOnAnotherFloor(GameTestHelper helper) {
		homeRaidScenario(helper, 14);
	}

	@GameTest(structure = ARENA, maxTicks = 850)
	public void houseReturnWaitsForBusyHumBeforeThreat(GameTestHelper helper) {
		homeRaidScenario(helper, 15);
	}

	@GameTest(structure = ARENA, maxTicks = 850)
	public void houseGreetsAtWindowWhenOwnerBehindJamb(GameTestHelper helper) {
		homeRaidScenario(helper, 16);
	}

	@GameTest(structure = ARENA, maxTicks = 1100)
	public void houseReturnThenLongFallResumesTnt(GameTestHelper helper) {
		homeRaidScenario(helper, 17);
	}

	@GameTest(structure = ARENA, maxTicks = 1100)
	public void houseReturnThenTauntLaughResumesTnt(GameTestHelper helper) {
		homeRaidScenario(helper, 20);
	}

	@GameTest(structure = ARENA, maxTicks = 850)
	public void chestAboveEntryDoesNotLeaveHeadHoveringOverRoof(GameTestHelper helper) {
		homeRaidScenario(helper, 18);
	}

	@GameTest(structure = ARENA, maxTicks = 850)
	public void roofApproachToOverheadChestFinishesEntry(GameTestHelper helper) {
		homeRaidScenario(helper, 19);
	}

	private void homeRaidScenario(GameTestHelper helper, int scenario) {
		fill(helper, 8, 0, 4, 18, 5, 14, Blocks.STONE.defaultBlockState());
		fill(helper, 9, 1, 5, 17, scenario == 9 ? 2 : 4, 13, Blocks.AIR.defaultBlockState());
		if (scenario == 7) {
			helper.setBlock(new BlockPos(8, 1, 7), Blocks.OAK_DOOR.defaultBlockState());
			helper.setBlock(new BlockPos(8, 2, 7), Blocks.OAK_DOOR.defaultBlockState()
				.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
		} else {
			helper.setBlock(new BlockPos(14, 1, 12), Blocks.BED.red().defaultBlockState());
		}
		if (scenario == 14) {
			fill(helper, 8, 5, 4, 18, 9, 14, Blocks.STONE.defaultBlockState());
			fill(helper, 9, 6, 5, 17, 8, 13, Blocks.AIR.defaultBlockState());
			fill(helper, 14, 5, 10, 15, 5, 11, Blocks.AIR.defaultBlockState());
		}
		if (scenario == 18 || scenario == 19) helper.setBlock(new BlockPos(8, 2, 9), Blocks.GLASS_PANE.defaultBlockState());
		if (scenario == 16) helper.setBlock(new BlockPos(14, 1, 7), Blocks.BED.red().defaultBlockState());
		if (scenario == 11 || scenario == 16) helper.setBlock(new BlockPos(13, 2, 4), Blocks.GLASS_PANE.defaultBlockState());
		List<BlockPos> chests = scenario == 18 || scenario == 19 ? List.of(new BlockPos(16, 3, 9)) : scenario == 2 ? List.of(new BlockPos(9, 1, 9), new BlockPos(9, 1, 10))
			: List.of(new BlockPos(9, scenario == 14 ? 6 : scenario == 8 ? 3 : 1, 9));
		Runnable place = () -> {
			for (int index = 0; index < chests.size(); index++) {
				var state = (scenario == 1 ? Blocks.TRAPPED_CHEST : Blocks.CHEST).defaultBlockState();
				if (scenario == 2) {
					state = state.setValue(net.minecraft.world.level.block.ChestBlock.FACING, net.minecraft.core.Direction.EAST)
						.setValue(net.minecraft.world.level.block.ChestBlock.TYPE, index == 0
							? net.minecraft.world.level.block.state.properties.ChestType.LEFT
							: net.minecraft.world.level.block.state.properties.ChestType.RIGHT);
				}
				helper.setBlock(chests.get(index), state);
				((net.minecraft.world.Container) helper.getLevel().getBlockEntity(helper.absolutePos(chests.get(index))))
					.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 17));
			}
		};
		if (scenario != 3) place.run();
		if (scenario == 19) fill(helper, 0, 6, 0, 23, 20, 23, Blocks.AIR.defaultBlockState());
		ServerPlayer target = player(helper, GameType.SURVIVAL, scenario == 16 ? new Vec3(12.3, 1, 5.462) : new Vec3(16.5, 1, 9.5), 90);
		TheHead head = helper.spawn(HeadEntities.THE_HEAD, new Vec3(6.0, scenario == 19 ? 7 : 1, 9.5));
		head.chase(target);
		if (scenario == 5) {
			try {
				var digging = TheHead.class.getDeclaredMethod("setDigging", boolean.class);
				digging.setAccessible(true);
				digging.invoke(head, true);
			} catch (ReflectiveOperationException e) {
				throw new AssertionError(e);
			}
		}
		List<HeadCue> cues = new ArrayList<>();
		long[] ignitionAt = { -1 };
		Vec3[] ignitionPos = { Vec3.ZERO };
		boolean[] food = { false };
		boolean[] changed = { false };
		boolean[] window = { false };
		long[] returnAt = { -1 };
		Vec3[] returnPos = { Vec3.ZERO };
		int[] waitingRevs = { -1 };
		boolean[] fallen = { false };
		boolean[] fallSpoken = { false };
		long[] laughAt = { -1 };
		HeadCue[] laughCue = { HeadCue.NONE };
		helper.onEachTick(() -> {
			HeadCue cue = head.cue();
			if (cues.isEmpty() || cues.getLast() != cue) cues.add(cue);
			if (waitingRevs[0] < 0 && returnAt[0] < 0 && cues.contains(HeadCue.HOME_ASK) && cue.digHum()) target.setPos(helper.absoluteVec(new Vec3(25.5, 1, 9.5)));
			if (scenario == 16 && cue == HeadCue.HOME_NICE) {
				Vec3 c = helper.relativeVec(head.centre());
				helper.assertTrue(c.z < 4 && c.distanceTo(new Vec3(13.5, 2.5, 4.5)) < 5, "occluded owner skipped exterior window: " + c);
			}
			if (scenario == 11) {
				Vec3 c = helper.relativeVec(head.centre());
				window[0] |= c.z < 4 && helper.getLevel().clip(new net.minecraft.world.level.ClipContext(
					helper.absoluteVec(new Vec3(16.5, 2.62, 9.5)), head.centre(), net.minecraft.world.level.ClipContext.Block.COLLIDER,
					net.minecraft.world.level.ClipContext.Fluid.NONE, head)).getBlockPos().equals(helper.absolutePos(new BlockPos(13, 2, 4)));
				if (cue.digHum()) helper.assertTrue(window[0], "house burst before approaching its window: " + c);
			}
			if (scenario == 3 && cue.digHum() && !food[0]) fill(helper, 8, 1, 7, 8, 3, 9, Blocks.AIR.defaultBlockState());
			if (scenario == 10 && cue.digHum() && !food[0]) {
				if (ignitionAt[0] < 0) {
					ignitionAt[0] = helper.getTick();
					ignitionPos[0] = head.position();
				} else if (helper.getTick() - ignitionAt[0] == 10) {
					helper.assertTrue(head.position().distanceTo(ignitionPos[0]) > 0.5, "house dash stopped after its first tick");
				}
			}
			if (cue == HeadCue.HOME_NICE && !changed[0]) {
				changed[0] = true;
				if (scenario == 18 || scenario == 19) helper.assertTrue(head.squeeze() == 1, "roof-entry fixture shrank before greeting");
				if (scenario == 19) head.setPos(helper.absoluteVec(new Vec3(6, 7, 9.5)));
				if (scenario == 3) place.run();
				if (scenario == 4) fill(helper, 8, 1, 7, 8, 3, 9, Blocks.AIR.defaultBlockState());
			}
			if (cue == HeadCue.HOME_HUNGRY || cue == HeadCue.HOME_THIRSTY) {
				if (scenario == 18 || scenario == 19) helper.assertTrue(helper.relativeVec(head.centre()).y < 4, "food speech began above the room: " + helper.relativeVec(head.centre()));
				if (!food[0]) helper.assertTrue(helper.relativeVec(head.centre()).x > 10.5
					&& helper.getLevel().noCollision(head, head.getBoundingBox()), "food line began before full house entry: " + helper.relativeVec(head.centre()));
				if (!food[0]) helper.assertTrue(chests.stream().map(helper::absolutePos).anyMatch(pos ->
					head.centre().distanceTo(Vec3.atCenterOf(pos)) < 4.2 && helper.getLevel().clip(new net.minecraft.world.level.ClipContext(
						head.centre(), Vec3.atCenterOf(pos), net.minecraft.world.level.ClipContext.Block.COLLIDER,
						net.minecraft.world.level.ClipContext.Fluid.NONE, head)).getBlockPos().equals(pos)), "food line began without a nearby visible chest");
				if (scenario == 6 && !food[0]) {
					for (BlockPos pos : chests) {
						((net.minecraft.world.Container) helper.getLevel().getBlockEntity(helper.absolutePos(pos))).clearContent();
						helper.setBlock(pos, Blocks.AIR.defaultBlockState());
					}
				}
				food[0] = true;
				target.setPos(helper.absoluteVec(new Vec3(25.5, 1, 9.5)));
			}
			if (scenario == 6 && food[0]) helper.assertTrue(!cue.digHum(), "empty raid started another dash: " + cues);
			if (changed[0] && !food[0]) {
				for (BlockPos pos : chests) helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(pos)) instanceof net.minecraft.world.Container,
					"ignition destroyed chest before food line, scenario=" + scenario + " cues=" + cues);
			}
			if (changed[0] && returnAt[0] < 0) helper.assertTrue(cue != HeadCue.UNDERGROUND && !target.isDeadOrDying(),
				"home raid fell back to player/underground, scenario=" + scenario + " cues=" + cues);
			if (scenario == 15 && food[0] && returnAt[0] < 0) {
				if (waitingRevs[0] < 0 && chests.stream().allMatch(pos -> helper.getBlockState(pos).isAir())) {
					waitingRevs[0] = head.revs();
					target.setPos(head.position().add(4, 0, 0));
					try {
						var next = TheHead.class.getDeclaredField("nextDigDash");
						next.setAccessible(true);
						next.setLong(head, helper.getLevel().getGameTime());
					} catch (ReflectiveOperationException e) {
						throw new AssertionError(e);
					}
				}
				if (waitingRevs[0] >= 0) helper.assertTrue(head.revs() == waitingRevs[0], "new dash before pending return threat");
			}
			if ((scenario == 12 || scenario == 13 || scenario == 15 || scenario == 17 || scenario == 20) && (cue == HeadCue.HOME_RETURN || returnAt[0] >= 0)) {
				if (returnAt[0] < 0) {
					try {
						Field ready = TheHead.class.getDeclaredField("tauntReadyAt");
						Field tempo = TheHead.class.getDeclaredField("tempo");
						ready.setAccessible(true);
						tempo.setAccessible(true);
						long expected = helper.getLevel().getGameTime() + (HeadCue.HOME_RETURN.clip().millis() + 49) / 50 + HeadClock.LATE_VOICE_TICKS + 600;
						helper.assertTrue(ready.getLong(head) >= expected - 1, "return threat did not start taunt cooldown");
						helper.assertTrue(((ChaseTempo) tempo.get(head)).humMultiplier() == 1.0, "return threat did not reset hum buildup");
					} catch (ReflectiveOperationException e) {
						throw new AssertionError(e);
					}
					if (scenario == 20) {
						try {
							var stalls = TheHead.class.getDeclaredField("stalls");
							var cutoff = TheHead.class.getDeclaredField("cutoffPending");
							stalls.setAccessible(true); cutoff.setAccessible(true);
							helper.assertFalse(((com.larsoyd.mariohead.entity.EarlyStall) stalls.get(head)).active(0), "house TNT retained early stalls");
							helper.assertFalse(cutoff.getBoolean(head), "house TNT queued cutoff speech");
						} catch (ReflectiveOperationException e) { throw new AssertionError(e); }
					}
					returnAt[0] = helper.getTick();
					returnPos[0] = head.position();
					target.setPos(head.position().add(4, 0, 0));
				}
				long elapsed = helper.getTick() - returnAt[0];
				if (scenario == 13 && elapsed > 0) target.setPos(target.position().add(0.2, 0, 0));
				if (elapsed < (HeadCue.HOME_RETURN.clip().millis() + 49) / 50 + 15) {
					helper.assertFalse(target.isDeadOrDying(), "house return charged during threat at tick " + elapsed);
					if (elapsed > 20) helper.assertTrue(head.chargeSpeed() <= (scenario == 13 ? 0.23 : 0.02), "return threat did not match owner pace: " + head.chargeSpeed());
					return;
				}
				if (scenario == 13) helper.assertTrue(head.position().distanceTo(returnPos[0]) > 1, "return threat did not follow moving owner");
				if (scenario == 17 || scenario == 20) {
					if (scenario == 20 && cue == HeadCue.FALL_019 && !fallSpoken[0]) {
						try {
							Field voices = TheHead.class.getDeclaredField("voices");
							voices.setAccessible(true);
							((com.larsoyd.mariohead.entity.ChaseVoices) voices.get(head)).follow(1, 0);
						} catch (ReflectiveOperationException e) {
							throw new AssertionError(e);
						}
					}
					if (cue.laugh() && laughAt[0] < 0) {
						laughAt[0] = helper.getTick();
						laughCue[0] = cue;
					}
					fallSpoken[0] |= cue == HeadCue.FALL_019;
					if (cue.digHum() && !fallen[0]) {
						try {
							Field ready = TheHead.class.getDeclaredField("tauntReadyAt");
							ready.setAccessible(true);
							ready.setLong(head, helper.getLevel().getGameTime());
							for (int seed = 0; seed < 10000; seed++) {
								if (net.minecraft.util.RandomSource.create(seed).nextDouble() < 0.15) {
									head.getRandom().setSeed(seed);
									break;
								}
							}
							target.setPos(head.position().add(12, 0, 0));
							target.causeFallDamage(8, 1, target.damageSources().fall());
							helper.assertTrue(target.getHealth() == 15, "post-house fall did not damage owner");
							fallen[0] = true;
						} catch (ReflectiveOperationException e) {
							throw new AssertionError(e);
						}
					}
					helper.assertFalse(target.isDeadOrDying(), "post-house fall reaction lost owner");
					if (!fallSpoken[0]) return;
				}
				if (head.cue().digHum()) {
					if (scenario == 20) {
						helper.assertTrue(laughAt[0] >= 0, "house TNT hum overtook queued laugh");
						helper.assertTrue(helper.getTick() - laughAt[0] >= (laughCue[0].clip().millis() + 49) / 50 + HeadClock.LATE_VOICE_TICKS,
							"house TNT dash cut laugh short");
					}
					helper.assertTrue(food[0], "return threat skipped chest routine");
					helper.assertTrue(head.digging(), "return threat lost persistent TNT mode");
					try {
						Field boost = TheHead.class.getDeclaredField("dashBoost");
						Field ready = TheHead.class.getDeclaredField("tauntReadyAt");
						boost.setAccessible(true);
						ready.setAccessible(true);
						helper.assertTrue(boost.getDouble(head) == 1.1, "return threat did not boost next dash");
						helper.assertTrue(ready.getLong(head) > helper.getLevel().getGameTime(), "return threat cooldown ended before resumed dash");
					} catch (ReflectiveOperationException e) {
						throw new AssertionError(e);
					}
					helper.succeed();
				}
				return;
			}
			if (cue == HeadCue.HOME_RETURN) {
				helper.assertTrue(cues.contains(HeadCue.HOME_ASK) && food[0], "missing house/food dialogue: " + cues);
				for (BlockPos pos : chests) helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(pos)).isAir(), "raid missed chest " + pos);
				helper.assertTrue(head.chased() == target, "raid lost escaped owner");
				var drops = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
					new AABB(helper.absolutePos(new BlockPos(7, 0, 3))).expandTowards(13, 7, 13),
					item -> item.getItem().is(net.minecraft.world.item.Items.DIAMOND));
				helper.assertTrue(drops.isEmpty(), "raid dropped inventory");
				helper.succeed();
			}
		});
	}

	@GameTest(structure = ARENA, maxTicks = 800)
	public void houseTntChasesThroughSecondHouseWithoutUndergroundSpeech(GameTestHelper helper) {
		fill(helper, 0, 0, 0, 23, 12, 23, Blocks.AIR.defaultBlockState());
		fill(helper, 0, -1, 0, 23, -1, 23, Blocks.STONE.defaultBlockState());
		fill(helper, 4, 0, 3, 10, 5, 9, Blocks.STONE.defaultBlockState());
		fill(helper, 5, 1, 4, 9, 4, 8, Blocks.AIR.defaultBlockState());
		helper.setBlock(new BlockPos(7, 1, 8), Blocks.BED.red().defaultBlockState());
		fill(helper, 12, 0, 12, 20, 5, 20, Blocks.STONE.defaultBlockState());
		fill(helper, 13, 1, 13, 19, 4, 19, Blocks.AIR.defaultBlockState());
		helper.setBlock(new BlockPos(17, 1, 18), Blocks.BED.red().defaultBlockState());
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(8.5, 1, 6.5), 90);
		TheHead head = helper.spawn(HeadEntities.THE_HEAD, new Vec3(0.5, 1, 6.5));
		head.chase(target);
		List<HeadCue> cues = new ArrayList<>();
		boolean[] moved = { false };
		helper.onEachTick(() -> {
			HeadCue cue = head.cue();
			if (cues.isEmpty() || cues.getLast() != cue) cues.add(cue);
			if (!moved[0] && cues.contains(HeadCue.HOME_ASK) && cue.digHum()) {
				moved[0] = true;
				target.setPos(helper.absoluteVec(new Vec3(18.5, 1, 16.5)));
			}
			helper.assertTrue(!cues.contains(HeadCue.UNDERGROUND), "second house repeated underground speech: " + cues);
			if (target.isDeadOrDying()) {
				Vec3 c = helper.relativeVec(head.centre());
				helper.assertTrue(moved[0] && c.x > 13 && c.z > 13, "did not reach player inside second house: " + c);
				helper.assertTrue(cues.stream().filter(v -> v == HeadCue.HOME_NICE).count() == 1, "house speech repeated");
				helper.succeed();
			}
		});
	}

	@GameTest(structure = ARENA, maxTicks = 900)
	public void undergroundChaseEntersHouseBeforeChestDialogue(GameTestHelper helper) {
		fill(helper, 0, -5, 0, 19, 14, 19, Blocks.AIR.defaultBlockState());
		fill(helper, 0, -5, 0, 19, 4, 19, Blocks.STONE.defaultBlockState());
		fill(helper, 2, -3, 7, 6, 0, 12, Blocks.AIR.defaultBlockState());
		fill(helper, 14, -3, 9, 15, -2, 10, Blocks.AIR.defaultBlockState());
		fill(helper, 8, 5, 4, 18, 10, 14, Blocks.STONE.defaultBlockState());
		fill(helper, 9, 5, 5, 17, 9, 13, Blocks.AIR.defaultBlockState());
		fill(helper, 0, 11, 0, 19, 14, 19, Blocks.AIR.defaultBlockState());
		helper.setBlock(new BlockPos(14, 5, 12), Blocks.BED.red().defaultBlockState());
		BlockPos chest = new BlockPos(11, 5, 9);
		helper.setBlock(chest, Blocks.CHEST.defaultBlockState());
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(14.5, -3, 9.5), 90);
		TheHead head = helper.spawn(HeadEntities.THE_HEAD, new Vec3(3.5, -3, 9.5));
		head.chase(target);
		List<HeadCue> cues = new ArrayList<>();
		boolean[] moved = { false };
		boolean[] escaped = { false };
		boolean[] food = { false };
		helper.onEachTick(() -> {
			HeadCue cue = head.cue();
			if (cues.isEmpty() || cues.getLast() != cue) cues.add(cue);
			if (!moved[0] && cues.contains(HeadCue.UNDERGROUND) && cue.digHum()) {
				moved[0] = true;
				target.setPos(helper.absoluteVec(new Vec3(16.5, 5, 9.5)));
			}
			if (!escaped[0] && cues.contains(HeadCue.HOME_ASK) && cue.digHum()) {
				escaped[0] = true;
				target.setPos(helper.absoluteVec(new Vec3(25.5, 5, 9.5)));
			}
			if (cue == HeadCue.HOME_HUNGRY || cue == HeadCue.HOME_THIRSTY) {
				Vec3 c = helper.relativeVec(head.centre());
				helper.assertTrue(c.x > 10.5 && c.y > 6 && helper.getLevel().noCollision(head, head.getBoundingBox()),
					"underground approach started food before entering house: " + c);
				food[0] = true;
			}
			helper.assertTrue(cues.stream().filter(c -> c == HeadCue.UNDERGROUND).count() <= 1, "house repeated underground line: " + cues);
			if (cue == HeadCue.HOME_RETURN) {
				helper.assertTrue(moved[0] && escaped[0] && food[0] && helper.getBlockState(chest).isAir(), "underground to house raid incomplete at " + helper.relativeVec(head.centre()) + ": " + cues);
				helper.succeed();
			}
		});
	}

	@GameTest(structure = ARENA)
	public void houseTntModePersistsAcrossOpenGround(GameTestHelper helper) {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(18.5, 1, 9.5), 90);
		TheHead head = helper.spawn(HeadEntities.THE_HEAD, new Vec3(2.5, 1, 9.5));
		head.chase(target);
		try {
			for (String name : List.of("digging", "homeUsed", "fitsWay")) {
				var field = TheHead.class.getDeclaredField(name);
				field.setAccessible(true);
				field.setBoolean(head, true);
			}
			var watch = TheHead.class.getDeclaredMethod("watchForSeal", ServerLevel.class);
			watch.setAccessible(true);
			watch.invoke(head, helper.getLevel());
			var digging = TheHead.class.getDeclaredField("digging");
			digging.setAccessible(true);
			helper.assertTrue(digging.getBoolean(head), "house TNT mode ended while crossing open ground");
			helper.succeed();
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	@GameTest(structure = ARENA, maxTicks = 160)
	public void touchingDuringChestSequenceIsFatal(GameTestHelper helper) {
		fatalHomeContact(helper, "ASK");
	}

	@GameTest(structure = ARENA, maxTicks = 160)
	public void touchingDuringFoodSpeechIsFatal(GameTestHelper helper) {
		fatalHomeContact(helper, "RAID_START");
	}

	@GameTest(structure = ARENA, maxTicks = 160)
	public void touchingDuringChestDashIsFatal(GameTestHelper helper) {
		fatalHomeContact(helper, "RAID");
	}

	@GameTest(structure = ARENA, maxTicks = 160)
	public void touchingDuringReturnThreatIsFatal(GameTestHelper helper) {
		fatalHomeContact(helper, "RETURN");
	}

	@GameTest(structure = ARENA, maxTicks = 160)
	public void touchingDuringOrdinaryTauntIsFatal(GameTestHelper helper) {
		fatalHomeContact(helper, "TAUNT");
	}

	private void fatalHomeContact(GameTestHelper helper, String name) {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(5.5, 1, 5.5), 90);
		TheHead head = helper.spawn(HeadEntities.THE_HEAD, new Vec3(5.5, 1, 5.5));
		head.chase(target);
		head.setNoAi(true);
		helper.runAtTickTime(120, () -> {
			try {
				var stage = TheHead.class.getDeclaredField("homeStage");
				stage.setAccessible(true);
				for (Object value : stage.getType().getEnumConstants()) {
					if (value.toString().equals(name)) stage.set(head, value);
				}
				if (name.startsWith("RAID")) {
					BlockPos pos = helper.absolutePos(new BlockPos(8, 1, 5));
					helper.getLevel().setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
					var chests = TheHead.class.getDeclaredField("homeChests");
					chests.setAccessible(true);
					@SuppressWarnings("unchecked")
					List<BlockPos> targets = (List<BlockPos>) chests.get(head);
					targets.add(pos);
					var until = TheHead.class.getDeclaredField("raidUntil");
					until.setAccessible(true);
					until.setLong(head, helper.getLevel().getGameTime() + 100);
				}
				Vec3 c = head.centre();
				target.setPos(c.x, c.y - 0.9, c.z);
				var cue = TheHead.class.getDeclaredMethod("setCue", HeadCue.class);
				cue.setAccessible(true);
				if (name.equals("TAUNT")) {
					var teasing = TheHead.class.getDeclaredField("teasing");
					teasing.setAccessible(true);
					teasing.setBoolean(head, true);
				}
				cue.invoke(head, name.equals("TAUNT") ? HeadCue.TAUNT_100 : name.equals("RAID_START") ? HeadCue.HOME_HUNGRY : name.equals("RETURN") ? HeadCue.HOME_RETURN
					: name.equals("RAID") ? HeadCue.DIG_MMM1 : HeadCue.HOME_NICE);
				var charge = TheHead.class.getDeclaredMethod("charge", ServerLevel.class, net.minecraft.world.entity.player.Player.class);
				charge.setAccessible(true);
				charge.invoke(head, helper.getLevel(), target);
				helper.assertTrue(target.isDeadOrDying(), "contact immunity during " + name);
				helper.succeed();
			} catch (ReflectiveOperationException e) {
				throw new AssertionError(e);
			}
		});
	}

	@GameTest(structure = ARENA, maxTicks = 160)
	public void prankWarningDoesNotGrantContactImmunity(GameTestHelper helper) {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(5.5, 1, 5.5), 90);
		TheHead head = helper.spawn(HeadEntities.THE_HEAD, new Vec3(5.5, 1, 5.5));
		head.chase(target);
		head.setNoAi(true);
		helper.runAtTickTime(120, () -> {
			try {
				var field = TheHead.class.getDeclaredField("prank");
				field.setAccessible(true);
				var prank = (com.larsoyd.mariohead.entity.BehindPrank) field.get(head);
				prank.warning(helper.getLevel().getGameTime(), 31);
				Vec3 c = head.centre();
				target.setPos(c.x, c.y - 0.9, c.z);
				var charge = TheHead.class.getDeclaredMethod("charge", ServerLevel.class, net.minecraft.world.entity.player.Player.class);
				charge.setAccessible(true);
				charge.invoke(head, helper.getLevel(), target);
				helper.assertTrue(target.isDeadOrDying(), "prank warning granted contact immunity");
				helper.succeed();
			} catch (ReflectiveOperationException e) {
				throw new AssertionError(e);
			}
		});
	}

	@GameTest(structure = ARENA)
	public void prankWithoutRouteDoesNotDriftOnOldSteering(GameTestHelper helper) throws ReflectiveOperationException {
		fill(helper, 0, 0, 6, 19, 7, 6, Blocks.STONE.defaultBlockState());
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(12.5, 1, 15.5), 0);
		TheHead head = head(helper, new Vec3(12.5, 1, 3.5), target);
		head.setNoAi(true);
		var field = TheHead.class.getDeclaredField("prank");
		field.setAccessible(true);
		var prank = (com.larsoyd.mariohead.entity.BehindPrank) field.get(head);
		long now = helper.getLevel().getGameTime();
		prank.observe(now, true, 0);
		prank.observe(now + 100, true, 0);
		for (String name : List.of("replanIn", "moveSpeed", "moveYaw", "aimDistance")) {
			var value = TheHead.class.getDeclaredField(name);
			value.setAccessible(true);
			if (name.equals("replanIn")) value.setInt(head, 20);
			else if (name.equals("moveYaw")) value.setFloat(head, 90);
			else value.setDouble(head, name.equals("moveSpeed") ? 0.3 : 10);
		}
		var tick = TheHead.class.getDeclaredMethod("tickPrank", ServerLevel.class, net.minecraft.world.entity.player.Player.class);
		tick.setAccessible(true);
		Vec3 before = head.position();
		tick.invoke(head, helper.getLevel(), target);
		helper.assertTrue(head.position().distanceTo(before) < 0.001, "no-route prank drifted on stale steering");
		var deadline = com.larsoyd.mariohead.entity.BehindPrank.class.getDeclaredField("deadline");
		deadline.setAccessible(true);
		deadline.setLong(prank, now);
		helper.assertTrue(!(boolean) tick.invoke(head, helper.getLevel(), target), "expired approach did not release chase");
		helper.assertTrue(prank.stage() == com.larsoyd.mariohead.entity.BehindPrank.Stage.DONE, "expired approach can retry");
		helper.succeed();
	}

	@GameTest(structure = ARENA, maxTicks = 1100)
	public void stalkingAfterHouseReturnResumesTntDash(GameTestHelper helper) throws ReflectiveOperationException {
		stalkingChase(helper, true, 0);
	}

	@GameTest(structure = ARENA, maxTicks = 1100)
	public void stalkingAfterOrdinaryChaseResumesDash(GameTestHelper helper) throws ReflectiveOperationException {
		stalkingChase(helper, false, 0);
	}

	@GameTest(structure = ARENA, maxTicks = 1100)
	public void stalkingAfterHouseReturnLookRestoresDash(GameTestHelper helper) throws ReflectiveOperationException {
		stalkingChase(helper, true, 1);
	}

	@GameTest(structure = ARENA, maxTicks = 1100)
	public void stalkingAfterHouseReturnDistantLookRestoresDash(GameTestHelper helper) throws ReflectiveOperationException {
		stalkingChase(helper, true, 2);
	}

	@GameTest(structure = ARENA, maxTicks = 1100)
	public void stalkingAfterHouseReturnEarlyLookRestoresDash(GameTestHelper helper) throws ReflectiveOperationException {
		stalkingChase(helper, true, 3);
	}

	@GameTest(structure = ARENA, maxTicks = 1100)
	public void stalkingAfterOrdinaryChaseLookRestoresDash(GameTestHelper helper) throws ReflectiveOperationException {
		stalkingChase(helper, false, 1);
	}

	@GameTest(structure = ARENA, maxTicks = 1100)
	public void stalkingAfterOrdinaryChaseDistantLookRestoresDash(GameTestHelper helper) throws ReflectiveOperationException {
		stalkingChase(helper, false, 2);
	}

	@GameTest(structure = ARENA, maxTicks = 1100)
	public void stalkingAfterOrdinaryChaseEarlyLookRestoresDash(GameTestHelper helper) throws ReflectiveOperationException {
		stalkingChase(helper, false, 3);
	}

	private void stalkingChase(GameTestHelper helper, boolean house, int reaction) throws ReflectiveOperationException {
		int y = 40 + reaction * 30 + (house ? 15 : 0);
		fill(helper, 0, y - 1, 0, 220, y - 1, 23, Blocks.STONE.defaultBlockState());
		if (house) {
			fill(helper, 8, y, 4, 18, y + 4, 14, Blocks.STONE.defaultBlockState());
			fill(helper, 9, y, 5, 17, y + 3, 13, Blocks.AIR.defaultBlockState());
			helper.setBlock(new BlockPos(14, y, 12), Blocks.BED.red().defaultBlockState());
			helper.setBlock(new BlockPos(9, y, 9), Blocks.CHEST.defaultBlockState());
		}
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(16.5, y, 9.5), -90);
		TheHead head = helper.spawn(HeadEntities.THE_HEAD, new Vec3(6, y, 9.5));
		head.chase(target);
		var field = TheHead.class.getDeclaredField("prank");
		field.setAccessible(true);
		var prank = (com.larsoyd.mariohead.entity.BehindPrank) field.get(head);
		List<HeadCue> cues = new ArrayList<>();
		boolean[] earlyLook = { false };
		boolean[] dashed = { false };
		long[] escaped = { -1 };
		long[] approach = { -1 };
		long[] finished = { -1 };
		Vec3[] resumed = { Vec3.ZERO };
		helper.onEachTick(() -> {
			head.getRandom().setSeed(12345);
			HeadCue cue = head.cue();
			if (cues.isEmpty() || cues.getLast() != cue) cues.add(cue);
			var stage = prank.stage();
			if (house && escaped[0] < 0 && cues.contains(HeadCue.HOME_ASK) && cue.digHum()) {
				target.setPos(helper.absoluteVec(new Vec3(42.5, y, 9.5)));
			}
			if (escaped[0] < 0 && (house ? cue == HeadCue.HOME_RETURN : (cue == HeadCue.MMM1 || cue == HeadCue.MMM2 || cue == HeadCue.MMM3))) {
				helper.assertTrue(head.digging() == house, "wrong dash mode before escape: " + cues);
				if (house) helper.assertTrue(cues.contains(HeadCue.HOME_NICE) && cues.contains(HeadCue.HOME_ASK)
					&& (cues.contains(HeadCue.HOME_HUNGRY) || cues.contains(HeadCue.HOME_THIRSTY)), "missing house sequence: " + cues);
				escaped[0] = helper.getTick();
			}
			if (escaped[0] >= 0 && approach[0] < 0) {
				if (stage == com.larsoyd.mariohead.entity.BehindPrank.Stage.APPROACH) {
					approach[0] = helper.getTick();
					helper.assertTrue(approach[0] - escaped[0] >= 100, "stalking skipped escape timer");
				} else {
					target.setPos(head.getX() + 70, helper.absoluteVec(new Vec3(0, y, 0)).y, head.getZ());
					helper.assertTrue(helper.getTick() - escaped[0] < 240, "live chase never stalked house=" + house + " cues=" + cues);
				}
			}
			if (approach[0] >= 0 && stage != com.larsoyd.mariohead.entity.BehindPrank.Stage.DONE) {
				helper.assertTrue(!head.digging(), "stalking retained TNT");
				if (stage == com.larsoyd.mariohead.entity.BehindPrank.Stage.APPROACH) helper.assertTrue(cue == HeadCue.NONE, "stalking was not quiet: " + cue);
			}
			if (stage == com.larsoyd.mariohead.entity.BehindPrank.Stage.APPROACH && (reaction == 2
				|| reaction == 3 && head.distanceTo(target) < 14)) {
				target.setYRot(90);
				earlyLook[0] = true;
			}
			if (reaction == 1 && cue == HeadCue.PRANK_WARNING) target.setYRot(90);
			if (stage == com.larsoyd.mariohead.entity.BehindPrank.Stage.DASH) {
				dashed[0] = true;
				helper.assertTrue(cues.contains(HeadCue.PRANK_WARNING) && prank.punishment() == (reaction == 0), "wrong warning reaction: " + cues);
				if (reaction == 1) helper.assertTrue(cues.contains(HeadCue.PRANK_RESPONSE), "missing looked-at response");
				target.setPos(head.getX() + 20, helper.absoluteVec(new Vec3(0, y, 0)).y, head.getZ());
			}
			if (stage == com.larsoyd.mariohead.entity.BehindPrank.Stage.DONE) {
				if (reaction < 2) helper.assertTrue(dashed[0] && cues.contains(HeadCue.PRANK_WARNING), "stalking ended without warning and dash: " + cues);
				else {
					helper.assertTrue(earlyLook[0] && !cues.contains(HeadCue.PRANK_WARNING), "early sighting did not cancel warning: " + cues);
					boolean greeted = cues.contains(HeadCue.TAUNT_026) || cues.contains(HeadCue.TAUNT_078);
					helper.assertTrue(greeted == (reaction == 3), "wrong greeting for distance: " + cues);
				}
				helper.assertTrue(head.digging() == house, "stalking failed to restore original dash mode");
				if (finished[0] < 0) {
					finished[0] = helper.getTick();
					resumed[0] = head.position();
					target.setPos(head.getX() + 40, helper.absoluteVec(new Vec3(0, y, 0)).y, head.getZ());
				}
				if ((house ? cue.digHum() : (cue == HeadCue.MMM1 || cue == HeadCue.MMM2 || cue == HeadCue.MMM3)) && head.position().distanceTo(resumed[0]) > 1) {
					com.larsoyd.mariohead.MarioHead.LOG.info("[stalking-test] house={} reaction={} escape={} approach={} resumed={} cue={}", house, reaction, escaped[0], approach[0], helper.getTick(), cue);
					helper.succeed();
				}
				helper.assertTrue(helper.getTick() - finished[0] < 150, "chase did not resume after stalking: " + cues);
			}
			helper.assertTrue(!head.isRemoved() && !target.isDeadOrDying(), "chase ended before stalking completed: " + cues);
		});
	}

	@GameTest(structure = ARENA, maxTicks = 140)
	public void prankTriggersAfterFiveSecondsOutsideHumRange(GameTestHelper helper) throws ReflectiveOperationException {
		prankEscape(helper, false);
	}

	@GameTest(structure = ARENA, maxTicks = 140)
	public void prankTemporarilySuspendsPersistentTnt(GameTestHelper helper) throws ReflectiveOperationException {
		prankEscape(helper, true);
	}

	private void prankEscape(GameTestHelper helper, boolean tnt) throws ReflectiveOperationException {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(12.5, 1, 12.5), 0);
		TheHead head = helper.spawn(HeadEntities.THE_HEAD, new Vec3(12.5, 100, 4.5));
		head.chase(target);
		head.setNoAi(true);
		if (tnt) {
			var digging = TheHead.class.getDeclaredMethod("setDigging", boolean.class);
			digging.setAccessible(true);
			digging.invoke(head, true);
		}
		var field = TheHead.class.getDeclaredField("prank");
		field.setAccessible(true);
		var prank = (com.larsoyd.mariohead.entity.BehindPrank) field.get(head);
		var tick = TheHead.class.getDeclaredMethod("tickPrank", ServerLevel.class, net.minecraft.world.entity.player.Player.class);
		tick.setAccessible(true);
		var finish = TheHead.class.getDeclaredMethod("endPrank");
		finish.setAccessible(true);
		long[] start = { -1 };
		helper.onEachTick(() -> {
			try {
				long now = helper.getLevel().getGameTime();
				if (start[0] < 0) start[0] = now;
				head.getRandom().setSeed(12345);
				boolean active = (boolean) tick.invoke(head, helper.getLevel(), target);
				if (now - start[0] < 100) helper.assertTrue(!active, "prank rolled before five seconds");
				else {
					helper.assertTrue(active && prank.stage() == com.larsoyd.mariohead.entity.BehindPrank.Stage.APPROACH, "escaped player did not trigger quiet approach");
					helper.assertTrue(head.cue() == HeadCue.NONE && !head.digging(), "quiet approach retained hum or TNT");
					finish.invoke(head);
					helper.assertTrue(head.digging() == tnt, "prank lost persistent TNT mode");
					helper.succeed();
				}
			} catch (ReflectiveOperationException e) {
				throw new AssertionError(e);
			}
		});
	}

	@GameTest(structure = ARENA, maxTicks = 180)
	public void prankLookDuringWarningDashesOnClosedMouthLaugh(GameTestHelper helper) throws ReflectiveOperationException {
		prankReaction(helper, true);
	}

	@GameTest(structure = ARENA, maxTicks = 180)
	public void prankIgnoringWarningGetsExactlyOneBoostedDash(GameTestHelper helper) throws ReflectiveOperationException {
		prankReaction(helper, false);
	}

	private void prankReaction(GameTestHelper helper, boolean look) throws ReflectiveOperationException {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(12.5, 1, 12.5), 0);
		TheHead head = head(helper, new Vec3(12.5, 1.0, 4.5), target);
		var phase = TheHead.class.getDeclaredMethod("setPhase", HeadPhase.class);
		phase.setAccessible(true);
		phase.invoke(head, HeadPhase.CHARGE);
		var field = TheHead.class.getDeclaredField("prank");
		field.setAccessible(true);
		var prank = (com.larsoyd.mariohead.entity.BehindPrank) field.get(head);
		long now = helper.getLevel().getGameTime();
		prank.observe(now, true, 0);
		prank.observe(now + 100, true, 0);
		long[] warning = { -1 };
		long[] response = { -1 };
		long[] dash = { -1 };
		Vec3[] stopped = { head.position() };
		helper.onEachTick(() -> {
			long tick = helper.getLevel().getGameTime();
			if (head.cue() == HeadCue.PRANK_WARNING && warning[0] < 0) {
				warning[0] = tick;
				stopped[0] = head.position();
			}
			if (look && warning[0] >= 0) target.setYRot(tick - warning[0] >= 5 && tick - warning[0] < 8 ? 180 : 0);
			if (head.cue() == HeadCue.PRANK_RESPONSE && response[0] < 0) response[0] = tick;
			if (prank.stage() == com.larsoyd.mariohead.entity.BehindPrank.Stage.DASH) {
				try {
					var until = TheHead.class.getDeclaredField("stallUntil"); until.setAccessible(true);
					var following = TheHead.class.getDeclaredField("stallFollowing"); following.setAccessible(true);
					helper.assertTrue(until.getLong(head) < 0 && !following.getBoolean(head), "stalking dash became a mock/follow");
				} catch (ReflectiveOperationException e) { throw new AssertionError(e); }
				if (dash[0] < 0) {
					dash[0] = tick;
					helper.assertTrue(warning[0] >= 0, "dash skipped warning");
					helper.assertTrue(prank.punishment() != look, "wrong response branch");
					if (look) {
						helper.assertTrue(response[0] >= 0 && tick - response[0] >= 30, "dash interrupted spoken made-you-look");
						helper.assertTrue(!com.larsoyd.mariohead.entity.HeadPose.mouthOpen(HeadPhase.CHARGE, head.cue(), 1.5, 0), "laugh mouth flapped");
					} else {
						helper.assertTrue(tick - warning[0] == 51, "punishment must wait warning plus one second: " + (tick - warning[0]));
						helper.assertTrue(response[0] < 0, "ignored warning got made-you-look");
						helper.assertTrue(head.chargeSpeed() >= RevSpring.speed(head.revs(), 0, 0) * 1.49,
							"punishment dash was not faster: " + head.chargeSpeed());
					}
					target.setPos(helper.absoluteVec(new Vec3(12.5, 1, 21.5)));
				}
			} else if (dash[0] < 0 && warning[0] >= 0) {
				helper.assertTrue(head.position().distanceTo(stopped[0]) < 0.01, "moved while waiting for reaction");
			}
			if (prank.stage() == com.larsoyd.mariohead.entity.BehindPrank.Stage.DONE) {
				helper.assertTrue(dash[0] >= 0, "prank ended without a dash");
				int expected = (int) Math.ceil(com.larsoyd.mariohead.entity.HeadCue.hum(head.revs()).clip().frames() / 30.0 * 20 * (look ? 1 : 1.5));
				helper.assertTrue(tick - dash[0] == expected, "wrong one-dash duration: " + (tick - dash[0]) + " expected=" + expected);
				helper.succeed();
			}
		});
	}

	@GameTest(structure = ARENA)
	public void prankSpottedBeyondEarshotResumesWithoutGreeting(GameTestHelper helper) throws ReflectiveOperationException {
		prankDistantLook(helper, false);
	}

	@GameTest(structure = ARENA)
	public void prankDistantLookAfterWarningSkipsReply(GameTestHelper helper) throws ReflectiveOperationException {
		prankDistantLook(helper, true);
	}

	@GameTest(structure = ARENA)
	public void prankRememberedLookCannotQueueDistantReply(GameTestHelper helper) throws ReflectiveOperationException {
		prankDistantLook(helper, true, true);
	}

	private void prankDistantLook(GameTestHelper helper, boolean warned) throws ReflectiveOperationException {
		prankDistantLook(helper, warned, false);
	}

	private void prankDistantLook(GameTestHelper helper, boolean warned, boolean left) throws ReflectiveOperationException {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(16.5, 1, 16.5), 135);
		TheHead head = head(helper, new Vec3(3.5, 1, 3.5), target);
		head.setNoAi(true);
		helper.assertTrue(HeadSight.seenBy(target, head.getBoundingBox()), "fixture must show distant head");
		var field = TheHead.class.getDeclaredField("prank");
		field.setAccessible(true);
		var prank = (com.larsoyd.mariohead.entity.BehindPrank) field.get(head);
		long now = helper.getLevel().getGameTime();
		prank.observe(now, true, 0);
		prank.observe(now + 100, true, 0);
		if (warned) prank.warning(now - 50, 31);
		if (left) {
			prank.sawPlayerLook();
			target.setYRot(0);
		}
		var tick = TheHead.class.getDeclaredMethod("tickPrank", ServerLevel.class, net.minecraft.world.entity.player.Player.class);
		tick.setAccessible(true);
		helper.assertTrue(!(boolean) tick.invoke(head, helper.getLevel(), target), "distant sighting kept prank active");
		helper.assertTrue(prank.stage() == com.larsoyd.mariohead.entity.BehindPrank.Stage.DONE, "distant sighting did not resume chase");
		helper.assertTrue(head.cue() == HeadCue.INHALE, "distant sighting played inaudible speech: " + head.cue());
		helper.succeed();
	}

	@GameTest(structure = ARENA)
	public void prankApproachWaitsForPreviousHum(GameTestHelper helper) throws ReflectiveOperationException {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(12.5, 1, 15.5), 0);
		TheHead head = head(helper, new Vec3(12.5, 1, 4.5), target);
		head.setNoAi(true);
		var field = TheHead.class.getDeclaredField("prank");
		field.setAccessible(true);
		var prank = (com.larsoyd.mariohead.entity.BehindPrank) field.get(head);
		long now = helper.getLevel().getGameTime();
		prank.observe(now, true, 0);
		prank.observe(now + 100, true, 0);
		var cue = TheHead.class.getDeclaredMethod("setCue", HeadCue.class);
		cue.setAccessible(true);
		cue.invoke(head, HeadCue.MMM1);
		var tick = TheHead.class.getDeclaredMethod("tickPrank", ServerLevel.class, net.minecraft.world.entity.player.Player.class);
		tick.setAccessible(true);
		Vec3 before = head.position();
		tick.invoke(head, helper.getLevel(), target);
		helper.assertTrue(head.position().distanceTo(before) < 0.001, "quiet approach moved during previous hum");
		helper.succeed();
	}

	@GameTest(structure = ARENA)
	public void prankStopsBehindPlayerAndWarns(GameTestHelper helper) throws ReflectiveOperationException {
		prankRehearsal(helper, false);
	}

	@GameTest(structure = ARENA)
	public void prankSpottedEarlyUsesGreeting(GameTestHelper helper) throws ReflectiveOperationException {
		prankRehearsal(helper, true);
	}

	private void prankRehearsal(GameTestHelper helper, boolean spotted) throws ReflectiveOperationException {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(12.5, 1, 12.5), spotted ? 180 : 0);
		TheHead head = head(helper, new Vec3(12.5, 1.0, 4.5), target);
		head.setNoAi(true);
		var field = TheHead.class.getDeclaredField("prank");
		field.setAccessible(true);
		var prank = (com.larsoyd.mariohead.entity.BehindPrank) field.get(head);
		long now = helper.getLevel().getGameTime();
		prank.observe(now, true, 0);
		prank.observe(now + 100, true, 0);
		var tick = TheHead.class.getDeclaredMethod("tickPrank", ServerLevel.class, net.minecraft.world.entity.player.Player.class);
		tick.setAccessible(true);
		Vec3 before = head.position();
		helper.assertTrue((boolean) tick.invoke(head, helper.getLevel(), target), "prank did not take over chase");
		if (spotted) {
			helper.assertTrue(head.cue() == HeadCue.TAUNT_026 || head.cue() == HeadCue.TAUNT_078, "spotted approach did not greet: " + head.cue());
		} else {
			helper.assertTrue(head.cue() == HeadCue.PRANK_WARNING, "behind approach did not warn: " + head.cue());
			helper.assertTrue(head.position().distanceTo(before) < 0.01, "warning moved instead of stopping");
		}
		helper.succeed();
	}

	@GameTest(structure = ARENA)
	public void chestDialogueFacesOwnerBeforeAskingForFood(GameTestHelper helper) throws ReflectiveOperationException {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 1, 14.5), 0);
		TheHead head = helper.spawn(HeadEntities.THE_HEAD, new Vec3(8.5, 1, 8.5));
		head.chase(target);
		head.setNoAi(true);
		head.setYRot(90);
		BlockPos chest = helper.absolutePos(new BlockPos(11, 1, 8));
		helper.getLevel().setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
		var stage = TheHead.class.getDeclaredField("homeStage");
		stage.setAccessible(true);
		for (Object value : stage.getType().getEnumConstants()) if (value.toString().equals("LOOT")) stage.set(head, value);
		var chests = TheHead.class.getDeclaredField("homeChests");
		chests.setAccessible(true);
		@SuppressWarnings("unchecked")
		List<BlockPos> targets = (List<BlockPos>) chests.get(head);
		targets.add(chest);
		var tick = TheHead.class.getDeclaredMethod("tickHome", ServerLevel.class, net.minecraft.world.entity.player.Player.class);
		tick.setAccessible(true);
		tick.invoke(head, helper.getLevel(), target);
		helper.assertTrue(head.cue() != HeadCue.HOME_HUNGRY && head.cue() != HeadCue.HOME_THIRSTY, "food speech began facing away from owner");
		float change = Math.abs(net.minecraft.util.Mth.wrapDegrees(head.getYRot() - 90));
		helper.assertTrue(change > 0 && change <= HeadTuning.TURN_DEGREES + 0.001, "chest turn snapped or did not start: " + change);
		for (int i = 0; i < 60; i++) tick.invoke(head, helper.getLevel(), target);
		helper.assertTrue(head.cue() == HeadCue.HOME_HUNGRY || head.cue() == HeadCue.HOME_THIRSTY, "aligned owner never got food speech");
		Vec3 toward = target.position().subtract(head.centre());
		float expected = (float) Math.toDegrees(Math.atan2(-toward.x, toward.z));
		helper.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(head.getYRot() - expected)) < 2, "hungry opening looked at chest instead of owner");
		helper.succeed();
	}

	@GameTest(structure = ARENA, maxTicks = 110)
	public void hungryTracksMovingOwnerThenTurnsToChest(GameTestHelper helper) throws ReflectiveOperationException {
		foodGazeScenario(helper, HeadCue.HOME_HUNGRY, 33);
	}

	@GameTest(structure = ARENA, maxTicks = 110)
	public void thirstyTracksMovingOwnerThenTurnsToChest(GameTestHelper helper) throws ReflectiveOperationException {
		foodGazeScenario(helper, HeadCue.HOME_THIRSTY, 45);
	}

	private void foodGazeScenario(GameTestHelper helper, HeadCue line, int turnTick) throws ReflectiveOperationException {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(8.5, 1, 15.5), 0);
		TheHead head = helper.spawn(HeadEntities.THE_HEAD, new Vec3(8.5, 1, 8.5));
		head.chase(target);
		head.setNoAi(true);
		BlockPos chest = helper.absolutePos(new BlockPos(12, 1, 8));
		helper.getLevel().setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
		var stage = TheHead.class.getDeclaredField("homeStage");
		stage.setAccessible(true);
		for (Object value : stage.getType().getEnumConstants()) if (value.toString().equals("RAID_START")) stage.set(head, value);
		var chests = TheHead.class.getDeclaredField("homeChests");
		chests.setAccessible(true);
		@SuppressWarnings("unchecked")
		List<BlockPos> targets = (List<BlockPos>) chests.get(head);
		targets.add(chest);
		var cue = TheHead.class.getDeclaredMethod("setCue", HeadCue.class);
		cue.setAccessible(true);
		cue.invoke(head, line);
		var elapsed = TheHead.class.getDeclaredField("cueTicks");
		elapsed.setAccessible(true);
		var tick = TheHead.class.getDeclaredMethod("tickHome", ServerLevel.class, net.minecraft.world.entity.player.Player.class);
		tick.setAccessible(true);
		Vec3 before = head.position();
		helper.onEachTick(() -> {
			int time = (int) helper.getTick();
			target.setPos(helper.absoluteVec(new Vec3(8.5 + Math.sin(time * 0.06) * 2, 1, 15.5)));
			try {
				elapsed.setInt(head, time);
				tick.invoke(head, helper.getLevel(), target);
			} catch (ReflectiveOperationException e) {
				throw new AssertionError(e);
			}
			Vec3 aim = time < turnTick ? target.position() : Vec3.atCenterOf(chest);
			Vec3 delta = aim.subtract(head.centre());
			float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
			if (time > 5 && time < turnTick || time > turnTick + 20) {
				helper.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(head.getYRot() - yaw)) < 3,
					"food gaze target wrong cue=" + line + " tick=" + time + " yaw=" + head.getYRot() + " expected=" + yaw);
			}
			helper.assertTrue(before.distanceTo(head.position()) < 0.01, "food speech drifted toward ceiling or floor");
			if (time == turnTick + 22) helper.succeed();
		});
	}

	@GameTest(structure = ARENA)
	public void returnThreatWaitsForEarshot(GameTestHelper helper) throws ReflectiveOperationException {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(30.5, 1, 8.5), 0);
		TheHead head = helper.spawn(HeadEntities.THE_HEAD, new Vec3(3.5, 1, 8.5));
		head.chase(target);
		head.setNoAi(true);
		var stage = TheHead.class.getDeclaredField("homeStage");
		stage.setAccessible(true);
		for (Object value : stage.getType().getEnumConstants()) if (value.toString().equals("RETURN")) stage.set(head, value);
		var tick = TheHead.class.getDeclaredMethod("charge", ServerLevel.class, net.minecraft.world.entity.player.Player.class);
		tick.setAccessible(true);
		Vec3 before = head.position();
		tick.invoke(head, helper.getLevel(), target);
		helper.assertTrue(head.cue() != HeadCue.HOME_RETURN, "return threat played outside clear earshot");
		helper.assertTrue(head.position().distanceTo(target.position()) < before.distanceTo(target.position()), "return approach did not advance");
		target.setPos(head.position().add(10, 0, 0));
		tick.invoke(head, helper.getLevel(), target);
		helper.assertTrue(head.cue() == HeadCue.HOME_RETURN, "return threat did not play in earshot");
		helper.succeed();
	}

	@GameTest(structure = ARENA, maxTicks = 180)
	public void replacingChestsCannotExtendRaidDeadline(GameTestHelper helper) {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(17.5, 1, 9.5), 90);
		TheHead head = helper.spawn(HeadEntities.THE_HEAD, new Vec3(3.5, 1, 9.5));
		head.chase(target);
		head.setNoAi(true);
		try {
			var stage = TheHead.class.getDeclaredField("homeStage");
			stage.setAccessible(true);
			for (Object value : stage.getType().getEnumConstants()) {
				if (value.toString().equals("RAID")) stage.set(head, value);
			}
			var until = TheHead.class.getDeclaredField("raidUntil");
			until.setAccessible(true);
			long deadline = helper.getLevel().getGameTime() + 100;
			until.setLong(head, deadline);
			var chests = TheHead.class.getDeclaredField("homeChests");
			chests.setAccessible(true);
			@SuppressWarnings("unchecked")
			List<BlockPos> targets = (List<BlockPos>) chests.get(head);
			var tick = TheHead.class.getDeclaredMethod("tickHome", ServerLevel.class, net.minecraft.world.entity.player.Player.class);
			tick.setAccessible(true);
			helper.onEachTick(() -> {
				try {
					BlockPos pos = helper.absolutePos(new BlockPos(15, 1, 5));
					helper.getLevel().setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
					if (!targets.contains(pos)) targets.add(pos);
					head.setPos(helper.absoluteVec(new Vec3(3.5, 1, 9.5)));
					tick.invoke(head, helper.getLevel(), target);
					if (helper.getLevel().getGameTime() >= deadline) {
						helper.assertTrue(!stage.get(head).toString().equals("RAID"), "new chests extended five-second raid");
						helper.assertTrue(targets.isEmpty(), "raid retained chest targets after its deadline");
						helper.assertTrue(head.chased() == target, "deadline lost the original player");
						helper.succeed();
					}
				} catch (ReflectiveOperationException e) {
					throw new AssertionError(e);
				}
			});
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	@GameTest(structure = ARENA)
	public void unopenedVillageChestDoesNotDropLoot(GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(new BlockPos(6, 1, 6));
		helper.getLevel().setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
		var container = (net.minecraft.world.RandomizableContainer) helper.getLevel().getBlockEntity(pos);
		container.setLootTable(net.minecraft.world.level.storage.loot.BuiltInLootTables.VILLAGE_PLAINS_HOUSE, 4321L);
		TheHead head = helper.spawn(HeadEntities.THE_HEAD, new Vec3(3.5, 1, 6.5));
		try {
			var destroy = TheHead.class.getDeclaredMethod("destroyRaidChest", ServerLevel.class, BlockPos.class);
			destroy.setAccessible(true);
			destroy.invoke(head, helper.getLevel(), pos);
			helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(), "unopened chest survived raid");
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
				new net.minecraft.world.phys.AABB(pos).inflate(2)).isEmpty(), "unopened village chest generated loot drops during raid");
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
		helper.succeed();
	}

	@GameTest(structure = ARENA)
	public void chestInventorySurvivesWhenGriefingIsOff(GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(new BlockPos(6, 1, 6));
		helper.getLevel().setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
		var container = (net.minecraft.world.Container) helper.getLevel().getBlockEntity(pos);
		container.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 17));
		TheHead head = helper.spawn(HeadEntities.THE_HEAD, new Vec3(3.5, 1, 6.5));
		var rules = helper.getLevel().getGameRules();
		var rule = net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING;
		boolean previous = rules.get(rule);
		try {
			rules.set(rule, false, helper.getLevel().getServer());
			var destroy = TheHead.class.getDeclaredMethod("destroyRaidChest", ServerLevel.class, BlockPos.class);
			destroy.setAccessible(true);
			destroy.invoke(head, helper.getLevel(), pos);
			helper.assertTrue(helper.getLevel().getBlockState(pos).is(Blocks.CHEST), "griefing-off destroyed chest");
			helper.assertTrue(container.getItem(0).getCount() == 17, "griefing-off deleted belongings");
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		} finally {
			rules.set(rule, previous, helper.getLevel().getServer());
		}
		helper.succeed();
	}

	@GameTest(structure = ARENA, maxTicks = 160)
	public void homeDiggingSuppressesUndergroundAnnouncement(GameTestHelper helper) {
		helper.runAtTickTime(120, () -> {
			TheHead head = helper.spawn(HeadEntities.THE_HEAD, new Vec3(4.5, 1, 4.5));
			try {
				for (String name : List.of("digging", "homeUsed", "sealed")) {
					var field = TheHead.class.getDeclaredField(name);
					field.setAccessible(true);
					field.setBoolean(head, true);
				}
				var since = TheHead.class.getDeclaredField("sealedSince");
				since.setAccessible(true);
				since.setLong(head, helper.getLevel().getGameTime() - 120);
				var watch = TheHead.class.getDeclaredMethod("watchForSeal", ServerLevel.class);
				watch.setAccessible(true);
				watch.invoke(head, helper.getLevel());
				helper.assertTrue(head.cue() != HeadCue.UNDERGROUND, "house TNT mode repeated underground announcement");
				helper.succeed();
			} catch (ReflectiveOperationException e) {
				throw new AssertionError(e);
			}
		});
	}

	@GameTest(structure = ARENA, maxTicks = 650)
	public void chestRaidDestroysContentsAndReturnsToOriginalPlayer(GameTestHelper helper) {
		fill(helper, 8, 0, 4, 18, 5, 14, Blocks.STONE.defaultBlockState());
		fill(helper, 9, 1, 5, 17, 4, 13, Blocks.AIR.defaultBlockState());
		var chests = List.of(new BlockPos(10, 1, 7), new BlockPos(10, 1, 10));
		for (BlockPos pos : chests) {
			helper.setBlock(pos, Blocks.CHEST.defaultBlockState());
			((net.minecraft.world.Container) helper.getLevel().getBlockEntity(helper.absolutePos(pos)))
				.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 17));
		}
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(16.5, 1, 9.5), 90);
		TheHead head = helper.spawn(HeadEntities.THE_HEAD, new Vec3(3.5, 1, 9.5));
		head.chase(target);
		boolean[] hungry = { false };
		long[] raid = { -1 };
		helper.onEachTick(() -> {
			if (head.cue().digHum()) target.setPos(helper.absoluteVec(new Vec3(25.5, 1, 9.5)));
			if (head.cue() == HeadCue.HOME_HUNGRY || head.cue() == HeadCue.HOME_THIRSTY) hungry[0] = true;
			long now = helper.getLevel().getGameTime();
			if (hungry[0] && head.cue().digHum() && raid[0] < 0) {
				raid[0] = now;
				Vec3 away = helper.absoluteVec(new Vec3(25.5, 1, 9.5));
				target.setPos(away);
			}
			if (head.cue() == HeadCue.HOME_RETURN) {
				helper.assertTrue(hungry[0], "raid needs a food or drink line");
				helper.assertTrue(raid[0] >= 0 && now - raid[0] <= 160, "raid and return voice must be bounded");
				for (BlockPos pos : chests) helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(pos)).isAir(), "target chest survived " + pos);
				helper.assertTrue(head.chased() == target, "return must preserve the original chase owner");
				var drops = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
					new AABB(helper.absolutePos(new BlockPos(7, 0, 3))).expandTowards(13, 7, 13),
					item -> item.getItem().is(net.minecraft.world.item.Items.DIAMOND));
				helper.assertTrue(drops.isEmpty(), "chest contents escaped as item drops");
				helper.succeed();
			}
		});
	}

	@GameTest(structure = ARENA, maxTicks = 600)
	public void homeDialogueStillRunsAfterDiggingStarts(GameTestHelper helper) {
		fill(helper, 8, 0, 4, 16, 5, 12, Blocks.STONE.defaultBlockState());
		fill(helper, 9, 1, 5, 15, 4, 11, Blocks.AIR.defaultBlockState());
		helper.setBlock(new BlockPos(12, 1, 9), Blocks.BED.red().defaultBlockState());
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(12.5, 1, 8.5), 90);
		TheHead head = helper.spawn(HeadEntities.THE_HEAD, new Vec3(3.5, 1, 8.5));
		head.chase(target);
		try {
			var digging = TheHead.class.getDeclaredMethod("setDigging", boolean.class);
			digging.setAccessible(true);
			digging.invoke(head, true);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
		long[] spoken = { -1, -1 };
		helper.onEachTick(() -> {
			long now = helper.getLevel().getGameTime();
			if (head.cue() == HeadCue.HOME_NICE && spoken[0] < 0) spoken[0] = now;
			if (head.cue() == HeadCue.HOME_ASK && spoken[1] < 0) {
				spoken[1] = now;
				helper.assertTrue(now - spoken[0] == (HeadCue.HOME_NICE.clip().millis() + 49) / 50 + 10, "nice-place pause must be half a second");
			}
			if (spoken[1] >= 0 && head.cue().digHum()) {
				helper.assertTrue(now - spoken[1] == (HeadCue.HOME_ASK.clip().millis() + 49) / 50 + 4, "first TNT hum must follow ask by 0.2 seconds");
				helper.assertTrue(head.digging(), "TNT dash mode must be enabled");
				helper.succeed();
			}
		});
	}

	@GameTest(structure = ARENA)
	public void shelterRequiresEnclosureAndBedOrDoor(GameTestHelper helper) {
		fill(helper, 3, 1, 3, 11, 5, 11, Blocks.STONE.defaultBlockState());
		fill(helper, 4, 2, 4, 10, 4, 10, Blocks.AIR.defaultBlockState());
		var player = helper.absolutePos(new BlockPos(7, 2, 7));
		var outside = helper.absolutePos(new BlockPos(1, 2, 7));
		var level = helper.getLevel();
		helper.assertTrue(!com.larsoyd.mariohead.entity.HeadShelter.inspect(level, player, outside).sheltered(), "bare sealed room is not a home");
		helper.setBlock(new BlockPos(7, 2, 8), Blocks.BED.red().defaultBlockState());
		helper.setBlock(new BlockPos(8, 2, 8), Blocks.CHEST.defaultBlockState());
		helper.setBlock(new BlockPos(9, 2, 8), Blocks.CHEST.defaultBlockState());
		var shelter = com.larsoyd.mariohead.entity.HeadShelter.inspect(level, player, outside);
		helper.assertTrue(shelter.sheltered(), "enclosed bed room should trigger");
		helper.assertTrue(shelter.chests().size() == 2, "both room chests must be captured");
		helper.assertTrue(!com.larsoyd.mariohead.entity.HeadShelter.inspect(level, player, player).sheltered(), "head already inside must not trigger");
		fill(helper, 3, 2, 6, 3, 4, 8, Blocks.AIR.defaultBlockState());
		helper.assertTrue(!com.larsoyd.mariohead.entity.HeadShelter.inspect(level, player, outside).sheltered(), "open room must not trigger");
		fill(helper, 3, 2, 6, 3, 4, 8, Blocks.STONE.defaultBlockState());
		helper.setBlock(new BlockPos(7, 2, 8), Blocks.AIR.defaultBlockState());
		helper.setBlock(new BlockPos(3, 2, 7), Blocks.OAK_DOOR.defaultBlockState());
		helper.assertTrue(com.larsoyd.mariohead.entity.HeadShelter.inspect(level, player, outside).sheltered(), "closed door home needs no bed");
		helper.succeed();
	}

	@GameTest(structure = ARENA, maxTicks = 400, dimension = "minecraft:the_end")
	public void catchesRepeatedRealPearlEscapes(GameTestHelper helper) {
		pearlEscape(helper, false);
	}

	@GameTest(structure = ARENA, maxTicks = 600, dimension = "minecraft:the_end")
	public void pearlCatchupStillRespectsWalls(GameTestHelper helper) {
		pearlEscape(helper, true);
	}

	private static void pearlEscape(GameTestHelper helper, boolean wall) {
		fill(helper, 0, 0, 0, 220, 8, 19, Blocks.AIR.defaultBlockState());
		fill(helper, 0, -1, 0, 220, -1, 19, Blocks.STONE.defaultBlockState());
		if (wall) {
			fill(helper, 40, 0, 0, 40, 5, 14, Blocks.STONE.defaultBlockState());
		}
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(18.5, 0, 10.5), 90);
		TheHead head = head(helper, new Vec3(6.5, 0.5, 10.5), target);
		try {
			Field ready = TheHead.class.getDeclaredField("tauntReadyAt");
			ready.setAccessible(true);
			ready.setLong(head, Long.MAX_VALUE);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		int[] pearls = { 0 };
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (tick >= 80 && tick <= 200 && tick % 20 == 0 && !target.isDeadOrDying()) {
				target.setHealth(20);
				Vec3 destination = target.position().add(24, 0, 0);
				var pearl = new net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl(
					helper.getLevel(), target, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_PEARL));
				pearl.setPos(destination);
				pearl.setOldPosAndRot();
				helper.getLevel().addFreshEntity(pearl);
				try {
					var hit = pearl.getClass().getDeclaredMethod("onHit", net.minecraft.world.phys.HitResult.class);
					hit.setAccessible(true);
					hit.invoke(pearl, new net.minecraft.world.phys.BlockHitResult(destination, net.minecraft.core.Direction.UP,
						BlockPos.containing(destination), false));
				} catch (ReflectiveOperationException e) {
					throw new IllegalStateException(e);
				}
				helper.assertTrue(target.position().distanceTo(destination) < 0.1, "pearl did not teleport its owner");
				pearls[0]++;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(pearls[0] >= 2 && target.isDeadOrDying() && head.isRemoved(),
				"pearl escape: throws=" + pearls[0] + " gap=" + head.distanceTo(target));
		});
	}

	@GameTest(structure = ARENA, maxTicks = 30)
	public void tntDashOnlyBlastsObstructions(GameTestHelper helper) {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0, 10.5), -90);
		TheHead head = head(helper, new Vec3(16.5, 0.5, 10.5), target);
		try {
			var dig = TheHead.class.getDeclaredMethod("digToward", ServerLevel.class, Vec3.class, HeadCue.class, boolean.class);
			dig.setAccessible(true);
			Field blast = TheHead.class.getDeclaredField("lastBlastAt");
			blast.setAccessible(true);
			Vec3 to = head.centre().add(-10, 0, 0);
			for (int dash = 0; dash < 6; dash++) {
				dig.invoke(head, helper.getLevel(), to, HeadCue.DIG_MMM1, true);
				helper.assertFalse(head.burrowing(), "stationary clear-air dash enabled phasing");
			}
			helper.assertTrue(blast.get(head) == null, "empty-air dash exploded");
			helper.assertTrue(explosionPackets(target) == 0, "empty-air dash sent explosion packet");
			fill(helper, 13, 0, 9, 14, 3, 11, Blocks.STONE.defaultBlockState());
			dig.invoke(head, helper.getLevel(), to, HeadCue.DIG_MMM1, false);
			helper.assertTrue(stoneLeft(helper, 13, 0, 9, 14, 3, 11) == 24, "non-dash cut stone");
			dig.invoke(head, helper.getLevel(), to, HeadCue.DIG_MMM1, true);
			helper.assertTrue(blast.get(head) != null, "blocked dash did not explode");
			helper.assertTrue(explosionPackets(target) == 1, "blocked dash did not send audible explosion");
			helper.assertTrue(stoneLeft(helper, 13, 0, 9, 14, 3, 11) < 24, "blocked dash did not cut stone");
			head.discard();
			helper.succeed();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	@GameTest(structure = ARENA, maxTicks = 30)
	public void surfaceTntCancelsQueuedTreasureChatter(GameTestHelper helper) throws ReflectiveOperationException {
		treasureChatter(helper, false);
	}

	@GameTest(structure = ARENA, maxTicks = 30)
	public void undergroundTntPlaysQueuedTreasureChatter(GameTestHelper helper) throws ReflectiveOperationException {
		treasureChatter(helper, true);
	}

	private void treasureChatter(GameTestHelper helper, boolean underground) throws ReflectiveOperationException {
		if (underground) fill(helper, 5, -4, 5, 19, 7, 15, Blocks.STONE.defaultBlockState());
		else helper.setBlock(new BlockPos(6, 1, 10), Blocks.STONE.defaultBlockState());
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(18.5, 0, 10.5), 90);
		TheHead head = head(helper, new Vec3(3.5, 0.5, 10.5), target);
		head.setNoAi(true);
		var digging = TheHead.class.getDeclaredMethod("setDigging", boolean.class);
		digging.setAccessible(true);
		digging.invoke(head, true);
		Field line = TheHead.class.getDeclaredField("dashLine");
		line.setAccessible(true);
		line.set(head, HeadCue.DASH_073);
		var dash = TheHead.class.getDeclaredMethod("tickDigDash", long.class);
		dash.setAccessible(true);
		dash.invoke(head, helper.getLevel().getGameTime() + 200);
		helper.assertTrue(head.cue().chatter() == underground, "wrong treasure chatter eligibility underground=" + underground);
		helper.assertTrue(line.get(head) == HeadCue.NONE, "surface TNT retained queued treasure chatter");
		Field roll = TheHead.class.getDeclaredField("chatterRoll");
		roll.setAccessible(true);
		if (!underground) helper.assertFalse(roll.getBoolean(head), "surface hum armed treasure chatter");
		head.discard();
		helper.succeed();
	}

	@GameTest(structure = ARENA, maxTicks = 30)
	public void tntDashCrossesSuccessiveWallsBetweenHums(GameTestHelper helper) throws ReflectiveOperationException {
		dashThroughWalls(helper, 0.8, false);
	}

	@GameTest(structure = ARENA, maxTicks = 30)
	public void fastTntDashClearsItsWholeMovement(GameTestHelper helper) throws ReflectiveOperationException {
		dashThroughWalls(helper, 4.8, true);
	}

	private void dashThroughWalls(GameTestHelper helper, double velocity, boolean solid) throws ReflectiveOperationException {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(18.5, 0, 10.5), 90);
		TheHead head = head(helper, new Vec3(3.5, 0.5, 10.5), target);
		head.setNoAi(true);
		fill(helper, 7, 0, 8, 7, 4, 13, Blocks.STONE.defaultBlockState());
		fill(helper, 11, 0, 8, 11, 4, 13, Blocks.STONE.defaultBlockState());
		if (solid) fill(helper, 7, 0, 8, 14, 4, 13, Blocks.STONE.defaultBlockState());
		var dig = TheHead.class.getDeclaredMethod("digToward", ServerLevel.class, Vec3.class, HeadCue.class, boolean.class);
		dig.setAccessible(true);
		Field speed = TheHead.class.getDeclaredField("chargeSpeed");
		speed.setAccessible(true);
		speed.setDouble(head, velocity);
		Field moving = TheHead.class.getDeclaredField("moveSpeed");
		moving.setAccessible(true);
		moving.setDouble(head, velocity);
		Vec3 to = head.centre().add(12, 0, 0);
		for (int tick = 0; tick < (int) Math.ceil(12 / velocity); tick++) {
			dig.invoke(head, helper.getLevel(), to, HeadCue.DIG_MMM1, tick == 0);
		}
		helper.assertTrue(head.centre().distanceTo(to) < 0.1,
			"dash stopped between hums at " + helper.relativeVec(head.centre()));
		helper.assertFalse(head.burrowing(), "breakable walls caused phasing");
		head.discard();
		helper.succeed();
	}

	@GameTest(structure = ARENA, maxTicks = 30)
	public void tntDashKeepsDiggingWhileStoneBreaks(GameTestHelper helper) {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0, 10.5), -90);
		TheHead head = head(helper, new Vec3(16.5, 0.5, 10.5), target);
		try {
			var dig = TheHead.class.getDeclaredMethod("digToward", ServerLevel.class, Vec3.class, HeadCue.class, boolean.class);
			dig.setAccessible(true);
			Vec3 to = head.centre().add(-10, 0, 0);
			for (int dash = 0; dash < 6; dash++) {
				fill(helper, 13, 0, 9, 14, 3, 11, Blocks.STONE.defaultBlockState());
				dig.invoke(head, helper.getLevel(), to, HeadCue.DIG_MMM1, true);
				helper.assertFalse(head.burrowing(), "phased despite breaking stone on dash " + dash);
			}
			fill(helper, 13, 0, 9, 14, 3, 11, Blocks.OBSIDIAN.defaultBlockState());
			for (int dash = 0; dash < HeadTuning.DIG_STALLS; dash++) {
				dig.invoke(head, helper.getLevel(), to, HeadCue.DIG_MMM1, true);
			}
			helper.assertTrue(head.burrowing(), "unbreakable route lost escape fallback");
			head.discard();
			helper.succeed();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	@GameTest(structure = ARENA, maxTicks = 30)
	public void tntDashLeavesOpenGroundIntact(GameTestHelper helper) {
		fill(helper, 0, -1, 9, 19, -1, 11, Blocks.STONE.defaultBlockState());
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0, 10.5), -90);
		TheHead head = head(helper, new Vec3(16.5, 0.5, 10.5), target);
		try {
			var dig = TheHead.class.getDeclaredMethod("dig", ServerLevel.class, net.minecraft.world.entity.player.Player.class, HeadCue.class, boolean.class);
			dig.setAccessible(true);
			Field speed = TheHead.class.getDeclaredField("chargeSpeed");
			speed.setAccessible(true);
			speed.setDouble(head, 0.4);
			for (int tick = 0; tick < 160; tick++) {
				dig.invoke(head, helper.getLevel(), target, HeadCue.DIG_MMM1, tick % 40 == 0);
			}
			helper.assertTrue(stoneLeft(helper, 0, -1, 9, 19, -1, 11) == 60, "TNT chase dug a trench into open ground");
			helper.assertTrue(explosionPackets(target) == 0, "open-ground chase sent explosions");
			helper.assertFalse(head.burrowing(), "open-ground chase entered phasing");
			head.discard();
			helper.succeed();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	@GameTest(structure = ARENA, maxTicks = 2200, dimension = "minecraft:the_end")
	public void houseTntCatchesRunnerOnOpenGround(GameTestHelper helper) {
		houseRunner(helper, false);
	}

	@GameTest(structure = ARENA, maxTicks = 2200, dimension = "minecraft:the_end")
	public void houseTntClimbsOutToEscapingRunner(GameTestHelper helper) {
		houseRunner(helper, true);
	}

	@GameTest(structure = ARENA, maxTicks = 2200, dimension = "minecraft:the_end")
	public void houseTntCatchesRunnerEscapingBeforeReturnThreat(GameTestHelper helper) {
		houseRunner(helper, false, true);
	}

	private void houseRunner(GameTestHelper helper, boolean hill) {
		houseRunner(helper, hill, false);
	}

	private void houseRunner(GameTestHelper helper, boolean hill, boolean chest) {
		int base = chest ? 100 : hill ? 220 : 160;
		fill(helper, 0, base - 1, 5, 850, base - 1, 15, Blocks.STONE.defaultBlockState());
		fill(helper, 7, base, 5, 18, base + 4, 15, Blocks.OAK_PLANKS.defaultBlockState());
		fill(helper, 8, base, 6, 17, base + 3, 14, Blocks.AIR.defaultBlockState());
		helper.setBlock(new BlockPos(14, base, 9), Blocks.BED.red().defaultBlockState());
		if (chest) helper.setBlock(new BlockPos(9, base, 9), Blocks.CHEST.defaultBlockState());
		if (hill) {
			for (int x = 20; x <= 850; x++) {
				fill(helper, x, base, 8, x, base + Math.min(7, x - 20), 12, Blocks.STONE.defaultBlockState());
			}
		}
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(14.5, base, 9.5), 90);
		target.setNoGravity(true);
		TheHead head = head(helper, new Vec3(3.5, base + 0.5, 9.5), target);
		head.getRandom().setSeed(12345);
		long[] escaped = { -1 };
		long[] tntAt = { -1 };
		boolean[] tnt = { false };
		boolean[] food = { false };
		boolean[] returned = { false };
		helper.onEachTick(() -> {
			if (target.isDeadOrDying()) return;
			food[0] |= head.cue() == HeadCue.HOME_HUNGRY || head.cue() == HeadCue.HOME_THIRSTY;
			returned[0] |= head.cue() == HeadCue.HOME_RETURN;
			if (head.cue() == HeadCue.HOME_ASK && escaped[0] < 0) escaped[0] = helper.getTick();
			if (escaped[0] >= 0) {
				double x = 14.5 + (helper.getTick() - escaped[0]) * 0.36;
				double y = base + (hill ? Math.clamp(Math.floor(x) - 19, 0, 8) : 0);
				target.setPos(helper.absoluteVec(new Vec3(x, y, 9.5)));
				target.setOnGround(true);
			}
			tnt[0] |= head.digging();
			if (head.digging() && tntAt[0] < 0) tntAt[0] = helper.getTick();
			if (hill && tntAt[0] >= 0 && helper.getTick() - tntAt[0] >= 200) {
				helper.assertTrue(helper.relativeVec(head.position()).y >= base + 8, "TNT pursuit stayed buried below the escaping runner");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(escaped[0] >= 0 && tnt[0] && target.isDeadOrDying() && (!chest || food[0] && returned[0]),
				"house runner escaped: hill=" + hill + " chest=" + chest + " food=" + food[0] + " returned=" + returned[0] + " head=" + helper.relativeVec(head.position())
					+ " gap=" + head.distanceTo(target) + " speed=" + head.chargeSpeed());
		});
	}

	@GameTest(structure = ARENA, maxTicks = 30)
	public void denseTerrainDashesFasterWithoutRestartingHum(GameTestHelper helper) {
		terrainDashCadence(helper, true);
	}

	@GameTest(structure = ARENA, maxTicks = 30)
	public void ordinaryHouseKeepsFullHumDashCadence(GameTestHelper helper) {
		terrainDashCadence(helper, false);
	}

	private void terrainDashCadence(GameTestHelper helper, boolean dense) {
		fill(helper, 5, -4, 5, 19, -1, 15, Blocks.STONE.defaultBlockState());
		fill(helper, 8, 0, 5, dense ? 19 : 8, 7, 15, Blocks.STONE.defaultBlockState());
		if (!dense) fill(helper, 8, 4, 5, 19, 4, 15, Blocks.OAK_PLANKS.defaultBlockState());
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(16.5, 0, 10.5), 90);
		TheHead head = head(helper, new Vec3(6.5, 0.5, 10.5), target);
		try {
			var dash = TheHead.class.getDeclaredMethod("tickDigDash", long.class);
			dash.setAccessible(true);
			Field next = TheHead.class.getDeclaredField("nextDigDash");
			next.setAccessible(true);
			long now = helper.getLevel().getGameTime();
			helper.assertTrue((boolean) dash.invoke(head, now), "digging did not start");
			HeadCue hum = head.cue();
			int full = (hum.clip().millis() + 49) / 50 + 5;
			helper.assertTrue(next.getLong(head) - now == (dense ? 10 : full), "wrong terrain cadence: dense=" + dense + " ticks=" + (next.getLong(head) - now));
			if (dense) {
				helper.assertFalse((boolean) dash.invoke(head, now + 9), "dense dash fired early");
				helper.assertTrue((boolean) dash.invoke(head, now + 10), "dense dash waited for hum to finish");
				helper.assertTrue(head.cue() == hum && head.revs() == 2, "faster dash restarted the full recording");
				fill(helper, 8, 0, 5, 19, 7, 15, Blocks.AIR.defaultBlockState());
				helper.assertTrue((boolean) dash.invoke(head, now + 20), "dash did not resume in open air");
				helper.assertTrue(next.getLong(head) - (now + 20) > 10, "fast terrain cadence persisted in open air");
			}
			head.discard();
			helper.succeed();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static int explosionPackets(ServerPlayer player) {
		int count = 0;
		Object packet;
		while ((packet = CHANNELS.get(player).readOutbound()) != null) {
			if (packet instanceof net.minecraft.network.protocol.game.ClientboundExplodePacket blast && blast.playSound()) count++;
		}
		return count;
	}

	@GameTest(structure = ARENA, maxTicks = 30)
	public void tntDashAllowsRegularTaunts(GameTestHelper helper) {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(10.5, 0, 10.5), -90);
		TheHead head = head(helper, new Vec3(16.5, 0.5, 10.5), target);
		try {
			var digging = TheHead.class.getDeclaredMethod("setDigging", boolean.class);
			var taunt = TheHead.class.getDeclaredMethod("tryTaunt", net.minecraft.world.entity.player.Player.class, double.class);
			var dash = TheHead.class.getDeclaredMethod("tickDigDash", long.class);
			digging.setAccessible(true);
			taunt.setAccessible(true);
			dash.setAccessible(true);
			digging.invoke(head, true);
			helper.assertTrue((boolean) taunt.invoke(head, target, 0.0), "TNT mode blocked regular taunt");
			Field voices = TheHead.class.getDeclaredField("voices");
			voices.setAccessible(true);
			((com.larsoyd.mariohead.entity.ChaseVoices) voices.get(head)).follow(0, 0);
			long now = helper.getLevel().getGameTime();
			helper.assertFalse((boolean) dash.invoke(head, now), "dash interrupted taunt");
			helper.assertTrue((boolean) dash.invoke(head, now + 300), "dash did not resume after taunt");
			Field boost = TheHead.class.getDeclaredField("dashBoost");
			boost.setAccessible(true);
			helper.assertTrue(boost.getDouble(head) == 1.1, "TNT taunt lost follow-up boost");
			Field rolled = TheHead.class.getDeclaredField("tauntRolled");
			rolled.setAccessible(true);
			helper.assertFalse(rolled.getBoolean(head), "new TNT dash did not refresh taunt roll");
			helper.assertFalse((boolean) taunt.invoke(head, target, 0.0), "TNT taunt ignored cooldown");
			head.discard();
			helper.succeed();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	@GameTest(structure = ARENA, maxTicks = 1500)
	public void tntDashDigsIntoDeepNarrowShaft(GameTestHelper helper) {
		deepNarrowChase(helper, false);
	}

	@GameTest(structure = ARENA, maxTicks = 1500)
	public void tntDashDigsIntoDeepNarrowTunnel(GameTestHelper helper) {
		deepNarrowChase(helper, true);
	}

	private void deepNarrowChase(GameTestHelper helper, boolean tunnel) {
		fill(helper, 0, 0, 0, 19, 42, 19, Blocks.STONE.defaultBlockState());
		fill(helper, 10, 1, 10, 10, 42, 10, Blocks.AIR.defaultBlockState());
		if (tunnel) fill(helper, 4, 1, 10, 10, 2, 10, Blocks.AIR.defaultBlockState());
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(tunnel ? 4.5 : 10.5, 1, 10.5), -90);
		TheHead head = head(helper, new Vec3(16.5, 43.5, 10.5), target);
		boolean[] announced = { false };
		boolean[] blasted = { false };
		int[] nearbyBlasts = { 0 };
		helper.onEachTick(() -> {
			int packets = explosionPackets(target);
			if (head.distanceTo(target) < 32) nearbyBlasts[0] += packets;
			announced[0] |= head.cue() == HeadCue.UNDERGROUND;
			try {
				Field blast = TheHead.class.getDeclaredField("lastBlastAt");
				blast.setAccessible(true);
				blasted[0] |= blast.get(head) != null;
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
			helper.assertFalse(head.burrowing(), "phased through ordinary stone at " + helper.relativeVec(head.position()));
			helper.assertTrue(head.squeeze() == 1, "shrank into narrow shaft at " + helper.relativeVec(head.position()));
			if (target.isDeadOrDying()) {
				helper.assertTrue(announced[0] && blasted[0] && nearbyBlasts[0] >= 2,
					"silent catch: announcement=" + announced[0] + " blast=" + blasted[0] + " nearby=" + nearbyBlasts[0]);
				helper.succeed();
			}
		});
	}

	@GameTest(structure = ARENA, maxTicks = 1000)
	public void diggingUsesFullHumsWithShorterPausesWhileDialogueKeepsPlaying(GameTestHelper helper) {
		fill(helper, 0, 6, 0, 19, 6, 19, Blocks.STONE.defaultBlockState());
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0, 10.5), -90);
		TheHead head = head(helper, new Vec3(16.5, 0.5, 10.5), target);
		head.getRandom().setSeed(12345);
		int[] previousRev = { -1 };
		long[] previousDash = { -1 };
		int[] beats = { 0 };
		int[] talkingBeats = { 0 };
		HeadCue[] talking = { HeadCue.NONE };
		long[] talkAt = { -1 };
		helper.onEachTick(() -> {
			if (helper.getTick() < 80) {
				return;
			}
			fill(helper, 8, -4, 5, 19, 7, 15, Blocks.STONE.defaultBlockState());
			fill(helper, 15, 0, 9, 18, 3, 12, Blocks.AIR.defaultBlockState());
			try {
				Field announced = TheHead.class.getDeclaredField("announced");
				announced.setAccessible(true);
				announced.setBoolean(head, true);
				Field taunt = TheHead.class.getDeclaredField("tauntReadyAt");
				taunt.setAccessible(true);
				taunt.setLong(head, Long.MAX_VALUE);
				var digging = TheHead.class.getDeclaredMethod("setDigging", boolean.class);
				digging.setAccessible(true);
				digging.invoke(head, true);
				Field stalls = TheHead.class.getDeclaredField("stalledBlasts");
				stalls.setAccessible(true);
				stalls.setInt(head, 0);
				head.setPos(helper.absoluteVec(new Vec3(16.5, 0.5, 10.5)));
				if (head.cue().chatter() && talking[0] != head.cue()) {
					talking[0] = head.cue();
					talkAt[0] = helper.getTick();
				}
				if (talkAt[0] >= 0 && helper.getTick() - talkAt[0] < (talking[0].clip().millis() + 49) / 50) {
					helper.assertTrue(head.cue() == talking[0], "dash interrupted dialogue");
				}
				if (beats[0] >= 2 && talkingBeats[0] < 3 && head.cue().digHum()) {
					Field line = TheHead.class.getDeclaredField("dashLine");
					line.setAccessible(true);
					if (line.get(head) == HeadCue.NONE) line.set(head, HeadCue.DASH_073);
				}
				Field blast = TheHead.class.getDeclaredField("lastBlastAt");
				blast.setAccessible(true);
				if ((head.cue().digHum() || head.cue().chatter()) && head.revs() != previousRev[0]) {
					if (previousDash[0] >= 0) {
						helper.assertTrue(helper.getTick() - previousDash[0] == HeadTuning.DENSE_DASH_TICKS,
							"dig dash interval=" + (helper.getTick() - previousDash[0]));
					}
					previousDash[0] = helper.getTick();
					previousRev[0] = head.revs();
					beats[0]++;
					helper.assertTrue(blast.get(head) != null, "dense-terrain dash did not cut while cue=" + head.cue());
					if (head.cue().chatter()) {
						talkingBeats[0]++;
						helper.assertTrue(head.chargeSpeed() > 0.1 && head.getDeltaMovement().length() > 0.01, "dialogue stopped dash movement");
					}
				}
				blast.set(head, null);
				if (beats[0] >= 12 && talkingBeats[0] >= 3) {
					head.discard();
					helper.succeed();
				}
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
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
		TheHead head = head(helper, new Vec3(16.5, 5.05, 10.5), target);
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
		TheHead head = head(helper, new Vec3(3.5, 0.05, 10.5), target);
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
		HeadCue[] spoken = { HeadCue.NONE };
		int[] spokenAt = { 0 };
		helper.onEachTick(() -> {
			if (head.isRemoved()) {
				return;
			}
			int t = (int) helper.getTick();
			if (head.cue() != last[0] && head.cue().clip() != null) {
				if (spoken[0].clip() != null) {
					int gap = spoken[0] == HeadCue.UNDERGROUND || head.cue() == HeadCue.UNDERGROUND ? 10 : 0;
					int earliest = spokenAt[0] + (spoken[0].clip().millis() + 49) / 50 + gap;
					helper.assertTrue(t >= earliest, "voice overlap: " + spoken[0] + " at " + spokenAt[0]
						+ " then " + head.cue() + " at " + t + " earliest=" + earliest);
				}
				spoken[0] = head.cue();
				spokenAt[0] = t;
			}
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
		try {
			Field ready = TheHead.class.getDeclaredField("tauntReadyAt");
			ready.setAccessible(true);
			ready.setLong(head, helper.getLevel().getGameTime() + 400);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		helper.onEachTick(() -> {
			long t = helper.getTick();
			if (t > 60 && t % 20 == 0 && !target.isDeadOrDying()) {
				Vec3 to = helper.absoluteVec(new Vec3((t / 20) % 2 == 0 ? 2.5 : 16.5, 0.0, 2.5));
				target.teleportTo(helper.getLevel(), to.x, to.y, to.z, java.util.Set.of(), -90.0f, 0.0f, true);
			}
			if (!head.isRemoved() && head.phase() == HeadPhase.CHARGE) {
				helper.assertTrue(head.chargeSpeed() <= RevSpring.peak(head.revs()) * (1.0 + (head.revs() + 1) * 0.003) + 1e-9,
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

	@GameTest(structure = ARENA, maxTicks = 650)
	public void fliesOverWaterToARowingBoat(GameTestHelper helper) {
		rowingBoat(helper, 4, false);
	}

	@GameTest(structure = ARENA, maxTicks = 650)
	public void fliesFromShoreToBoat(GameTestHelper helper) {
		rowingBoat(helper, 4, true);
	}

	@GameTest(structure = ARENA, maxTicks = 650)
	public void fliesOverShallowWater(GameTestHelper helper) {
		rowingBoat(helper, 1, false);
	}

	@GameTest(structure = ARENA, maxTicks = 650)
	public void fliesOverDeepWater(GameTestHelper helper) {
		rowingBoat(helper, 5, false);
	}

	private static void rowingBoat(GameTestHelper helper, int depth, boolean shore) {
		fill(helper, 0, 0, 0, 19, depth - 1, 19, Blocks.WATER.defaultBlockState());
		if (shore) {
			fill(helper, 13, 0, 0, 19, depth - 1, 19, Blocks.STONE.defaultBlockState());
		}
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(8.5, depth, 10.5), -90.0f);
		var boat = helper.spawn(net.minecraft.world.entity.EntityTypes.OAK_BOAT, new Vec3(8.5, depth, 10.5));
		helper.assertTrue(target.startRiding(boat, true, false), "player did not board");
		TheHead head = head(helper, new Vec3(17.5, depth + 0.05, 10.5), target);
		java.util.Random random = new java.util.Random(8017 + depth);
		helper.onEachTick(() -> {
			if (!head.isRemoved()) {
				helper.assertTrue(head.centre().y >= helper.absoluteVec(new Vec3(0, depth, 0)).y,
					"flyer dived toward lake bed at " + helper.relativeVec(head.centre()));
				helper.assertFalse(head.burrowing() || head.digging(), "open water triggered underground pursuit");
			}
			if (head.phase() == HeadPhase.CHARGE && helper.relativeVec(boat.position()).x > 3.0) {
				boat.setDeltaMovement(-0.05 - random.nextDouble() * 0.2, 0.0, (random.nextDouble() - 0.5) * 0.15);
			}
		});
		caughtWithin(helper, target, head);
	}

	@GameTest(structure = ARENA, maxTicks = 100)
	public void facesElevationBeforeDashing(GameTestHelper helper) {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0.0, 10.5), -90.0f);
		TheHead head = head(helper, new Vec3(14.5, 0.5, 10.5), target);
		helper.runAtTickTime(30, () -> {
			try {
				var face = TheHead.class.getDeclaredMethod("faceTowards", float.class, net.minecraft.world.entity.player.Player.class);
				face.setAccessible(true);
				java.util.Random random = new java.util.Random(5081);
				for (int i = 0; i < 250; i++) {
					double horizontal = i < 2 ? 0.0 : random.nextDouble() * 30.0;
					double vertical = i == 0 ? 20.0 : i == 1 ? -20.0 : i < 20
						? (random.nextDouble() * 2.0 - 1.0) * HeadTuning.BOB_BLOCKS : random.nextDouble() * 40.0 - 20.0;
					Vec3 c = target.position().add(horizontal, HeadTuning.HOVER + vertical, 0.0);
					head.setPos(c.x, c.y - head.getBbHeight() / 2.0, c.z);
					for (int tick = 0; tick < 80; tick++) {
						face.invoke(head, 90.0f, target);
					}
					double expected = Math.abs(vertical) <= HeadTuning.BOB_BLOCKS + 0.1 ? 0.0
						: Math.toDegrees(Math.atan2(c.y - target.getEyeY(), horizontal));
					helper.assertTrue(Math.abs(head.getXRot() - expected) < 0.01,
						"elevation " + vertical + " range " + horizontal + " pitch " + head.getXRot() + " expected " + expected);
				}
				head.discard();
				leave(helper, target);
				helper.succeed();
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		});
	}

	@GameTest(structure = ARENA, maxTicks = 250)
	public void tiltsUpDuringInhale(GameTestHelper helper) {
		firstDashPitch(helper, 5.0, false, -1);
	}

	@GameTest(structure = ARENA, maxTicks = 130)
	public void ignoresOrdinaryJumpHeightWhileChasing(GameTestHelper helper) {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(2.5, 0.0, 10.5), -90.0f);
		target.setNoGravity(true);
		TheHead head = head(helper, new Vec3(16.5, 0.5, 10.5), target);
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (tick >= 72 && tick <= 110) {
				double jump = Math.max(0.0, Math.sin((tick - 72) * Math.PI / 12.0)) * 1.25;
				target.setPos(target.getX(), helper.absoluteVec(new Vec3(0, jump, 0)).y, target.getZ());
				double height = helper.relativeVec(head.centre()).y;
				helper.assertTrue(Math.abs(height - HeadTuning.HOVER) <= 0.2,
					"head followed jump: height=" + height + " jump=" + jump);
			}
		});
		helper.runAtTickTime(111, () -> {
			head.discard();
			helper.succeed();
		});
	}

	@GameTest(structure = ARENA, maxTicks = 400)
	public void boatDashesGrowInSpeedAndDuration(GameTestHelper helper) {
		boatDashGrowth(helper, false);
	}

	@GameTest(structure = ARENA, maxTicks = 400)
	public void iceBoatReboardingRetainsDashGrowth(GameTestHelper helper) {
		boatDashGrowth(helper, true);
	}

	private static void boatDashGrowth(GameTestHelper helper, boolean reboard) {
		if (reboard) {
			fill(helper, 1, -1, 8, 5, -1, 12, Blocks.BLUE_ICE.defaultBlockState());
		}
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0.0, 10.5), -90.0f);
		var boat = helper.spawn(net.minecraft.world.entity.EntityTypes.OAK_BOAT, new Vec3(3.5, 0.0, 10.5));
		var otherBoat = helper.spawn(net.minecraft.world.entity.EntityTypes.OAK_BOAT, new Vec3(3.5, 0.0, 7.5));
		helper.assertTrue(target.startRiding(boat, true, false), "could not board boat");
		TheHead head = head(helper, new Vec3(16.5, 0.5, 10.5), target);
		HeadCue[] last = { HeadCue.NONE };
		long[] started = { 0 };
		int[] dashes = { 0 };
		helper.onEachTick(() -> {
			if (reboard && helper.getTick() % 20 == 5) {
				target.stopRiding();
				target.setPos(helper.absoluteVec(new Vec3(3.5, 0, 10.5)));
				target.setOnGround(true);
			} else if (reboard && helper.getTick() % 20 == 6) {
				var next = helper.getTick() % 40 == 6 ? otherBoat : boat;
				next.setPos(helper.absoluteVec(new Vec3(3.5, 0, 10.5)));
				helper.assertTrue(target.startRiding(next, true, false), "reboarding failed");
			}
			head.setPos(helper.absoluteVec(new Vec3(16.5, 0.5, 10.5)));
			HeadCue cue = head.cue();
			if (cue != last[0]) {
				if (cue.clip() != null && !cue.spoken()) {
					dashes[0]++;
					started[0] = helper.getTick();
					double expected = RevSpring.peak(head.revs()) * Math.pow(1.03, head.revs() + 1) * (1.0 + (head.revs() + 1) * 0.003);
					helper.assertTrue(Math.abs(head.chargeSpeed() - expected) < 1e-6,
						"dash " + dashes[0] + " speed=" + head.chargeSpeed() + " expected=" + expected);
				} else if (cue == HeadCue.INHALE && last[0].clip() != null && !last[0].spoken()) {
					long duration = helper.getTick() - started[0];
					int expected = (int) Math.ceil(last[0].clip().frames() / 30.0 * 20 * Math.pow(1.03, dashes[0]));
					helper.assertTrue(duration >= expected, "dash duration=" + duration + " expected at least=" + expected);
					if (dashes[0] == 3) {
						head.discard();
						helper.succeed();
					}
				}
				last[0] = cue;
			}
		});
	}

	@GameTest(structure = ARENA, maxTicks = 280)
	public void sustainedRunningAndJumpingResetsBoatGrowth(GameTestHelper helper) {
		transportReset(helper, false);
	}

	@GameTest(structure = ARENA, maxTicks = 280)
	public void elytraGrowthSurvivesLandingAndRunning(GameTestHelper helper) {
		transportReset(helper, true);
	}

	@GameTest(structure = ARENA, maxTicks = 14000, dimension = "minecraft:the_end")
	public void catchesSustainedFastIceBoat(GameTestHelper helper) {
		fastTransportChase(helper, false);
	}

	@GameTest(structure = ARENA, maxTicks = 14000, dimension = "minecraft:the_end")
	public void catchesSustainedFastElytraFlight(GameTestHelper helper) {
		fastTransportChase(helper, true);
	}

	private static void fastTransportChase(GameTestHelper helper, boolean flying) {
		double speed = flying ? 1.8 : 1.6;
		fill(helper, -24, 63, 7, 24, 63, 13, Blocks.BLUE_ICE.defaultBlockState());
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 64, 10.5), -90.0f);
		target.setNoGravity(true);
		var boat = helper.spawn(net.minecraft.world.entity.EntityTypes.OAK_BOAT, new Vec3(3.5, 64, 10.5));
		boat.setNoGravity(true);
		if (flying) {
			boat.discard();
			target.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ELYTRA));
		} else {
			helper.assertTrue(target.startRiding(boat, true, false), "could not board fast boat");
		}
		TheHead head = head(helper, new Vec3(-12.5, 64.5, 10.5), target);
		double[] travelled = { 0 };
		double[] highestSpeed = { 0 };
		int[] laidTo = { 24 };
		helper.onEachTick(() -> {
			if (target.isDeadOrDying()) {
				return;
			}
			if (helper.getTick() >= 75) {
				travelled[0] += speed;
				Vec3 next = helper.absoluteVec(new Vec3(3.5 + travelled[0], 64, 10.5));
				if (flying) {
					target.setPos(next.add(0, 3, 0));
					target.setOnGround(false);
					target.startFallFlying();
				} else {
					int end = (int) Math.ceil(3.5 + travelled[0]) + 4;
					if (end > laidTo[0]) {
						fill(helper, laidTo[0] + 1, 63, 7, end, 63, 13, Blocks.BLUE_ICE.defaultBlockState());
						laidTo[0] = end;
					}
					boat.setPos(next);
					boat.setDeltaMovement(Vec3.ZERO);
					boat.positionRider(target);
				}
				highestSpeed[0] = Math.max(highestSpeed[0], head.getDeltaMovement().horizontalDistance());
			}
			try {
				Thread.sleep(2);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(target.isDeadOrDying(), "fast target alive: flying=" + flying + " travelled=" + travelled[0]
				+ " gap=" + head.distanceTo(target) + " revs=" + head.revs() + " speed=" + head.chargeSpeed());
			helper.assertTrue(travelled[0] > 100 && highestSpeed[0] > speed,
				"did not overtake sustained transport: distance=" + travelled[0] + " peak=" + highestSpeed[0]);
			com.larsoyd.mariohead.MarioHead.LOG.info("[chase-test] flying={} distance={} peak={} revs={} ticks={}",
				flying, travelled[0], highestSpeed[0], head.revs(), helper.getTick());
		});
	}

	private static void transportReset(GameTestHelper helper, boolean fly) {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0, 10.5), -90.0f);
		var boat = helper.spawn(net.minecraft.world.entity.EntityTypes.OAK_BOAT, new Vec3(3.5, 0, 10.5));
		helper.assertTrue(target.startRiding(boat, true, false), "could not board");
		TheHead head = head(helper, new Vec3(16.5, 0.5, 10.5), target);
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			head.setPos(helper.absoluteVec(new Vec3(16.5, 0.5, 10.5)));
			if (tick == 90) {
				helper.assertTrue(head.revs() >= 0, "no mounted dash before dismount");
				target.stopRiding();
				target.setNoGravity(true);
				try {
					Field phaseTicks = TheHead.class.getDeclaredField("phaseTicks");
					phaseTicks.setAccessible(true);
					phaseTicks.setInt(head, 6000);
				} catch (ReflectiveOperationException e) {
					throw new IllegalStateException(e);
				}
			}
			if (fly && tick >= 90 && tick < 110) {
				target.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ELYTRA));
				target.setPos(helper.absoluteVec(new Vec3(3.5, 4, 10.5)));
				target.setOnGround(false);
				target.startFallFlying();
				helper.assertTrue(target.isFallFlying(), "elytra flag did not activate");
			}
			if (tick >= 110) {
				target.stopFallFlying();
				double jump = Math.max(0.0, Math.sin(tick * Math.PI / 12.0)) * 1.25;
				target.setPos(helper.absoluteVec(new Vec3(3.5, jump, 10.5 + 2.0 * Math.sin(tick * 0.15))));
				target.setOnGround(jump < 0.01);
			}
		});
		helper.runAtTickTime(250, () -> {
			try {
				Field field = TheHead.class.getDeclaredField("tempo");
				field.setAccessible(true);
				double multiplier = ((com.larsoyd.mariohead.entity.ChaseTempo) field.get(head)).multiplier();
				helper.assertTrue(fly ? multiplier > 1.03 : multiplier == 1.0, "fly=" + fly + " multiplier=" + multiplier);
				head.discard();
				helper.succeed();
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		});
	}

	@GameTest(structure = ARENA, maxTicks = 250)
	public void tiltsDownDuringInhale(GameTestHelper helper) {
		firstDashPitch(helper, 0.0, true, 1);
	}

	@GameTest(structure = ARENA, maxTicks = 100)
	public void shortDamagingFallDoesNotTaunt(GameTestHelper helper) {
		fallRejection(helper, 0);
	}

	@GameTest(structure = ARENA, maxTicks = 100)
	public void longHarmlessFallDoesNotTaunt(GameTestHelper helper) {
		fallRejection(helper, 1);
	}

	@GameTest(structure = ARENA, maxTicks = 100)
	public void absorbedLongFallDoesNotTaunt(GameTestHelper helper) {
		fallRejection(helper, 2);
	}

	@GameTest(structure = ARENA, maxTicks = 100)
	public void fatalLongFallDoesNotTaunt(GameTestHelper helper) {
		fallRejection(helper, 3);
	}

	@GameTest(structure = ARENA, maxTicks = 100)
	public void longFallRespectsTauntCooldown(GameTestHelper helper) {
		fallRejection(helper, 4);
	}

	@GameTest(structure = ARENA, maxTicks = 100)
	public void distantLongFallDoesNotTaunt(GameTestHelper helper) {
		fallRejection(helper, 5);
	}

	private void fallRejection(GameTestHelper helper, int scenario) {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0, 10.5), -90);
		TheHead head = helper.spawn(HeadEntities.THE_HEAD, new Vec3(16.5, 0.5, 10.5));
		head.chase(target);
		head.setNoAi(true);
		helper.runAtTickTime(80, () -> {
			try {
				var phase = TheHead.class.getDeclaredMethod("setPhase", HeadPhase.class);
				phase.setAccessible(true);
				phase.invoke(head, HeadPhase.CHARGE);
				Field pending = TheHead.class.getDeclaredField("fallTauntUntil");
				Field ready = TheHead.class.getDeclaredField("tauntReadyAt");
				pending.setAccessible(true);
				ready.setAccessible(true);
				for (int seed = 0; seed < 10000; seed++) {
					if (net.minecraft.util.RandomSource.create(seed).nextDouble() < 0.15) {
						head.getRandom().setSeed(seed);
						break;
					}
				}
				if (scenario == 2) {
					target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_ABSORPTION).setBaseValue(20);
					target.setAbsorptionAmount(20);
				}
				if (scenario == 4) ready.setLong(head, helper.getLevel().getGameTime() + 600);
				if (scenario == 5) head.setPos(target.position().add(51, 0, 0));
				target.causeFallDamage(scenario == 0 ? 7.99 : scenario == 3 ? 80 : 8, scenario == 1 ? 0 : 1, target.damageSources().fall());
				helper.assertTrue(pending.getLong(head) < 0, "ineligible fall queued a taunt: " + scenario);
				if (scenario == 1 || scenario == 2) helper.assertTrue(target.getHealth() == 20, "harmless fixture lost health");
				else if (scenario == 3) helper.assertTrue(target.isDeadOrDying(), "fatal fixture survived");
				else helper.assertTrue(target.getHealth() < 20, "damaging fixture lost no health");
				helper.succeed();
			} catch (ReflectiveOperationException e) {
				throw new AssertionError(e);
			}
		});
	}

	@GameTest(structure = ARENA, maxTicks = 5)
	public void longFallRollUsesFifteenPercentOnce(GameTestHelper helper) throws ReflectiveOperationException {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0, 10.5), -90);
		TheHead head = helper.spawn(HeadEntities.THE_HEAD, new Vec3(16.5, 0.5, 10.5));
		head.chase(target);
		head.setNoAi(true);
		var phase = TheHead.class.getDeclaredMethod("setPhase", HeadPhase.class);
		phase.setAccessible(true);
		phase.invoke(head, HeadPhase.CHARGE);
		Field pending = TheHead.class.getDeclaredField("fallTauntUntil");
		pending.setAccessible(true);
		int accepted = 0;
		for (int sample = 0; sample < 200; sample++) {
			long seed = sample * 17431L;
			double roll = net.minecraft.util.RandomSource.create(seed).nextDouble();
			head.getRandom().setSeed(seed);
			pending.setLong(head, -1);
			head.playerFell(target);
			boolean queued = pending.getLong(head) >= helper.getLevel().getGameTime();
			helper.assertTrue(queued == (roll < 0.15), "wrong fall chance at roll " + roll);
			if (queued) {
				accepted++;
				long deadline = pending.getLong(head);
				head.playerFell(target);
				helper.assertTrue(pending.getLong(head) == deadline, "repeated fall replaced queued reaction");
			}
		}
		helper.assertTrue(accepted > 0 && accepted < 200, "fall chance fixture did not exercise both outcomes");
		helper.succeed();
	}

	@GameTest(structure = ARENA, maxTicks = 240)
	public void hurtByFallQueuesTauntAfterWholeHum(GameTestHelper helper) {
		fallQueueScenario(helper, 0);
	}

	@GameTest(structure = ARENA, maxTicks = 240)
	public void queuedFallTauntCancelsOutsideHearing(GameTestHelper helper) {
		fallQueueScenario(helper, 1);
	}

	@GameTest(structure = ARENA, maxTicks = 240)
	public void queuedFallTauntCancelsWhenOwnerTurnsCreative(GameTestHelper helper) {
		fallQueueScenario(helper, 2);
	}

	@GameTest(structure = ARENA, maxTicks = 240)
	public void queuedFallTauntCancelsWhenOwnerDies(GameTestHelper helper) {
		fallQueueScenario(helper, 3);
	}

	private void fallQueueScenario(GameTestHelper helper, int scenario) {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0, 10.5), -90);
		TheHead head = head(helper, new Vec3(16.5, 0.5, 10.5), target);
		head.setNoAi(true);
		long[] humAt = { -1 };
		boolean[] spoken = { false };
		helper.runAtTickTime(80, () -> {
			try {
				var phase = TheHead.class.getDeclaredMethod("setPhase", HeadPhase.class);
				var cue = TheHead.class.getDeclaredMethod("setCue", HeadCue.class);
				phase.setAccessible(true);
				cue.setAccessible(true);
				phase.invoke(head, HeadPhase.CHARGE);
				cue.invoke(head, HeadCue.MMM1);
				for (int seed = 0; seed < 10000; seed++) {
					if (net.minecraft.util.RandomSource.create(seed).nextDouble() < 0.15) {
						head.getRandom().setSeed(seed);
						break;
					}
				}
				humAt[0] = helper.getTick();
				helper.assertTrue(target.causeFallDamage(8.0, 1.0f, target.damageSources().fall()), "fall damage was refused");
				helper.assertTrue(target.getHealth() == 15, "fall did not remove health");
				helper.assertTrue(head.cue() == HeadCue.MMM1, "fall interrupted hum");
				head.setNoAi(false);
			} catch (ReflectiveOperationException e) {
				throw new AssertionError(e);
			}
		});
		helper.onEachTick(() -> {
			if (head.cue().name().equals("FALL_019")) {
				spoken[0] = true;
				helper.assertTrue(helper.getTick() - humAt[0] >= 46, "fall line cut off active hum");
				helper.assertFalse(target.isDeadOrDying(), "fall taunt killed owner");
				helper.assertTrue(head.chargeSpeed() < 0.02, "fall taunt did not match stationary owner");
			}
			if (scenario == 0 && spoken[0] && head.cue().clip() != null && !head.cue().spoken()) {
				try {
					Field boost = TheHead.class.getDeclaredField("dashBoost");
					Field ready = TheHead.class.getDeclaredField("tauntReadyAt");
					boost.setAccessible(true);
					ready.setAccessible(true);
					helper.assertTrue(boost.getDouble(head) == 1.1, "fall taunt did not boost follow-up dash");
					helper.assertTrue(ready.getLong(head) > helper.getLevel().getGameTime(), "fall taunt did not start cooldown");
					helper.succeed();
				} catch (ReflectiveOperationException e) {
					throw new AssertionError(e);
				}
			}
		});
		if (scenario != 0) {
			helper.runAtTickTime(82, () -> {
				try {
					Field pending = TheHead.class.getDeclaredField("pendingVoice");
					pending.setAccessible(true);
					helper.assertTrue(pending.get(head) == HeadCue.FALL_019, "cancellation fixture never queued fall voice");
				} catch (ReflectiveOperationException e) { throw new AssertionError(e); }
				if (scenario == 1) target.setPos(head.position().add(60, 0, 0));
				if (scenario == 2) target.setGameMode(GameType.CREATIVE);
				if (scenario == 3) target.setHealth(0);
			});
			helper.runAtTickTime(90, () -> {
				if (scenario == 1) target.setPos(helper.absoluteVec(new Vec3(3.5, 0, 10.5)));
				else helper.assertTrue(head.isRemoved(), "invalid owner retained the chasing head");
			});
		}
		helper.runAtTickTime(140, () -> {
			helper.assertTrue(spoken[0] == (scenario == 0), "wrong queued fall outcome: " + scenario);
			if (scenario != 0) helper.succeed();
		});
	}

	@GameTest(structure = ARENA, maxTicks = 900)
	public void waterPursuitTauntLaughResumesOrdinaryDash(GameTestHelper helper) throws ReflectiveOperationException {
		waterPursuit(helper, false, false);
	}

	@GameTest(structure = ARENA, maxTicks = 1100)
	public void waterPursuitTauntLaughAfterHouseResumesTnt(GameTestHelper helper) throws ReflectiveOperationException {
		waterPursuit(helper, true, false);
	}

	@GameTest(structure = ARENA, maxTicks = 900)
	public void waterPursuitDrowningLaughResumesOrdinaryDash(GameTestHelper helper) throws ReflectiveOperationException {
		waterPursuit(helper, false, true);
	}

	@GameTest(structure = ARENA, maxTicks = 1100)
	public void waterPursuitDrowningLaughAfterHouseResumesTnt(GameTestHelper helper) throws ReflectiveOperationException {
		waterPursuit(helper, true, true);
	}

	@GameTest(structure = ARENA, maxTicks = 900)
	public void waterPursuitEmergingCancelsQueuedTaunt(GameTestHelper helper) throws ReflectiveOperationException {
		waterPursuit(helper, false, false, true);
	}

	@GameTest(structure = ARENA, maxTicks = 1100)
	public void waterPursuitEmergingAfterHouseCancelsQueuedTaunt(GameTestHelper helper) throws ReflectiveOperationException {
		waterPursuit(helper, true, false, true);
	}

	@GameTest(structure = ARENA, maxTicks = 900)
	public void waterPursuitEmergingCancelsQueuedDrowning(GameTestHelper helper) throws ReflectiveOperationException {
		waterPursuit(helper, false, true, true);
	}

	@GameTest(structure = ARENA, maxTicks = 1100)
	public void waterPursuitEmergingAfterHouseCancelsQueuedDrowning(GameTestHelper helper) throws ReflectiveOperationException {
		waterPursuit(helper, true, true, true);
	}

	private void waterPursuit(GameTestHelper helper, boolean house, boolean drowning) throws ReflectiveOperationException {
		waterPursuit(helper, house, drowning, false);
	}

	private void waterPursuit(GameTestHelper helper, boolean house, boolean drowning, boolean cancel) throws ReflectiveOperationException {
		int y = 120 + (house ? 20 : 0) + (drowning ? 40 : 0);
		fill(helper, 0, y - 1, 0, 90, y - 1, 20, Blocks.STONE.defaultBlockState());
		fill(helper, 0, y, 0, 90, y + 10, 0, Blocks.STONE.defaultBlockState());
		fill(helper, 0, y, 20, 90, y + 10, 20, Blocks.STONE.defaultBlockState());
		fill(helper, 0, y, 0, 0, y + 10, 20, Blocks.STONE.defaultBlockState());
		fill(helper, 90, y, 0, 90, y + 10, 20, Blocks.STONE.defaultBlockState());
		fill(helper, 1, y, 1, 89, y + 9, 19, Blocks.WATER.defaultBlockState());
		fill(helper, 0, 39, 0, 50, 39, 23, Blocks.STONE.defaultBlockState());
		if (house) {
			fill(helper, 8, 40, 4, 18, 44, 14, Blocks.STONE.defaultBlockState());
			fill(helper, 9, 40, 5, 17, 43, 13, Blocks.AIR.defaultBlockState());
			helper.setBlock(new BlockPos(14, 40, 12), Blocks.BED.red().defaultBlockState());
			helper.setBlock(new BlockPos(9, 40, 9), Blocks.CHEST.defaultBlockState());
		}
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(16.5, 40, 9.5), -90);
		TheHead head = helper.spawn(HeadEntities.THE_HEAD, new Vec3(6, 40, 9.5));
		head.chase(target);
		var voicesField = TheHead.class.getDeclaredField("voices"); voicesField.setAccessible(true);
		var voices = (com.larsoyd.mariohead.entity.ChaseVoices) voicesField.get(head);
		List<HeadCue> cues = new ArrayList<>();
		long[] entered = { -1 }, spoken = { -1 }, laughed = { -1 };
		long[] canceled = { -1 };
		int[] cancelRev = { -1 };
		var queued = TheHead.class.getDeclaredField(drowning ? "drowningUntil" : "waterPending"); queued.setAccessible(true);
		HeadCue[] line = { HeadCue.NONE }, laugh = { HeadCue.NONE };
		boolean[] damaged = { false };
		long seed = 0;
		for (; seed < 10000; seed++) {
			var random = net.minecraft.util.RandomSource.create(seed);
			random.nextInt(1000);
			if (random.nextDouble() < 0.15) break;
		}
		final long waterSeed = seed;
		helper.onEachTick(() -> {
			HeadCue cue = head.cue();
			if (cues.isEmpty() || cues.getLast() != cue) cues.add(cue);
			if (house && entered[0] < 0 && cues.contains(HeadCue.HOME_ASK) && cue.digHum()) target.setPos(helper.absoluteVec(new Vec3(42.5, 40, 9.5)));
			if (entered[0] < 0 && (house ? cue == HeadCue.HOME_RETURN : cue.clip() != null && !cue.spoken())) {
				if (house) helper.assertTrue(cues.contains(HeadCue.HOME_NICE) && cues.contains(HeadCue.HOME_ASK)
					&& (cues.contains(HeadCue.HOME_HUNGRY) || cues.contains(HeadCue.HOME_THIRSTY)), "water fixture skipped house/chest sequence: " + cues);
				helper.assertTrue(head.digging() == house, "wrong initial water pursuit mode");
				entered[0] = helper.getTick();
				head.setPos(helper.absoluteVec(new Vec3(8.5, y + 2.5, 10.5)));
				target.setPos(helper.absoluteVec(new Vec3(14.5, y + 2, 10.5)));
				target.setNoGravity(true);
			}
			if (entered[0] < 0) return;
			head.getRandom().setSeed(waterSeed);
			target.setSprinting(true); target.setSwimming(true);
			target.setPos(target.position().add(0.18, 0, 0));
			if (target.getX() - head.getX() < 5 || target.getX() - head.getX() > 7) target.setPos(head.getX() + 6, helper.absoluteVec(new Vec3(0, y + 2, 0)).y, head.getZ());
			if (drowning && !damaged[0] && helper.getTick() - entered[0] >= 40) {
				helper.assertTrue(target.hurtServer(helper.getLevel(), target.damageSources().drown(), 2), "water fixture drowning damage refused");
				damaged[0] = true;
			}
			if (cancel) {
				try {
					if (canceled[0] < 0 && (drowning ? queued.getLong(head) >= 0 : queued.getBoolean(head))) {
						canceled[0] = helper.getTick(); cancelRev[0] = head.revs();
					}
				} catch (ReflectiveOperationException e) { throw new AssertionError(e); }
				if (canceled[0] >= 0) {
					target.setPos(head.getX() + 6, helper.absoluteVec(new Vec3(0, y + 12, 0)).y, head.getZ());
					helper.assertTrue(cue != HeadCue.VARIETY_DROWNING && cue != HeadCue.VARIETY_071 && cue != HeadCue.VARIETY_085, "surfaced owner received stale water speech");
					if (head.revs() > cancelRev[0]) {
						helper.assertTrue(head.digging() == house && cue.digHum() == house, "emerging lost dash mode");
						head.discard(); helper.succeed(); return;
					}
				}
			}
			boolean reaction = drowning ? cue == HeadCue.VARIETY_DROWNING : cue == HeadCue.VARIETY_071 || cue == HeadCue.VARIETY_085;
			if (reaction && spoken[0] < 0) {
				spoken[0] = helper.getTick(); line[0] = cue;
				helper.assertTrue(spoken[0] - entered[0] >= 40, "water reaction preceded two seconds of swimming/damage");
				if (!drowning) voices.follow(1, 0);
			}
			if (spoken[0] >= 0 && cue.laugh() && laughed[0] < 0) {
				laughed[0] = helper.getTick(); laugh[0] = cue;
				helper.assertTrue(laughed[0] - spoken[0] >= (line[0].clip().millis() + 49) / 50, "laugh cut off water recording");
			}
			if (spoken[0] >= 0 && cue.clip() != null && !cue.spoken()) {
				helper.assertTrue(laughed[0] >= 0 && helper.getTick() - laughed[0] >= (laugh[0].clip().millis() + 49) / 50, "dash overtook water laugh: " + cues);
				helper.assertTrue(head.digging() == house && cue.digHum() == house && head.chargeSpeed() > 0.1, "water reaction lost original chase mode");
				helper.assertTrue(cues.stream().filter(c -> c == HeadCue.VARIETY_071 || c == HeadCue.VARIETY_085).count() <= 1, "water reaction chained both lines");
				head.discard(); helper.succeed();
			}
			helper.assertFalse(target.isDeadOrDying() || head.isRemoved() && spoken[0] < 0, "water fixture lost owner before reaction: " + cues);
			if (helper.getTick() - entered[0] >= 300) {
				try {
					var field = TheHead.class.getDeclaredField("diveTaunt"); field.setAccessible(true);
					var ticks = com.larsoyd.mariohead.entity.DiveTaunt.class.getDeclaredField("swimmingTicks"); ticks.setAccessible(true);
					var rolled = com.larsoyd.mariohead.entity.DiveTaunt.class.getDeclaredField("rolled"); rolled.setAccessible(true);
					helper.fail("water reaction did not finish: " + cues + " swimming=" + target.isSwimming() + " activeTicks=" + ticks.getInt(field.get(head))
						+ " rolled=" + rolled.getBoolean(field.get(head)) + " head=" + helper.relativeVec(head.position()) + " player=" + helper.relativeVec(target.position()));
				} catch (ReflectiveOperationException e) { throw new AssertionError(e); }
			}
		});
	}

	@GameTest(structure = ARENA, maxTicks = 40)
	public void waterTauntChoosesOneRequestedLineAtFifteenPercent(GameTestHelper helper) throws ReflectiveOperationException {
		fill(helper, 0, 20, 0, 20, 27, 20, Blocks.WATER.defaultBlockState());
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(4.5, 22, 10.5), -90);
		TheHead head = head(helper, new Vec3(10.5, 22.5, 10.5), target);
		head.setNoAi(true);
		var dive = TheHead.class.getDeclaredField("diveTaunt"); dive.setAccessible(true);
		var policy = (com.larsoyd.mariohead.entity.DiveTaunt) dive.get(head);
		for (int tick = 0; tick < 40; tick++) policy.observe(true);
		var speak = TheHead.class.getDeclaredMethod("tickWaterSpeech", net.minecraft.world.entity.player.Player.class, double.class);
		speak.setAccessible(true);
		helper.assertTrue((boolean) speak.invoke(head, target, 0.149999), "eligible dive did not speak");
		helper.assertTrue(head.cue() == HeadCue.VARIETY_071 || head.cue() == HeadCue.VARIETY_085, "wrong water line: " + head.cue());
		helper.assertTrue(head.cue().taunt(), "water speech did not use taunt movement/boost");
		helper.assertFalse((boolean) speak.invoke(head, target, 0), "water taunt queued a second line");
		head.discard(); helper.succeed();
	}

	@GameTest(structure = ARENA, maxTicks = 260)
	public void drowningThroughAbsorptionQueuesOneReactionWithGuaranteedLaugh(GameTestHelper helper) throws ReflectiveOperationException {
		fill(helper, 0, 20, 0, 20, 27, 20, Blocks.WATER.defaultBlockState());
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(4.5, 22, 10.5), -90);
		target.setNoGravity(true);
		TheHead head = head(helper, new Vec3(10.5, 22.5, 10.5), target);
		head.setNoAi(true);
		var phase = TheHead.class.getDeclaredMethod("setPhase", HeadPhase.class); phase.setAccessible(true);
		phase.invoke(head, HeadPhase.CHARGE);
		helper.runAtTickTime(100, () -> {
			try {
				target.setPos(helper.absoluteVec(new Vec3(4.5, 22, 10.5)));
				target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.ABSORPTION, 400));
				helper.assertTrue(target.hurtServer(helper.getLevel(), target.damageSources().drown(), 2), "drowning damage refused");
				helper.assertTrue(target.getHealth() == 20 && target.getAbsorptionAmount() == 2, "drowning did not consume absorption health=" + target.getHealth() + " absorption=" + target.getAbsorptionAmount());
				var speak = TheHead.class.getDeclaredMethod("tickWaterSpeech", net.minecraft.world.entity.player.Player.class, double.class);
				speak.setAccessible(true);
				helper.assertTrue((boolean) speak.invoke(head, target, 1), "real drowning did not queue speech");
				helper.assertTrue(head.cue() == HeadCue.VARIETY_DROWNING, "drowning selected wrong voice");
				var voices = TheHead.class.getDeclaredField("voices"); voices.setAccessible(true);
				helper.assertTrue(((com.larsoyd.mariohead.entity.ChaseVoices) voices.get(head)).waiting(), "drowning omitted guaranteed laugh");
			} catch (ReflectiveOperationException e) { throw new AssertionError(e); }
		});
		helper.runAtTickTime(150, () -> {
			try {
				var cue = TheHead.class.getDeclaredMethod("setCue", HeadCue.class); cue.setAccessible(true);
				cue.invoke(head, HeadCue.VARIETY_LAUGH1);
			} catch (ReflectiveOperationException e) { throw new AssertionError(e); }
		});
		helper.runAtTickTime(240, () -> {
			try {
				target.setPos(helper.absoluteVec(new Vec3(4.5, 22, 10.5)));
				var update = TheHead.class.getDeclaredMethod("updateDive", net.minecraft.world.entity.player.Player.class); update.setAccessible(true);
				update.invoke(head, target);
				helper.assertTrue(target.hurtServer(helper.getLevel(), target.damageSources().drown(), 2), "second drowning damage refused");
				var speak = TheHead.class.getDeclaredMethod("tickWaterSpeech", net.minecraft.world.entity.player.Player.class, double.class); speak.setAccessible(true);
				helper.assertFalse((boolean) speak.invoke(head, target, 1), "drowning reaction repeated after laugh");
				head.discard(); helper.succeed();
			} catch (ReflectiveOperationException e) { throw new AssertionError(e); }
		});
	}

	@GameTest(structure = ARENA, maxTicks = 30)
	public void earlyStallResetsSpeedAndRemainsDistinctFromTaunt(GameTestHelper helper) throws ReflectiveOperationException {
		earlyStallRoll(helper, true);
	}

	@GameTest(structure = ARENA, maxTicks = 30)
	public void earlyStallFailedRollKeepsAccumulatedSpeed(GameTestHelper helper) throws ReflectiveOperationException {
		earlyStallRoll(helper, false);
	}

	@GameTest(structure = ARENA, maxTicks = 40)
	public void earlyStallEquipmentDisablesPermanentlyAndSilently(GameTestHelper helper) throws ReflectiveOperationException {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0, 10.5), -90);
		var update = TheHead.class.getDeclaredMethod("updateStalls", net.minecraft.world.entity.player.Player.class);
		var state = TheHead.class.getDeclaredField("stalls");
		var pending = TheHead.class.getDeclaredField("cutoffPending");
		update.setAccessible(true); state.setAccessible(true); pending.setAccessible(true);
		for (var item : new net.minecraft.world.item.Item[] { net.minecraft.world.item.Items.ENDER_PEARL,
			net.minecraft.world.item.Items.ELYTRA, net.minecraft.world.item.Items.GOLDEN_APPLE, net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE,
			net.minecraft.world.item.Items.LEATHER_HELMET, net.minecraft.world.item.Items.IRON_CHESTPLATE,
			net.minecraft.world.item.Items.GOLDEN_LEGGINGS, net.minecraft.world.item.Items.DIAMOND_BOOTS }) {
			TheHead head = head(helper, new Vec3(10.5, 0.5, 10.5), target);
			head.setNoAi(true);
			target.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(item));
			update.invoke(head, target);
			target.getInventory().setItem(0, net.minecraft.world.item.ItemStack.EMPTY);
			update.invoke(head, target);
			helper.assertFalse(((com.larsoyd.mariohead.entity.EarlyStall) state.get(head)).active(1), "equipment did not latch stall disable: " + item);
			helper.assertFalse(pending.getBoolean(head), "equipment queued cutoff speech: " + item);
			head.discard();
		}
		helper.succeed();
	}

	@GameTest(structure = ARENA, maxTicks = 40)
	public void earlyStallNaturalCutoffSpeaksOnceWithoutLaugh(GameTestHelper helper) throws ReflectiveOperationException {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0, 10.5), -90);
		TheHead head = head(helper, new Vec3(10.5, 0.5, 10.5), target);
		head.setNoAi(true);
		var update = TheHead.class.getDeclaredMethod("updateStalls", net.minecraft.world.entity.player.Player.class);
		var speak = TheHead.class.getDeclaredMethod("tickCutoff", net.minecraft.world.entity.player.Player.class);
		var age = TheHead.class.getDeclaredField("phaseTicks");
		var state = TheHead.class.getDeclaredField("stalls");
		var voices = TheHead.class.getDeclaredField("voices");
		update.setAccessible(true); speak.setAccessible(true);
		for (var field : new Field[] { age, state, voices }) field.setAccessible(true);
		state.set(head, new com.larsoyd.mariohead.entity.EarlyStall(0));
		age.setInt(head, 6000);
		update.invoke(head, target);
		helper.assertTrue((boolean) speak.invoke(head, target), "natural cutoff did not announce");
		helper.assertTrue(java.util.List.of(com.larsoyd.mariohead.entity.ChaseVoices.CUTOFF).contains(head.cue()), "cutoff selected wrong line");
		helper.assertFalse(((com.larsoyd.mariohead.entity.ChaseVoices) voices.get(head)).waiting(), "cutoff queued laugh");
		update.invoke(head, target);
		helper.assertFalse((boolean) speak.invoke(head, target), "cutoff announced twice");
		head.discard();
		helper.succeed();
	}

	@GameTest(structure = ARENA, maxTicks = 30)
	public void earlyStallWaitsForActualDashPitch(GameTestHelper helper) throws ReflectiveOperationException {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0, 10.5), -90);
		TheHead head = head(helper, new Vec3(6.5, 0.5, 10.5), target);
		head.setNoAi(true); head.setXRot(-80);
		var cue = TheHead.class.getDeclaredMethod("setCue", HeadCue.class);
		var charge = TheHead.class.getDeclaredMethod("charge", net.minecraft.server.level.ServerLevel.class, net.minecraft.world.entity.player.Player.class);
		cue.setAccessible(true); charge.setAccessible(true);
		cue.invoke(head, HeadCue.INHALE);
		for (String name : new String[] { "cueTicks", "revs", "phaseTicks" }) {
			var field = TheHead.class.getDeclaredField(name); field.setAccessible(true);
			field.setInt(head, name.equals("revs") ? 12 : 100);
		}
		var state = TheHead.class.getDeclaredField("stalls"); state.setAccessible(true);
		state.set(head, new com.larsoyd.mariohead.entity.EarlyStall(0.5));
		var ready = TheHead.class.getDeclaredField("tauntReadyAt"); ready.setAccessible(true);
		ready.setLong(head, Long.MAX_VALUE);
		for (int seed = 0; seed < 10000; seed++) {
			if (net.minecraft.util.RandomSource.create(seed).nextDouble() < 0.15) { head.getRandom().setSeed(seed); break; }
		}
		charge.invoke(head, helper.getLevel(), target);
		helper.assertTrue(head.cue() == HeadCue.INHALE && head.revs() == 12, "stall rolled before dash pitch was ready: " + head.cue());
		head.discard(); helper.succeed();
	}

	@GameTest(structure = ARENA, maxTicks = 40)
	public void earlyStallEscalationCancelsMockAndFollowing(GameTestHelper helper) throws ReflectiveOperationException {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0, 10.5), -90);
		var update = TheHead.class.getDeclaredMethod("updateStalls", net.minecraft.world.entity.player.Player.class);
		var until = TheHead.class.getDeclaredField("stallUntil");
		var following = TheHead.class.getDeclaredField("stallFollowing");
		var state = TheHead.class.getDeclaredField("stalls");
		var voices = TheHead.class.getDeclaredField("voices");
		var pending = TheHead.class.getDeclaredField("cutoffPending");
		var digging = TheHead.class.getDeclaredMethod("setDigging", boolean.class);
		update.setAccessible(true); digging.setAccessible(true);
		for (var field : new Field[] { until, following, state, voices, pending }) field.setAccessible(true);
		for (int mode = 0; mode < 6; mode++) {
			TheHead head = head(helper, new Vec3(10.5, 0.5, 10.5), target);
			head.setNoAi(true);
			update.invoke(head, target);
			until.setLong(head, mode < 3 ? helper.getLevel().getGameTime() + 100 : -1);
			following.setBoolean(head, true); pending.setBoolean(head, true);
			((com.larsoyd.mariohead.entity.ChaseVoices) voices.get(head)).follow(1, 0);
			if (mode % 3 == 0) target.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_PEARL));
			if (mode % 3 == 1) target.setPos(head.position().add(65, 0, 0));
			if (mode % 3 == 2) digging.invoke(head, true);
			update.invoke(head, target);
			helper.assertTrue(until.getLong(head) < 0 && !following.getBoolean(head), "escalation retained mock/follow: " + mode);
			helper.assertFalse(pending.getBoolean(head) || ((com.larsoyd.mariohead.entity.ChaseVoices) voices.get(head)).waiting(), "escalation retained queued speech: " + mode);
			target.getInventory().setItem(0, net.minecraft.world.item.ItemStack.EMPTY);
			target.setPos(helper.absoluteVec(new Vec3(3.5, 0, 10.5)));
			digging.invoke(head, false); update.invoke(head, target);
			helper.assertFalse(((com.larsoyd.mariohead.entity.EarlyStall) state.get(head)).active(1), "escalation was not permanent: " + mode);
			head.discard();
		}
		helper.succeed();
	}

	@GameTest(structure = ARENA, maxTicks = 40)
	public void earlyStallIceUnderBoatEdgeDisablesPermanently(GameTestHelper helper) throws ReflectiveOperationException {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0, 10.5), -90);
		helper.setBlock(new BlockPos(4, -1, 10), Blocks.BLUE_ICE);
		var boat = helper.spawn(net.minecraft.world.entity.EntityTypes.OAK_BOAT, new Vec3(3.5, 0, 10.5));
		target.startRiding(boat, true, true);
		TheHead head = head(helper, new Vec3(10.5, 0.5, 10.5), target);
		head.setNoAi(true);
		var update = TheHead.class.getDeclaredMethod("updateStalls", net.minecraft.world.entity.player.Player.class);
		var state = TheHead.class.getDeclaredField("stalls");
		update.setAccessible(true); state.setAccessible(true);
		update.invoke(head, target);
		helper.assertFalse(((com.larsoyd.mariohead.entity.EarlyStall) state.get(head)).active(1), "boat edge on ice retained stalls");
		target.stopRiding(); boat.discard(); head.discard(); helper.succeed();
	}

	@GameTest(structure = ARENA, maxTicks = 400)
	public void earlyStallLiveMockGivesSmallGapAndRestartsAtBaseSpeed(GameTestHelper helper) throws ReflectiveOperationException {
		liveStall(helper, false);
	}

	@GameTest(structure = ARENA, maxTicks = 200)
	public void earlyStallContactDuringMockIsFatal(GameTestHelper helper) throws ReflectiveOperationException {
		liveStall(helper, true);
	}

	private void liveStall(GameTestHelper helper, boolean contact) throws ReflectiveOperationException {
		fill(helper, -100, 179, 5, 24, 179, 15, Blocks.STONE.defaultBlockState());
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(12.5, 180, 10.5), 90);
		target.setNoGravity(true);
		TheHead head = head(helper, new Vec3(15.5, 180.5, 10.5), target);
		head.setNoAi(true);
		var phase = TheHead.class.getDeclaredMethod("setPhase", HeadPhase.class);
		var cue = TheHead.class.getDeclaredMethod("setCue", HeadCue.class);
		phase.setAccessible(true); cue.setAccessible(true);
		var revs = TheHead.class.getDeclaredField("revs");
		var ticks = TheHead.class.getDeclaredField("cueTicks");
		var pace = TheHead.class.getDeclaredField("pace");
		var until = TheHead.class.getDeclaredField("stallUntil");
		var state = TheHead.class.getDeclaredField("stalls");
		var movement = TheHead.class.getDeclaredField("moveSpeed");
		var voices = TheHead.class.getDeclaredField("voices");
		for (var field : new Field[] { revs, ticks, pace, until, state, movement, voices }) field.setAccessible(true);
		long[] started = { -1 };
		double[] gap = { 0 };
		boolean[] completed = { false };
		helper.runAtTickTime(100, () -> {
			try {
				phase.invoke(head, HeadPhase.CHARGE); cue.invoke(head, HeadCue.INHALE);
				revs.setInt(head, 12); ticks.setInt(head, 100); pace.setDouble(head, 0.28); movement.setDouble(head, 0.28);
				state.set(head, new com.larsoyd.mariohead.entity.EarlyStall(0.5));
				for (int seed = 0; seed < 10000; seed++) {
					var random = net.minecraft.util.RandomSource.create(seed);
					random.nextInt(1000);
					double chance = random.nextDouble();
					double line = random.nextDouble();
					if (chance < 0.15 && line >= 0.5 && line < 0.5625) { head.getRandom().setSeed(seed); break; }
				}
				head.setNoAi(false);
			} catch (ReflectiveOperationException e) { throw new AssertionError(e); }
		});
		helper.onEachTick(() -> {
			if (helper.getTick() < 100) return;
			target.setPos(target.position().add(-0.28, 0, 0));
			target.setOnGround(true);
			try {
				if (until.getLong(head) >= 0 && started[0] < 0) {
					started[0] = helper.getTick(); gap[0] = head.position().distanceTo(target.position());
					((com.larsoyd.mariohead.entity.ChaseVoices) voices.get(head)).follow(1, 0);
				}
				if (contact && started[0] >= 0) {
					if (target.isDeadOrDying()) { helper.succeed(); return; }
					target.setPos(head.centre().add(0, -0.9, 0));
					return;
				}
				if (started[0] >= 0 && until.getLong(head) < 0 && !completed[0]) {
					double gain = head.position().distanceTo(target.position()) - gap[0];
					helper.assertTrue(gain > 0 && gain < 2, "mock gap gain not subtle: " + gain);
					completed[0] = true;
				}
				if (completed[0] && (head.cue().spoken() || head.cue() == HeadCue.INHALE)) {
					helper.assertTrue(head.chargeSpeed() > 0.26 && head.chargeSpeed() < 0.28, "post-stall follow lost runner pace: " + head.chargeSpeed());
				}
				if (completed[0] && head.cue().clip() != null && !head.cue().spoken()) {
					helper.assertTrue(head.revs() == 0 && head.chargeSpeed() < 0.3, "first real dash retained pre-stall speed: " + head.chargeSpeed());
					helper.assertFalse(target.isDeadOrDying(), "mock or first real dash caught escaping runner");
					head.discard(); helper.succeed();
				}
				if (helper.getTick() == 110) helper.assertTrue(started[0] >= 0, "live dash did not enter stall cue=" + head.cue()
					+ " revs=" + head.revs() + " head=" + helper.relativeVec(head.position()) + " target=" + helper.relativeVec(target.position())
					+ " active=" + ((com.larsoyd.mariohead.entity.EarlyStall) state.get(head)).active(10));
			} catch (ReflectiveOperationException e) { throw new AssertionError(e); }
		});
	}

	@GameTest(structure = ARENA, maxTicks = 30)
	public void earlyStallRejectsStalkingDashEvenWithWinningRoll(GameTestHelper helper) throws ReflectiveOperationException {
		earlyStallRoll(helper, false, true);
	}

	private void earlyStallRoll(GameTestHelper helper, boolean success) throws ReflectiveOperationException {
		earlyStallRoll(helper, success, false);
	}

	private void earlyStallRoll(GameTestHelper helper, boolean success, boolean stalking) throws ReflectiveOperationException {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0, 10.5), -90);
		TheHead head = head(helper, new Vec3(6.5, 0.5, 10.5), target);
		head.setNoAi(true);
		head.setYRot(90);
		var revs = TheHead.class.getDeclaredField("revs");
		var tempo = TheHead.class.getDeclaredField("tempo");
		var pace = TheHead.class.getDeclaredField("pace");
		var boost = TheHead.class.getDeclaredField("dashBoost");
		var teasing = TheHead.class.getDeclaredField("teasing");
		var until = TheHead.class.getDeclaredField("stallUntil");
		var age = TheHead.class.getDeclaredField("phaseTicks");
		for (var field : new Field[] { revs, tempo, pace, boost, teasing, until, age }) field.setAccessible(true);
		age.setInt(head, 0);
		revs.setInt(head, 12);
		pace.setDouble(head, 0.28);
		boost.setDouble(head, 1.1);
		ChaseTempo speed = (ChaseTempo) tempo.get(head);
		speed.observe(true, false, false, 0);
		for (int i = 0; i < 20; i++) { speed.dash(); speed.hum(); }
		var start = TheHead.class.getDeclaredMethod("tryStall", net.minecraft.world.entity.player.Player.class, double.class);
		start.setAccessible(true);
		if (stalking) {
			var prank = TheHead.class.getDeclaredField("prank"); prank.setAccessible(true);
			((com.larsoyd.mariohead.entity.BehindPrank) prank.get(head)).dash(true);
		}
		helper.assertTrue((boolean) start.invoke(head, target, success || stalking ? 0.149999 : 0.15) == success, "wrong imminent stall roll outcome");
		if (success) {
			helper.assertTrue(java.util.List.of(com.larsoyd.mariohead.entity.ChaseVoices.STALLS).contains(head.cue()), "stall used wrong line");
			helper.assertTrue(until.getLong(head) == helper.getLevel().getGameTime() + (head.cue().clip().millis() + 49) / 50, "mock duration differs from recording");
			helper.assertFalse(teasing.getBoolean(head), "stall borrowed taunt movement state");
			helper.assertTrue(revs.getInt(head) == -1 && boost.getDouble(head) == 1 && speed.multiplier() == 1 && speed.humMultiplier() == 1,
				"successful stall retained accumulated speed");
		} else {
			helper.assertTrue(head.cue() == HeadCue.NONE && revs.getInt(head) == 12 && boost.getDouble(head) == 1.1 && speed.multiplier() > 1 && speed.humMultiplier() > 1,
				"failed stall reset speed or played a line");
		}
		head.discard();
		helper.succeed();
	}

	@GameTest(structure = ARENA, maxTicks = 240)
	public void tauntLaughPrecedesOrdinaryDash(GameTestHelper helper) throws ReflectiveOperationException {
		tauntLaugh(helper, false);
	}

	@GameTest(structure = ARENA, maxTicks = 240)
	public void tauntLaughPrecedesTntDash(GameTestHelper helper) throws ReflectiveOperationException {
		tauntLaugh(helper, true);
	}

	private void tauntLaugh(GameTestHelper helper, boolean tnt) throws ReflectiveOperationException {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0, 10.5), -90);
		TheHead head = head(helper, new Vec3(10.5, 0.5, 10.5), target);
		var phase = TheHead.class.getDeclaredMethod("setPhase", HeadPhase.class);
		var cue = TheHead.class.getDeclaredMethod("setCue", HeadCue.class);
		var digging = TheHead.class.getDeclaredMethod("setDigging", boolean.class);
		var teasing = TheHead.class.getDeclaredField("teasing");
		var voices = TheHead.class.getDeclaredField("voices");
		var boost = TheHead.class.getDeclaredField("dashBoost");
		for (var method : new java.lang.reflect.Method[] { phase, cue, digging }) method.setAccessible(true);
		for (var field : new Field[] { teasing, voices, boost }) field.setAccessible(true);
		phase.invoke(head, HeadPhase.CHARGE);
		digging.invoke(head, tnt);
		teasing.setBoolean(head, true);
		cue.invoke(head, HeadCue.TAUNT_100);
		((com.larsoyd.mariohead.entity.ChaseVoices) voices.get(head)).follow(1, 0);
		long[] laughAt = { -1 };
		HeadCue[] laugh = { HeadCue.NONE };
		helper.onEachTick(() -> {
			if (head.cue().laugh() && laughAt[0] < 0) {
				laughAt[0] = helper.getLevel().getGameTime();
				laugh[0] = head.cue();
			}
			if (head.cue().clip() != null && !head.cue().spoken()) {
				helper.assertTrue(laughAt[0] >= 0, "hum overtook queued laugh tnt=" + tnt);
				long elapsed = helper.getLevel().getGameTime() - laughAt[0];
				helper.assertTrue(elapsed >= (laugh[0].clip().millis() + 49) / 50 + HeadClock.LATE_VOICE_TICKS,
					"dash cut off laugh at " + elapsed);
				try {
					helper.assertTrue(boost.getDouble(head) == 1.1, "laugh consumed post-taunt boost");
				} catch (IllegalAccessException e) {
					throw new IllegalStateException(e);
				}
				head.discard();
				helper.succeed();
			}
		});
	}

	@GameTest(structure = ARENA, maxTicks = 500)
	public void tauntMatchesPaceThenBoostsOneDash(GameTestHelper helper) {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0, 10.5), -90.0f);
		TheHead head = head(helper, new Vec3(16.5, 0.5, 10.5), target);
		boolean[] taunted = { false };
		boolean[] resumed = { false };
		helper.runAtTickTime(80, () -> {
			target.setOnGround(true);
			head.setPos(helper.absoluteVec(new Vec3(7.0, 0.5, 10.5)));
			try {
				var taunt = TheHead.class.getDeclaredMethod("tryTaunt", net.minecraft.world.entity.player.Player.class, double.class);
				taunt.setAccessible(true);
				helper.assertTrue((boolean) taunt.invoke(head, target, 0.0), "reachable player did not trigger taunt");
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		});
		helper.onEachTick(() -> {
			if (head.cue().taunt()) {
				taunted[0] = true;
				helper.assertFalse(target.isDeadOrDying(), "taunt killed the player");
				helper.assertTrue(head.chargeSpeed() <= 0.01, "stationary player chased during taunt");
			}
			if (taunted[0] && !resumed[0] && head.cue().clip() != null && !head.cue().spoken()) {
				resumed[0] = true;
				double expected = RevSpring.peak(head.revs()) * 1.1 * 1.003;
				helper.assertTrue(Math.abs(head.chargeSpeed() - expected) < 1e-6,
					"post-taunt dash speed=" + head.chargeSpeed() + " expected=" + expected);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(taunted[0] && resumed[0] && target.isDeadOrDying(), "taunt did not resume into a catch");
		});
	}

	@GameTest(structure = ARENA, maxTicks = 120)
	public void nearbyRunnersGetOneTauntRollEvenDuringJumps(GameTestHelper helper) {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0, 10.5), -90.0f);
		TheHead head = head(helper, new Vec3(16.5, 0.5, 10.5), target);
		head.setNoAi(true);
		helper.runAtTickTime(80, () -> {
			try {
				var taunt = TheHead.class.getDeclaredMethod("tryTaunt", net.minecraft.world.entity.player.Player.class, double.class);
				taunt.setAccessible(true);
				Field velocity = TheHead.class.getDeclaredField("chasedVelocity");
				velocity.setAccessible(true);
				velocity.set(head, new Vec3(0, 0, 0.8));
				target.setOnGround(true);
				helper.assertFalse((boolean) taunt.invoke(head, target, 0.0), "distant runner triggered taunt");
				head.setPos(helper.absoluteVec(new Vec3(11.5, 0.5, 10.5)));
				target.setPos(target.position().add(0, 1.0, 0));
				target.setOnGround(false);
				helper.assertTrue((boolean) taunt.invoke(head, target, 0.0), "nearby jumping sideways runner did not get a taunt");
				head.discard();
				helper.succeed();
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		});
	}

	@GameTest(structure = ARENA, maxTicks = 120)
	public void failedTauntRollCannotRepeatUntilNextDash(GameTestHelper helper) {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0, 10.5), -90.0f);
		TheHead head = head(helper, new Vec3(7.0, 0.5, 10.5), target);
		head.setNoAi(true);
		helper.runAtTickTime(80, () -> {
			try {
				var taunt = TheHead.class.getDeclaredMethod("tryTaunt", net.minecraft.world.entity.player.Player.class, double.class);
				taunt.setAccessible(true);
				target.setOnGround(true);
				helper.assertFalse((boolean) taunt.invoke(head, target, 0.55), "55 percent boundary accepted");
				helper.assertFalse((boolean) taunt.invoke(head, target, 0.0), "failed roll retried within the same dash");
				var cue = TheHead.class.getDeclaredMethod("setCue", HeadCue.class);
				var charge = TheHead.class.getDeclaredMethod("charge", ServerLevel.class, net.minecraft.world.entity.player.Player.class);
				Field cueTicks = TheHead.class.getDeclaredField("cueTicks");
				cue.setAccessible(true);
				charge.setAccessible(true);
				cueTicks.setAccessible(true);
				cue.invoke(head, HeadCue.MMM1);
				cueTicks.setInt(head, 1000);
				charge.invoke(head, helper.getLevel(), target);
				helper.assertTrue(head.cue() == HeadCue.INHALE, "dash did not finish");
				helper.assertTrue((boolean) taunt.invoke(head, target, 0.0), "next dash did not get a fresh roll");
				head.discard();
				helper.succeed();
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		});
	}

	@GameTest(structure = ARENA, maxTicks = 120)
	public void tauntCooldownBlocksNearbyRollsUntilReady(GameTestHelper helper) {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0, 10.5), -90.0f);
		TheHead head = head(helper, new Vec3(7.0, 0.5, 10.5), target);
		head.setNoAi(true);
		helper.runAtTickTime(80, () -> {
			try {
				var taunt = TheHead.class.getDeclaredMethod("tryTaunt", net.minecraft.world.entity.player.Player.class, double.class);
				Field ready = TheHead.class.getDeclaredField("tauntReadyAt");
				taunt.setAccessible(true);
				ready.setAccessible(true);
				target.setOnGround(true);
				java.util.Random random = new java.util.Random(7431);
				for (int sample = 0; sample < 100; sample++) {
					ready.setLong(head, helper.getLevel().getGameTime() + 1 + random.nextInt(600));
					helper.assertFalse((boolean) taunt.invoke(head, target, random.nextDouble() * 0.55), "taunt ignored cooldown at sample " + sample);
				}
				ready.setLong(head, helper.getLevel().getGameTime());
				helper.assertTrue((boolean) taunt.invoke(head, target, 0.0), "taunt unavailable when cooldown expires");
				head.discard();
				helper.succeed();
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		});
	}

	@GameTest(structure = ARENA, maxTicks = 500)
	public void approachingDashPausesForTauntAndStartsCooldown(GameTestHelper helper) {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(3.5, 0, 10.5), -90.0f);
		TheHead head = head(helper, new Vec3(16.5, 0.5, 10.5), target);
		head.setNoAi(true);
		long[] started = { -1 };
		long[] humAt = { -1 };
		helper.runAtTickTime(80, () -> {
			try {
				var phase = TheHead.class.getDeclaredMethod("setPhase", HeadPhase.class);
				var cue = TheHead.class.getDeclaredMethod("setCue", HeadCue.class);
				var charge = TheHead.class.getDeclaredMethod("charge", ServerLevel.class, net.minecraft.world.entity.player.Player.class);
				phase.setAccessible(true);
				cue.setAccessible(true);
				charge.setAccessible(true);
				phase.invoke(head, HeadPhase.CHARGE);
				cue.invoke(head, HeadCue.MMM1);
				humAt[0] = helper.getLevel().getGameTime();
				target.setOnGround(true);
				charge.invoke(head, helper.getLevel(), target);
				head.setPos(helper.absoluteVec(new Vec3(10.5, 0.5, 10.5)));
				head.getRandom().setSeed(4096);
				charge.invoke(head, helper.getLevel(), target);
				Field teasing = TheHead.class.getDeclaredField("teasing");
				teasing.setAccessible(true);
				helper.assertTrue(teasing.getBoolean(head), "approaching active dash did not roll for a nearby runner");
				helper.assertTrue(head.cue() == HeadCue.MMM1, "taunt interrupted the current hum");
				head.setNoAi(false);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		});
		helper.onEachTick(() -> {
			if (head.cue().taunt() && started[0] < 0) {
				started[0] = helper.getLevel().getGameTime();
				helper.assertFalse(target.isDeadOrDying(), "player died waiting for taunt");
				helper.assertTrue(started[0] >= humAt[0] + (HeadCue.MMM1.clip().millis() + 49) / 50 + 15, "mid-dash taunt skipped voice spacing");
				try {
					Field ready = TheHead.class.getDeclaredField("tauntReadyAt");
					ready.setAccessible(true);
					long duration = (head.cue().clip().millis() + 49) / 50;
					helper.assertTrue(ready.getLong(head) >= started[0] + duration + 600, "taunt cooldown does not cover speech plus thirty seconds");
					head.discard();
					helper.succeed();
				} catch (ReflectiveOperationException e) {
					throw new IllegalStateException(e);
				}
			}
		});
	}

	@GameTest(structure = ARENA, maxTicks = 500)
	public void movingTauntFollowsPaceAndBoostsOnlyOneWholeDash(GameTestHelper helper) {
		fill(helper, -12, 63, 7, 120, 63, 13, Blocks.STONE.defaultBlockState());
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(10.5, 64, 10.5), -90.0f);
		TheHead head = head(helper, new Vec3(-4.5, 64.5, 10.5), target);
		HeadCue[] last = { HeadCue.NONE };
		long[] tauntAt = { -1 };
		long[] boostAt = { -1 };
		boolean[] durationChecked = { false };
		int[] resumed = { 0 };
		helper.runAtTickTime(80, () -> {
			head.setPos(target.getX() - 4, helper.absoluteVec(new Vec3(0, 64.5, 0)).y, target.getZ());
			try {
				var taunt = TheHead.class.getDeclaredMethod("tryTaunt", net.minecraft.world.entity.player.Player.class, double.class);
				taunt.setAccessible(true);
				Field velocity = TheHead.class.getDeclaredField("chasedVelocity");
				velocity.setAccessible(true);
				velocity.set(head, Vec3.ZERO);
				helper.assertTrue((boolean) taunt.invoke(head, target, 0.0), "could not start moving-taunt fixture");
				Field pending = TheHead.class.getDeclaredField("pendingVoice");
				pending.setAccessible(true);
				pending.set(head, HeadCue.TAUNT_088);
				Field elapsed = TheHead.class.getDeclaredField("phaseTicks");
				elapsed.setAccessible(true);
				elapsed.setInt(head, 6000);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		});
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			target.setOnGround(true);
			if (tick >= 80 && !target.isDeadOrDying()) {
				target.setPos(target.position().add(0.2, 0, 0));
			}
			HeadCue cue = head.cue();
			if (cue.taunt()) {
				if (tauntAt[0] < 0) {
					tauntAt[0] = tick;
				}
				helper.assertFalse(target.isDeadOrDying(), "moving player died during taunt");
				if (tick - tauntAt[0] >= 45) {
					double step = head.getDeltaMovement().horizontalDistance();
					helper.assertTrue(Math.abs(step - 0.2) < 0.03, "taunt follow speed=" + step);
					helper.assertTrue(head.distanceTo(target) >= 2.5 && head.distanceTo(target) < 9,
						"taunt lost audible separation: " + head.distanceTo(target));
				}
			}
			if (tauntAt[0] >= 0 && cue != last[0]) {
				if (cue.clip() != null && !cue.spoken()) {
					resumed[0]++;
					double expected = RevSpring.peak(head.revs()) * (resumed[0] == 1 ? 1.1 : 1.0) * (1.0 + resumed[0] * 0.003);
					helper.assertTrue(Math.abs(head.chargeSpeed() - expected) < 1e-6, "dash " + resumed[0] + " speed=" + head.chargeSpeed());
					if (resumed[0] == 1) {
						boostAt[0] = tick;
					} else {
						helper.assertTrue(durationChecked[0], "boosted duration not checked");
						head.discard();
						helper.succeed();
					}
				} else if (cue == HeadCue.INHALE && resumed[0] == 1) {
					long duration = tick - boostAt[0];
					int expected = (int) Math.ceil(last[0].clip().frames() / 30.0 * 20 * 1.1);
					helper.assertTrue(duration >= expected && duration <= expected + 1, "boost duration=" + duration + " expected=" + expected);
					durationChecked[0] = true;
				}
			}
			last[0] = cue;
		});
	}

	@GameTest(structure = ARENA, maxTicks = 250)
	public void staysLevelAtHoverHeight(GameTestHelper helper) {
		firstDashPitch(helper, 0.0, false, 0);
	}

	private static void firstDashPitch(GameTestHelper helper, double targetY, boolean dive, int sign) {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(8.5, targetY, 10.5), -90.0f);
		target.setNoGravity(true);
		TheHead head = head(helper, new Vec3(14.5, 0.5, 10.5), target);
		boolean[] raised = { false };
		helper.onEachTick(() -> {
			if (dive && !raised[0] && head.phase() == HeadPhase.CHARGE) {
				head.setPos(head.getX(), helper.absoluteVec(new Vec3(0, 6.0, 0)).y - head.getBbHeight() / 2.0, head.getZ());
				raised[0] = true;
			}
			if (head.cue() == HeadCue.MMM1) {
				helper.assertTrue(sign == 0 ? Math.abs(head.getXRot()) < 0.01 : head.getXRot() * sign > 15.0,
					"dash pitch=" + head.getXRot() + " expected sign=" + sign
					+ " head=" + helper.relativeVec(head.centre()) + " target=" + helper.relativeVec(target.position()));
				head.discard();
				leave(helper, target);
				helper.succeed();
			}
		});
	}

	@GameTest(structure = ARENA, maxTicks = 250)
	public void finishesTurningBeforeDashAfterElevationReverses(GameTestHelper helper) {
		ServerPlayer target = player(helper, GameType.SURVIVAL, new Vec3(8.5, 5.0, 10.5), -90.0f);
		target.setNoGravity(true);
		TheHead head = head(helper, new Vec3(14.5, 0.5, 10.5), target);
		boolean[] reversed = { false };
		helper.onEachTick(() -> {
			if (!reversed[0] && head.phase() == HeadPhase.CHARGE && head.cue() == HeadCue.INHALE && head.cueSeconds(0) >= 0.35) {
				target.setPos(target.getX(), helper.absoluteVec(new Vec3(0, 0, 0)).y, target.getZ());
				head.setPos(head.getX(), helper.absoluteVec(new Vec3(0, 6, 0)).y - head.getBbHeight() / 2.0, head.getZ());
				reversed[0] = true;
			}
			if (head.cue() == HeadCue.MMM1) {
				helper.assertTrue(reversed[0] && head.getXRot() > 15.0f, "dash before turning downward: " + head.getXRot());
				head.discard();
				leave(helper, target);
				helper.succeed();
			}
		});
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
		helper.onEachTick(() -> announced[0] |= !head.isRemoved() && head.cue() == HeadCue.HOME_NICE);
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
