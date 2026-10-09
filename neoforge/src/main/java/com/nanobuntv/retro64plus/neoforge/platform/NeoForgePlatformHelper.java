package com.nanobuntv.retro64plus.neoforge.platform;

import com.nanobuntv.retro64plus.platform.PlatformHelper;
import com.nanobuntv.retro64plus.Retro64Plus;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
//? if <1.21.11 {
import net.minecraft.world.level.GameRules;
//?}
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

public final class NeoForgePlatformHelper implements PlatformHelper {
    /** Registrations and creative tab entries made before the mod bus is attached, done in its events. */
    private static final List<Consumer<RegisterEvent>> REGISTRATIONS = new ArrayList<>();
    private static final List<Consumer<BuildCreativeModeTabContentsEvent>> TAB_ENTRIES = new ArrayList<>();
    private static final List<Consumer<net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent>> ATTRIBUTES =
            new ArrayList<>();
    private static final List<Consumer<net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent>> PLACEMENTS =
            new ArrayList<>();

    /** From the mod constructor, after common init: registrations happen in the registry events. */
    public static void attach(IEventBus modBus) {
        modBus.addListener((RegisterEvent event) -> REGISTRATIONS.forEach(r -> r.accept(event)));
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> TAB_ENTRIES.forEach(t -> t.accept(event)));
        modBus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) ->
                ATTRIBUTES.forEach(a -> a.accept(event)));
        modBus.addListener((net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent event) ->
                PLACEMENTS.forEach(p -> p.accept(event)));
    }

    @Override
    public String loaderName() {
        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        //? if >=1.21.9 {
        /*return !FMLEnvironment.isProduction();
        *///?} else {
        return !FMLEnvironment.production;
        //?}
    }

    @Override
    public Path gameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public void onServerStarting(Consumer<MinecraftServer> listener) {
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent event) -> listener.accept(event.getServer()));
    }

    @Override
    public void onPlayerJoin(Consumer<ServerPlayer> listener) {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) listener.accept(player);
        });
    }

    @Override
    public void onPlayerLeave(Consumer<ServerPlayer> listener) {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) listener.accept(player);
        });
    }

    //? if <1.21.11 {
    @Override
    public GameRules.Key<GameRules.BooleanValue> registerBooleanGameRule(String name, GameRules.Category category,
            boolean defaultValue, BiConsumer<MinecraftServer, GameRules.BooleanValue> onChange) {
        // Public through NeoForge's access transformer.
        return GameRules.register(name, category, GameRules.BooleanValue.create(defaultValue, onChange));
    }
    //?}

    @Override
    public <R, T extends R> Supplier<T> register(ResourceKey<? extends Registry<R>> registry, String name,
            Supplier<T> factory) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Retro64Plus.MOD_ID, name);
        Object[] made = new Object[1];
        REGISTRATIONS.add(event -> event.register(registry, id, () -> {
            T value = factory.get();
            made[0] = value;
            return value;
        }));
        return () -> {
            @SuppressWarnings("unchecked") T value = (T) made[0];
            if (value == null) throw new IllegalStateException(id + " isn't registered yet");
            return value;
        };
    }

    @Override
    public void addToCreativeTab(ResourceKey<CreativeModeTab> tab, Supplier<? extends ItemLike> item) {
        TAB_ENTRIES.add(event -> {
            if (event.getTabKey().equals(tab)) event.accept(item.get());
        });
    }

    @Override
    public <T extends net.minecraft.world.entity.LivingEntity> void registerAttributes(
            Supplier<net.minecraft.world.entity.EntityType<T>> type,
            Supplier<net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder> attributes) {
        ATTRIBUTES.add(event -> event.put(type.get(), attributes.get().build()));
    }

    @Override
    public <T extends net.minecraft.world.entity.Mob> void registerSpawnPlacement(
            Supplier<net.minecraft.world.entity.EntityType<T>> type,
            net.minecraft.world.entity.SpawnPlacementType placement,
            net.minecraft.world.level.levelgen.Heightmap.Types heightmap,
            net.minecraft.world.entity.SpawnPlacements.SpawnPredicate<T> predicate) {
        PLACEMENTS.add(event -> event.register(type.get(), placement, heightmap, predicate,
                net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.REPLACE));
    }

    @Override
    public void addMonsterSpawn(net.minecraft.tags.TagKey<net.minecraft.world.level.biome.Biome> biomes,
            Supplier<? extends net.minecraft.world.entity.EntityType<?>> type, int weight, int min, int max) {
        // data/retro64plus/neoforge/biome_modifier/sm64_enemies.json
    }

    @Override
    public <T extends net.minecraft.world.level.block.entity.BlockEntity> net.minecraft.world.level.block.entity.BlockEntityType<T>
            blockEntityType(java.util.function.BiFunction<net.minecraft.core.BlockPos,
                    net.minecraft.world.level.block.state.BlockState, T> factory,
                    net.minecraft.world.level.block.Block block) {
        //? if >=1.21.2 {
        /*return new net.minecraft.world.level.block.entity.BlockEntityType<>(factory::apply, java.util.Set.of(block));
        *///?} else {
        return net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(factory::apply, block).build(null);
        //?}
    }

    @Override
    public void addFeature(net.minecraft.tags.TagKey<net.minecraft.world.level.biome.Biome> biomes,
            net.minecraft.world.level.levelgen.GenerationStep.Decoration step,
            ResourceKey<net.minecraft.world.level.levelgen.placement.PlacedFeature> feature) {
        // data/retro64plus/neoforge/biome_modifier/chomp_yards.json
    }

    @Override
    public void addLootPool(ResourceKey<LootTable> table, Supplier<LootPool> pool) {
        NeoForge.EVENT_BUS.addListener((LootTableLoadEvent event) -> {
            if (event.getName().equals(table.location())) event.getTable().addPool(pool.get());
        });
    }
}
