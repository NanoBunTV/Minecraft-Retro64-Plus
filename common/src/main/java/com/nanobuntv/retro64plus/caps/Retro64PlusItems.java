package com.nanobuntv.retro64plus.caps;

import com.nanobuntv.retro64plus.util.Compat;
import com.nanobuntv.retro64plus.platform.Services;
import com.nanobuntv.retro64plus.shop.CoinShopBlock;
import com.nanobuntv.retro64plus.shop.RechargeMushroomItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;

/**
 * The caps as items (D16, D40, D41): registered on both loaders, listed in the Tools tab, crafted (data/.../recipe)
 * and found in structure chests (pools added here). Also the Coin Shop block and the Recharge Mushroom (M3, D58, D70).
 */
public final class Retro64PlusItems {
    public static Supplier<DataComponentType<CapCharge>> CAP_CHARGE;
    public static final Map<MarioCap, Supplier<CapItem>> CAPS = new EnumMap<>(MarioCap.class);
    /** Vanilla keeps CreativeModeTabs' keys private (the loaders widen them); the same key by name. */
    private static final ResourceKey<CreativeModeTab> TOOLS_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            ResourceLocation.withDefaultNamespace("tools_and_utilities"));
    private static final ResourceKey<CreativeModeTab> FUNCTIONAL_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            ResourceLocation.withDefaultNamespace("functional_blocks"));
    public static Supplier<CoinShopBlock> COIN_SHOP;
    public static Supplier<RechargeMushroomItem> RECHARGE_MUSHROOM;

    private Retro64PlusItems() {}

    /** Common init. */
    public static void register() {
        CAP_CHARGE = Services.PLATFORM.register(Registries.DATA_COMPONENT_TYPE, "cap_charge",
                () -> DataComponentType.<CapCharge>builder().persistent(CapCharge.CODEC)
                        .networkSynchronized(CapCharge.STREAM_CODEC).build());
        for (MarioCap cap : MarioCap.values()) {
            Supplier<CapItem> item = Services.PLATFORM.register(Registries.ITEM, cap.itemName,
                    () -> new CapItem(cap, Compat.itemProperties(cap.itemName).stacksTo(1).rarity(Rarity.UNCOMMON)));
            CAPS.put(cap, item);
            Services.PLATFORM.addToCreativeTab(TOOLS_TAB, item);
        }
        // The Coin Shop (D58) and what it sells of ours (D70).
        COIN_SHOP = Services.PLATFORM.register(Registries.BLOCK, "coin_shop",
                () -> new CoinShopBlock(Compat.blockProperties("coin_shop").mapColor(MapColor.WOOD).strength(2.5f)
                        .sound(SoundType.WOOD).ignitedByLava()));
        Supplier<BlockItem> shopItem = Services.PLATFORM.register(Registries.ITEM, "coin_shop",
                () -> new BlockItem(COIN_SHOP.get(), Compat.blockItemProperties("coin_shop")));
        Services.PLATFORM.addToCreativeTab(FUNCTIONAL_TAB, shopItem);
        RECHARGE_MUSHROOM = Services.PLATFORM.register(Registries.ITEM, "recharge_mushroom",
                () -> new RechargeMushroomItem(Compat.itemProperties("recharge_mushroom").rarity(Rarity.UNCOMMON)));
        Services.PLATFORM.addToCreativeTab(TOOLS_TAB, RECHARGE_MUSHROOM);
        // Structure loot (D40): one roll; the weights against nothing give each chest's chance.
        loot("chests/simple_dungeon", 27, MarioCap.WING, 1, MarioCap.METAL, 1, MarioCap.VANISH, 1); // 10 %
        loot("chests/desert_pyramid", 17, MarioCap.WING, 1, MarioCap.METAL, 1, MarioCap.VANISH, 1); // 15 %
        loot("chests/jungle_temple", 17, MarioCap.WING, 1, MarioCap.METAL, 1, MarioCap.VANISH, 1); // 15 %
        loot("chests/ancient_city", 12, MarioCap.METAL, 1, MarioCap.VANISH, 2); // 20 %
        loot("chests/woodland_mansion", 3, MarioCap.VANISH, 1); // 25 %
        loot("chests/end_city_treasure", 7, MarioCap.WING, 3); // 30 %
    }

    /** {@code empty} weight of nothing, then cap / weight pairs. */
    private static void loot(String table, int empty, Object... capsAndWeights) {
        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.withDefaultNamespace(table));
        Services.PLATFORM.addLootPool(key, () -> {
            LootPool.Builder pool = LootPool.lootPool() // one roll, the default
                    .add(EmptyLootItem.emptyItem().setWeight(empty));
            for (int i = 0; i < capsAndWeights.length; i += 2) {
                pool.add(LootItem.lootTableItem(CAPS.get((MarioCap) capsAndWeights[i]).get())
                        .setWeight((Integer) capsAndWeights[i + 1]));
            }
            return pool.build();
        });
    }
}
