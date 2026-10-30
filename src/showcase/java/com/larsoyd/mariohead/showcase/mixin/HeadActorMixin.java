package com.larsoyd.mariohead.showcase.mixin;

import com.larsoyd.mariohead.entity.TheHead;
import com.larsoyd.mariohead.showcase.Actor;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TheHead.class)
public class HeadActorMixin {
	@Inject(method = "customServerAiStep", at = @At("HEAD"), cancellable = true)
	private void perform(ServerLevel level, CallbackInfo ci) {
		if (Actor.tick((TheHead) (Object) this, level)) ci.cancel();
	}
}
