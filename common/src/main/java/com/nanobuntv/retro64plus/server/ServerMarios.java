package com.nanobuntv.retro64plus.server;

import com.nanobuntv.retro64plus.util.Compat;
import com.nanobuntv.retro64plus.MarioCharacter;
import com.nanobuntv.retro64plus.MarioHooks;
import com.nanobuntv.retro64plus.config.Retro64PlusConfig;
import com.nanobuntv.retro64plus.enemies.KoopaShellEntity;
import com.nanobuntv.retro64plus.net.MarioPose;
import com.nanobuntv.retro64plus.net.MarioUpdate;
import com.nanobuntv.retro64plus.net.Payloads;
import com.nanobuntv.retro64plus.platform.Services;
import com.nanobuntv.retro64plus.sm64.Actions;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Who is Mario, server side (D13): set by each client's {@link Payloads.MarioState}, relayed to the players near them,
 * and dropped when they turn back or leave. The server never loads native code; it relays poses as plain data. The game
 * rules of D14 decide whether players may be Mario, or must be; the server settings (D34) which characters they may be.
 * It also keeps each Mario's last few actions, to check the attacks and ground pounds they report (D29, D18,
 * {@link ServerMarioAttacks}, {@link ServerGroundPound}).
 */
public final class ServerMarios {
    /** {@code /gamerule retro64plusForceMario true}: everyone is Mario (clients turn it on themselves). */
    public static ModGameRules.Rule FORCE_MARIO;
    /** {@code /gamerule retro64plusAllowMario false}: nobody may be Mario. */
    public static ModGameRules.Rule ALLOW_MARIO;
    /** The rules' values last seen, to notice a change ({@link #checkRules}); the server tick they were checked. */
    private static boolean lastForce, lastAllow = true;
    private static long rulesCheckedAt = Long.MIN_VALUE;

    /** Actions kept per Mario: a tick's attack may be reported with the pose of the tick before or after it. */
    private static final int RECENT_ACTIONS = 3;
    private static final Map<UUID, int[]> RECENT = new ConcurrentHashMap<>();
    /** Each Mario's facing from their last pose (SM64's angle units). */
    private static final Map<UUID, Short> YAWS = new ConcurrentHashMap<>();
    /**
     * States handled per player per tick: the client sends one a tick, and two may land in the same server tick; more
     * are dropped, so a modified client can't flood the players around it.
     */
    private static final int MAX_STATES_PER_TICK = 3;
    private static final Map<UUID, long[]> STATE_RATE = new ConcurrentHashMap<>();

    private ServerMarios() {}

    /** Common init. */
    public static void init() {
        FORCE_MARIO = ModGameRules.bool("retro64plusForceMario", "force_mario", false);
        ALLOW_MARIO = ModGameRules.bool("retro64plusAllowMario", "allow_mario", true);
        Services.PLATFORM.onServerStarting(server -> Retro64PlusConfig.loadServer());
        Services.PLATFORM.onPlayerJoin(player -> Services.NETWORK.sendToPlayer(player, rules(Compat.server(player))));
        Services.PLATFORM.onPlayerLeave(player -> {
            MarioHooks.setServerMario(player.getUUID(), false);
            RECENT.remove(player.getUUID());
            YAWS.remove(player.getUUID());
            STATE_RATE.remove(player.getUUID());
            ServerMarioAttacks.forget(player);
            ServerGroundPound.forget(player);
            ServerMarioHealth.forget(player);
            ServerCaps.forget(player);
            ServerCoins.forget(player);
        });
    }

    public static void onState(Payloads.MarioState payload, ServerPlayer player) {
        Optional<MarioUpdate> update = payload.update();
        if (update.isPresent() && overRate(player)) return; // turning back is always handled
        if (update.isPresent() && !ALLOW_MARIO.get(Compat.server(player))) {
            // Not allowed here: remind the client, which turns Mario off.
            Services.NETWORK.sendToPlayer(player, rules(Compat.server(player)));
            return;
        }
        update = update.map(ServerMarios::allowedCharacter);
        // A shell ride ended (D89: the shell is gone): SM64's puff where it was.
        int[] before = RECENT.get(player.getUUID());
        boolean wasRiding = before != null && Actions.has(before[0], Actions.FLAG_RIDING_SHELL);
        if (wasRiding && (update.isEmpty() || !Actions.has(update.get().pose().action(), Actions.FLAG_RIDING_SHELL))) {
            KoopaShellEntity.poof(Compat.level(player), player.position());
        }
        update.ifPresent(u -> {
            remember(player, u.pose().action());
            YAWS.put(player.getUUID(), u.pose().angleY());
        });
        setMario(player, update.isPresent());
        Services.NETWORK.sendToTracking(player, new Payloads.RemoteMario(player.getId(), update));
    }

    private static boolean overRate(ServerPlayer player) {
        long tick = Compat.server(player).getTickCount();
        long[] rate = STATE_RATE.computeIfAbsent(player.getUUID(), uuid -> new long[] {tick, 0});
        if (rate[0] != tick) {
            rate[0] = tick;
            rate[1] = 0;
        }
        return ++rate[1] > MAX_STATES_PER_TICK;
    }

    /** A character this server doesn't allow (D34) is shown as the first one it does (the client switches too). */
    private static MarioUpdate allowedCharacter(MarioUpdate update) {
        int allowed = Retro64PlusConfig.server().allowedCharacterBits();
        MarioCharacter character = MarioCharacter.byId(update.pose().model());
        if ((allowed & character.bit()) != 0) return update;
        MarioCharacter fallback = MarioCharacter.byId(Integer.numberOfTrailingZeros(allowed));
        MarioPose pose = update.pose().withModel((byte) fallback.id());
        return new MarioUpdate(pose, update.sounds());
    }

    /**
     * Which way {@code player}'s Mario faces, SM64's yaw in radians (0 faces +z, growing towards +x), from their last
     * pose; the player's own facing before any.
     */
    public static double yaw(ServerPlayer player) {
        Short yaw = YAWS.get(player.getUUID());
        return yaw != null ? yaw * Math.PI / 32768 : Math.toRadians(-player.getYRot());
    }

    private static void remember(ServerPlayer player, int action) {
        int[] recent = RECENT.computeIfAbsent(player.getUUID(), uuid -> new int[RECENT_ACTIONS]);
        System.arraycopy(recent, 0, recent, 1, RECENT_ACTIONS - 1);
        recent[0] = action;
    }

    /** The actions in {@code player}'s last few poses, newest first (zeros before any). */
    static int[] recentActions(ServerPlayer player) {
        int[] recent = RECENT.get(player.getUUID());
        return recent != null ? recent : new int[RECENT_ACTIONS];
    }

    private static void setMario(ServerPlayer player, boolean mario) {
        boolean was = MarioHooks.isServerMario(player);
        MarioHooks.setServerMario(player.getUUID(), mario);
        if (!mario) {
            RECENT.remove(player.getUUID());
            YAWS.remove(player.getUUID());
            ServerCaps.clear(player);
        }
        // The hitbox follows (mixin/PlayerMixin). A respawned player entity starts with the vanilla one, so this checks
        // on every state payload, not just on changes.
        boolean marioSized = player.getBbWidth() == MarioHooks.MARIO_DIMENSIONS.width();
        if (was != mario || mario != marioSized) player.refreshDimensions();
        if (was && !mario) {
            Services.NETWORK.sendToTracking(player, new Payloads.RemoteMario(player.getId(), Optional.empty()));
        }
    }

    private static Payloads.MarioRules rules(MinecraftServer server) {
        return new Payloads.MarioRules(FORCE_MARIO.get(server), ALLOW_MARIO.get(server),
                Retro64PlusConfig.server().allowedCharacterBits());
    }

    /**
     * Once a server tick (from any player's tick): a changed rule reaches every client, and with Mario no longer allowed
     * everyone turns back. Polled, as the ways to hear of a change differ between versions.
     */
    public static void checkRules(MinecraftServer server) {
        long tick = server.getTickCount();
        if (tick == rulesCheckedAt) return;
        rulesCheckedAt = tick;
        boolean force = FORCE_MARIO.get(server), allow = ALLOW_MARIO.get(server);
        if (force == lastForce && allow == lastAllow) return;
        if (!allow && lastAllow) server.getPlayerList().getPlayers().forEach(p -> setMario(p, false));
        lastForce = force;
        lastAllow = allow;
        broadcastRules(server);
    }

    private static void broadcastRules(MinecraftServer server) {
        Payloads.MarioRules rules = rules(server);
        server.getPlayerList().getPlayers().forEach(p -> Services.NETWORK.sendToPlayer(p, rules));
    }
}
