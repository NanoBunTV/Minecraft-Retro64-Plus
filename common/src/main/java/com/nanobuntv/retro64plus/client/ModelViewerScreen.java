package com.nanobuntv.retro64plus.client;

//? if <1.21.2 {
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.nanobuntv.retro64plus.sm64.RomModels;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

/**
 * Dev builds only (the {@code m4models} run): every ROM model M4 decodes, in a grid, turning slowly and playing its
 * first animation, drawn through {@link RomModelRenderer} as the game draws them.
 */
final class ModelViewerScreen extends Screen {
    /** The size in SM64 units that fills a cell. */
    private static final float UNITS = 150;
    private int ticks;

    ModelViewerScreen() {
        super(Component.literal("ROM models"));
    }

    @Override
    public void tick() {
        ticks++;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF3A6EA5); // a plain sky blue, like SM64's menus
        RomModels.Kind[] kinds = RomModels.Kind.values();
        int columns = 5;
        float time = ticks + partialTick;
        float cell = Math.min(width / (float) columns, height / 2f) * 0.55f;
        Lighting.setupForEntityInInventory();
        for (int i = 0; i < kinds.length; i++) {
            RomModels.Model model = RomModelRenderer.model(kinds[i]);
            float cx = (i % columns + 0.5f) * width / (float) columns;
            float cy = (i / columns + 0.5f) * height / 2f;
            graphics.drawCenteredString(font, kinds[i].name().toLowerCase(), (int) cx, (int) (cy + cell * 0.7f),
                    0xFFFFFF);
            if (model == null) continue;
            PoseStack pose = graphics.pose();
            pose.pushPose();
            pose.translate(cx, cy, 200);
            float scale = cell / UNITS * fit(kinds[i]);
            pose.scale(scale, -scale, scale); // screens' y points down
            Quaternionf turn = new Quaternionf().rotateX((float) Math.toRadians(15))
                    .rotateY((float) Math.toRadians(30 + time * 3));
            pose.mulPose(turn);
            RomModels.Animation animation = model.animations().isEmpty() ? null : model.animations().get(0);
            RomModelRenderer.render(model, animation, time * 1.5f, 0, pose, Draw.of(graphics.bufferSource()),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1, new Matrix3f().set(turn));
            graphics.flush();
            pose.popPose();
        }
        Lighting.setupFor3DItems();
    }

    /** Scales the big ones down: their sizes after the geo layout's own scale. */
    private static float fit(RomModels.Kind kind) {
        return switch (kind) {
            case CHAIN_CHOMP -> 0.4f;
            case WOODEN_POST -> 0.3f;
            case STAR, BOBOMB, KOOPA_SHELL -> 0.9f;
            default -> 1.3f;
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
//?}
