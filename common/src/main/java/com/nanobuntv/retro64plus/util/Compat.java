package com.nanobuntv.retro64plus.util;

import com.nanobuntv.retro64plus.Retro64Plus;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Small differences between Minecraft versions, each in one place (Porting.md, as Cartridge's): the code calls these
 * instead of the version's own API. Client-only ones are in {@code client/ClientCompat}.
 */
public final class Compat {
    private Compat() {}

    /** The level a server player is in: serverLevel() to 1.21.5, level() since. */
    public static ServerLevel level(ServerPlayer player) {
        //? if >=1.21.6 {
        /*return player.level();
        *///?} else {
        return player.serverLevel();
        //?}
    }

    /** The server a player is on (the field went private in 1.21.6). */
    public static MinecraftServer server(ServerPlayer player) {
        return level(player).getServer();
    }

    /** A message over the hotbar (26.1 renamed displayClientMessage(message, true)). */
    public static void overlay(Player player, Component message) {
        //? if >=26.1 {
        /*player.sendOverlayMessage(message);
        *///?} else {
        player.displayClientMessage(message, true);
        //?}
    }

    /** Drops a stack at the player's feet, as Minecraft does when an inventory is full (26.3 added the prediction). */
    public static void drop(Player player, ItemStack stack) {
        //? if >=26.3 {
        /*player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        *///?} else {
        player.drop(stack, false);
        //?}
    }

    /** Throws a stack out around the player, as a death does. */
    public static void dropAround(Player player, ItemStack stack) {
        //? if >=26.3 {
        /*player.drop(stack, true, net.minecraft.util.Prediction.SERVER_ONLY);
        *///?} else {
        player.drop(stack, true, false);
        //?}
    }

    /** Puts a stack back into an inventory, or drops what doesn't fit. */
    public static void placeBack(Inventory inventory, ItemStack stack) {
        //? if >=26.3 {
        /*inventory.placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);
        *///?} else {
        inventory.placeItemBackInInventory(stack);
        //?}
    }

    /** A chat message to this player only (26.1 dropped displayClientMessage). */
    public static void chat(Player player, Component message) {
        //? if >=26.1 {
        /*player.sendSystemMessage(message);
        *///?} else {
        player.displayClientMessage(message, false);
        //?}
    }

    /** The 36 main-inventory and hotbar stacks; a public field before 1.21.5. */
    public static List<ItemStack> mainItems(Inventory inventory) {
        //? if >=1.21.5 {
        /*return inventory.getNonEquipmentItems();
        *///?} else {
        return inventory.items;
        //?}
    }

    /** The hotbar slot in use; a public field before 1.21.5, accessors after. */
    public static int selectedSlot(Inventory inventory) {
        //? if >=1.21.5 {
        /*return inventory.getSelectedSlot();
        *///?} else {
        return inventory.selected;
        //?}
    }

    public static void setSelectedSlot(Inventory inventory, int slot) {
        //? if >=1.21.5 {
        /*inventory.setSelectedSlot(slot);
        *///?} else {
        inventory.selected = slot;
        //?}
    }

    /** A chunk's position as one long. */
    public static long chunkKey(ChunkPos pos) {
        //? if >=26.1 {
        /*return ChunkPos.pack(pos.x(), pos.z());
        *///?} else {
        return pos.toLong();
        //?}
    }

    /** Whether an entity's type is in a tag (26.1 dropped EntityType.is(tag)). */
    public static boolean is(Entity entity, net.minecraft.tags.TagKey<EntityType<?>> tag) {
        return entity.getType().builtInRegistryHolder().is(tag);
    }

    /** Hurts an entity, true if it took the hit: hurtServer from 1.21.2 (hurt became a final void there). */
    public static boolean hurt(Entity target, net.minecraft.world.damagesource.DamageSource source, float amount) {
        //? if >=1.21.2 {
        /*return target.level() instanceof ServerLevel level && target.hurtServer(level, source, amount);
        *///?} else {
        return target.hurt(source, amount);
        //?}
    }

    /** Pushes an entity away from (x, z) as a hit does (26.2 added the hit's source and damage). */
    public static void knockback(net.minecraft.world.entity.LivingEntity target, double strength, double x, double z) {
        //? if >=26.2 {
        /*target.knockback(strength, x, z, target.damageSources().generic(), 0);
        *///?} else {
        target.knockback(strength, x, z);
        //?}
    }

    /** A mob's melee hit on {@code target} (it takes the level from 1.21.2). */
    public static boolean doHurtTarget(net.minecraft.world.entity.Mob mob, Entity target) {
        //? if >=1.21.2 {
        /*return mob.level() instanceof ServerLevel level && mob.doHurtTarget(level, target);
        *///?} else {
        return mob.doHurtTarget(target);
        //?}
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(Retro64Plus.MOD_ID, name);
    }

    /** An item's properties; from 1.21.2 they carry its id, which every item needs. */
    public static Item.Properties itemProperties(String name) {
        //? if >=1.21.2 {
        /*return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id(name)));
        *///?} else {
        return new Item.Properties();
        //?}
    }

    /** A block item's properties: from 1.21.2 its name stays the block's ({@code block.retro64plus.<id>}). */
    public static Item.Properties blockItemProperties(String name) {
        //? if >=1.21.2 {
        /*return itemProperties(name).useBlockDescriptionPrefix();
        *///?} else {
        return itemProperties(name);
        //?}
    }

    /** A block's properties; from 1.21.2 they carry its id, which every block needs. */
    public static BlockBehaviour.Properties blockProperties(String name) {
        //? if >=1.21.2 {
        /*return BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, id(name)));
        *///?} else {
        return BlockBehaviour.Properties.of();
        //?}
    }

    /**
     * A spawn egg: two colours on vanilla's egg up to 1.21.4; from 1.21.5 the item's own texture
     * ({@code textures/item/<name>.png}) and its entity in the properties.
     */
    @SuppressWarnings("deprecation") // SpawnEggItem's constructor is marked for change; in 1.21.1 it's still the way
    public static Item spawnEgg(EntityType<? extends net.minecraft.world.entity.Mob> type, int background,
            int highlight, String name) {
        //? if >=1.21.5 {
        /*return new net.minecraft.world.item.SpawnEggItem(itemProperties(name).spawnEgg(type));
        *///?} else {
        return new net.minecraft.world.item.SpawnEggItem(type, background, highlight, itemProperties(name));
        //?}
    }

    /** Builds an entity type; from 1.21.2 the builder takes its registry key. */
    public static <T extends Entity> EntityType<T> build(EntityType.Builder<T> builder, String name) {
        //? if >=1.21.2 {
        /*return builder.build(ResourceKey.create(Registries.ENTITY_TYPE, id(name)));
        *///?} else {
        return builder.build(name);
        //?}
    }
}
