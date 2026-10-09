package com.nanobuntv.retro64plus.client;

import net.minecraft.resources.ResourceLocation;
//? if >=1.21.11 {
/*import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
*///?} else {
import net.minecraft.client.renderer.RenderType;
//?}

/**
 * The render types the mod draws with, for every Minecraft version (Porting.md): 1.21.11 moved them into
 * {@code RenderTypes}, and 26.1 made {@code entityCutout} two-sided (culling became {@code entityCutoutCull}).
 */
public final class ModRenderTypes {
    private ModRenderTypes() {}

    /** Lit, textured, see-through, both faces. */
    public static RenderType entityTranslucent(ResourceLocation texture) {
        //? if >=1.21.11 {
        /*return RenderTypes.entityTranslucent(texture);
        *///?} else {
        return RenderType.entityTranslucent(texture);
        //?}
    }

    /** Lit, textured, cut out, back faces culled. */
    public static RenderType entityCutoutCull(ResourceLocation texture) {
        //? if >=26.1 {
        /*return RenderTypes.entityCutoutCull(texture);
        *///?} else if >=1.21.11 {
        /*return RenderTypes.entityCutout(texture);
        *///?} else {
        return RenderType.entityCutout(texture);
        //?}
    }

    /** Lit, textured, cut out, both faces. */
    public static RenderType entityCutoutNoCull(ResourceLocation texture) {
        //? if >=26.1 {
        /*return RenderTypes.entityCutout(texture);
        *///?} else if >=1.21.11 {
        /*return RenderTypes.entityCutoutNoCull(texture);
        *///?} else {
        return RenderType.entityCutoutNoCull(texture);
        //?}
    }
}
