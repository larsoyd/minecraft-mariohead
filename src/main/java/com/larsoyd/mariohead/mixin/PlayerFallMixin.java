package com.larsoyd.mariohead.mixin;

import com.larsoyd.mariohead.entity.TheHead;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Player.class)
abstract class PlayerFallMixin {
	@WrapMethod(method = "causeFallDamage")
	private boolean mariohead$afterFall(double distance, float multiplier, DamageSource source, Operation<Boolean> original) {
		Player player = (Player) (Object) this;
		float before = player.getHealth();
		boolean result = original.call(distance, multiplier, source);
		if (distance >= 8.0 && player instanceof ServerPlayer serverPlayer && player.isAlive() && player.getHealth() < before) {
			for (var entity : serverPlayer.level().getAllEntities()) {
				if (entity instanceof TheHead head) head.playerFell(serverPlayer);
			}
		}
		return result;
	}
}
