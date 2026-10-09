package com.nanobuntv.retro64plus.world;

import com.nanobuntv.retro64plus.caps.CapTags;
import com.nanobuntv.retro64plus.sm64.Surfaces;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How a Minecraft block feels to Mario: the SM64 surface type (slipperiness, quicksand) and terrain type (footstep
 * sounds). The Vanish cap's blocks are SM64's vanish cap walls (D43). Ported from upstream's rules: slippery blocks are ice, slow blocks are shallow quicksand, and the step
 * sound picks the terrain. Upstream's block tags and quicksand blocks come back with the M1 content port.
 */
public final class SurfaceTypes {
    private SurfaceTypes() {}

    /** {@code type} and {@code terrain} as shorts, packed: {@code type << 16 | terrain}. */
    @SuppressWarnings("deprecation") // vanilla's "call through the state" mark: this is the state's own method
    public static int of(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.is(CapTags.VANISH_PASSABLE)) return pack(Surfaces.VANISH_CAP_WALLS, terrain(state.getSoundType()));
        if (state.getBlock().getFriction() >= 0.7f) return pack(Surfaces.ICE, Surfaces.TERRAIN_SNOW);
        if (state.getBlock().getSpeedFactor() < 0.6f) return pack(Surfaces.SHALLOW_QUICKSAND, Surfaces.TERRAIN_SAND);
        return pack(Surfaces.DEFAULT, terrain(state.getSoundType()));
    }

    static short type(int packed) {
        return (short) (packed >> 16);
    }

    static short terrain(int packed) {
        return (short) packed;
    }

    static int pack(short type, short terrain) {
        return type << 16 | (terrain & 0xFFFF);
    }

    private static short terrain(SoundType sound) {
        SoundEvent step = sound.getStepSound();
        if (step == SoundEvents.GRAVEL_STEP || step == SoundEvents.SAND_STEP) return Surfaces.TERRAIN_SAND;
        if (step == SoundEvents.GRASS_STEP) return Surfaces.TERRAIN_GRASS;
        if (step == SoundEvents.SNOW_STEP) return Surfaces.TERRAIN_SNOW;
        if (step == SoundEvents.WOOD_STEP) return Surfaces.TERRAIN_SPOOKY; // SM64's creaky wooden floor
        return Surfaces.TERRAIN_STONE;
    }
}
