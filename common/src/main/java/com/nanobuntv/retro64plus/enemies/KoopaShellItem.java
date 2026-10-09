package com.nanobuntv.retro64plus.enemies;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/** The Koopa shell as an item (D88): used on a block, it's put down there for Mario to ride. Our own art. */
public class KoopaShellItem extends Item {
    public KoopaShellItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        Vec3 at = context.getClickedFace() == net.minecraft.core.Direction.UP ? context.getClickLocation()
                : Vec3.atBottomCenterOf(pos);
        KoopaShellEntity shell = new KoopaShellEntity(level, at);
        if (!level.noCollision(shell, shell.getBoundingBox())) return InteractionResult.FAIL;
        if (level instanceof ServerLevel server) {
            server.addFreshEntity(shell);
            level.gameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, at);
            if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
