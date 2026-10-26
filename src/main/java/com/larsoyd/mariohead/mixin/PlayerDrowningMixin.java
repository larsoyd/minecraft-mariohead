package com.larsoyd.mariohead.mixin;

import com.larsoyd.mariohead.entity.TheHead;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerPlayer.class)
abstract class PlayerDrowningMixin {
	@WrapMethod(method = "hurtServer")
	private boolean mariohead$afterDrowning(ServerLevel level, DamageSource source, float damage, Operation<Boolean> original) {
		ServerPlayer player = (ServerPlayer) (Object) this;
		float before = player.getHealth() + player.getAbsorptionAmount();
		boolean result = original.call(level, source, damage);
		if (source.is(DamageTypes.DROWN) && player.isAlive() && player.getHealth() + player.getAbsorptionAmount() < before) {
			for (var entity : level.getAllEntities()) {
				if (entity instanceof TheHead head) head.playerDrowned(player);
			}
		}
		return result;
	}
}
