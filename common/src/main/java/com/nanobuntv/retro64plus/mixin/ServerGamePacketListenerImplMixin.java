package com.nanobuntv.retro64plus.mixin;

import com.nanobuntv.retro64plus.util.Compat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nanobuntv.retro64plus.MarioHooks;
import com.nanobuntv.retro64plus.config.Retro64PlusConfig;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Server-side movement for Mario players (D13). Mario's moves come from libsm64, which vanilla physics can't reproduce:
 * he stands partly inside stair steps (world/CollisionBuilder.MAX_STEP) and swims where the player box wouldn't fit,
 * so the "collides with something new" and "moved wrongly" checks would teleport him back every tick. Instead, a Mario
 * move is accepted if it's plausible for SM64 (within {@link #MAX_HORIZONTAL} / {@link #MAX_VERTICAL} blocks of the
 * last position: his fastest moves cover about one block a tick) and his body's core, above any stair step he may
 * stand in, isn't inside a solid block (so a modified client can't walk through walls); with the server's
 * {@code marioMoveCheck} off (D34), every Mario move. Otherwise vanilla's checks run as usual. Flying with the Wing cap
 * isn't kicked for "flying".
 */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerImplMixin {
    @Unique
    private static final double MAX_HORIZONTAL = 3, MAX_VERTICAL = 4;
    /**
     * His body's core (blocks from his feet): a thin column above the highest stair step he stands in
     * (CollisionBuilder.MAX_STEP), below his 1.3-block head.
     */
    @Unique
    private static final double CORE_HALF_WIDTH = 0.1, CORE_BOTTOM = 0.6, CORE_TOP = 1.0;

    @Shadow
    public ServerPlayer player;

    @Shadow
    private boolean clientIsFloating;

    /** Whether the move packet being handled is a plausible Mario move. */
    @Unique
    private boolean retro64plus$marioMove;

    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void retro64plus$checkMarioMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        // handleMovePlayer first runs on the network thread and hands itself to the server thread; only that pass counts.
        if (!Compat.server(player).isSameThread()) return;
        retro64plus$marioMove = false;
        if (!MarioHooks.isServerMario(player)) return;
        if (!Retro64PlusConfig.server().marioMoveCheck) {
            retro64plus$marioMove = true;
            return;
        }
        double x = packet.getX(player.getX()), y = packet.getY(player.getY()), z = packet.getZ(player.getZ());
        double dx = x - player.getX(), dy = y - player.getY(), dz = z - player.getZ();
        retro64plus$marioMove = dx * dx + dz * dz <= MAX_HORIZONTAL * MAX_HORIZONTAL && Math.abs(dy) <= MAX_VERTICAL
                && !retro64plus$coreInBlock(x, y, z);
    }

    /**
     * Whether Mario's core at {@code x, y, z} is inside a block's collision shape. Under water he's let be (SM64 swims
     * him through gaps the core would graze); with the Vanish cap, its passable blocks don't count (D43).
     */
    @Unique
    private boolean retro64plus$coreInBlock(double x, double y, double z) {
        if (player.isInWater()) return false;
        boolean vanish = com.nanobuntv.retro64plus.server.ServerCaps.has(player,
                com.nanobuntv.retro64plus.caps.MarioCap.VANISH);
        AABB core = new AABB(x - CORE_HALF_WIDTH, y + CORE_BOTTOM, z - CORE_HALF_WIDTH,
                x + CORE_HALF_WIDTH, y + CORE_TOP, z + CORE_HALF_WIDTH);
        LevelReader level = player.level();
        for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(
                net.minecraft.util.Mth.floor(core.minX), net.minecraft.util.Mth.floor(core.minY),
                net.minecraft.util.Mth.floor(core.minZ), net.minecraft.util.Mth.floor(core.maxX),
                net.minecraft.util.Mth.floor(core.maxY), net.minecraft.util.Mth.floor(core.maxZ))) {
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
            if (vanish && state.is(com.nanobuntv.retro64plus.caps.CapTags.VANISH_PASSABLE)) continue;
            for (AABB box : state.getCollisionShape(level, pos).toAabbs()) {
                if (box.move(pos).intersects(core)) return true;
            }
        }
        return false;
    }

    //? if >=1.21.9 {
    /*@Inject(method = "isEntityCollidingWithAnythingNew", at = @At("HEAD"), cancellable = true)
    private void retro64plus$marioCollides(LevelReader level, Entity entity, AABB box, double x, double y, double z,
            CallbackInfoReturnable<Boolean> cir) {
        // Vehicles' moves come through here too: only the player's own move.
        if (retro64plus$marioMove && entity == player) cir.setReturnValue(false);
    }
    *///?} else {
    @Inject(method = "isPlayerCollidingWithAnythingNew", at = @At("HEAD"), cancellable = true)
    private void retro64plus$marioCollides(LevelReader level, AABB box, double x, double y, double z,
            CallbackInfoReturnable<Boolean> cir) {
        if (retro64plus$marioMove) cir.setReturnValue(false);
    }
    //?}

    /** "Moved wrongly" teleports back only where the old box was free; telling it the box wasn't free skips that. */
    //? if >=26.3 {
    /*@WrapOperation(method = "handlePlayerPositionChange", at = @At(value = "INVOKE",
    *///?} else {
    @WrapOperation(method = "handleMovePlayer", at = @At(value = "INVOKE",
    //?}
            target = "Lnet/minecraft/server/level/ServerLevel;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z"))
    private boolean retro64plus$marioMovedWrongly(ServerLevel level, Entity entity, AABB box, Operation<Boolean> original) {
        if (retro64plus$marioMove) return false;
        return original.call(level, entity, box);
    }

    /** Flying with the Wing cap (D17) isn't "flying" for the kick; anything else that hovers is, as for anyone. */
    @Inject(method = "handleMovePlayer", at = @At("TAIL"))
    private void retro64plus$marioNotFloating(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (MarioHooks.isServerMario(player) && com.nanobuntv.retro64plus.server.ServerCaps.has(player,
                com.nanobuntv.retro64plus.caps.MarioCap.WING)) {
            clientIsFloating = false;
        }
    }
}
