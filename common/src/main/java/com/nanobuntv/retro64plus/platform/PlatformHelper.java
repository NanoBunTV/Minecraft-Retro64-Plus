package com.nanobuntv.retro64plus.platform;

import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
//? if <1.21.11 {
import net.minecraft.world.level.GameRules;
//?}
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.storage.loot.LootPool;

/** Facts about the running loader and environment, server-side events, and registration (M2: the caps). */
public interface PlatformHelper {
    /** "Fabric" or "NeoForge". */
    String loaderName();

    boolean isModLoaded(String modId);

    boolean isDevelopmentEnvironment();

    Path gameDir();

    /** Runs {@code listener} on the server thread as a server (dedicated or singleplayer) starts. */
    void onServerStarting(Consumer<MinecraftServer> listener);

    /** Runs {@code listener} on the server thread when a player has joined (logged in). */
    void onPlayerJoin(Consumer<ServerPlayer> listener);

    /** Runs {@code listener} on the server thread when a player is leaving (logging out). */
    void onPlayerLeave(Consumer<ServerPlayer> listener);

    //? if <1.21.11 {
    /**
     * A boolean game rule (up to 1.21.10; vanilla's registration is private: NeoForge opens it, Fabric has its own registry; ModGameRules).
     * {@code onChange} runs on the server thread when it's changed.
     */
    GameRules.Key<GameRules.BooleanValue> registerBooleanGameRule(String name, GameRules.Category category,
            boolean defaultValue, BiConsumer<MinecraftServer, GameRules.BooleanValue> onChange);
    //?}

    /**
     * Registers {@code retro64plus:<name>} in {@code registry}, made by {@code factory} when the loader allows it
     * (Fabric at once, NeoForge in its registry event). The supplier gives the object once registered.
     */
    <R, T extends R> Supplier<T> register(ResourceKey<? extends Registry<R>> registry, String name,
            Supplier<T> factory);

    /** Lists an item in a vanilla creative tab. */
    void addToCreativeTab(ResourceKey<CreativeModeTab> tab, Supplier<? extends ItemLike> item);

    /** A mob type's default attributes (M4; Fabric at once, NeoForge in its event). */
    <T extends net.minecraft.world.entity.LivingEntity> void registerAttributes(
            Supplier<net.minecraft.world.entity.EntityType<T>> type,
            Supplier<net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder> attributes);

    /** Where and when a mob type may spawn naturally (M4). */
    <T extends net.minecraft.world.entity.Mob> void registerSpawnPlacement(
            Supplier<net.minecraft.world.entity.EntityType<T>> type,
            net.minecraft.world.entity.SpawnPlacementType placement,
            net.minecraft.world.level.levelgen.Heightmap.Types heightmap,
            net.minecraft.world.entity.SpawnPlacements.SpawnPredicate<T> predicate);

    /**
     * Natural monster spawns in the biomes of a tag (M4). Fabric adds them in code; NeoForge's come from a biome
     * modifier in data ({@code data/retro64plus/neoforge/biome_modifier/}), so there this does nothing.
     */
    void addMonsterSpawn(net.minecraft.tags.TagKey<net.minecraft.world.level.biome.Biome> biomes,
            Supplier<? extends net.minecraft.world.entity.EntityType<?>> type, int weight, int min, int max);

    /**
     * A block entity type for one block (M4 checkpoint 4): vanilla's builder takes a package-private factory type that
     * each loader opens up its own way.
     */
    <T extends net.minecraft.world.level.block.entity.BlockEntity> net.minecraft.world.level.block.entity.BlockEntityType<T>
            blockEntityType(java.util.function.BiFunction<net.minecraft.core.BlockPos,
                    net.minecraft.world.level.block.state.BlockState, T> factory,
                    net.minecraft.world.level.block.Block block);

    /**
     * A placed feature in the biomes of a tag (M4 checkpoint 4). Fabric adds it in code; NeoForge's come from a biome
     * modifier in data ({@code data/retro64plus/neoforge/biome_modifier/}), so there this does nothing.
     */
    void addFeature(net.minecraft.tags.TagKey<net.minecraft.world.level.biome.Biome> biomes,
            net.minecraft.world.level.levelgen.GenerationStep.Decoration step,
            ResourceKey<net.minecraft.world.level.levelgen.placement.PlacedFeature> feature);

    /** Adds a pool to a loot table (a vanilla chest's) whenever loot tables load. */
    void addLootPool(ResourceKey<net.minecraft.world.level.storage.loot.LootTable> table, Supplier<LootPool> pool);
}
