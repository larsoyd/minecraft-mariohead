package com.larsoyd.mariohead.mixin;

import com.larsoyd.mariohead.entity.HeadDread;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// this goal sets its random heading every tick, so near him it would undo the flight
@Mixin(targets = "net.minecraft.world.entity.monster.cubemob.AbstractCubeMob$CubeMobRandomDirectionGoal")
abstract class CubeDirectionMixin {
	@Shadow
	@Final
	private AbstractCubeMob cubeMob;

	@Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
	private void mariohead$noWandering(CallbackInfoReturnable<Boolean> cir) {
		if (HeadDread.near(this.cubeMob)) cir.setReturnValue(false);
	}
}
