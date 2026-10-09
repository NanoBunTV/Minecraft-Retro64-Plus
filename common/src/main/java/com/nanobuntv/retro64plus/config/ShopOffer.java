package com.nanobuntv.retro64plus.config;

import java.util.ArrayList;
import java.util.List;

/** One Coin Shop offer (D59, D69, D71), as written in the server settings. */
public final class ShopOffer {
    /** An item id, e.g. "minecraft:diamond" or "retro64plus:wing_cap". */
    public String item;
    public int count = 1;
    /** In coins. */
    public int price;
    /** Power Stars the buyer must own (D62). */
    public int stars;

    public ShopOffer() {}

    ShopOffer(String item, int count, int price, int stars) {
        this.item = item;
        this.count = count;
        this.price = price;
        this.stars = stars;
    }

    /** The defaults (D69). */
    static List<ShopOffer> defaults() {
        return new ArrayList<>(List.of(
                new ShopOffer("retro64plus:wing_cap", 1, 150, 1),
                new ShopOffer("retro64plus:metal_cap", 1, 250, 3),
                new ShopOffer("retro64plus:vanish_cap", 1, 250, 5),
                new ShopOffer("retro64plus:recharge_mushroom", 1, 40, 0),
                new ShopOffer("minecraft:golden_carrot", 1, 5, 0),
                new ShopOffer("minecraft:cooked_beef", 1, 3, 0),
                new ShopOffer("minecraft:golden_apple", 1, 60, 0),
                new ShopOffer("minecraft:emerald", 1, 10, 0),
                new ShopOffer("minecraft:diamond", 1, 200, 0),
                new ShopOffer("minecraft:ender_pearl", 1, 15, 0)));
    }
}
