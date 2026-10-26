package com.larsoyd.mariohead.playtest;

import com.larsoyd.mariohead.entity.TheHead;
import com.larsoyd.mariohead.MarioHead;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PlayerBot implements ClientModInitializer {
	private enum Mode { STOP, HOLD, FLEE, APPROACH, GOTO }
	private Mode mode = Mode.STOP;
	private long deadline;
	private int ticks;
	private int stuck;
	private Vec3 previous = Vec3.ZERO;
	private Vec3 rememberedThreat;
	private Vec3 destination;
	private double heading;
	private final List<Vec3> detour = new ArrayList<>();
	private int planAfter;
	private volatile String status = "mode=STOP";

	@Override
	public void onInitializeClient() {
		ClientTickEvents.START_CLIENT_TICK.register(this::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> this.stop(client, "disconnect"));
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, selection) -> {
			var root = Commands.literal("headbot")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("status").executes(c -> {
					String snapshot = this.status;
					c.getSource().sendSuccess(() -> Component.literal(snapshot), false);
					return 1;
				}))
				.then(Commands.literal("stop").executes(c -> {
					Minecraft.getInstance().execute(() -> this.stop(Minecraft.getInstance(), "command"));
					return 1;
				}));
			for (Mode choice : List.of(Mode.FLEE, Mode.APPROACH, Mode.HOLD)) {
				root.then(Commands.literal(choice.name().toLowerCase(Locale.ROOT))
					.then(Commands.argument("seconds", IntegerArgumentType.integer(1, 1200)).executes(c -> {
						int seconds = IntegerArgumentType.getInteger(c, "seconds");
						Minecraft.getInstance().execute(() -> this.start(choice, seconds, null));
						return 1;
					})));
			}
			root.then(Commands.literal("goto").then(Commands.argument("position", Vec3Argument.vec3())
				.then(Commands.argument("seconds", IntegerArgumentType.integer(1, 1200)).executes(c -> {
					Vec3 point = Vec3Argument.getVec3(c, "position");
					int seconds = IntegerArgumentType.getInteger(c, "seconds");
					Minecraft.getInstance().execute(() -> this.start(Mode.GOTO, seconds, point));
					return 1;
				}))));
			dispatcher.register(root);
		});
	}

	private void start(Mode choice, int seconds, Vec3 point) {
		Minecraft client = Minecraft.getInstance();
		this.stop(client, "new command");
		if (client.player == null || client.getSingleplayerServer() == null || !client.player.isAlive()
			|| client.player.isCreative() || client.player.isSpectator()) {
			this.status = "mode=STOP reason=requires_living_singleplayer_survival_player";
			return;
		}
		this.mode = choice;
		this.deadline = System.nanoTime() + seconds * 1_000_000_000L;
		this.destination = point;
		this.previous = client.player.position();
		this.heading = client.player.getYRot();
		this.ticks = 0;
		this.stuck = 0;
		this.rememberedThreat = null;
		this.detour.clear();
		this.planAfter = 0;
		this.status = "mode=" + choice + " seconds=" + seconds;
	}

	private void tick(Minecraft client) {
		if (this.mode == Mode.STOP) return;
		if (client.player == null || client.level == null || !client.player.isAlive() || client.getSingleplayerServer() == null
			|| client.player.isCreative() || client.player.isSpectator() || System.nanoTime() >= this.deadline) {
			this.stop(client, "deadline_or_player_unavailable");
			return;
		}
		var player = client.player;
		Vec3 position = player.position();
		this.stuck = position.subtract(this.previous).horizontalDistance() < 0.03 ? this.stuck + 1 : 0;
		this.previous = position;
		this.ticks++;
		List<Vec3> threats = client.level.getEntitiesOfClass(TheHead.class, player.getBoundingBox().inflate(192))
			.stream().filter(TheHead::isAlive).map(TheHead::position).toList();
		Vec3 nearest = threats.stream().min(Comparator.comparingDouble(position::distanceToSqr)).orElse(null);
		if (nearest != null) this.rememberedThreat = nearest;
		if (threats.isEmpty() && this.rememberedThreat != null) threats = List.of(this.rememberedThreat);
		Vec3 goal = this.mode == Mode.GOTO ? this.destination : this.rememberedThreat;
		if (this.mode == Mode.HOLD || this.mode == Mode.APPROACH && goal == null) {
			this.keys(client, false, false);
		} else if (this.mode == Mode.GOTO && position.distanceTo(goal) < 1) {
			this.stop(client, "arrived");
			return;
		} else {
			double desired = goal == null ? this.heading : yaw(this.mode == Mode.FLEE ? position.subtract(goal) : goal.subtract(position));
			while (!this.detour.isEmpty() && position.subtract(this.detour.getFirst()).horizontalDistance() < 0.8) this.detour.removeFirst();
			EscapePolicy.Route route = this.route(client, desired, threats, goal);
			if (this.detour.isEmpty() && route.clearance() < 6 && this.ticks >= this.planAfter) {
				this.plan(client, threats, goal);
				this.planAfter = this.ticks + 20;
			}
			if (!this.detour.isEmpty()) {
				Vec3 waypoint = this.detour.getFirst();
				int skipped = 0;
				for (Vec3 point : this.detour) {
					double length = position.subtract(point).horizontalDistance();
					if (length > 5) break;
					if (this.route(client, yaw(point.subtract(position)), threats, goal).clearance() < length + 0.5) break;
					waypoint = point;
					skipped++;
				}
				if (skipped > 1) this.detour.subList(0, skipped - 1).clear();
				route = this.route(client, yaw(waypoint.subtract(position)), threats, goal);
				if (route.clearance() < 0.75 || this.stuck > 20) this.detour.clear();
			} else {
				List<EscapePolicy.Route> routes = new ArrayList<>();
				for (int i = 0; i < 24; i++) routes.add(this.route(client, desired + i * 15, threats, goal));
				route = EscapePolicy.choose(routes, this.heading);
			}
			if (route == null) {
				this.keys(client, false, player.isInWater());
			} else {
				this.heading = route.yaw();
				player.setYRot(Mth.approachDegrees(player.getYRot(), (float) this.heading, 35));
				player.setXRot(player.isInWater() ? -15 : 0);
				boolean facing = Math.abs(Mth.wrapDegrees(player.getYRot() - this.heading)) < 45;
				this.keys(client, facing, facing && (route.jump() || route.clearance() >= 4 || player.isInWater() || this.stuck > 10));
			}
		}
		this.status = String.format(Locale.ROOT, "mode=%s ticks=%d pos=%.3f,%.3f,%.3f gap=%.3f stuck=%d sprint=%s health=%.1f",
			this.mode, this.ticks, position.x, position.y, position.z,
			nearest == null ? -1 : position.distanceTo(nearest), this.stuck, player.isSprinting(), player.getHealth());
		if (this.ticks % 20 == 0) MarioHead.LOG.info("[headbot] {}", this.status);
	}

	private void plan(Minecraft client, List<Vec3> threats, Vec3 goal) {
		Vec3 origin = client.player.position();
		double ground = this.floor(client, origin.x, origin.y, origin.z);
		if (Double.isNaN(ground)) return;
		var start = new EscapePlanner.Point(0, ground, 0);
		var path = EscapePlanner.plan(start, (from, dx, dz) -> {
			double x = origin.x + from.x() + dx, z = origin.z + from.z() + dz;
			double floor = this.floor(client, x, from.y(), z);
			if (Double.isNaN(floor) || from.y() - floor > 2.5) return null;
			for (double step : new double[] {0.5, 1}) {
				double sx = x - dx * (1 - step), sz = z - dz * (1 - step);
				if (!this.safe(client, sx, Math.max(floor, from.y()), sz)) return null;
			}
			return new EscapePlanner.Point(from.x() + dx, floor, from.z() + dz);
		}, point -> {
			Vec3 at = new Vec3(origin.x + point.x(), point.y(), origin.z + point.z());
			if (this.mode == Mode.FLEE && !threats.isEmpty()) return threats.stream().mapToDouble(at::distanceTo).min().orElse(0) * 3;
			return goal == null ? at.subtract(origin).horizontalDistance() : -at.distanceTo(goal) * 3;
		});
		this.detour.clear();
		for (var point : path) this.detour.add(new Vec3(origin.x + point.x(), point.y(), origin.z + point.z()));
	}

	private boolean safe(Minecraft client, double x, double y, double z) {
		var box = new AABB(x - 0.3, y + 0.01, z - 0.3, x + 0.3, y + 1.8, z + 0.3);
		if (!client.level.noCollision(client.player, box)) return false;
		for (double dy : new double[] {-0.1, 0, 1}) {
			var block = client.level.getBlockState(BlockPos.containing(x, y + dy, z));
			if (block.getFluidState().is(FluidTags.LAVA) || block.is(Blocks.FIRE) || block.is(Blocks.MAGMA_BLOCK)
				|| block.is(Blocks.CACTUS) || block.is(Blocks.SWEET_BERRY_BUSH) || block.is(Blocks.CAMPFIRE)) return false;
		}
		return true;
	}

	private EscapePolicy.Route route(Minecraft client, double yaw, List<Vec3> threats, Vec3 goal) {
		var player = client.player;
		Vec3 start = player.position();
		Vec3 direction = new Vec3(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
		Vec3 end = start;
		double clearance = 0;
		double danger = 0;
		boolean jump = false;
		for (int step = 1; step <= 12; step++) {
			Vec3 next = start.add(direction.scale(step * 0.5));
			double floor = this.floor(client, next.x, end.y, next.z);
			if (Double.isNaN(floor) || end.y - floor > 2.5) break;
			next = new Vec3(next.x, floor, next.z);
			if (!this.safe(client, next.x, next.y, next.z)) break;
			var feet = client.level.getBlockState(BlockPos.containing(next));
			if (feet.getFluidState().is(FluidTags.WATER)) danger += 0.25;
			jump |= floor > end.y + 0.5;
			end = next;
			clearance = step * 0.5;
		}
		double gain;
		if (this.mode == Mode.FLEE && !threats.isEmpty()) {
			Vec3 finish = end;
			gain = threats.stream().mapToDouble(finish::distanceTo).min().orElse(0)
				- threats.stream().mapToDouble(start::distanceTo).min().orElse(0);
		} else if (goal != null) {
			gain = start.distanceTo(goal) - end.distanceTo(goal);
		} else {
			gain = clearance * Math.cos(Math.toRadians(yaw - this.heading));
		}
		return new EscapePolicy.Route(yaw, clearance, danger, gain, jump);
	}

	private double floor(Minecraft client, double x, double y, double z) {
		for (int dy = 1; dy >= -3; dy--) {
			BlockPos pos = BlockPos.containing(x, y + dy, z);
			var state = client.level.getBlockState(pos);
			var shape = state.getCollisionShape(client.level, pos);
			if (!shape.isEmpty()) {
				double top = pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
				if (top <= y + 1.25) return top;
			}
			if (state.getFluidState().is(FluidTags.WATER)) return pos.getY();
		}
		return Double.NaN;
	}

	private static double yaw(Vec3 direction) {
		return Math.toDegrees(Math.atan2(-direction.x, direction.z));
	}

	private void keys(Minecraft client, boolean forward, boolean jump) {
		client.options.keyUp.setDown(forward);
		client.options.keyDown.setDown(false);
		client.options.keyLeft.setDown(false);
		client.options.keyRight.setDown(false);
		client.options.keySprint.setDown(forward);
		client.options.keyJump.setDown(jump);
		client.options.keyShift.setDown(false);
	}

	private void stop(Minecraft client, String reason) {
		this.keys(client, false, false);
		this.mode = Mode.STOP;
		this.status = "mode=STOP reason=" + reason;
		MarioHead.LOG.info("[headbot] {}", this.status);
	}
}
