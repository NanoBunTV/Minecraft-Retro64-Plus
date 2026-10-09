package com.nanobuntv.retro64plus.fabric.platform;

import com.nanobuntv.retro64plus.platform.PlatformHelper;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import com.nanobuntv.retro64plus.Retro64Plus;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
//? if <1.21.11 {
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
//?}
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
//? if <1.21.11 {
import net.minecraft.world.level.GameRules;
//?}

public final class FabricPlatformHelper implements PlatformHelper {
    @Override
    public String loaderName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public void onServerStarting(Consumer<MinecraftServer> listener) {
        ServerLifecycleEvents.SERVER_STARTING.register(listener::accept);
    }

    @Override
    public void onPlayerJoin(Consumer<ServerPlayer> listener) {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> listener.accept(handler.getPlayer()));
    }

    @Override
    public void onPlayerLeave(Consumer<ServerPlayer> listener) {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> listener.accept(handler.getPlayer()));
    }

    //? if <1.21.11 {
    @Override
    public GameRules.Key<GameRules.BooleanValue> registerBooleanGameRule(String name, GameRules.Category category,
            boolean defaultValue, BiConsumer<MinecraftServer, GameRules.BooleanValue> onChange) {
        return GameRuleRegistry.register(name, category, GameRuleFactory.createBooleanRule(defaultValue, onChange::accept));
    }
    //?}

    @Override
    @SuppressWarnings("unchecked")
    public <R, T extends R> Supplier<T> register(ResourceKey<? extends Registry<R>> registry, String name,
            Supplier<T> factory) {
        Registry<R> target = (Registry<R>) BuiltInRegistries.REGISTRY.get(registry.location());
        T value = Registry.register(target, ResourceLocation.fromNamespaceAndPath(Retro64Plus.MOD_ID, name), factory.get());
        return () -> value;
    }

    @Override
    public void addToCreativeTab(ResourceKey<CreativeModeTab> tab, Supplier<? extends ItemLike> item) {
        ItemGroupEvents.modifyEntriesEvent(tab).register(entries -> entries.accept(item.get()));
    }

    @Override
    public <T extends net.minecraft.world.entity.LivingEntity> void registerAttributes(
            Supplier<net.minecraft.world.entity.EntityType<T>> type,
            Supplier<net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder> attributes) {
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(type.get(),
                attributes.get());
    }

    @Override
    public <T extends net.minecraft.world.entity.Mob> void registerSpawnPlacement(
            Supplier<net.minecraft.world.entity.EntityType<T>> type,
            net.minecraft.world.entity.SpawnPlacementType placement,
            net.minecraft.world.level.levelgen.Heightmap.Types heightmap,
            net.minecraft.world.entity.SpawnPlacements.SpawnPredicate<T> predicate) {
        com.nanobuntv.retro64plus.mixin.SpawnPlacementsInvoker.retro64plus$register(type.get(), placement, heightmap,
                predicate);
    }

    @Override
    public void addMonsterSpawn(net.minecraft.tags.TagKey<net.minecraft.world.level.biome.Biome> biomes,
            Supplier<? extends net.minecraft.world.entity.EntityType<?>> type, int weight, int min, int max) {
        net.fabricmc.fabric.api.biome.v1.BiomeModifications.addSpawn(
                net.fabricmc.fabric.api.biome.v1.BiomeSelectors.tag(biomes),
                net.minecraft.world.entity.MobCategory.MONSTER, type.get(), weight, min, max);
    }

    @Override
    public <T extends net.minecraft.world.level.block.entity.BlockEntity> net.minecraft.world.level.block.entity.BlockEntityType<T>
            blockEntityType(java.util.function.BiFunction<net.minecraft.core.BlockPos,
                    net.minecraft.world.level.block.state.BlockState, T> factory,
                    net.minecraft.world.level.block.Block block) {
        return net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder
                .create(factory::apply, block).build();
    }

    @Override
    public void addFeature(net.minecraft.tags.TagKey<net.minecraft.world.level.biome.Biome> biomes,
            net.minecraft.world.level.levelgen.GenerationStep.Decoration step,
            ResourceKey<net.minecraft.world.level.levelgen.placement.PlacedFeature> feature) {
        net.fabricmc.fabric.api.biome.v1.BiomeModifications.addFeature(
                net.fabricmc.fabric.api.biome.v1.BiomeSelectors.tag(biomes), step, feature);
    }

    @Override
    public void addLootPool(ResourceKey<LootTable> table, Supplier<LootPool> pool) {
        LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
            // Also into tables a data pack replaced, as NeoForge's LootTableLoadEvent does (same loot on both loaders).
            if (key.equals(table)) builder.pool(pool.get());
        });
    }
}
