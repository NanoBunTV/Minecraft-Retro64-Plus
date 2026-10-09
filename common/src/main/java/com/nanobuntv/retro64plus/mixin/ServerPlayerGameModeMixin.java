package com.nanobuntv.retro64plus.mixin;

import com.nanobuntv.retro64plus.server.ServerCoins;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A coin now and then from grass, leaves and ores Mario breaks (D52). */
@Mixin(ServerPlayerGameMode.class)
abstract class ServerPlayerGameModeMixin {
    @Shadow
    protected ServerLevel level;
    @Shadow
    @Final
    protected ServerPlayer player;

    /** The block being broken, read before it's gone. */
    @Unique
    private BlockState retro64plus$breaking;

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void retro64plus$before(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        retro64plus$breaking = level.getBlockState(pos);
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void retro64plus$after(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockState state = retro64plus$breaking;
        retro64plus$breaking = null;
        if (cir.getReturnValueZ() && state != null) ServerCoins.onBlockBroken(player, state, pos);
    }
}
