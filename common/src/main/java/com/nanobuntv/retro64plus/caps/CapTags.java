package com.nanobuntv.retro64plus.caps;

import com.nanobuntv.retro64plus.Retro64Plus;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class CapTags {
    /** Blocks whose sides the Vanish cap walks through (D43): SM64's vanish cap walls. Packs can change it. */
    public static final TagKey<Block> VANISH_PASSABLE = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Retro64Plus.MOD_ID, "vanish_passable"));

    private CapTags() {}
}
