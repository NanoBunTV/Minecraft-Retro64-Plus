package com.nanobuntv.retro64plus.enemies;

import com.nanobuntv.retro64plus.util.Compat;
import com.nanobuntv.retro64plus.Retro64Plus;
import com.nanobuntv.retro64plus.config.Retro64PlusConfig;
import com.nanobuntv.retro64plus.platform.Services;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The SM64 enemies (M4, D72–D79, D93), the Koopa shell, and the Chain Chomp's post and yards (D78, D94–D104): entity types, spawn eggs, attributes and natural spawning beside vanilla's
 * hostiles (D73, D84): in the dark, in every Overworld land biome ({@link #SPAWN_BIOMES}), as common as spiders between
 * them, scaled by the {@code enemySpawnRate} server setting.
 */
public final class Enemies {
    public static Supplier<EntityType<GoombaEntity>> GOOMBA;
    public static Supplier<EntityType<BobombEntity>> BOBOMB;
    public static Supplier<EntityType<KoopaEntity>> KOOPA;
    public static Supplier<EntityType<KoopaShellEntity>> KOOPA_SHELL;
    public static Supplier<KoopaShellItem> KOOPA_SHELL_ITEM;
    public static Supplier<EntityType<ChainChompEntity>> CHAIN_CHOMP;
    public static Supplier<ChompPostBlock> CHOMP_POST;
    public static Supplier<net.minecraft.world.level.block.entity.BlockEntityType<ChompPostBlockEntity>> CHOMP_POST_ENTITY;
    /** World generation (D78, D101): our own yard, and the Chomp in villages. */
    public static final net.minecraft.resources.ResourceKey<net.minecraft.world.level.levelgen.placement.PlacedFeature>
            CHOMP_YARD = placed("chomp_yard"), VILLAGE_CHOMP = placed("village_chomp");

    /** Where they spawn naturally: the Overworld's land biomes (no oceans, rivers, mushroom fields or deep dark). */
    public static final TagKey<Biome> SPAWN_BIOMES = TagKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(Retro64Plus.MOD_ID, "spawns_sm64_enemies"));
    /**
     * Spawn weights (a spider's is 100) and group sizes. NeoForge reads its own copy from
     * {@code data/retro64plus/neoforge/biome_modifier/sm64_enemies.json}: keep them the same.
     */
    public static final int GOOMBA_WEIGHT = 40, BOBOMB_WEIGHT = 30, KOOPA_WEIGHT = 30;

    private static final net.minecraft.resources.ResourceKey<net.minecraft.world.item.CreativeModeTab> SPAWN_EGGS_TAB =
            net.minecraft.resources.ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                    ResourceLocation.withDefaultNamespace("spawn_eggs"));
    private static final net.minecraft.resources.ResourceKey<net.minecraft.world.item.CreativeModeTab> TOOLS_TAB =
            net.minecraft.resources.ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                    ResourceLocation.withDefaultNamespace("tools_and_utilities"));

    private Enemies() {}

    public static void register() {
        GOOMBA = Services.PLATFORM.register(Registries.ENTITY_TYPE, "goomba",
                () -> Compat.build(EntityType.Builder.of(GoombaEntity::new, MobCategory.MONSTER)
                        .sized(0.9f, 1.0f).eyeHeight(0.75f).clientTrackingRange(8), "goomba"));
        BOBOMB = Services.PLATFORM.register(Registries.ENTITY_TYPE, "bobomb",
                () -> Compat.build(EntityType.Builder.of(BobombEntity::new, MobCategory.MONSTER)
                        .sized(0.75f, 1.0f).eyeHeight(0.7f).clientTrackingRange(8), "bobomb"));
        KOOPA = Services.PLATFORM.register(Registries.ENTITY_TYPE, "koopa",
                () -> Compat.build(EntityType.Builder.of(KoopaEntity::new, MobCategory.MONSTER)
                        .sized(1.2f, 1.9f).eyeHeight(1.6f).clientTrackingRange(8), "koopa"));
        KOOPA_SHELL = Services.PLATFORM.register(Registries.ENTITY_TYPE, "koopa_shell",
                () -> Compat.build(EntityType.Builder.<KoopaShellEntity>of(KoopaShellEntity::new, MobCategory.MISC)
                        .sized(0.8f, 0.45f).clientTrackingRange(8).updateInterval(2), "koopa_shell"));
        KOOPA_SHELL_ITEM = Services.PLATFORM.register(Registries.ITEM, "koopa_shell",
                () -> new KoopaShellItem(Compat.itemProperties("koopa_shell").stacksTo(16)));
        Services.PLATFORM.addToCreativeTab(TOOLS_TAB, KOOPA_SHELL_ITEM);
        CHAIN_CHOMP = Services.PLATFORM.register(Registries.ENTITY_TYPE, "chain_chomp",
                () -> Compat.build(EntityType.Builder.of(ChainChompEntity::new, MobCategory.MISC)
                        .sized(2.2f, 2.4f).eyeHeight(1.4f).clientTrackingRange(10), "chain_chomp"));
        CHOMP_POST = Services.PLATFORM.register(Registries.BLOCK, "chomp_post",
                () -> new ChompPostBlock(Compat.blockProperties("chomp_post")
                        .mapColor(net.minecraft.world.level.material.MapColor.WOOD).strength(-1, 3_600_000)
                        .noLootTable().noOcclusion().forceSolidOn()
                        .sound(net.minecraft.world.level.block.SoundType.WOOD)));
        CHOMP_POST_ENTITY = Services.PLATFORM.register(Registries.BLOCK_ENTITY_TYPE, "chomp_post",
                () -> Services.PLATFORM.blockEntityType(ChompPostBlockEntity::new, CHOMP_POST.get()));
        //? if >=26.3 {
        /*Services.PLATFORM.register(Registries.FEATURE_TYPE, "chomp_yard",
                () -> com.nanobuntv.retro64plus.world.ChompYardFeature.YARD_CODEC);
        Services.PLATFORM.register(Registries.FEATURE_TYPE, "village_chomp",
                () -> com.nanobuntv.retro64plus.world.ChompYardFeature.VILLAGE_CODEC);
        *///?} else {
        Services.PLATFORM.register(Registries.FEATURE, "chomp_yard",
                com.nanobuntv.retro64plus.world.ChompYardFeature::yard);
        Services.PLATFORM.register(Registries.FEATURE, "village_chomp",
                com.nanobuntv.retro64plus.world.ChompYardFeature::village);
        //?}
        Supplier<Item> goombaEgg = Services.PLATFORM.register(Registries.ITEM, "goomba_spawn_egg",
                () -> Compat.spawnEgg(GOOMBA.get(), 0x8A4B1E, 0xF2D3A2, "goomba_spawn_egg"));
        Supplier<Item> bobombEgg = Services.PLATFORM.register(Registries.ITEM, "bobomb_spawn_egg",
                () -> Compat.spawnEgg(BOBOMB.get(), 0x1C1C1C, 0xF2A31E, "bobomb_spawn_egg"));
        Supplier<Item> koopaEgg = Services.PLATFORM.register(Registries.ITEM, "koopa_spawn_egg",
                () -> Compat.spawnEgg(KOOPA.get(), 0xF2D04A, 0x3CAA3C, "koopa_spawn_egg"));
        Services.PLATFORM.addToCreativeTab(SPAWN_EGGS_TAB, goombaEgg);
        Services.PLATFORM.addToCreativeTab(SPAWN_EGGS_TAB, bobombEgg);
        Supplier<Item> chompEgg = Services.PLATFORM.register(Registries.ITEM, "chain_chomp_spawn_egg",
                () -> Compat.spawnEgg(CHAIN_CHOMP.get(), 0x202020, 0xE8E8E8, "chain_chomp_spawn_egg"));
        Services.PLATFORM.addToCreativeTab(SPAWN_EGGS_TAB, koopaEgg);
        Services.PLATFORM.addToCreativeTab(SPAWN_EGGS_TAB, chompEgg);

        Services.PLATFORM.registerAttributes(GOOMBA, GoombaEntity::createAttributes);
        Services.PLATFORM.registerAttributes(BOBOMB, BobombEntity::createAttributes);
        Services.PLATFORM.registerAttributes(KOOPA, KoopaEntity::createAttributes);
        Services.PLATFORM.registerAttributes(CHAIN_CHOMP, ChainChompEntity::createAttributes);
        Services.PLATFORM.registerSpawnPlacement(GOOMBA, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Enemies::canSpawn);
        Services.PLATFORM.registerSpawnPlacement(BOBOMB, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Enemies::canSpawn);
        Services.PLATFORM.registerSpawnPlacement(KOOPA, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Enemies::canSpawn);
        Services.PLATFORM.addMonsterSpawn(SPAWN_BIOMES, GOOMBA, GOOMBA_WEIGHT, 1, 3);
        Services.PLATFORM.addMonsterSpawn(SPAWN_BIOMES, BOBOMB, BOBOMB_WEIGHT, 1, 2);
        Services.PLATFORM.addMonsterSpawn(SPAWN_BIOMES, KOOPA, KOOPA_WEIGHT, 1, 2);
        Services.PLATFORM.addFeature(SPAWN_BIOMES,
                net.minecraft.world.level.levelgen.GenerationStep.Decoration.SURFACE_STRUCTURES, CHOMP_YARD);
        Services.PLATFORM.addFeature(SPAWN_BIOMES,
                net.minecraft.world.level.levelgen.GenerationStep.Decoration.TOP_LAYER_MODIFICATION, VILLAGE_CHOMP);
    }

    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.levelgen.placement.PlacedFeature>
            placed(String name) {
        return net.minecraft.resources.ResourceKey.create(Registries.PLACED_FEATURE,
                ResourceLocation.fromNamespaceAndPath(Retro64Plus.MOD_ID, name));
    }

    /** Vanilla's hostile rules (darkness, difficulty), then the server's rate for natural spawns. */
    private static <T extends Monster> boolean canSpawn(EntityType<T> type, ServerLevelAccessor level,
            MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (!Monster.checkMonsterSpawnRules(type, level, reason, pos, random)) return false;
        boolean natural = reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION;
        return !natural || random.nextDouble() < Retro64PlusConfig.server().enemySpawnRate;
    }
}
