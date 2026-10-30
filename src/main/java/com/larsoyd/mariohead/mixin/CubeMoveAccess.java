package com.larsoyd.mariohead.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.world.entity.monster.cubemob.AbstractCubeMob$CubeMobMoveControl")
public interface CubeMoveAccess {
	@Invoker("setDirection")
	void mariohead$setDirection(float yaw, boolean aggressive);

	@Invoker("setWantedMovement")
	void mariohead$setWantedMovement(double speed);
}
