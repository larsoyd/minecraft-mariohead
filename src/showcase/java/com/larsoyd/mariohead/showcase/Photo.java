package com.larsoyd.mariohead.showcase;

import com.google.gson.JsonObject;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

public final class Photo {
	public static boolean enabled;
	public static double x, y, z;
	public static float yaw, pitch, fogStart = 5, fogEnd = 18;

	private Photo() {}

	public static void apply(JsonObject settings) {
		var mc = Minecraft.getInstance();
		enabled = settings.has("position");
		if (mc.gui.hud.isHidden() != enabled) mc.gui.hud.toggle();
		mc.options.setCameraType(enabled && settings.has("shoulder") ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON);
		if (!enabled) return;
		var p = settings.getAsJsonArray("position");
		x = p.get(0).getAsDouble(); y = p.get(1).getAsDouble(); z = p.get(2).getAsDouble();
		yaw = settings.get("yaw").getAsFloat(); pitch = settings.get("pitch").getAsFloat();
		fogStart = settings.has("fogStart") ? settings.get("fogStart").getAsFloat() : 5;
		fogEnd = settings.has("fogEnd") ? settings.get("fogEnd").getAsFloat() : 18;
	}
}
