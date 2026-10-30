package com.larsoyd.mariohead.showcase.mixin;

import com.larsoyd.mariohead.showcase.Showcase;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseMotionMixin {
	@Shadow private double accumulatedDX;
	@Shadow private double accumulatedDY;

	@Inject(method = "handleAccumulatedMovement", at = @At("HEAD"))
	private void input(CallbackInfo ci) {
		double[] delta = Showcase.frame();
		this.accumulatedDX += delta[0];
		this.accumulatedDY += delta[1];
	}
}
