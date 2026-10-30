package com.larsoyd.mariohead.mixin;

import com.larsoyd.mariohead.entity.HeadDread;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// clearing targets from his tick leaves a gap a mob beside the player can still swing in
@Mixin(Mob.class)
abstract class MobDreadMixin {
	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void mariohead$noNewTarget(@Nullable LivingEntity target, CallbackInfo ci) {
		if (target != null && HeadDread.near((Mob) (Object) this)) ci.cancel();
	}

	@Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
	private void mariohead$noHit(ServerLevel level, Entity target, CallbackInfoReturnable<Boolean> cir) {
		if (HeadDread.near((Mob) (Object) this)) cir.setReturnValue(false);
	}
}
