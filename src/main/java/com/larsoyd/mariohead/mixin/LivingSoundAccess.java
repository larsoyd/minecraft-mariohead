package com.larsoyd.mariohead.mixin;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingSoundAccess {
	@Invoker("getDeathSound")
	@Nullable SoundEvent mariohead$deathSound();
}
