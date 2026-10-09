package com.nanobuntv.retro64plus.shop;

import com.mojang.serialization.MapCodec;
import com.nanobuntv.retro64plus.server.ServerShop;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Coin Shop (D58): a market stall of our own design that opens the shop's offers (D68) when used. Faces the player
 * who placed it.
 */
public final class CoinShopBlock extends HorizontalDirectionalBlock {
    //? if <26.3 {
    public static final MapCodec<CoinShopBlock> CODEC = simpleCodec(CoinShopBlock::new);
    //?}

    public CoinShopBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    //? if <26.3 {
    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }
    //?}

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) ServerShop.open(serverPlayer, pos);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
