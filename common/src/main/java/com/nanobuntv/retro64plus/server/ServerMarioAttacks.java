package com.nanobuntv.retro64plus.server;

import com.nanobuntv.retro64plus.util.Compat;
import com.nanobuntv.retro64plus.MarioHooks;
import com.nanobuntv.retro64plus.combat.MarioAttack;
import com.nanobuntv.retro64plus.config.Retro64PlusConfig;
import com.nanobuntv.retro64plus.net.Payloads;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Applies the attacks Mario players report (D29), as vanilla trusts a client's "I hit that" within limits: the target
 * must be a living entity near the player, the attack must fit one of the player's last actions
 * ({@link MarioAttack#possibleIn}), and one attacker hits one target at most every {@link #COOLDOWN_TICKS}. Damage is a
 * player attack, so mobs fight back and drop loot, and players are only hurt where PvP is on. Server thread.
 */
public final class ServerMarioAttacks {
    /** Furthest a target's box may be from the player (blocks): Mario's reach plus a tick of movement either side. */
    private static final double MAX_DISTANCE = 4;
    private static final int COOLDOWN_TICKS = 8;

    /** Per attacker: target entity id → game time of the last hit. */
    private static final Map<UUID, Map<Integer, Long>> LAST_HIT = new HashMap<>();

    private ServerMarioAttacks() {}

    public static void onAttack(Payloads.Attack payload, ServerPlayer player) {
        MarioAttack attack = MarioAttack.byId(payload.attack());
        if (attack == null || !MarioHooks.isServerMario(player) || !player.isAlive() || player.isSpectator()) return;
        Entity entity = Compat.level(player).getEntity(payload.entityId());
        if (!(entity instanceof LivingEntity target) || target == player || !target.isAlive()) return;
        if (target.getBoundingBox().distanceToSqr(player.position()) > MAX_DISTANCE * MAX_DISTANCE) return;
        if (!fitsRecentAction(player, attack)) return;
        long now = Compat.level(player).getGameTime();
        Map<Integer, Long> hits = LAST_HIT.computeIfAbsent(player.getUUID(), uuid -> new HashMap<>());
        Long last = hits.get(target.getId());
        if (last != null && now - last < COOLDOWN_TICKS) return;
        hits.put(target.getId(), now);
        if (hits.size() > 64) hits.values().removeIf(t -> now - t >= COOLDOWN_TICKS);

        // SM64's enemies take Mario's attacks their own way (D74).
        if (target instanceof com.nanobuntv.retro64plus.enemies.Sm64Enemy enemy && enemy.onMarioAttack(player, attack)) {
            return;
        }
        float damage = (float) (attack.damage * Retro64PlusConfig.server().attackDamageMultiplier);
        if (damage <= 0) return;
        boolean hurt = attack == MarioAttack.GROUND_POUND
                ? ServerCoins.groundPoundHurt(() -> Compat.hurt(target, player.damageSources().playerAttack(player), damage))
                : Compat.hurt(target, player.damageSources().playerAttack(player), damage);
        if (hurt) {
            Vec3 away = target.position().subtract(player.position()).multiply(1, 0, 1);
            if (away.lengthSqr() > 1e-6) {
                away = away.normalize();
                Compat.knockback(target, attack.knockback, -away.x, -away.z);
            }
            player.setLastHurtMob(target);
        }
    }

    private static boolean fitsRecentAction(ServerPlayer player, MarioAttack attack) {
        for (int action : ServerMarios.recentActions(player)) {
            if (action != 0 && attack.possibleIn(action)) return true;
        }
        return false;
    }

    static void forget(ServerPlayer player) {
        LAST_HIT.remove(player.getUUID());
    }
}
