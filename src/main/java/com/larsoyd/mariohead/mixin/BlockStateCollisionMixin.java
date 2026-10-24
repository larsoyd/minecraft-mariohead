package com.larsoyd.mariohead.mixin;

import com.larsoyd.mariohead.entity.TheHead;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// leaves are nothing to him, for moving, fitting and every line he checks
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class BlockStateCollisionMixin {
	@Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
		at = @At("HEAD"), cancellable = true)
	private void mariohead$throughLeaves(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
		if (context instanceof EntityCollisionContext entity && entity.getEntity() instanceof TheHead
			&& ((BlockBehaviour.BlockStateBase) (Object) this).is(BlockTags.LEAVES)) {
			cir.setReturnValue(Shapes.empty());
		}
	}
}
