package com.larsoyd.mariohead.showcase;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.larsoyd.mariohead.HeadEntities;
import com.larsoyd.mariohead.MarioHead;
import com.larsoyd.mariohead.entity.HeadCue;
import com.larsoyd.mariohead.entity.HeadPhase;
import com.larsoyd.mariohead.entity.TheHead;
import com.larsoyd.mariohead.showcase.mixin.HeadActorAccess;
import java.util.ArrayDeque;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;

public final class Actor {
	private static TheHead head;
	private static final ArrayDeque<Vec3> route = new ArrayDeque<>();
	private static final ArrayDeque<HeadCue> voices = new ArrayDeque<>();
	private static double speed = .16;
	private static boolean pursuing;
	private static long expires;
	private static long speakingUntil;
	private static boolean dashing, raid;
	private static Vec3 facing;
	private static long lastDash;
	private static long cueBegan;
	private static long windupUntil;
	private static boolean firstDash;
	private static Vec3 driftOrigin;
	private static double followDrift;
	public static volatile Vec3 lookPosition;
	public static volatile boolean busy;
	public static volatile String status = "absent";

	private Actor() {}

	public static void apply(MinecraftServer server, JsonObject action) {
		var player = server.getPlayerList().getPlayers().getFirst();
		ServerLevel level = player.level();
		if (action.has("spawn")) {
			if (head != null && !head.isRemoved()) head.discard();
			head = HeadEntities.THE_HEAD.create(level, EntitySpawnReason.COMMAND);
			Vec3 position = vector(action.getAsJsonArray("spawn"));
			head.snapTo(position.x, position.y, position.z, 0, 0);
			head.chase(player);
			route.clear();
			voices.clear();
			pursuing = false;
			expires = level.getGameTime() + 3600;
			speakingUntil = 0;
			dashing = false;
			raid = false;
			facing = null;
			followDrift = 0;
			((HeadActorAccess) head).phase(HeadPhase.CHARGE);
			level.addFreshEntity(head);
		}
		if (head == null || head.isRemoved()) throw new IllegalStateException("recording head is absent");
		if (action.has("place")) {
			Vec3 p = vector(action.getAsJsonArray("place"));
			head.snapTo(p.x,p.y,p.z,head.getYRot(),head.getXRot());
		}
		if (action.has("speed")) speed = action.get("speed").getAsDouble();
		if (action.has("followDrift")) {
			followDrift = action.get("followDrift").getAsDouble();
			driftOrigin = player.position();
		}
		if (action.has("pursue")) pursuing = action.get("pursue").getAsBoolean();
		if (action.has("dash")) {
			if (!dashing && action.get("dash").getAsBoolean()) {
				lastDash=-1;
				firstDash=true;
			}
			dashing = action.get("dash").getAsBoolean();
			((HeadActorAccess) head).showcaseDigging(dashing);
		}
		if (action.has("raid")) raid = action.get("raid").getAsBoolean();
		if (action.has("face")) facing = action.get("face").isJsonNull() ? null : vector(action.getAsJsonArray("face"));
		if (action.has("route")) {
			route.clear();
			for (var point : action.getAsJsonArray("route")) route.add(vector(point.getAsJsonArray()));
		}
		if (action.has("appendRoute")) for (var point : action.getAsJsonArray("appendRoute")) route.add(vector(point.getAsJsonArray()));
		if (action.has("voices")) for (var cue : action.getAsJsonArray("voices")) voices.add(HeadCue.valueOf(cue.getAsString()));
		if (action.has("breach")) {
			for (BlockPos pos : BlockPos.betweenClosed(76, 76, 33, 80, 79, 36)) level.destroyBlock(pos, true, head);
			blast(level, new Vec3(78.5, 77.5, 34.5));
		}
		if (action.has("loot")) {
			for (int x : new int[] {75,76,79,80}) for (int y : new int[] {70,72}) {
				level.destroyBlock(new BlockPos(x,y,35), true, head);
			}
			blast(level, new Vec3(77.5, 72, 35.5));
		}
		busy = !route.isEmpty() || !voices.isEmpty() || level.getGameTime() < speakingUntil;
		MarioHead.LOG.info("[showcase-actor] action={}", action);
	}

	private static Vec3 vector(JsonArray p) {
		return new Vec3(p.get(0).getAsDouble(),p.get(1).getAsDouble(),p.get(2).getAsDouble());
	}

	private static void blast(ServerLevel level, Vec3 p) {
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,p.x,p.y,p.z,1,0,0,0,0);
		level.playSound(null,p.x,p.y,p.z,SoundEvents.GENERIC_EXPLODE.value(),SoundSource.BLOCKS,1,.85f);
	}

	public static boolean tick(TheHead entity, ServerLevel level) {
		if (entity != head) return false;
		var player = head.chased();
		if (player == null || !player.isAlive() || level.getGameTime() > expires) {
			head.discard();
			busy = false;
			status = "ended";
			return true;
		}
		HeadActorAccess access = (HeadActorAccess) head;
		long now = level.getGameTime();
		if (!voices.isEmpty() && access.speech().canStart(voices.peek(), now)) {
			HeadCue cue = voices.remove();
			access.cue(cue);
			cueBegan = now;
			speakingUntil = now + (cue.clip().millis() + 49) / 50 + 10;
		}
		if (dashing && voices.isEmpty() && now>=speakingUntil && access.showcaseDigDash(now)) {
			lastDash=now;
			if (firstDash) windupUntil=now+3;
			firstDash=false;
		}
		Vec3 from = head.centre();
		lookPosition = from;
		float yaw = (float) Math.toDegrees(Math.atan2(player.getZ()-head.getZ(),player.getX()-head.getX()))-90;
		if (facing == null || head.cue()==HeadCue.HOME_HUNGRY && now-cueBegan<33) access.face(yaw, player);
		else access.showcaseFaceChest(facing);
		Vec3 goal = route.peek();
		if (goal != null && followDrift != 0) goal = goal.add(player.position().subtract(driftOrigin).multiply(followDrift,0,followDrift));
		if (goal == null && pursuing) goal = player.position().add(0,.05,0);
		Vec3 delta = goal == null ? Vec3.ZERO : goal.subtract(head.position());
		double length = delta.length();
		double pace = dashing ? (lastDash<0 || now<windupUntil ? 0 : speed*(.45+.95*Math.exp(-(now-lastDash)/9.0))) : speed;
		if (length > 0) delta = delta.scale(Math.min(pace,length)/length);
		if (raid && head.getZ()<38.1) {
			for (int x : new int[] {75,76,79,80}) for (int y : new int[] {70,72}) level.destroyBlock(new BlockPos(x,y,35), true, head);
			blast(level,new Vec3(77.5,72,35.5));
			raid=false;
		}
		access.move(delta.x,delta.y,delta.z);
		if (!route.isEmpty() && head.position().distanceTo(goal) < .06) route.remove();
		if (access.showcaseTouching(player, from)) access.showcaseCaught(level, player);
		busy = !route.isEmpty() || !voices.isEmpty() || now < speakingUntil;
		status = String.format(java.util.Locale.ROOT,"cue=%s route=%d pos=%.2f,%.2f,%.2f",head.cue(),route.size(),head.getX(),head.getY(),head.getZ());
		return true;
	}
}
