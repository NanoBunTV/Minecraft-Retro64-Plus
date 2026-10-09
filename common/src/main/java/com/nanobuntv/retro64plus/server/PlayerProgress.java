package com.nanobuntv.retro64plus.server;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
//? if >=1.21.5 {
/*import net.minecraft.world.level.saveddata.SavedDataType;
*///?} else {
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
//?}

/**
 * What each player has collected (M3): coins (D50), the Power Stars they own (D63, by id), and the coins collected in
 * each dimension since their last death (for the 100-coin stars, D61). Saved with the world
 * ({@code data/retro64plus_players.dat} in the Overworld), not with the player, so it works the same on both loaders
 * and survives death and dimension changes. One codec on every Minecraft version (a CompoundTag through NbtOps up to
 * 1.21.4, a saved data type after). Server thread.
 */
public final class PlayerProgress extends SavedData {
    private static final String NAME = "retro64plus_players";
    private static final class Entry {
        int coins;
        final Set<String> stars = new LinkedHashSet<>();
        /** Dimension id → coins collected there since the last death. */
        final Map<String, Integer> lifeCoins = new HashMap<>();
    }

    private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("Coins", 0).forGetter(e -> e.coins),
            Codec.STRING.listOf().optionalFieldOf("Stars", List.of()).forGetter(e -> List.copyOf(e.stars)),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("LifeCoins", Map.of()).forGetter(e -> e.lifeCoins)
    ).apply(i, (coins, stars, life) -> {
        Entry e = new Entry();
        e.coins = Math.max(0, coins);
        e.stars.addAll(stars);
        life.forEach((dimension, count) -> e.lifeCoins.put(dimension, Math.max(0, count)));
        return e;
    }));
    /** {"Players": {uuid: {"Coins", "Stars", "LifeCoins"}}}, as the first (tag-written) version of the file. */
    private static final Codec<PlayerProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, ENTRY_CODEC).optionalFieldOf("Players", Map.of()).forGetter(p -> {
                Map<String, Entry> byId = new LinkedHashMap<>();
                p.players.forEach((uuid, entry) -> byId.put(uuid.toString(), entry));
                return byId;
            })
    ).apply(i, byId -> {
        PlayerProgress progress = new PlayerProgress();
        byId.forEach((key, entry) -> {
            try {
                progress.players.put(UUID.fromString(key), entry);
            } catch (IllegalArgumentException ignored) {
                // not a player's
            }
        });
        return progress;
    }));

    /*
     * Vanilla runs every saved file through a data fixer and has no "none" (NeoForge allows null, Fabric doesn't).
     * Command storage's fixes don't touch this file's keys, and a file of the current version isn't fixed at all.
     */
    //? if >=26.1 {
    /*private static final SavedDataType<PlayerProgress> TYPE = new SavedDataType<>(
            net.minecraft.resources.ResourceLocation.withDefaultNamespace(NAME), PlayerProgress::new, CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    *///?} else if >=1.21.5 {
    /*private static final SavedDataType<PlayerProgress> TYPE = new SavedDataType<>(NAME, PlayerProgress::new, CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    *///?} else {
    private static final Factory<PlayerProgress> FACTORY = new Factory<>(PlayerProgress::new,
            (tag, registries) -> CODEC.parse(NbtOps.INSTANCE, tag).result().orElseGet(PlayerProgress::new),
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    //?}

    private final Map<UUID, Entry> players = new HashMap<>();

    public static PlayerProgress get(MinecraftServer server) {
        //? if >=1.21.5 {
        /*return server.overworld().getDataStorage().computeIfAbsent(TYPE);
        *///?} else {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
        //?}
    }

    private Entry entry(UUID player) {
        return players.computeIfAbsent(player, uuid -> new Entry());
    }

    public int coins(UUID player) {
        Entry e = players.get(player);
        return e == null ? 0 : e.coins;
    }

    public void setCoins(UUID player, int amount) {
        Entry e = entry(player);
        int clamped = Math.max(0, amount);
        if (e.coins != clamped) {
            e.coins = clamped;
            setDirty();
        }
    }

    public int starCount(UUID player) {
        Entry e = players.get(player);
        return e == null ? 0 : e.stars.size();
    }

    public boolean hasStar(UUID player, String id) {
        Entry e = players.get(player);
        return e != null && e.stars.contains(id);
    }

    /** False if the player already had it. */
    public boolean addStar(UUID player, String id) {
        boolean added = entry(player).stars.add(id);
        if (added) setDirty();
        return added;
    }

    /** Adds to the coins collected in {@code dimension} this life; returns the new total. */
    public int addLifeCoins(UUID player, String dimension, int amount) {
        int total = entry(player).lifeCoins.merge(dimension, amount, Integer::sum);
        setDirty();
        return total;
    }

    public void clearLifeCoins(UUID player) {
        Entry e = players.get(player);
        if (e != null && !e.lifeCoins.isEmpty()) {
            e.lifeCoins.clear();
            setDirty();
        }
    }

    /** Dev runs only: starts a player's progress over. */
    public void reset(UUID player) {
        if (players.remove(player) != null) setDirty();
    }

    //? if <1.21.5 {
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CODEC.encodeStart(NbtOps.INSTANCE, this).result()
                .ifPresent(encoded -> tag.merge((CompoundTag) encoded));
        return tag;
    }
    //?}
}
