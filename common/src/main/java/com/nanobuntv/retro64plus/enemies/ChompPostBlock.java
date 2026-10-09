package com.nanobuntv.retro64plus.enemies;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import org.jetbrains.annotations.Nullable;

/**
 * The Chain Chomp's wooden post (D78, D94, D99): SM64's model (drawn from the ROM by the client's block entity
 * renderer), half SM64's object size as in Bob-omb Battlefield, about 1.9 blocks tall. Mario lands on it (its shape is
 * in his collision) and each ground pound drives it in by a third (SM64's 190 units in all); the third frees the
 * Chomp. Only Mario frees it (D99): the post can't be broken in survival and shrugs off blasts.
 */
public final class ChompPostBlock extends BaseEntityBlock {
    //? if <26.3 {
    public static final MapCodec<ChompPostBlock> CODEC = simpleCodec(ChompPostBlock::new);
    //?}
    public static final int MAX_POUNDS = 3;
    public static final IntegerProperty POUNDS = IntegerProperty.create("pounds", 0, MAX_POUNDS);
    /** SM64 units: the post's top above the ground (half of the model's 410), and how far the three pounds sink it. */
    public static final double TOP_UNITS = 205, SINK_UNITS = 190;
    private static final VoxelShape[] SHAPES = new VoxelShape[MAX_POUNDS + 1];
    /** A post pounded all the way in goes this long after (the playtest: it shouldn't stay in the ground forever). */
    static final int REMOVE_DELAY_TICKS = 60;

    static {
        for (int p = 0; p <= MAX_POUNDS; p++) {
            SHAPES[p] = Shapes.create(new AABB(0.27, 0, 0.27, 0.73, top(p), 0.73));
        }
    }

    public ChompPostBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POUNDS, 0));
    }

    /** The post's top above its block's bottom, in blocks, after {@code pounds} pounds. */
    public static double top(int pounds) {
        return (TOP_UNITS - SINK_UNITS * pounds / MAX_POUNDS) / 110;
    }

    //? if <26.3 {
    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
    //?}

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POUNDS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(POUNDS)];
    }

    /** Pounded all the way in, or its Chomp killed, a little while ago: gone in a puff. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (state.getValue(POUNDS) < MAX_POUNDS && !level.getEntitiesOfClass(ChainChompEntity.class,
                new AABB(pos).inflate(ChainChompEntity.LUNGE_CHAIN + 2),
                c -> c.isAlive() && c.post().filter(pos::equals).isPresent()).isEmpty()) {
            return; // a Chomp is still tied to it
        }
        level.removeBlock(pos, false);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.2,
                pos.getZ() + 0.5, 12, 0.3, 0.1, 0.3, 0.02);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChompPostBlockEntity(pos, state);
    }

    /**
     * A Mario ground pound landed at {@code at} (ServerGroundPound): if that's the top of a post (in the block below his
     * feet, or the one below that: the post stands taller than a block), it sinks a third. True if it was a post. Player
     * rules apply as for the soft blocks (spawn protection, adventure mode, claim mods through mayInteract).
     */
    public static boolean onGroundPound(ServerLevel level, net.minecraft.server.level.ServerPlayer player, Vec3 at) {
        BlockPos pos = BlockPos.containing(at.x, at.y - 0.01, at.z);
        for (int i = 0; i < 2; i++, pos = pos.below()) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof ChompPostBlock)) continue;
            int pounds = state.getValue(POUNDS);
            if (pounds >= MAX_POUNDS || Math.abs(pos.getY() + top(pounds) - at.y) > 0.5) return false;
            if (!level.mayInteract(player, pos)
                    || player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())) {
                return true; // a post all the same: no shockwave through the floor below
            }
            level.setBlock(pos, state.setValue(POUNDS, pounds + 1), 3);
            if (pounds + 1 >= MAX_POUNDS) level.scheduleTick(pos, state.getBlock(), REMOVE_DELAY_TICKS);
            BlockPos post = pos;
            for (ChainChompEntity chomp : level.getEntitiesOfClass(ChainChompEntity.class,
                    new AABB(post).inflate(ChainChompEntity.LUNGE_CHAIN + 2),
                    c -> c.post().filter(post::equals).isPresent())) {
                chomp.postPounded();
            }
            return true;
        }
        return false;
    }
}
