package com.nanobuntv.retro64plus.enemies;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Holds nothing: it's there so the client can draw the post's ROM model ({@code client/ChompPostRenderer}). */
public class ChompPostBlockEntity extends BlockEntity {
    public ChompPostBlockEntity(BlockPos pos, BlockState state) {
        super(Enemies.CHOMP_POST_ENTITY.get(), pos, state);
    }
}
