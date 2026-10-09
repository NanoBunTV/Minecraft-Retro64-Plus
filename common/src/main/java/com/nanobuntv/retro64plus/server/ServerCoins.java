package com.nanobuntv.retro64plus.server;

import com.nanobuntv.retro64plus.util.Compat;
import com.nanobuntv.retro64plus.MarioHooks;
import com.nanobuntv.retro64plus.coins.CoinEntity;
import com.nanobuntv.retro64plus.coins.Coins;
import com.nanobuntv.retro64plus.config.Retro64PlusConfig;
import com.nanobuntv.retro64plus.config.ServerSettings;
import com.nanobuntv.retro64plus.health.MarioHealth;
import com.nanobuntv.retro64plus.net.Payloads;
import com.nanobuntv.retro64plus.platform.Services;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Coins on the server (D50–D56): the per-player counter ({@link PlayerProgress}), where coins come from (mobs Mario
 * kills, blocks he breaks, structure chests), healing while Mario, and the half that scatters on death. Server thread.
 */
public final class ServerCoins {
    /** Coin healing still to give, per player, in SM64 health units (given at SM64's rate, D54). */
    private static final Map<UUID, Double> PENDING_HEAL = new HashMap<>();
    /** Set while a ground pound (the landing or its shockwave) deals its damage: a kill then gives one coin more. */
    private static boolean groundPoundHit;

    private ServerCoins() {}

    /** Common init. */
    public static void init() {
        Services.PLATFORM.onPlayerJoin(ServerCoins::sync);
    }

    public static int coins(ServerPlayer player) {
        return PlayerProgress.get(Compat.server(player)).coins(player.getUUID());
    }

    /** Adds (or with a negative amount takes) coins and tells the player's client. */
    public static void add(ServerPlayer player, int amount) {
        PlayerProgress progress = PlayerProgress.get(Compat.server(player));
        long total = (long) progress.coins(player.getUUID()) + amount;
        progress.setCoins(player.getUUID(), Math.clamp(total, 0, Integer.MAX_VALUE));
        sync(player);
    }

    private static void sync(ServerPlayer player) {
        Services.NETWORK.sendToPlayer(player, new Payloads.CoinCount(coins(player)));
    }

    /** {@code player} touched {@code coin} (D55: anyone collects; only Mario heals, D54). */
    public static void collect(ServerPlayer player, CoinEntity coin) {
        add(player, coin.value());
        if (!coin.dropped()) ServerStars.onCoins(player, coin.value()); // a dead player's coins aren't new ones
        ServerSounds.play(coin, coin.position(), ServerSounds.COIN);
        if (MarioHooks.isServerMario(player)) {
            PENDING_HEAL.merge(player.getUUID(), (double) coin.value() * MarioHealth.COIN_HEAL, Double::sum);
        }
    }

    /** End of every server player tick: coin healing, at SM64's pace. */
    public static void tick(ServerPlayer player) {
        Double pending = PENDING_HEAL.get(player.getUUID());
        if (pending == null) return;
        if (!MarioHooks.isServerMario(player) || !player.isAlive() || player.getHealth() >= player.getMaxHealth()) {
            // Healing past full isn't kept for later, as in SM64.
            PENDING_HEAL.remove(player.getUUID());
            return;
        }
        double units = Math.min(pending, MarioHealth.COIN_HEAL_PER_TICK);
        player.heal(MarioHealth.toHealth(units, player.getMaxHealth()));
        if (pending - units <= 0) PENDING_HEAL.remove(player.getUUID());
        else PENDING_HEAL.put(player.getUUID(), pending - units);
    }

    public static void forget(ServerPlayer player) {
        PENDING_HEAL.remove(player.getUUID());
    }

    /** Half the coins scatter where the player died (D56); keepInventory keeps them. */
    public static void onDeath(ServerPlayer player) {
        PENDING_HEAL.remove(player.getUUID());
        if (ModGameRules.keepInventory(Compat.level(player)) || player.isSpectator()) return;
        int lost = coins(player) / 2;
        if (lost <= 0) return;
        add(player, -lost);
        Coins.burst(Compat.level(player), player.position().add(0, 0.5, 0), lost, true);
    }

    /** Runs {@code hurt} as a ground pound's damage, so a kill by it gives a coin more (D52). */
    static boolean groundPoundHurt(java.util.function.BooleanSupplier hurt) {
        groundPoundHit = true;
        try {
            return hurt.getAsBoolean();
        } finally {
            groundPoundHit = false;
        }
    }

    /**
     * A mob died and drops its loot (D52): a hostile one killed by a Mario player drops coins, more for strong mobs
     * and bosses. {@code lastPlayer} is the player who last hurt it, for kills by arrows or falls.
     */
    public static void onMobDeath(LivingEntity victim, DamageSource source, @Nullable Player lastPlayer) {
        if (!(victim.level() instanceof ServerLevel level) || !(victim instanceof Enemy)) return;
        if (victim instanceof com.nanobuntv.retro64plus.enemies.Sm64Enemy) return; // drops SM64's own coins (D76)
        if (!ModGameRules.mobLoot(level)) return;
        ServerPlayer killer = source.getEntity() instanceof ServerPlayer p ? p
                : lastPlayer instanceof ServerPlayer p ? p : null;
        if (killer == null || !MarioHooks.isServerMario(killer)) return;
        ServerSettings settings = Retro64PlusConfig.server();
        int amount;
        if (Compat.is(victim, Coins.BOSSES)) amount = settings.bossCoins;
        else if (Compat.is(victim, Coins.STRONG_MOBS)) amount = settings.strongMobCoins;
        else amount = settings.mobCoinsMin + level.getRandom().nextInt(Math.max(1, settings.mobCoinsMax - settings.mobCoinsMin + 1));
        if (groundPoundHit) amount += settings.groundPoundKillCoins;
        Coins.burst(level, victim.position().add(0, victim.getBbHeight() / 2, 0), amount);
    }

    /** A player broke a block (D52): a chance of a coin from grass, leaves and ores, for Mario in survival. */
    public static void onBlockBroken(ServerPlayer player, BlockState state, BlockPos pos) {
        if (!MarioHooks.isServerMario(player) || player.isCreative() || !state.is(Coins.COIN_BLOCKS)) return;
        ServerLevel level = Compat.level(player);
        if (level.getRandom().nextDouble() >= Retro64PlusConfig.server().blockCoinChance) return;
        Coins.burst(level, Vec3.atCenterOf(pos), 1);
    }

    /**
     * A loot table filled a container for a player (D53): a structure chest's first opening, or with Lootr each
     * player's first opening of it (Lootr fills a container per player the same way). Coins burst out of it.
     */
    public static void onLootFill(LootParams params) {
        if (!(params.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof ServerPlayer player)) return;
        Vec3 origin = params.getParamOrNull(LootContextParams.ORIGIN);
        if (origin == null || player.isSpectator()) return;
        ServerSettings settings = Retro64PlusConfig.server();
        int amount = settings.chestCoinsMin
                + player.getRandom().nextInt(Math.max(1, settings.chestCoinsMax - settings.chestCoinsMin + 1));
        Coins.burst(params.getLevel(), origin.add(0, 0.6, 0), amount);
    }
}
