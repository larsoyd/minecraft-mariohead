package com.larsoyd.mariohead.showcase.mixin;

import com.larsoyd.mariohead.showcase.Photo;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class PhotoCameraMixin {
	@Shadow protected abstract void setPosition(double x, double y, double z);
	@Shadow protected abstract void setRotation(float yaw, float pitch);

	@Inject(method = "alignWithEntity", at = @At("RETURN"))
	private void photograph(float partialTicks, CallbackInfo ci) {
		if (!Photo.enabled) return;
		setPosition(Photo.x, Photo.y, Photo.z);
		setRotation(Photo.yaw, Photo.pitch);
	}
}
