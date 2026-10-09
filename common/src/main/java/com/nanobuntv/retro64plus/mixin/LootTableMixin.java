package com.nanobuntv.retro64plus.mixin;

import com.nanobuntv.retro64plus.server.ServerCoins;
import net.minecraft.world.Container;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Coins burst out of a structure chest when a player's opening fills it (D53). Hooked on the loot table itself, not
 * the chest, so Lootr's per-player containers (filled the same way, once per player) work without depending on it.
 */
@Mixin(LootTable.class)
abstract class LootTableMixin {
    @Inject(method = "fill", at = @At("HEAD"))
    private void retro64plus$chestCoins(Container container, LootParams params, long seed, CallbackInfo ci) {
        ServerCoins.onLootFill(params);
    }
}
