package com.larsoyd.mariohead.mixin;

import com.larsoyd.mariohead.entity.HeadDread;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// a mob that ticks after him would pick its target straight back up and turn around
@Mixin(Mob.class)
abstract class MobTargetMixin {
	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void mariohead$noNewTarget(@Nullable LivingEntity target, CallbackInfo ci) {
		if (target != null && HeadDread.near((Mob) (Object) this)) ci.cancel();
	}
}
