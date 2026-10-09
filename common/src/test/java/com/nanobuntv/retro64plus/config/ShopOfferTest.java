package com.nanobuntv.retro64plus.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** D69, D71: the default stock, and hand-edited offers cleaned up instead of crashing. */
class ShopOfferTest {
    @Test
    void defaultsAreTheAgreedPrices() {
        List<ShopOffer> offers = ShopOffer.defaults();
        assertEquals(10, offers.size());
        ShopOffer wing = offers.get(0), diamond = find(offers, "minecraft:diamond");
        assertEquals("retro64plus:wing_cap", wing.item);
        assertEquals(150, wing.price);
        assertEquals(1, wing.stars);
        assertEquals(5, find(offers, "retro64plus:vanish_cap").stars);
        assertEquals(40, find(offers, "retro64plus:recharge_mushroom").price);
        assertEquals(200, diamond.price, "the user raised the diamond to 200");
    }

    @Test
    void brokenOffersAreCleaned() {
        ServerSettings settings = new ServerSettings();
        ShopOffer odd = new ShopOffer("minecraft:stick", 500, -3, -1);
        settings.shopOffers = new ArrayList<>(java.util.Arrays.asList(odd, null, new ShopOffer(null, 1, 1, 0)));
        settings.clean();
        assertEquals(1, settings.shopOffers.size());
        assertEquals(64, odd.count);
        assertEquals(0, odd.price);
        assertEquals(0, odd.stars);
        settings.shopOffers = null;
        settings.clean();
        assertEquals(10, settings.shopOffers.size());
    }

    private static ShopOffer find(List<ShopOffer> offers, String item) {
        return offers.stream().filter(o -> item.equals(o.item)).findFirst().orElseThrow();
    }
}
