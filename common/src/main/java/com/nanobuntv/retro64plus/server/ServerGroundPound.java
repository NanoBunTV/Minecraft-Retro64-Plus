package com.nanobuntv.retro64plus.server;

import com.nanobuntv.retro64plus.util.Compat;
import com.nanobuntv.retro64plus.MarioHooks;
import com.nanobuntv.retro64plus.combat.GroundPoundTags;
import com.nanobuntv.retro64plus.config.Retro64PlusConfig;
import com.nanobuntv.retro64plus.net.Payloads;
import com.nanobuntv.retro64plus.sm64.Actions;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A Mario player's ground pound landing (D18, D46–D49), reported by the client ({@link Payloads.GroundPound}) and
 * checked like the attacks: Mario, close to the player, standing on something, this tick's pose a ground pound landing,
 * once per {@link #COOLDOWN_TICKS}. Then: the one soft block he landed on breaks (with a plant he landed in) as if the player broke
 * it by hand; a button or lever he landed on is pressed; and a small shockwave hurts and pushes the mobs around.
 * Pressure plates need nothing: Mario's player box presses them as he stands there. Server thread.
 */
public final class ServerGroundPound {
    /**
     * Furthest the reported landing may be from the player, across and up or down (blocks): the server has the position
     * of the tick before, and a pound falls about 0.7 blocks a tick.
     */
    private static final double MAX_DISTANCE = 1.5;
    private static final int COOLDOWN_TICKS = 10;
    /** Shockwave reach (blocks from the landing) and damage at its middle, half that at the edge (D48). */
    static final double SHOCKWAVE_RADIUS = 2.5;
    static final float SHOCKWAVE_DAMAGE = 3;
    private static final float SHOCKWAVE_KNOCKBACK = 0.6f;

    private static final Map<UUID, Long> LAST_POUND = new HashMap<>();

    private ServerGroundPound() {}

    public static void onLanding(Payloads.GroundPound payload, ServerPlayer player) {
        if (!MarioHooks.isServerMario(player) || !player.isAlive() || player.isSpectator()) return;
        Vec3 at = new Vec3(payload.x(), payload.y(), payload.z());
        if (!Double.isFinite(at.x) || !Double.isFinite(at.y) || !Double.isFinite(at.z)) return;
        Vec3 offset = at.subtract(player.position());
        if (offset.horizontalDistanceSqr() > MAX_DISTANCE * MAX_DISTANCE || Math.abs(offset.y) > MAX_DISTANCE) return;
        // The payload follows this tick's pose, which must be the landing.
        if (ServerMarios.recentActions(player)[0] != Actions.ACT_GROUND_POUND_LAND) return;
        ServerLevel level = Compat.level(player);
        // The Chain Chomp's post (D94): it stands taller than a block, so it's checked before the floor below.
        if (!onCooldown(player, level) && com.nanobuntv.retro64plus.enemies.ChompPostBlock.onGroundPound(level, player, at)) {
            LAST_POUND.put(player.getUUID(), level.getGameTime());
            shockwave(level, player, at);
            return;
        }
        BlockPos feet = BlockPos.containing(at.x, at.y + 0.01, at.z);
        BlockPos under = BlockPos.containing(at.x, at.y - 0.01, at.z);
        BlockState floor = level.getBlockState(under);
        if (floor.getCollisionShape(level, under).isEmpty()) return; // nothing to land on there
        long now = level.getGameTime();
        Long last = LAST_POUND.get(player.getUUID());
        if (last != null && now - last < COOLDOWN_TICKS) return;
        LAST_POUND.put(player.getUUID(), now);

        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, floor), at.x, at.y + 0.1, at.z, 24,
                0.8, 0.05, 0.8, 0.15);
        press(level, player, feet, at);
        if (Retro64PlusConfig.server().groundPoundBreaksBlocks) {
            breakSoft(level, player, feet);
            if (!under.equals(feet)) breakSoft(level, player, under);
        }
        shockwave(level, player, at);
    }

    private static boolean onCooldown(ServerPlayer player, ServerLevel level) {
        Long last = LAST_POUND.get(player.getUUID());
        return last != null && level.getGameTime() - last < COOLDOWN_TICKS;
    }

    /**
     * A button or lever under his feet, used as if the player clicked it with an empty hand: through the game mode, so
     * the loaders' use events (claim mods) and adventure mode apply. Sneaking (Z) would skip the block's use, so it's
     * off for the call.
     */
    private static void press(ServerLevel level, ServerPlayer player, BlockPos pos, Vec3 at) {
        if (!level.getBlockState(pos).is(GroundPoundTags.PRESSABLE) || !level.mayInteract(player, pos)) return;
        boolean sneaking = player.isShiftKeyDown();
        player.setShiftKeyDown(false);
        try {
            player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                    new BlockHitResult(at, Direction.UP, pos, false));
        } finally {
            player.setShiftKeyDown(sneaking);
        }
    }

    /**
     * Breaks a soft block as the player would with an empty hand (D47): the loaders' break events (protection mods),
     * adventure mode and drops all as usual; spawn protection is checked here, as vanilla does before breaking. The
     * player's own client predicted nothing, so it gets the break effects the server leaves out for the breaker.
     */
    private static void breakSoft(ServerLevel level, ServerPlayer player, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(GroundPoundTags.BREAKABLE) || !level.mayInteract(player, pos)) return;
        ItemStack held = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        boolean broken = false;
        try {
            broken = player.gameMode.destroyBlock(pos);
        } finally {
            // A pickup mod may have put a drop into the borrowed slot: keep it.
            ItemStack landed = player.getMainHandItem();
            if (player.isDeadOrDying() && !ModGameRules.keepInventory(level)) {
                Compat.dropAround(player, held); // the inventory already dropped; this player entity is done
            } else {
                player.setItemInHand(InteractionHand.MAIN_HAND, held);
            }
            if (!landed.isEmpty()) Compat.placeBack(player.getInventory(), landed);
        }
        if (broken) player.connection.send(new ClientboundLevelEventPacket(2001, pos, Block.getId(state), false));
    }

    /**
     * Hurts and pushes away the living things around the landing, as the player's attack (PvP rules apply). Villagers
     * and other players' pets are left alone, as are the player's own pets and teammates.
     */
    private static void shockwave(ServerLevel level, ServerPlayer player, Vec3 at) {
        double multiplier = Retro64PlusConfig.server().attackDamageMultiplier;
        AABB area = new AABB(at, at).inflate(SHOCKWAVE_RADIUS, 1, SHOCKWAVE_RADIUS);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (target == player || !target.isAlive() || target.isSpectator()) continue;
            if (target instanceof ArmorStand stand && stand.isMarker()) continue;
            if (target instanceof AbstractVillager) continue;
            if (target instanceof OwnableEntity pet && pet.getOwnerUUID() != null) continue;
            if (player.isAlliedTo(target) || target.isAlliedTo(player)) continue;
            Vec3 away = target.position().subtract(at).multiply(1, 0, 1);
            double distance = away.length();
            if (distance > SHOCKWAVE_RADIUS) continue;
            float damage = (float) (shockwaveDamage(distance) * multiplier);
            // Not hurt (immune, PvP off, harmless setting): no push either.
            if (damage <= 0) continue;
            if (!ServerCoins.groundPoundHurt(() -> Compat.hurt(target, player.damageSources().playerAttack(player), damage))) {
                continue;
            }
            if (distance > 1e-3) {
                Compat.knockback(target, SHOCKWAVE_KNOCKBACK, -away.x / distance, -away.z / distance);
            }
            player.setLastHurtMob(target);
        }
    }

    /** Shockwave damage {@code distance} blocks from the landing: full at the middle, half at the edge. */
    static float shockwaveDamage(double distance) {
        if (distance > SHOCKWAVE_RADIUS) return 0;
        return (float) (SHOCKWAVE_DAMAGE * (1 - 0.5 * distance / SHOCKWAVE_RADIUS));
    }

    static void forget(ServerPlayer player) {
        LAST_POUND.remove(player.getUUID());
    }
}
