package com.nanobuntv.retro64plus.world;

import com.nanobuntv.retro64plus.Retro64Plus;
import com.nanobuntv.retro64plus.enemies.ChainChompEntity;
import com.nanobuntv.retro64plus.enemies.Enemies;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
//? if >=26.3 {
/*import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.chunk.ChunkGenerator;
*///?} else {
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
//?}
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;

/**
 * The Chain Chomp's places in world generation (D78, D101, D103): just its post on open ground and the Chomp chained
 * to it. {@link #yard()} is our own yard (rare, placed by {@code placed_feature/chomp_yard}), with a chest of mid-tier
 * loot (D102) three blocks from the post, two blocks of air between. {@link #village()} runs in every chunk but only
 * acts in the chunk a village starts in, one village in four: it finds natural, open ground there (no paths, a clear
 * 3 × 3) after the village's own pieces are placed (it runs in the last generation step). Up to 26.2 a feature with no
 * configuration; from 26.3 features are their own configuration, registered as two feature types.
 */
//? if >=26.3 {
/*public final class ChompYardFeature implements Feature {
    public static final MapCodec<ChompYardFeature> YARD_CODEC = MapCodec.unit(() -> new ChompYardFeature(false));
    public static final MapCodec<ChompYardFeature> VILLAGE_CODEC = MapCodec.unit(() -> new ChompYardFeature(true));

    private ChompYardFeature(boolean village) {
        this.village = village;
    }

    @Override
    public MapCodec<ChompYardFeature> codec() {
        return village ? VILLAGE_CODEC : YARD_CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        return village ? placeInVillage(level, origin) : placeYard(level, random, origin);
    }
*///?} else {
public final class ChompYardFeature extends Feature<NoneFeatureConfiguration> {
    private ChompYardFeature(boolean village) {
        super(NoneFeatureConfiguration.CODEC);
        this.village = village;
    }

    public static ChompYardFeature yard() {
        return new ChompYardFeature(false);
    }

    public static ChompYardFeature village() {
        return new ChompYardFeature(true);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        return village ? placeInVillage(context.level(), context.origin())
                : placeYard(context.level(), context.random(), context.origin());
    }
//?}

    public static final ResourceKey<LootTable> YARD_CHEST = ResourceKey.create(Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath(Retro64Plus.MOD_ID, "chests/chomp_yard"));
    /** One village in this many gets a Chomp (D101). */
    private static final int VILLAGE_CHANCE = 4;

    private final boolean village;

    private boolean placeYard(WorldGenLevel level, RandomSource random, BlockPos origin) {
        BlockPos post = surface(level, origin);
        if (!openGround(level, post, 1)) return false;
        Direction toChest = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        BlockPos chest = surface(level, post.relative(toChest, 3));
        if (Math.abs(chest.getY() - post.getY()) > 1 || !openGround(level, chest, 0)) return false;
        for (int i = 1; i <= 2; i++) {
            // The two blocks between: air (plants cleared), on whatever ground is there.
            BlockPos gap = surface(level, post.relative(toChest, i));
            for (int up = 0; up < 3; up++) clear(level, gap.above(up));
        }
        placePost(level, post);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, toChest.getOpposite()), 2);
        RandomizableContainer.setBlockEntityLootTable(level, random, chest, YARD_CHEST);
        spawnChomp(level, post, toChest.getOpposite(), random);
        return true;
    }

    private boolean placeInVillage(WorldGenLevel level, BlockPos origin) {
        ChunkAccess chunk = level.getChunk(origin);
        if (!startsVillage(level, chunk)) return false;
        // Mixed well: nearby chunks' seeds differ in a few bits, which the plain random's first numbers barely feel.
        RandomSource random = RandomSource.create(net.minecraft.world.level.levelgen.RandomSupport.mixStafford13(
                level.getSeed() ^ com.nanobuntv.retro64plus.util.Compat.chunkKey(chunk.getPos())));
        if (random.nextInt(VILLAGE_CHANCE) != 0) {
            devLog("village at {}: not this one (1 in {})", chunk.getPos(), VILLAGE_CHANCE);
            return false;
        }
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        for (int attempt = 0; attempt < 96; attempt++) {
            // Inside this chunk (its village pieces are all placed by now): a village's middle is crowded, so a clear
            // 3 × 3 is enough.
            BlockPos post = surface(level, new BlockPos(x0 + 1 + random.nextInt(14), 0, z0 + 1 + random.nextInt(14)));
            if (!openGround(level, post, 1)) continue;
            placePost(level, post);
            spawnChomp(level, post, Direction.Plane.HORIZONTAL.getRandomDirection(random), random);
            devLog("village at {}: Chomp at {}", chunk.getPos(), post);
            return true;
        }
        devLog("village at {}: no open ground", chunk.getPos());
        return false;
    }

    /** What the village feature decided, in dev builds (the scripted run's village count). */
    private static void devLog(String format, Object... args) {
        if (com.nanobuntv.retro64plus.platform.Services.PLATFORM.isDevelopmentEnvironment()) {
            Retro64Plus.LOGGER.info("Chomp yards: " + format, args);
        }
    }

    /** Whether a village's structure starts in this chunk. */
    private static boolean startsVillage(WorldGenLevel level, ChunkAccess chunk) {
        var structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (var entry : chunk.getAllStarts().entrySet()) {
            StructureStart start = entry.getValue();
            if (start == null || !start.isValid()) continue;
            Structure structure = entry.getKey();
            if (structures.wrapAsHolder(structure).is(StructureTags.VILLAGE)) return true;
        }
        return false;
    }

    /**
     * The first open space above the surface at that column: the world-generation heightmap while generating, the
     * live one otherwise ({@code /place feature}).
     */
    private static BlockPos surface(WorldGenLevel level, BlockPos pos) {
        Heightmap.Types type = level instanceof net.minecraft.server.level.WorldGenRegion
                ? Heightmap.Types.WORLD_SURFACE_WG : Heightmap.Types.WORLD_SURFACE;
        return new BlockPos(pos.getX(), level.getHeight(type, pos.getX(), pos.getZ()), pos.getZ());
    }

    /**
     * Natural ground (dirt, grass, sand, snow, stone) under {@code pos} and its neighbours within {@code margin}, at the
     * same height, with room above (plants don't count), no water, and nothing built around.
     */
    private static boolean openGround(WorldGenLevel level, BlockPos pos, int margin) {
        for (int dx = -margin; dx <= margin; dx++) {
            for (int dz = -margin; dz <= margin; dz++) {
                BlockPos p = pos.offset(dx, 0, dz);
                if (!naturalGround(level.getBlockState(p.below()))) return false;
                for (int up = 0; up < 3; up++) {
                    BlockState above = level.getBlockState(p.above(up));
                    if (!above.isAir() && !(above.canBeReplaced() && above.getFluidState().isEmpty())) return false;
                }
            }
        }
        return true;
    }

    private static boolean naturalGround(BlockState state) {
        return (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.SNOW_BLOCK)
                || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.GRAVEL)) && !state.is(Blocks.DIRT_PATH)
                && !state.is(Blocks.FARMLAND);
    }

    private static void clear(WorldGenLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.isAir() && state.canBeReplaced() && state.getFluidState().isEmpty()) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    private static void placePost(WorldGenLevel level, BlockPos post) {
        for (int up = 0; up < 3; up++) clear(level, post.above(up));
        level.setBlock(post, Enemies.CHOMP_POST.get().defaultBlockState(), 2);
    }

    /** The Chomp, chained to the post, sitting two blocks off it on the side {@code side}. */
    private static void spawnChomp(WorldGenLevel level, BlockPos post, Direction side, RandomSource random) {
        //? if >=1.21.2 {
        /*@Nullable ChainChompEntity chomp = Enemies.CHAIN_CHOMP.get().create(level.getLevel(), MobSpawnType.STRUCTURE);
        *///?} else {
        @Nullable ChainChompEntity chomp = Enemies.CHAIN_CHOMP.get().create(level.getLevel());
        //?}
        if (chomp == null) return;
        BlockPos at = surface(level, post.relative(side, 2));
        //? if >=1.21.5 {
        /*chomp.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360, 0);
        *///?} else {
        chomp.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360, 0);
        //?}
        chomp.setPost(post);
        chomp.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.STRUCTURE, null);
        level.addFreshEntityWithPassengers(chomp);
    }
}
