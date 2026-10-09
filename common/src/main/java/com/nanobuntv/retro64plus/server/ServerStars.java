package com.nanobuntv.retro64plus.server;

import com.nanobuntv.retro64plus.util.Compat;
import com.nanobuntv.retro64plus.config.Retro64PlusConfig;
import com.nanobuntv.retro64plus.config.ServerSettings;
import com.nanobuntv.retro64plus.net.Payloads;
import com.nanobuntv.retro64plus.platform.Services;
import com.nanobuntv.retro64plus.stars.PowerStarEntity;
import com.nanobuntv.retro64plus.stars.Stars;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Power Stars on the server (D60–D67): which stars each player owns ({@link PlayerProgress}), where new ones appear
 * (100 coins in a dimension in one life, the first kill of each boss, every challenge advancement), and collecting
 * them. A star appears for one player only, a little above them (a boss's where it fell) and waits there. Server
 * thread.
 */
public final class ServerStars {
    /** How far above the player's feet a star appears: one jump up, as SM64's stars often hang. */
    private static final double ABOVE_PLAYER = 2.4;

    private ServerStars() {}

    /** Common init. */
    public static void init() {
        Services.PLATFORM.onPlayerJoin(ServerStars::sync);
    }

    public static int count(ServerPlayer player) {
        return PlayerProgress.get(Compat.server(player)).starCount(player.getUUID());
    }

    public static boolean has(ServerPlayer player, String id) {
        return PlayerProgress.get(Compat.server(player)).hasStar(player.getUUID(), id);
    }

    /** Tells the player's client their star count and what it unlocks (for the HUD and the cap tooltips). */
    public static void sync(ServerPlayer player) {
        int stars = count(player);
        ServerSettings settings = Retro64PlusConfig.server();
        Services.NETWORK.sendToPlayer(player, new Payloads.StarCount(stars,
                (float) settings.capDurationMultiplier(stars), (float) settings.rechargeMultiplier(stars)));
    }

    /**
     * Puts star {@code id} out for {@code player} at {@code at} (blocks), unless they own it or it's already waiting
     * for them somewhere in this world.
     */
    public static void spawn(ServerPlayer player, String id, ServerLevel level, Vec3 at) {
        if (has(player, id) || waiting(player, id)) return;
        level.addFreshEntity(new PowerStarEntity(level, at.x, at.y, at.z, player.getUUID(), id));
    }

    private static boolean waiting(ServerPlayer player, String id) {
        // Loaded stars only: one in an unloaded chunk can be doubled, and the first collected counts.
        for (ServerLevel level : Compat.server(player).getAllLevels()) {
            if (!level.getEntities(Stars.POWER_STAR.get(),
                    s -> player.getUUID().equals(s.owner()) && s.starId().equals(id)).isEmpty()) return true;
        }
        return false;
    }

    /** A star above the player, lower if something is in the way. */
    public static void spawnAbove(ServerPlayer player, String id) {
        ServerLevel level = Compat.level(player);
        Vec3 at = player.position().add(0, ABOVE_PLAYER, 0);
        AABB box = Stars.POWER_STAR.get().getDimensions().makeBoundingBox(at);
        while (at.y > player.getY() + 0.5 && !level.noCollision(box)) {
            at = at.add(0, -0.25, 0);
            box = box.move(0, -0.25, 0);
        }
        spawn(player, id, level, at);
    }

    /** {@code player} touched their star: it's theirs, with SM64's star dance on their client (D63). */
    public static void collect(ServerPlayer player, PowerStarEntity star) {
        if (!PlayerProgress.get(Compat.server(player)).addStar(player.getUUID(), star.starId())) return;
        int total = count(player);
        Services.NETWORK.sendToPlayer(player, new Payloads.StarCollected(star.starId(), total));
        sync(player);
    }

    /** Coins collected (D61): the dimension's star once a player has collected enough there in one life. */
    static void onCoins(ServerPlayer player, int amount) {
        int needed = Retro64PlusConfig.server().coinStarCoins;
        if (needed <= 0) return;
        var dimension = player.level().dimension().location();
        int total = PlayerProgress.get(Compat.server(player)).addLifeCoins(player.getUUID(), dimension.toString(), amount);
        if (total >= needed && total - amount < needed) spawnAbove(player, Stars.coinStar(dimension));
    }

    /** A new life: the coin count towards the 100-coin stars starts again. */
    public static void onDeath(ServerPlayer player) {
        PlayerProgress.get(Compat.server(player)).clearLifeCoins(player.getUUID());
    }

    /** A boss died (D61): its killer's first kill of that kind gives a star where it fell (on the ground below). */
    public static void onBossDeath(LivingEntity boss, @Nullable Player killer) {
        if (!(killer instanceof ServerPlayer player) || !(boss.level() instanceof ServerLevel level)) return;
        if (!Compat.is(boss, Stars.BOSSES)) return;
        String id = Stars.bossStar(BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType()));
        // The Ender Dragon dies high in the air: bring the star down to the ground under it (or to the player, over
        // the void).
        BlockPos column = boss.blockPosition();
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ());
        Vec3 at;
        if (ground > level.getMinBuildHeight()) {
            at = new Vec3(boss.getX(), Math.min(boss.getY() + 0.5, ground + 1.2), boss.getZ());
        } else {
            spawnAbove(player, id);
            return;
        }
        spawn(player, id, level, at);
    }

    /** {@code player} completed {@code advancement}: a star for every challenge (D65). */
    public static void onAdvancement(ServerPlayer player, AdvancementHolder advancement) {
        boolean challenge = advancement.value().display()
                .map(display -> display.getType() == AdvancementType.CHALLENGE).orElse(false);
        if (challenge) spawnAbove(player, Stars.advancementStar(advancement.id()));
    }
}
