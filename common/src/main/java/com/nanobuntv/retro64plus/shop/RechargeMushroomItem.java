package com.nanobuntv.retro64plus.shop;

import com.nanobuntv.retro64plus.util.Compat;
import com.nanobuntv.retro64plus.caps.CapCharge;
import com.nanobuntv.retro64plus.caps.CapItem;
import com.nanobuntv.retro64plus.caps.Retro64PlusItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
//? if <1.21.2 {
import net.minecraft.world.InteractionResultHolder;
//?}
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The Recharge Mushroom (D70, our own name and art): used, it makes a recharging cap ready at once. The cap in the
 * other hand first, otherwise the first recharging cap in the inventory. Bought in the Coin Shop.
 */
public final class RechargeMushroomItem extends Item {
    public RechargeMushroomItem(Properties properties) {
        super(properties);
    }

    //? if >=1.21.2 {
    /*@Override
    public net.minecraft.world.InteractionResult use(Level level, Player player, InteractionHand hand) {
        return useMushroom(level, player, hand) ? net.minecraft.world.InteractionResult.SUCCESS
                : net.minecraft.world.InteractionResult.FAIL;
    }
    *///?} else {
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack mushroom = player.getItemInHand(hand);
        return useMushroom(level, player, hand) ? InteractionResultHolder.sidedSuccess(mushroom, level.isClientSide())
                : InteractionResultHolder.fail(mushroom);
    }
    //?}

    /** Recharges a cap (server); false if there's none to recharge. */
    private boolean useMushroom(Level level, Player player, InteractionHand hand) {
        ItemStack mushroom = player.getItemInHand(hand);
        ItemStack cap = recharging(player, hand, level.getGameTime());
        if (cap == null) {
            if (!level.isClientSide()) {
                Compat.overlay(player, Component.translatable("message.retro64plus.mushroom.nothing"));
            }
            return false;
        }
        if (!level.isClientSide()) {
            cap.remove(Retro64PlusItems.CAP_CHARGE.get());
            mushroom.consume(1, player);
            // SM64's 1-up jingle (D108).
            com.nanobuntv.retro64plus.server.ServerSounds.play(player, player.position(),
                    com.nanobuntv.retro64plus.server.ServerSounds.ONE_UP);
            Compat.overlay(player, Component.translatable("message.retro64plus.mushroom.recharged",
                    cap.getHoverName()));
        }
        return true;
    }

    /** The cap to recharge: in the other hand, else the first recharging one in the inventory. */
    private static @Nullable ItemStack recharging(Player player, InteractionHand hand, long now) {
        ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (isRecharging(other, now)) return other;
        for (ItemStack stack : Compat.mainItems(player.getInventory())) {
            if (isRecharging(stack, now)) return stack;
        }
        return null;
    }

    private static boolean isRecharging(ItemStack stack, long now) {
        if (!(stack.getItem() instanceof CapItem)) return false;
        CapCharge charge = stack.get(Retro64PlusItems.CAP_CHARGE.get());
        return charge != null && !charge.ready(now);
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
        tooltip.accept(Component.translatable("item.retro64plus.recharge_mushroom.description")
                .withStyle(ChatFormatting.GRAY));
    }
}
