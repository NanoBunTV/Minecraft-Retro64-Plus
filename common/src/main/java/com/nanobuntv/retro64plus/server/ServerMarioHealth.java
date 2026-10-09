package com.nanobuntv.retro64plus.server;

import com.nanobuntv.retro64plus.util.Compat;
import com.nanobuntv.retro64plus.MarioHooks;
import com.nanobuntv.retro64plus.health.MarioHealth;
import com.nanobuntv.retro64plus.net.Payloads;
import com.nanobuntv.retro64plus.sm64.Actions;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;

/**
 * Mario's health on the server (D36–D38). Health is Minecraft's; SM64 adds two things: its own damage (falls, lava),
 * which the client reports ({@link Payloads.SelfDamage}) and this applies as Minecraft damage of that kind, and
 * healing while swimming at the water surface, which this works out itself every tick. Server thread.
 */
public final class ServerMarioHealth {
    /** SM64's biggest own damage: 24 hurt-counter steps of 0x40 (a fall of over 3000 units without a cap). */
    private static final int MAX_SELF_DAMAGE = 24 * 0x40;
    /**
     * At most {@link #MAX_SELF_DAMAGE} per player in this many ticks: a fall onto lava, or two hits close together, both
     * count, but a client can't report more than SM64's worst landing per half second.
     */
    private static final int SELF_DAMAGE_TICKS = 10;

    /** Per player: when the current window started, and the units reported in it. */
    private record Budget(long start, int used) {}

    private static final Map<UUID, Budget> SELF_DAMAGE = new HashMap<>();

    private ServerMarioHealth() {}

    public static void onSelfDamage(Payloads.SelfDamage payload, ServerPlayer player) {
        if (!MarioHooks.isServerMario(player) || !player.isAlive() || payload.units() <= 0) return;
        long now = Compat.level(player).getGameTime();
        Budget budget = SELF_DAMAGE.get(player.getUUID());
        if (budget == null || now - budget.start() >= SELF_DAMAGE_TICKS) budget = new Budget(now, 0);
        int units = Math.min(payload.units(), MAX_SELF_DAMAGE - budget.used());
        if (units <= 0) return;
        SELF_DAMAGE.put(player.getUUID(), new Budget(budget.start(), budget.used() + units));
        DamageSource source = payload.kind() == Payloads.SelfDamage.LAVA
                ? player.damageSources().lava() : player.damageSources().fall();
        Compat.hurt(player, source, MarioHealth.toHealth(units, player.getMaxHealth()));
    }

    public static void forget(ServerPlayer player) {
        SELF_DAMAGE.remove(player.getUUID());
    }

    /** End of every server player tick: SM64's healing at the water surface (D38). */
    public static void tick(ServerPlayer player) {
        if (!MarioHooks.isServerMario(player) || !player.isAlive() || player.getHealth() >= player.getMaxHealth()) {
            return;
        }
        int action = ServerMarios.recentActions(player)[0];
        if (!Actions.has(action, Actions.FLAG_SWIMMING) || Actions.has(action, Actions.FLAG_INTANGIBLE)) return;
        double surface = waterSurface(player.level(), player.blockPosition());
        if (Double.isNaN(surface) || player.getY() < surface - MarioHealth.SURFACE_DEPTH) return;
        player.heal(MarioHealth.toHealth(MarioHealth.SURFACE_HEAL_SEGMENTS_PER_TICK * MarioHealth.SEGMENT,
                player.getMaxHealth()));
    }

    /** The top of the water at {@code feet} (blocks), or NaN if there's no water there. */
    private static double waterSurface(Level level, BlockPos feet) {
        BlockPos.MutableBlockPos pos = feet.mutable();
        // Swimming at the surface, Mario's feet may be just above the water block's top.
        if (!level.getFluidState(pos).is(FluidTags.WATER)) pos.move(0, -1, 0);
        if (!level.getFluidState(pos).is(FluidTags.WATER)) return Double.NaN;
        while (level.getFluidState(pos.above()).is(FluidTags.WATER)) pos.move(0, 1, 0);
        return pos.getY() + level.getFluidState(pos).getHeight(level, pos);
    }
}
