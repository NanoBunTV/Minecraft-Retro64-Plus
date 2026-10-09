package com.nanobuntv.retro64plus.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
//? if >=1.21.9 {
/*import net.minecraft.client.input.MouseButtonEvent;
*///?}

/**
 * The mod's screens, drawn the same way on every Minecraft version (Porting.md, as Cartridge's): the background, then
 * vanilla's widgets, then {@link #drawOver}; clicks through {@link #click}. Only this class has each version's
 * overrides: 26.1 renamed rendering to extracting render state, 1.21.9 made a click an event.
 */
public abstract class ModScreen extends Screen {
    protected ModScreen(Component title) {
        super(title);
    }

    /** Over the widgets. */
    protected void drawOver(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    /** Over vanilla's background, under the widgets. */
    protected void drawBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    /** A mouse click; true if handled (the widgets get it otherwise). */
    protected boolean click(double mouseX, double mouseY, int button) {
        return false;
    }

    //? if >=26.1 {
    /*@Override
    public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        drawOver(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        drawBackground(g, mouseX, mouseY, partialTick);
    }
    *///?} else {
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        drawOver(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        drawBackground(g, mouseX, mouseY, partialTick);
    }
    //?}

    //? if >=1.21.9 {
    /*@Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return click(event.x(), event.y(), event.button()) || super.mouseClicked(event, doubleClick);
    }
    *///?} else {
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return click(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }
    //?}
}
