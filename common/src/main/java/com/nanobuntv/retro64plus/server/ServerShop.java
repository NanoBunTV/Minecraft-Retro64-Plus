package com.nanobuntv.retro64plus.server;

import com.nanobuntv.retro64plus.util.Compat;
import com.nanobuntv.retro64plus.caps.Retro64PlusItems;
import com.nanobuntv.retro64plus.config.Retro64PlusConfig;
import com.nanobuntv.retro64plus.config.ShopOffer;
import com.nanobuntv.retro64plus.net.Payloads;
import com.nanobuntv.retro64plus.platform.Services;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The Coin Shop on the server (D58–D62, D68–D71): using the block sends its offers (the server settings' list, unknown
 * items left out); a purchase is checked against the shop the player opened (still there, within reach), their coins
 * and their stars, then paid for and handed over. Server thread.
 */
public final class ServerShop {
    /** How far from the shop a purchase still counts (blocks, to its centre). */
    private static final double REACH = 8;

    private record OpenShop(ResourceKey<Level> dimension, BlockPos pos, List<Payloads.Offer> offers) {}

    private static final Map<UUID, OpenShop> OPEN = new HashMap<>();

    private ServerShop() {}

    /** Common init. */
    public static void init() {
        Services.PLATFORM.onPlayerLeave(player -> OPEN.remove(player.getUUID()));
    }

    /** {@code player} used the shop at {@code pos}. */
    public static void open(ServerPlayer player, BlockPos pos) {
        List<Payloads.Offer> offers = offers();
        OPEN.put(player.getUUID(), new OpenShop(player.level().dimension(), pos.immutable(), offers));
        Services.NETWORK.sendToPlayer(player, new Payloads.ShopOffers(offers));
    }

    /** The configured offers that name a real item. */
    static List<Payloads.Offer> offers() {
        List<Payloads.Offer> offers = new ArrayList<>();
        for (ShopOffer offer : Retro64PlusConfig.server().shopOffers) {
            Item item = item(offer.item);
            if (item == null) continue;
            offers.add(new Payloads.Offer(new ItemStack(item, offer.count), offer.price, offer.stars));
        }
        return offers;
    }

    private static @Nullable Item item(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) return null;
        Item item = BuiltInRegistries.ITEM.get(key);
        return item == Items.AIR ? null : item;
    }

    public static void onBuy(Payloads.ShopBuy payload, ServerPlayer player) {
        OpenShop shop = OPEN.get(player.getUUID());
        if (shop == null || !player.isAlive() || player.isSpectator()) return;
        if (!player.level().dimension().equals(shop.dimension())
                || !player.level().getBlockState(shop.pos()).is(Retro64PlusItems.COIN_SHOP.get())
                || player.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(shop.pos())) > REACH * REACH) {
            OPEN.remove(player.getUUID());
            return;
        }
        if (payload.index() < 0 || payload.index() >= shop.offers().size()) return;
        Payloads.Offer offer = shop.offers().get(payload.index());
        if (ServerStars.count(player) < offer.stars()) {
            Compat.overlay(player, Component.translatable("message.retro64plus.shop.stars", offer.stars()));
            return;
        }
        if (ServerCoins.coins(player) < offer.price()) {
            Compat.overlay(player, Component.translatable("message.retro64plus.shop.coins", offer.price()));
            return;
        }
        ServerCoins.add(player, -offer.price());
        ItemStack bought = offer.stack().copy();
        player.getInventory().add(bought); // takes what fits
        if (!bought.isEmpty()) Compat.drop(player, bought);
        ServerSounds.play(player, net.minecraft.world.phys.Vec3.atCenterOf(shop.pos()), ServerSounds.COIN); // D108
    }
}
