package com.nanobuntv.retro64plus.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
//? if >=1.21.6 {
/*import net.minecraft.client.renderer.RenderPipelines;
*///?} else {
import com.mojang.blaze3d.systems.RenderSystem;
//?}

/**
 * The GUI drawing the mod's HUD and screens do, for every Minecraft version (Porting.md, as Cartridge's): from 1.21.6
 * the GUI's pose is a 2D matrix stack, a texture is drawn with a render pipeline (which blends itself), and 26.1
 * renamed the text methods.
 */
public final class GuiDraw {
    private GuiDraw() {}

    public static void push(GuiGraphics g) {
        //? if >=1.21.6 {
        /*g.pose().pushMatrix();
        *///?} else {
        g.pose().pushPose();
        //?}
    }

    public static void pop(GuiGraphics g) {
        //? if >=1.21.6 {
        /*g.pose().popMatrix();
        *///?} else {
        g.pose().popPose();
        //?}
    }

    public static void translate(GuiGraphics g, float x, float y) {
        //? if >=1.21.6 {
        /*g.pose().translate(x, y);
        *///?} else {
        g.pose().translate(x, y, 0);
        //?}
    }

    public static void scale(GuiGraphics g, float x, float y) {
        //? if >=1.21.6 {
        /*g.pose().scale(x, y);
        *///?} else {
        g.pose().scale(x, y, 1);
        //?}
    }

    /** Blending on for what follows (up to 1.21.5; the GUI's textured pipeline blends by itself after). */
    public static void blend(boolean on) {
        //? if <1.21.6 {
        if (on) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        } else {
            RenderSystem.disableBlend();
        }
        //?}
    }

    /** The region {@code u, v} of a {@code textureWidth x textureHeight} texture, {@code width x height}, at its size. */
    public static void blit(GuiGraphics g, ResourceLocation texture, int x, int y, float u, float v, int width, int height,
            int textureWidth, int textureHeight) {
        //? if >=1.21.6 {
        /*g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, textureWidth, textureHeight);
        *///?} else {
        g.blit(texture, x, y, u, v, width, height, textureWidth, textureHeight);
        //?}
    }

    /** A GUI sprite (from the GUI atlas) stretched to a size. */
    public static void sprite(GuiGraphics g, ResourceLocation sprite, int x, int y, int width, int height) {
        //? if >=1.21.6 {
        /*g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height);
        *///?} else {
        g.blitSprite(sprite, x, y, width, height);
        //?}
    }

    /** Text at x, y ({@code color} with its alpha byte), with a shadow. */
    public static void text(GuiGraphics g, Font font, String text, int x, int y, int color) {
        //? if >=26.1 {
        /*g.text(font, text, x, y, color, true);
        *///?} else {
        g.drawString(font, text, x, y, color);
        //?}
    }

    public static void text(GuiGraphics g, Font font, Component text, int x, int y, int color, boolean shadow) {
        //? if >=26.1 {
        /*g.text(font, text, x, y, color, shadow);
        *///?} else {
        g.drawString(font, text, x, y, color, shadow);
        //?}
    }

    /** Text centred on x, with a shadow. */
    public static void centered(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        //? if >=26.1 {
        /*g.centeredText(font, text, x, y, color);
        *///?} else {
        g.drawCenteredString(font, text, x, y, color);
        //?}
    }

    public static void centered(GuiGraphics g, Font font, net.minecraft.util.FormattedCharSequence text, int x, int y,
            int color) {
        //? if >=26.1 {
        /*g.centeredText(font, text, x, y, color);
        *///?} else {
        g.drawCenteredString(font, text, x, y, color);
        //?}
    }

    /** An item with its count and bar, as in a slot. */
    public static void item(GuiGraphics g, Font font, net.minecraft.world.item.ItemStack stack, int x, int y) {
        //? if >=26.1 {
        /*g.item(stack, x, y);
        g.itemDecorations(font, stack, x, y);
        *///?} else {
        g.renderItem(stack, x, y);
        g.renderItemDecorations(font, stack, x, y);
        //?}
    }

    /** An item's tooltip at the mouse (queued for the end of the frame from 1.21.6). */
    public static void itemTooltip(GuiGraphics g, Font font, net.minecraft.world.item.ItemStack stack, int x, int y) {
        //? if >=1.21.6 {
        /*g.setTooltipForNextFrame(font, stack, x, y);
        *///?} else {
        g.renderTooltip(font, stack, x, y);
        //?}
    }

    /** Text wrapped to {@code width}, without a shadow. */
    public static void wordWrap(GuiGraphics g, Font font, Component text, int x, int y, int width, int color) {
        //? if >=26.1 {
        /*g.textWithWordWrap(font, text, x, y, width, color, false);
        *///?} else {
        g.drawWordWrap(font, text, x, y, width, color);
        //?}
    }
}
