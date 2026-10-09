package com.nanobuntv.retro64plus.coins;

import com.nanobuntv.retro64plus.util.Compat;
import com.nanobuntv.retro64plus.Retro64Plus;
import com.nanobuntv.retro64plus.platform.Services;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/** The coin entity's registration, its tags (D52) and spawning coins that pop out of something. */
public final class Coins {
    public static Supplier<EntityType<CoinEntity>> COIN;

    /** Blocks that may drop a coin when Mario breaks them: grass, leaves, ores (D52). */
    public static final TagKey<Block> COIN_BLOCKS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Retro64Plus.MOD_ID, "coin_blocks"));
    /** Mobs worth the "strong" amount of coins (an Enderman, D52). */
    public static final TagKey<EntityType<?>> STRONG_MOBS = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Retro64Plus.MOD_ID, "coin_strong"));
    /** Mobs worth the boss amount of coins (D52). */
    public static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Retro64Plus.MOD_ID, "coin_bosses"));

    /** Most coin entities one burst makes; bigger amounts are split between them. */
    public static final int MAX_ENTITIES = 40;

    private Coins() {}

    /** Common init. */
    public static void register() {
        COIN = Services.PLATFORM.register(Registries.ENTITY_TYPE, "coin",
                () -> Compat.build(EntityType.Builder.<CoinEntity>of(CoinEntity::new, MobCategory.MISC)
                        .sized(0.5f, 0.5f).clientTrackingRange(6).updateInterval(20), "coin"));
    }

    /**
     * Pops {@code amount} coins out at {@code at} (blocks), flying up and outward like SM64's coins from a defeated
     * enemy. Above {@link #MAX_ENTITIES} each coin stands for several.
     */
    public static void burst(ServerLevel level, Vec3 at, int amount) {
        burst(level, at, amount, false);
    }

    /** As {@link #burst(ServerLevel, Vec3, int)}; {@code dropped}: a dead player's coins (CoinEntity#dropped). */
    public static void burst(ServerLevel level, Vec3 at, int amount, boolean dropped) {
        if (amount <= 0) return;
        int entities = Math.min(amount, MAX_ENTITIES);
        RandomSource random = level.getRandom();
        for (int i = 0; i < entities; i++) {
            int value = amount / entities + (i < amount % entities ? 1 : 0);
            CoinEntity coin = new CoinEntity(level, at.x, at.y, at.z, value);
            if (dropped) coin.markDropped();
            double angle = random.nextDouble() * Math.PI * 2, speed = 0.05 + random.nextDouble() * 0.12;
            coin.setDeltaMovement(Math.cos(angle) * speed, 0.3 + random.nextDouble() * 0.15, Math.sin(angle) * speed);
            level.addFreshEntity(coin);
        }
    }
}
