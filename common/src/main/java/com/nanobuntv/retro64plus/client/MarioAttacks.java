package com.nanobuntv.retro64plus.client;

import com.nanobuntv.retro64plus.combat.MarioAttack;
import com.nanobuntv.retro64plus.net.Payloads;
import com.nanobuntv.retro64plus.sm64.Units;
import com.nanobuntv.retro64plus.sm64.lib.SM64MarioState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The local Mario's attacks (D29): after every SM64 frame, the living entities touching Mario (his SM64 hitbox, a
 * cylinder 37 units wide and 160 high, plus the attack's reach) are checked against SM64's rules
 * ({@link MarioAttack#determine}). Hits go to the server, which checks and applies them; a stomp bounces Mario off at
 * once, as {@code bounce_off_object} does. Each target is hit at most once per {@link #COOLDOWN_FRAMES}. A punch that
 * reaches a Bob-omb picks it up instead (M4).
 */
final class MarioAttacks {
    private static final double RADIUS = 37.0 / Units.PER_BLOCK, HEIGHT = 160.0 / Units.PER_BLOCK;
    /** The longest {@link MarioAttack#reach()}. */
    private static final double MAX_REACH = 1.0;
    /** SM64's bounce off a stomped enemy: 30 units a frame upwards. */
    private static final double BOUNCE = 30.0 / Units.PER_BLOCK;
    private static final int COOLDOWN_FRAMES = 12;

    private final Map<Integer, Long> lastHit = new HashMap<>();
    /** A punch that reaches something Mario can pick up (a Bob-omb) tries this first; true if he picked it up. */
    private final java.util.function.Predicate<Entity> grab;
    private final List<Payloads.Attack> pending = new ArrayList<>();
    /** Frames seen; Mario's own count restarts on teleports. */
    private long frames;
    /** Hits this session (dev diagnostics). */
    int hits;

    MarioAttacks(java.util.function.Predicate<Entity> grab) {
        this.grab = grab;
    }

    /** After each SM64 frame. */
    void afterFrame(MarioInstance mario, LocalPlayer player, Level level) {
        SM64MarioState s = mario.state;
        Vec3 pos = mario.position();
        AABB area = new AABB(pos.x - RADIUS, pos.y, pos.z - RADIUS, pos.x + RADIUS, pos.y + HEIGHT, pos.z + RADIUS)
                .inflate(MAX_REACH + 0.1);
        long frame = ++frames;
        boolean bounced = false;
        for (Entity entity : level.getEntities(player, area, e -> e instanceof LivingEntity && e.isAlive()
                && !e.isSpectator() && e != player.getVehicle() && !e.hasPassenger(player))) {
            Long last = lastHit.get(entity.getId());
            if (last != null && frame - last < COOLDOWN_FRAMES) continue;
            AABB box = entity.getBoundingBox();
            double dx = (box.minX + box.maxX) / 2 - pos.x, dz = (box.minZ + box.maxZ) / 2 - pos.z;
            double gap = Math.sqrt(dx * dx + dz * dz) - RADIUS - entity.getBbWidth() / 2;
            boolean overlapsVertically = pos.y <= box.maxY + 0.1 && pos.y + HEIGHT >= box.minY;
            if (!overlapsVertically) continue;
            boolean fromAbove = pos.y > box.minY + entity.getBbHeight() * 0.5;
            MarioAttack attack = MarioAttack.determine(s.action, s.flags, s.faceAngle, s.velocity[0], s.velocity[1],
                    s.velocity[2], dx, dz, fromAbove);
            if (attack == null || gap > attack.reach()) continue;
            lastHit.put(entity.getId(), frame);
            if (attack == MarioAttack.PUNCH && grab.test(entity)) continue; // SM64: B at a grabbable object picks it up
            pending.add(new Payloads.Attack(entity.getId(), attack.ordinal()));
            hits++;
            if (attack == MarioAttack.STOMP && !bounced) {
                bounced = true;
                Vec3 v = mario.velocity();
                mario.setVelocity(new Vec3(v.x, BOUNCE, v.z));
            }
        }
        if (lastHit.size() > 64) lastHit.values().removeIf(t -> frame - t >= COOLDOWN_FRAMES);
    }

    /** The hits since the last call, to send after this tick's state. */
    List<Payloads.Attack> drain() {
        if (pending.isEmpty()) return List.of();
        List<Payloads.Attack> out = List.copyOf(pending);
        pending.clear();
        return out;
    }
}
