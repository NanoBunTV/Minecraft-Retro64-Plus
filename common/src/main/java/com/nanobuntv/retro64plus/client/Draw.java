package com.nanobuntv.retro64plus.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
//? if <26.2 {
import net.minecraft.client.renderer.MultiBufferSource;
//?}
//? if >=1.21.11 {
/*import net.minecraft.client.renderer.rendertype.RenderType;
*///?} else {
import net.minecraft.client.renderer.RenderType;
//?}

/**
 * Where the mod's renderers draw in the world, on every Minecraft version (Porting.md, as Cartridge's): geometry by
 * render type. Up to 1.21.8 a {@code MultiBufferSource} ({@link #of}); from 1.21.9 renderers record and submit instead
 * ({@link RecordingBuffers}), and 26.2 dropped buffer sources altogether.
 */
public interface Draw {
    /** A consumer for geometry of this render type (positions as transformed by the caller's matrices). */
    VertexConsumer getBuffer(RenderType type);

    //? if <26.2 {
    static Draw of(MultiBufferSource buffers) {
        return buffers::getBuffer;
    }
    //?}
}
