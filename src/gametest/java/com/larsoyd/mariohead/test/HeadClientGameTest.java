package com.larsoyd.mariohead.test;

import com.larsoyd.mariohead.HeadEntities;
import com.larsoyd.mariohead.client.HeadVoiceSound;
import com.larsoyd.mariohead.client.TheHeadRenderer;
import com.larsoyd.mariohead.entity.HeadCue;
import com.larsoyd.mariohead.entity.HeadPhase;
import com.larsoyd.mariohead.entity.TheHead;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HeadClientGameTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mariohead-client-test");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().adjustSettings(s -> {
			s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
			s.setWorldType(s.getNormalPresetList().stream().filter(t -> t.preset() != null && t.preset().is(WorldPresets.FLAT)).findFirst().orElseThrow());
		}).create()) {
			var server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			server.runCommand("tp @p 0.5 -60 0.5 -90 0");
			world.getConnection().waitForChunksRender();
			this.seenAndHeard(context, world);
			this.turnsWithoutWobble(context, world);
			this.lateObserver(context, world);
			this.announcementIsHeard(context, world);
		}
	}

	private static List<TheHead> clientHeads() {
		var level = Minecraft.getInstance().level;
		return level.getEntities(HeadEntities.THE_HEAD, new AABB(-400, -100, -400, 400, 100, 400), h -> true);
	}

	private void seenAndHeard(ClientGameTestContext context, TestSingleplayerContext world) {
		int before = HeadVoiceSound.started();
		world.getServer().runCommand("give @p mariohead:the_head_spawn_egg");
		world.getServer().runCommand("mariohead summon @p");
		context.waitFor(mc -> !clientHeads().isEmpty(), 100);
		boolean rendered = context.computeOnClient(mc -> mc.getEntityRenderDispatcher().getRenderer(clientHeads().getFirst()) instanceof TheHeadRenderer);
		check(rendered, "no head renderer");
		context.waitFor(mc -> clientHeads().getFirst().cue() == HeadCue.HELLO, 120);
		context.waitTicks(12);
		context.takeScreenshot("the-head-hello");
		check(HeadVoiceSound.started() == before + 1, "hello started " + (HeadVoiceSound.started() - before) + " voices");
		context.waitFor(mc -> clientHeads().getFirst().cue() == HeadCue.HELLO
			&& com.larsoyd.mariohead.entity.HeadPose.mouthOpen(HeadPhase.TALK, HeadCue.HELLO, clientHeads().getFirst().cueSeconds(0.0f), 1.0), 60);
		this.portrait(context, world, "the-head-portrait-open");
		context.waitFor(mc -> clientHeads().getFirst().cue() == HeadCue.MMM1, 120);
		context.waitTicks(5);
		this.portrait(context, world, "the-head-portrait-shut");
		check(HeadVoiceSound.started() == before + 2, "first hum gave " + (HeadVoiceSound.started() - before) + " voices");
		world.getServer().runCommand("kill @e[type=mariohead:the_head]");
		context.waitFor(mc -> clientHeads().isEmpty(), 40);
	}

	// frozen and seen face on from 3.6 blocks, a little above like the original's camera
	private void portrait(ClientGameTestContext context, TestSingleplayerContext world, String name) {
		var server = world.getServer();
		server.runCommand("tick freeze");
		var back = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().position());
		server.runOnServer(s -> {
			TheHead head = serverHead(s);
			var player = s.getPlayerList().getPlayers().getFirst();
			var c = head.centre();
			double yaw = Math.toRadians(head.getYRot());
			double x = c.x - Math.sin(yaw) * 3.6;
			double z = c.z + Math.cos(yaw) * 3.6;
			float pitch = (float) Math.toDegrees(Math.atan2(0.75, 3.6));
			player.teleportTo(s.overworld(), x, c.y + 0.75 - player.getEyeHeight(), z, java.util.Set.of(), head.getYRot() + 180.0f, pitch, true);
		});
		context.getInput().pressKey(options -> options.keyToggleGui);
		context.waitTicks(4);
		context.takeScreenshot(name);
		context.getInput().pressKey(options -> options.keyToggleGui);
		server.runCommand("tp @p " + back.x + " " + back.y + " " + back.z);
		server.runCommand("tick unfreeze");
	}

	// he says hello while nobody watches, then the player arrives partway through
	private void lateObserver(ClientGameTestContext context, TestSingleplayerContext world) {
		var server = world.getServer();
		server.runCommand("forceload add -32 -32 32 32");
		server.runCommand("tp @p 900.5 -60 0.5 -90 0");
		context.waitFor(mc -> clientHeads().isEmpty(), 100);
		server.runOnServer(s -> {
			var level = s.overworld();
			TheHead head = HeadEntities.THE_HEAD.create(level, EntitySpawnReason.COMMAND);
			head.snapTo(9.5, -59.0, 0.5, 90.0f, 0.0f);
			head.chase(s.getPlayerList().getPlayers().getFirst());
			level.addFreshEntity(head);
		});
		server.waitFor(s -> serverHead(s) != null && serverHead(s).cue() == HeadCue.HELLO && serverHead(s).cueSeconds(0.0f) >= 0.6, 200);
		int before = HeadVoiceSound.started();
		server.runCommand("tp @p 0.5 -60 0.5 -90 0");
		context.waitFor(mc -> !clientHeads().isEmpty(), 200);
		context.waitTick();
		double clientSeconds = context.computeOnClient(mc -> clientHeads().getFirst().cueSeconds(0.0f));
		double serverSeconds = server.computeOnServer(s -> serverHead(s).cueSeconds(0.0f));
		HeadCue clientCue = context.computeOnClient(mc -> clientHeads().getFirst().cue());
		LOG.info("[client-test] late cue={} client_s={} server_s={} voices={}", clientCue, clientSeconds, serverSeconds, HeadVoiceSound.started() - before);
		check(clientCue == HeadCue.HELLO, "late watcher saw cue " + clientCue);
		check(Math.abs(clientSeconds - serverSeconds) < 0.15, "late watcher mouth at " + clientSeconds + " s, server at " + serverSeconds + " s");
		check(HeadVoiceSound.started() == before, "a hello joined late started anyway");
		context.waitFor(mc -> clientHeads().getFirst().cue() == HeadCue.MMM1, 120);
		check(HeadVoiceSound.started() == before + 1, "the next hum gave " + (HeadVoiceSound.started() - before) + " voices");
		check(context.computeOnClient(mc -> clientHeads().getFirst().phase()) == HeadPhase.CHARGE, "not charging after hello");
		server.runCommand("kill @e[type=mariohead:the_head]");
	}

	// the player circles him while he charges, the client sees his turn and lean every tick
	private void turnsWithoutWobble(ClientGameTestContext context, TestSingleplayerContext world) {
		var server = world.getServer();
		server.runCommand("tp @p 0.5 -60 0.5 -90 0");
		server.runOnServer(s -> {
			var level = s.overworld();
			TheHead head = HeadEntities.THE_HEAD.create(level, EntitySpawnReason.COMMAND);
			head.snapTo(14.5, -59.0, 0.5, 90.0f, 0.0f);
			head.chase(s.getPlayerList().getPlayers().getFirst());
			level.addFreshEntity(head);
		});
		server.waitFor(s -> serverHead(s) != null && serverHead(s).phase() == HeadPhase.CHARGE, 200);
		StringBuilder trace = new StringBuilder();
		float lastYaw = context.computeOnClient(mc -> clientHeads().getFirst().getYRot());
		float lastTurn = 0.0f;
		double jerk = 0.0;
		int flips = 0;
		float lastRoll = 0.0f;
		for (int t = 0; t < 60; t++) {
			int step = t;
			server.runOnServer(s -> {
				var c = serverHead(s).centre();
				double a = Math.toRadians(step * 3.0);
				s.getPlayerList().getPlayers().getFirst().teleportTo(s.overworld(), c.x - 9.0 * Math.cos(a), -60.0, c.z + 9.0 * Math.sin(a), java.util.Set.of(), 0.0f, 0.0f, true);
			});
			context.waitTick();
			float yaw = context.computeOnClient(mc -> clientHeads().getFirst().getYRot());
			float roll = context.computeOnClient(mc -> clientHeads().getFirst().roll(1.0f));
			float turn = net.minecraft.util.Mth.wrapDegrees(yaw - lastYaw);
			if (t > 0) {
				jerk += Math.abs(turn - lastTurn);
			}
			if (Math.abs(roll) > 1.0f && Math.abs(lastRoll) > 1.0f && Math.signum(roll) != Math.signum(lastRoll)) {
				flips++;
			}
			trace.append(String.format(" %.1f/%.1f", turn, roll));
			lastYaw = yaw;
			lastTurn = turn;
			lastRoll = roll;
		}
		LOG.info("[client-test] turn jerk={} roll_flips={} trace={}", String.format("%.1f", jerk), flips, trace);
		check(flips == 0 && jerk < 40.0, "he weaves while circling: jerk " + jerk + " roll flips " + flips);
		server.runCommand("kill @e[type=mariohead:the_head]");
		context.waitFor(mc -> clientHeads().isEmpty(), 40);
	}

	// sealed in stone, the player hears him announce the way underground
	private void announcementIsHeard(ClientGameTestContext context, TestSingleplayerContext world) {
		var server = world.getServer();
		context.waitFor(mc -> clientHeads().isEmpty(), 40);
		server.runCommand("tp @p 0.5 -60 0.5 -90 0");
		server.runCommand("fill -2 -61 -2 2 -57 2 minecraft:stone");
		server.runCommand("fill 0 -60 0 0 -59 0 minecraft:air");
		server.runOnServer(s -> {
			var level = s.overworld();
			TheHead head = HeadEntities.THE_HEAD.create(level, EntitySpawnReason.COMMAND);
			head.snapTo(12.5, -59.0, 0.5, 90.0f, 0.0f);
			head.chase(s.getPlayerList().getPlayers().getFirst());
			level.addFreshEntity(head);
		});
		context.waitFor(mc -> !clientHeads().isEmpty() && clientHeads().getFirst().phase() == HeadPhase.CHARGE, 200);
		int before = HeadVoiceSound.started();
		int cues = 0;
		HeadCue last = HeadCue.INHALE;
		for (int t = 0; t < 200 && last != HeadCue.UNDERGROUND; t++) {
			context.waitTick();
			HeadCue cue = context.computeOnClient(mc -> clientHeads().getFirst().cue());
			if (cue != last && cue.clip() != null) {
				cues++;
			}
			last = cue;
		}
		context.waitTicks(2);
		int voices = HeadVoiceSound.started() - before;
		LOG.info("[client-test] underground cue={} voiced_cues={} voices={}", last, cues, voices);
		check(last == HeadCue.UNDERGROUND && voices == cues, "the announcement: cue " + last + ", " + voices + " voices for " + cues + " voiced cues");
		server.runCommand("kill @e[type=mariohead:the_head]");
		server.runCommand("fill -2 -61 -2 2 -57 2 minecraft:air");
	}

	private static TheHead serverHead(net.minecraft.server.MinecraftServer s) {
		var heads = s.overworld().getEntities(HeadEntities.THE_HEAD, new AABB(-64, -100, -64, 64, 100, 64), h -> true);
		return heads.isEmpty() ? null : heads.getFirst();
	}

	private static void check(boolean ok, String failure) {
		if (!ok) {
			throw new AssertionError("[client-test] " + failure);
		}
	}
}
