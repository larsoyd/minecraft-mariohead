package com.larsoyd.mariohead.showcase;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.larsoyd.mariohead.MarioHead;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.sdl.SDLMouse;

public final class Showcase implements ClientModInitializer {
	private static final double[] ZERO = {0, 0};
	private static JsonArray track;
	private static JsonObject step;
	private static int index;
	private static long began;
	private static long deadline;
	private static double yaw;
	private static double pitch;
	private static double yawRate, pitchRate;
	private static double cursorX;
	private static double cursorY;
	private static boolean active;
	private static int waypoint;
	private static boolean arrived;
	private static long lastFrame;
	private static Vec3 pathOrigin;
	private static Vec3 watched;
	private static double nextGlance;
	private static volatile String status = "idle";

	@Override
	public void onInitializeClient() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, selection) -> dispatcher.register(
			Commands.literal("showcase").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("load").then(Commands.argument("take", StringArgumentType.word()).executes(c -> {
					String name = StringArgumentType.getString(c, "take");
					Minecraft.getInstance().execute(() -> load(name));
					return 1;
				})))
				.then(Commands.literal("play").executes(c -> {
					Minecraft.getInstance().execute(Showcase::play);
					return 1;
				}))
				.then(Commands.literal("abort").executes(c -> {
					Minecraft.getInstance().execute(() -> stop("aborted"));
					return 1;
				}))
				.then(Commands.literal("status").executes(c -> {
					c.getSource().sendSuccess(() -> Component.literal(status + " actor=" + Actor.status), false);
					return active ? 1 : 0;
				}))
				.then(Commands.literal("save").executes(c -> {
					c.getSource().getServer().saveEverything(true, true, true);
					return 1;
				}))
				.then(Commands.literal("photo").then(Commands.argument("settings", StringArgumentType.greedyString()).executes(c -> {
					var settings = JsonParser.parseString(StringArgumentType.getString(c, "settings")).getAsJsonObject();
					Minecraft.getInstance().execute(() -> Photo.apply(settings));
					return 1;
				})))
				.then(Commands.literal("actor").then(Commands.argument("action", StringArgumentType.greedyString()).executes(c -> {
					Actor.apply(c.getSource().getServer(), JsonParser.parseString(StringArgumentType.getString(c,"action")).getAsJsonObject());
					return 1;
				})))));
	}

	private static void load(String name) {
		stop("loading");
		try {
			if (!name.matches("[a-zA-Z0-9_-]+")) throw new IllegalArgumentException("invalid take name");
			track = JsonParser.parseString(Files.readString(Path.of("showcase", "takes", name + ".json"))).getAsJsonArray();
			if (track.isEmpty()) throw new IllegalArgumentException("empty take");
			status = "loaded " + name + " steps=" + track.size();
		} catch (Exception e) {
			track = null;
			status = "failed " + e.getMessage();
		}
		MarioHead.LOG.info("[showcase] {}", status);
	}

	private static void play() {
		Minecraft mc = Minecraft.getInstance();
		if (track == null || mc.player == null || mc.getSingleplayerServer() == null) {
			stop("refused missing track or singleplayer world");
			return;
		}
		index = -1;
		yawRate = pitchRate = 0;
		mc.gui.toastManager().clear();
		active = true;
		deadline = System.nanoTime() + 180_000_000_000L;
		next(mc);
	}

	private static double number(String key, double fallback) {
		return step.has(key) ? step.get(key).getAsDouble() : fallback;
	}

	private static void next(Minecraft mc) {
		if (++index >= track.size()) {
			stop("complete");
			return;
		}
		step = track.get(index).getAsJsonObject();
		waypoint = 0;
		watched = null;
		nextGlance = 0;
		pathOrigin = mc.player.position();
		arrived = !step.has("path");
		if (step.has("requireCrafting") && !(mc.player.containerMenu instanceof CraftingMenu)) {
			stop("invalid take: crafting menu must conceal spawn");
			return;
		}
		if (step.has("expectPos")) {
			var p = step.getAsJsonArray("expectPos");
			double distance = mc.player.position().distanceTo(new net.minecraft.world.phys.Vec3(
				p.get(0).getAsDouble(), p.get(1).getAsDouble(), p.get(2).getAsDouble()));
			if (distance > number("tolerance", 1)) {
				stop("invalid take: route checkpoint " + index + " missed by " + distance);
				return;
			}
		}
		if (step.has("slot") && !(mc.player.containerMenu instanceof ChestMenu chest && chest.getRowCount() == 6)) {
			stop("invalid take: double chest is not open");
			return;
		}
		began = System.nanoTime();
		lastFrame = began;
		yaw = mc.player.getYRot();
		pitch = mc.player.getXRot();
		cursorX = mc.mouseHandler.xpos();
		cursorY = mc.mouseHandler.ypos();
		String keys = step.has("keys") ? step.get("keys").getAsString() : "";
		mc.options.keyUp.setDown(keys.contains("w"));
		mc.options.keyDown.setDown(keys.contains("s"));
		mc.options.keyLeft.setDown(keys.contains("a"));
		mc.options.keyRight.setDown(keys.contains("d"));
		mc.options.keySprint.setDown(keys.contains("!"));
		mc.options.keyJump.setDown(keys.contains("j"));
		mc.options.keyShift.setDown(keys.contains("c"));
		mc.options.keyUse.setDown(keys.contains("u"));
		if (step.has("perspective")) KeyMapping.click(mc.options.keyTogglePerspective.getDefaultKey());
		if (step.has("close")) mc.player.closeContainer();
		if (step.has("slot")) mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId,
			step.get("slot").getAsInt(), 0, ContainerInput.PICKUP, mc.player);
		if (step.has("command")) {
			String command = step.get("command").getAsString();
			var server = mc.getSingleplayerServer();
			server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
		}
		if (step.has("actor")) {
			var action = step.getAsJsonObject("actor");
			var server = mc.getSingleplayerServer();
			server.execute(() -> Actor.apply(server, action));
		}
		MarioHead.LOG.info(String.format("[showcase] step=%d pos=%.3f,%.3f,%.3f yaw=%.2f pitch=%.2f", index,
			mc.player.getX(), mc.player.getY(), mc.player.getZ(), yaw, pitch));
	}

	public static double[] frame() {
		if (!active) return ZERO;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !mc.player.isAlive() || System.nanoTime() > deadline) {
			stop("ended player unavailable or deadline");
			return ZERO;
		}
		try {
			double elapsed = (System.nanoTime() - began) / 1e9;
			if (elapsed > number("timeout", 18)) throw new IllegalStateException("step deadline " + index);
			boolean waitingHeight = step.has("untilActorBelow") && (Actor.lookPosition == null || Actor.lookPosition.y > number("untilActorBelow",0));
			if (elapsed >= number("seconds", 1) && arrived && !waitingHeight && !(step.has("waitActor") && Actor.busy)) {
				next(mc);
				if (!active) return ZERO;
				elapsed = 0;
			}
			status = String.format(java.util.Locale.ROOT, "playing step=%d elapsed=%.2f pos=%.3f,%.3f,%.3f health=%.1f", index,
				elapsed, mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getHealth());
			double t = elapsed / Math.max(.01, number("turn", number("seconds", 1)));
			if (mc.gui.screen() != null) {
				double p = MotionCurve.progress(t);
				double x = cursorX + (number("cursorX", cursorX) - cursorX) * p;
				double y = cursorY + (number("cursorY", cursorY) - cursorY) * p;
				SDLMouse.SDL_WarpMouseInWindow(mc.getWindow().handle(), (float) x, (float) y);
				mc.mouseHandler.onMove(mc.getWindow().handle(), x, y, 0, 0);
				return ZERO;
			}
			double targetYaw = MotionCurve.angle(yaw, number("yaw", yaw), t);
			double targetPitch = pitch + (number("pitch", pitch) - pitch) * MotionCurve.progress(t);
			double dt = (System.nanoTime()-lastFrame)/1e9;
			lastFrame = System.nanoTime();
			Vec3 aim = null;
			if (step.has("path")) {
				var path = step.getAsJsonArray("path");
				Vec3 goal = point(path.get(waypoint).getAsJsonArray());
				Vec3 delta = goal.subtract(mc.player.position());
				if (delta.horizontalDistance() < number("reach", .35) && Math.abs(delta.y)<1.2) {
					if (waypoint+1 < path.size()) goal = point(path.get(++waypoint).getAsJsonArray());
					else arrived = true;
				}
				delta = goal.subtract(mc.player.position());
				double heading = Math.toDegrees(Math.atan2(-delta.x,delta.z));
				int direction = Walking.direction(mc.player.getYRot(), heading);
				mc.options.keyUp.setDown(!arrived && (direction==0 || direction==1 || direction==7));
				mc.options.keyDown.setDown(!arrived && direction>=3 && direction<=5);
				mc.options.keyLeft.setDown(!arrived && direction>=5);
				mc.options.keyRight.setDown(!arrived && direction>=1 && direction<=3);
				if (!step.has("yaw")) {
					Vec3 ahead = goal;
					if (waypoint+1<path.size()) {
						double blend = .6*Math.clamp(1-delta.horizontalDistance()/number("anticipation",2.8),0,1);
						ahead = goal.lerp(point(path.get(waypoint+1).getAsJsonArray()),blend);
					}
					if (waypoint+1==path.size()) {
						Vec3 previous = waypoint>0 ? point(path.get(waypoint-1).getAsJsonArray()) : pathOrigin;
						Vec3 travelDirection = goal.subtract(previous).multiply(1,0,1).normalize();
						ahead = goal.add(travelDirection.scale(2.5));
					}
					aim = ahead.add(0,1.3,0);
				}
			}
			if (step.has("lookAt")) aim = point(step.getAsJsonArray("lookAt"));
			if (step.has("watchActor") && Actor.lookPosition != null) {
				if (watched == null || elapsed >= nextGlance) {
					watched = Actor.lookPosition;
					nextGlance = elapsed+number("glanceInterval",0);
				}
				aim = watched;
				if (step.has("gazeOffset")) aim = aim.add(point(step.getAsJsonArray("gazeOffset")));
			}
			if (aim != null) {
				Vec3 gaze = aim.subtract(mc.player.getEyePosition());
				targetYaw = Math.toDegrees(Math.atan2(-gaze.x,gaze.z));
				targetPitch = -Math.toDegrees(Math.atan2(gaze.y,gaze.horizontalDistance()));
				if (!step.has("watchActor") && !step.has("lookAt")) targetPitch = number("pitch", Math.clamp(targetPitch,-12,24));
			}
			if (step.has("path") || step.has("watchActor") || step.has("lookAt")) {
				double hand = number("hand", .4);
				targetYaw += hand*(Math.sin(elapsed*7.1+index)+.36*Math.sin(elapsed*15.3));
				targetPitch += hand*.65*Math.sin(elapsed*9.2+index*.4);
				yawRate = Walking.rate(yawRate,mc.player.getYRot(),targetYaw,dt,number("response",8),number("turnSpeed",180),number("acceleration",650));
				pitchRate = Walking.rate(pitchRate,mc.player.getXRot(),targetPitch,dt,number("response",8),number("turnSpeed",180),number("acceleration",650));
				targetYaw = mc.player.getYRot()+yawRate*Math.clamp(dt,0,.05);
				targetPitch = mc.player.getXRot()+pitchRate*Math.clamp(dt,0,.05);
			}
			double sensitivity = mc.options.sensitivity().get() * .6 + .2;
			double scale = sensitivity * sensitivity * sensitivity * 8 * .15;
			double dx = ((targetYaw - mc.player.getYRot()) % 360 + 540) % 360 - 180;
			return new double[] {dx / scale, (targetPitch - mc.player.getXRot()) / scale};
		} catch (Exception e) {
			stop("failed " + e);
			return ZERO;
		}
	}

	private static Vec3 point(JsonArray p) {
		return new Vec3(p.get(0).getAsDouble(),p.get(1).getAsDouble(),p.get(2).getAsDouble());
	}

	private static void stop(String reason) {
		active = false;
		Minecraft mc = Minecraft.getInstance();
		for (KeyMapping key : new KeyMapping[] {mc.options.keyUp, mc.options.keyDown, mc.options.keyLeft,
			mc.options.keyRight, mc.options.keySprint, mc.options.keyJump, mc.options.keyShift, mc.options.keyUse}) key.setDown(false);
		status = reason;
		MarioHead.LOG.info("[showcase] {}", status);
	}
}
