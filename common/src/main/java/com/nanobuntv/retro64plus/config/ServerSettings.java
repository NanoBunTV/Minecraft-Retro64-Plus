package com.nanobuntv.retro64plus.config;

import com.nanobuntv.retro64plus.MarioCharacter;
import java.util.ArrayList;
import java.util.List;

/**
 * The server's settings (D34): {@code config/retro64plus-server.json5}, read when a server (or a singleplayer world)
 * starts. Who may be Mario at all is the game rules' job (D14).
 */
public final class ServerSettings {
    @SerialEntry(comment = "Multiplies the damage Mario's attacks deal (punches, kicks, dives, ground pounds, stomps).\n"
            + "1.0 is the mod's default, scaled to vanilla weapons. 0 makes them harmless.")
    public double attackDamageMultiplier = 1.0;

    @SerialEntry(comment = "Characters players may choose: any of \"mario\", \"luigi\", \"skin\" (their own skin on\n"
            + "Mario's skeleton). A player who picked another one plays as the first of these.")
    public List<String> allowedCharacters = new ArrayList<>(List.of("mario", "luigi", "skin"));

    @SerialEntry(comment = "Minutes a cap takes to recharge after it runs out (D35). 0: ready again at once.")
    public double capRechargeMinutes = 5.0;

    @SerialEntry(comment = "Whether a ground pound breaks the soft block Mario lands on (the\n"
            + "retro64plus:ground_pound_breakable block tag: glass, leaves, plants, ice, snow). Player rules apply\n"
            + "either way: spawn protection, adventure mode and protection mods stop it.")
    public boolean groundPoundBreaksBlocks = true;

    @SerialEntry(comment = "Coins a hostile mob drops when a Mario player kills it: a random amount from min to max\n"
            + "(D52). Mobs in the retro64plus:coin_strong entity tag drop strongMobCoins, those in\n"
            + "retro64plus:coin_bosses drop bossCoins. 0 turns that source off.")
    public int mobCoinsMin = 1;

    @SerialEntry(comment = "See mobCoinsMin.")
    public int mobCoinsMax = 3;

    @SerialEntry(comment = "See mobCoinsMin. By default an Enderman, an Evoker, a Ravager, a Piglin Brute, a Breeze.")
    public int strongMobCoins = 5;

    @SerialEntry(comment = "See mobCoinsMin. By default the Ender Dragon, the Wither, the Warden, an Elder Guardian.")
    public int bossCoins = 20;

    @SerialEntry(comment = "Extra coins when a ground pound (or its shockwave) makes the kill.")
    public int groundPoundKillCoins = 1;

    @SerialEntry(comment = "Chance (0 to 1) that a block in the retro64plus:coin_blocks tag (grass, leaves, ores) drops a\n"
            + "coin when a Mario player breaks it.")
    public double blockCoinChance = 0.1;

    @SerialEntry(comment = "Coins that burst out of a structure chest the first time it's opened (with Lootr: each\n"
            + "player's first time): a random amount from min to max. 0 and 0 turn it off.")
    public int chestCoinsMin = 5;

    @SerialEntry(comment = "See chestCoinsMin.")
    public int chestCoinsMax = 15;

    @SerialEntry(comment = "Coins a player must collect in one dimension without dying for that dimension's Power Star\n"
            + "(D61; once per dimension). 0 turns these stars off.")
    public int coinStarCoins = 100;

    @SerialEntry(comment = "Power Star unlocks (D66): each star makes caps last this many percent longer...")
    public double starCapDurationPercent = 3.0;

    @SerialEntry(comment = "...up to this many percent longer in all.")
    public double starCapDurationMaxPercent = 60.0;

    @SerialEntry(comment = "Each star makes a cap recharge this many percent faster...")
    public double starRechargePercent = 3.0;

    @SerialEntry(comment = "...up to this many percent faster in all (100: no recharge at all).")
    public double starRechargeMaxPercent = 60.0;

    @SerialEntry(comment = "What the Coin Shop sells (D59, D69), in this order: \"item\" (an item id), \"count\", \"price\"\n"
            + "(coins) and \"stars\" (Power Stars the buyer must own). An unknown item is left out.")
    public List<ShopOffer> shopOffers = ShopOffer.defaults();

    @SerialEntry(comment = "SM64 enemies' natural spawns (D73, D84): 1 is the normal rate (about as common as spiders\n"
            + "between them), 0.5 half that, 0 none. Spawn eggs and commands always work.")
    public double enemySpawnRate = 1.0;

    @SerialEntry(comment = "Check that Mario players' moves are plausible (at most 3 blocks across and 4 up or down\n"
            + "per tick) before skipping vanilla's collision checks for them. Turn off only on servers that trust\n"
            + "their players: every Mario move then skips them (vanilla's speed check still applies).")
    public boolean marioMoveCheck = true;

    /** Allowed characters as {@link MarioCharacter#bit()}s; never empty (Mario if none are valid). */
    public int allowedCharacterBits() {
        int bits = 0;
        for (String key : allowedCharacters) {
            MarioCharacter c = key == null ? null : MarioCharacter.byKey(key);
            if (c != null) bits |= c.bit();
        }
        return bits == 0 ? MarioCharacter.MARIO.bit() : bits;
    }

    private static double percent(double value, double fallback, double max) {
        return Double.isFinite(value) ? Math.clamp(value, 0, max) : fallback;
    }

    public double capDurationMultiplier(int stars) {
        return com.nanobuntv.retro64plus.caps.StarBonus.durationMultiplier(stars, starCapDurationPercent,
                starCapDurationMaxPercent);
    }

    public double rechargeMultiplier(int stars) {
        return com.nanobuntv.retro64plus.caps.StarBonus.rechargeMultiplier(stars, starRechargePercent,
                starRechargeMaxPercent);
    }

    /** A missing or broken value in a hand-edited file reads as its default, not a crash. */
    /** The most offers the shop's network message carries (Payloads.ShopOffers). */
    public static final int MAX_SHOP_OFFERS = 256;

    public ServerSettings clean() {
        if (!Double.isFinite(attackDamageMultiplier) || attackDamageMultiplier < 0) attackDamageMultiplier = 1.0;
        attackDamageMultiplier = Math.min(attackDamageMultiplier, 100);
        if (!Double.isFinite(capRechargeMinutes) || capRechargeMinutes < 0) capRechargeMinutes = 5.0;
        capRechargeMinutes = Math.min(capRechargeMinutes, 24 * 60);
        mobCoinsMin = Math.clamp(mobCoinsMin, 0, 1000);
        mobCoinsMax = Math.clamp(mobCoinsMax, mobCoinsMin, 1000);
        strongMobCoins = Math.clamp(strongMobCoins, 0, 1000);
        bossCoins = Math.clamp(bossCoins, 0, 10000);
        groundPoundKillCoins = Math.clamp(groundPoundKillCoins, 0, 1000);
        if (!Double.isFinite(blockCoinChance)) blockCoinChance = 0.1;
        blockCoinChance = Math.clamp(blockCoinChance, 0, 1);
        chestCoinsMin = Math.clamp(chestCoinsMin, 0, 1000);
        chestCoinsMax = Math.clamp(chestCoinsMax, chestCoinsMin, 1000);
        coinStarCoins = Math.clamp(coinStarCoins, 0, 1_000_000);
        starCapDurationPercent = percent(starCapDurationPercent, 3.0, 1000);
        starCapDurationMaxPercent = percent(starCapDurationMaxPercent, 60.0, 1000);
        starRechargePercent = percent(starRechargePercent, 3.0, 100);
        starRechargeMaxPercent = percent(starRechargeMaxPercent, 60.0, 100);
        if (!Double.isFinite(enemySpawnRate)) enemySpawnRate = 1.0;
        enemySpawnRate = Math.clamp(enemySpawnRate, 0, 1);
        if (shopOffers == null) shopOffers = ShopOffer.defaults();
        shopOffers.removeIf(o -> o == null || o.item == null || o.item.isBlank());
        if (shopOffers.size() > MAX_SHOP_OFFERS) shopOffers = new ArrayList<>(shopOffers.subList(0, MAX_SHOP_OFFERS));
        for (ShopOffer offer : shopOffers) {
            offer.count = Math.clamp(offer.count, 1, 64);
            offer.price = Math.clamp(offer.price, 0, 1_000_000);
            offer.stars = Math.clamp(offer.stars, 0, 10_000);
        }
        if (allowedCharacters == null) allowedCharacters = new ArrayList<>(List.of("mario", "luigi", "skin"));
        return this;
    }
}
