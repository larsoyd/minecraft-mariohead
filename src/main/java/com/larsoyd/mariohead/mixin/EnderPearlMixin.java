package com.larsoyd.mariohead.mixin;

import com.larsoyd.mariohead.entity.TheHead;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ThrownEnderpearl.class)
abstract class EnderPearlMixin {
	@Unique
	private @Nullable Vec3 mariohead$before;

	@Inject(method = "onHit", at = @At("HEAD"))
	private void mariohead$beforeTeleport(HitResult hit, CallbackInfo ci) {
		var pearl = (ThrownEnderpearl) (Object) this;
		this.mariohead$before = pearl.getOwner() instanceof ServerPlayer player ? player.position() : null;
	}

	@Inject(method = "onHit", at = @At("RETURN"))
	private void mariohead$afterTeleport(HitResult hit, CallbackInfo ci) {
		var pearl = (ThrownEnderpearl) (Object) this;
		if (this.mariohead$before != null && pearl.getOwner() instanceof ServerPlayer player
			&& player.level() == pearl.level() && player.position().distanceToSqr(this.mariohead$before) > 1.0) {
			for (var entity : player.level().getAllEntities()) {
				if (entity instanceof TheHead head) {
					head.pearlTeleported(player, this.mariohead$before);
				}
			}
		}
	}
}
