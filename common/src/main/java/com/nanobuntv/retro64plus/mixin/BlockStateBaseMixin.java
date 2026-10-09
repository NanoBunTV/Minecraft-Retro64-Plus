package com.nanobuntv.retro64plus.mixin;

import com.nanobuntv.retro64plus.enemies.ChainChompEntity;
import com.nanobuntv.retro64plus.enemies.ChompPostBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Chain Chomp passes through its post and chests (the user's request, M4 checkpoint 4): its yard's chest stands
 * within reach of the chain and a 2.6-block ball would snag on either. Only for Chain Chomps; everything else collides
 * as usual.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class BlockStateBaseMixin {
    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("HEAD"), cancellable = true)
    private void retro64plus$chompPassesThrough(BlockGetter level, BlockPos pos, CollisionContext context,
            CallbackInfoReturnable<VoxelShape> cir) {
        if (context instanceof EntityCollisionContext entityContext
                && entityContext.getEntity() instanceof ChainChompEntity) {
            var block = ((BlockBehaviour.BlockStateBase) (Object) this).getBlock();
            if (block instanceof ChompPostBlock || block instanceof ChestBlock) cir.setReturnValue(Shapes.empty());
        }
    }
}
