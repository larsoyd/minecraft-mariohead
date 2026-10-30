package com.larsoyd.mariohead.showcase.mixin;

import com.larsoyd.mariohead.entity.TheHead;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class FloatingSoundMixin {
	@Inject(method="playStepSound",at=@At("HEAD"),cancellable=true)
	private void floating(BlockPos pos, BlockState state, CallbackInfo ci) {
		if ((Object)this instanceof TheHead) ci.cancel();
	}
}
