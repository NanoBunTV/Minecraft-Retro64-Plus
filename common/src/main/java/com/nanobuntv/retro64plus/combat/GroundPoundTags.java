package com.nanobuntv.retro64plus.combat;

import com.nanobuntv.retro64plus.Retro64Plus;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class GroundPoundTags {
    /** Soft blocks a ground pound breaks (D39): glass and panes, leaves and plants, ice and snow. Packs can change it. */
    public static final TagKey<Block> BREAKABLE = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Retro64Plus.MOD_ID, "ground_pound_breakable"));
    /** Blocks a ground pound presses when Mario lands on them (D18): buttons and levers. */
    public static final TagKey<Block> PRESSABLE = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Retro64Plus.MOD_ID, "ground_pound_pressable"));

    private GroundPoundTags() {}
}
