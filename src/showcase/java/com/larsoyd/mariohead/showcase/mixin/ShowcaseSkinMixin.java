package com.larsoyd.mariohead.showcase.mixin;

import java.util.UUID;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.ClientAsset.ResourceTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DefaultPlayerSkin.class)
public abstract class ShowcaseSkinMixin {
	@Inject(method = "get(Ljava/util/UUID;)Lnet/minecraft/world/entity/player/PlayerSkin;", at = @At("HEAD"), cancellable = true)
	private static void steve(UUID profile, CallbackInfoReturnable<PlayerSkin> cir) {
		cir.setReturnValue(new PlayerSkin(new ResourceTexture(Identifier.withDefaultNamespace("entity/player/wide/steve")),
			null, null, PlayerModelType.WIDE, true));
	}
}
