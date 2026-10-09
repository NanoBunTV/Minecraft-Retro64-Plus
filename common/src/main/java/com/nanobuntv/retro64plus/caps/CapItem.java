package com.nanobuntv.retro64plus.caps;

import com.nanobuntv.retro64plus.util.Compat;
import com.nanobuntv.retro64plus.MarioHooks;
import com.nanobuntv.retro64plus.config.Retro64PlusConfig;
import com.nanobuntv.retro64plus.server.ServerCaps;
import com.nanobuntv.retro64plus.server.ServerStars;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
//? if <1.21.2 {
import net.minecraft.world.InteractionResultHolder;
//?}
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A Wing, Metal or Vanish cap (D16, D17), used from the hand (D41): Mario puts it on and its timer starts (the server
 * owns it, {@link ServerCaps}); the item stays and recharges ({@link CapCharge}, D35) once the cap has run out. Only
 * Mario can use it.
 */
public final class CapItem extends Item {
    private final MarioCap cap;

    public CapItem(MarioCap cap, Properties properties) {
        super(properties);
        this.cap = cap;
    }

    public MarioCap cap() {
        return cap;
    }

    //? if >=1.21.2 {
    /*@Override
    public net.minecraft.world.InteractionResult use(Level level, Player player, InteractionHand hand) {
        return useCap(level, player, hand) ? net.minecraft.world.InteractionResult.SUCCESS
                : net.minecraft.world.InteractionResult.FAIL;
    }
    *///?} else {
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        return useCap(level, player, hand) ? InteractionResultHolder.success(stack) : InteractionResultHolder.fail(stack);
    }
    //?}

    /** Puts the cap on (server), or says whether it would (client); false if refused. */
    private boolean useCap(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        CapCharge charge = stack.get(Retro64PlusItems.CAP_CHARGE.get());
        if (level.isClientSide()) {
            return MarioHooks.isLocalMario(player) && (charge == null || charge.ready(level.getGameTime()));
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !MarioHooks.isServerMario(player)) {
            Compat.overlay(player, Component.translatable("message.retro64plus.cap.only_mario"));
            return false;
        }
        long now = level.getGameTime();
        if (charge != null && !charge.ready(now)) {
            Compat.overlay(player, Component.translatable("message.retro64plus.cap.recharging",
                    seconds(charge.readyAt() - now)));
            return false;
        }
        long duration = ServerCaps.start(serverPlayer, cap);
        if (!player.getAbilities().instabuild) {
            // Power Stars shorten the recharge (D66).
            long recharge = Math.round(Retro64PlusConfig.server().capRechargeMinutes * 60 * 20
                    * Retro64PlusConfig.server().rechargeMultiplier(ServerStars.count(serverPlayer)));
            stack.set(Retro64PlusItems.CAP_CHARGE.get(), new CapCharge(now, now + duration + recharge));
        }
        // SM64's cap pickup: its sound and "Here we go!" (D108).
        com.nanobuntv.retro64plus.server.ServerSounds.play(player, player.position(),
                com.nanobuntv.retro64plus.server.ServerSounds.CAP);
        com.nanobuntv.retro64plus.server.ServerSounds.play(player, player.position(),
                com.nanobuntv.retro64plus.server.ServerSounds.HERE_WE_GO);
        return true;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        CapCharge charge = stack.get(Retro64PlusItems.CAP_CHARGE.get());
        return charge != null && !charge.ready(CapCharge.clientTime);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        CapCharge charge = stack.get(Retro64PlusItems.CAP_CHARGE.get());
        return charge == null ? 13 : Math.round(13 * charge.progress(CapCharge.clientTime));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x5AB4FF;
    }

    //? if >=1.21.5 {
    /*@Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
            java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip(stack, tooltip);
    }
    *///?} else {
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip(stack, tooltip::add);
    }
    //?}

    private void tooltip(ItemStack stack, java.util.function.Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable("item.retro64plus." + cap.itemName + ".description")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.retro64plus.cap.duration",
                Math.round(cap.ticks * StarBonus.clientDuration / 20)).withStyle(ChatFormatting.DARK_GRAY));
        CapCharge charge = stack.get(Retro64PlusItems.CAP_CHARGE.get());
        if (charge != null && !charge.ready(CapCharge.clientTime)) {
            tooltip.accept(Component.translatable("item.retro64plus.cap.recharging",
                    seconds(charge.readyAt() - CapCharge.clientTime)).withStyle(ChatFormatting.BLUE));
        }
    }

    private static long seconds(long ticks) {
        return (ticks + 19) / 20;
    }
}
