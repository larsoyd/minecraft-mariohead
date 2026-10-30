package com.larsoyd.mariohead.showcase.mixin;

import com.larsoyd.mariohead.showcase.Photo;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public abstract class PhotoFogMixin {
	@Inject(method = "setupFog", at = @At("RETURN"))
	private void photograph(CallbackInfoReturnable<FogData> cir) {
		if (!Photo.enabled) return;
		var fog = cir.getReturnValue();
		fog.environmentalStart = fog.renderDistanceStart = Photo.fogStart;
		fog.environmentalEnd = fog.renderDistanceEnd = fog.skyEnd = fog.cloudEnd = Photo.fogEnd;
		fog.color.set(.22f, .26f, .28f, 1);
	}
}
