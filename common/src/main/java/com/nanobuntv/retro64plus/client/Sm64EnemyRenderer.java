package com.nanobuntv.retro64plus.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nanobuntv.retro64plus.enemies.BobombEntity;
import com.nanobuntv.retro64plus.enemies.GoombaEntity;
import com.nanobuntv.retro64plus.enemies.KoopaEntity;
import com.nanobuntv.retro64plus.sm64.RomModels;
import com.nanobuntv.retro64plus.sm64.Units;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Draws an SM64 enemy with its model and animations from the ROM (M4, D19; the ROM is always there, D81): turned to
 * the body's yaw (SM64's models face +z), at SM64's object scale, flashing red when hurt. Dying, it shrinks away over
 * Minecraft's death time; a squashed Goomba is flat first.
 */
public abstract class Sm64EnemyRenderer<T extends Mob> extends ModEntityRenderer<T> {
    private final RomModels.Kind kind;
    private final float scale;

    protected Sm64EnemyRenderer(EntityRendererProvider.Context context, RomModels.Kind kind, float scale,
            float shadow) {
        super(context);
        this.kind = kind;
        this.scale = scale;
        shadowRadius = shadow;
    }

    @Override
    protected void draw(T entity, float partialTick, PoseStack poseStack, Draw buffers, int light) {
        RomModels.Model model = RomModelRenderer.model(kind(entity));
        if (model != null) {
            poseStack.pushPose();
            Vec3 offset = offset(entity, partialTick);
            if (offset != null) poseStack.translate(offset.x, offset.y, offset.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot)));
            float s = scale / Units.PER_BLOCK;
            float dying = entity.deathTime > 0 ? Math.max(0, 1 - (entity.deathTime + partialTick - 6) / 14f) : 1;
            if (entity.deathTime <= 6) dying = 1;
            float[] squash = squash(entity, partialTick);
            poseStack.scale(s * squash[0] * dying, s * squash[1] * dying, s * squash[0] * dying);
            int animation = animation(entity);
            RomModels.Animation anim = animation < model.animations().size() ? model.animations().get(animation) : null;
            RomModelRenderer.render(model, anim, frame(entity, partialTick), animState(entity), poseStack, buffers,
                    light, overlay(entity), 1);
            poseStack.popPose();
        }
    }

    /** The model to draw (the Koopa's changes when it loses its shell). */
    protected RomModels.Kind kind(T entity) {
        return kind;
    }

    /** Animation index into the model's table. */
    protected abstract int animation(T entity);

    /** SM64 frames played (fractional). */
    protected abstract float frame(T entity, float partialTick);

    /** The switch nodes' choice (the blink). */
    protected abstract int animState(T entity);

    /** Horizontal and vertical scale factors (1, 1 normally). */
    protected float[] squash(T entity, float partialTick) {
        return new float[] {1, 1};
    }

    /** Moves the model away from the entity's position (a carried Bob-omb drawn in the local Mario's hands). */
    protected Vec3 offset(T entity, float partialTick) {
        return null;
    }

    public static final class Goomba extends Sm64EnemyRenderer<GoombaEntity> {
        public Goomba(EntityRendererProvider.Context context) {
            super(context, RomModels.Kind.GOOMBA, GoombaEntity.SCALE, 0.45f);
        }

        @Override
        protected int animation(GoombaEntity goomba) {
            return 0;
        }

        @Override
        protected float frame(GoombaEntity goomba, float partialTick) {
            return Mth.lerp(partialTick, goomba.lastAnimFrames, goomba.animFrames);
        }

        @Override
        protected int animState(GoombaEntity goomba) {
            return goomba.blinkTicks > 0 ? 1 : 0;
        }

        /** SM64's squashed Goomba: flat and a little wider. */
        @Override
        protected float[] squash(GoombaEntity goomba, float partialTick) {
            return goomba.squished() ? new float[] {1.3f, 0.25f} : new float[] {1, 1};
        }
    }

    public static final class Bobomb extends Sm64EnemyRenderer<BobombEntity> {
        public Bobomb(EntityRendererProvider.Context context) {
            super(context, RomModels.Kind.BOBOMB, 1f, 0.4f);
        }

        /** SM64's walk; its "held" animation while carried. */
        @Override
        protected int animation(BobombEntity bobomb) {
            return bobomb.held() ? 1 : 0;
        }

        @Override
        protected float frame(BobombEntity bobomb, float partialTick) {
            return Mth.lerp(partialTick, bobomb.lastAnimFrames, bobomb.animFrames);
        }

        @Override
        protected int animState(BobombEntity bobomb) {
            return bobomb.eyesShut() ? 1 : 0;
        }

        /** SM64 swells it to twice its size in the frames before the blast. */
        @Override
        protected float[] squash(BobombEntity bobomb, float partialTick) {
            float grow = bobomb.swell() > 0 ? 1 + Math.min(1, (bobomb.swell() + partialTick) / BobombEntity.SWELL_TICKS)
                    : 1;
            return new float[] {grow, grow};
        }

        @Override
        protected Vec3 offset(BobombEntity bobomb, float partialTick) {
            Vec3 held = LocalMario.heldBobombPosition(bobomb.getId(), partialTick);
            return held == null ? null : held.subtract(bobomb.getPosition(partialTick));
        }
    }

    public static final class Koopa extends Sm64EnemyRenderer<KoopaEntity> {
        public Koopa(EntityRendererProvider.Context context) {
            super(context, RomModels.Kind.KOOPA_WITH_SHELL, KoopaEntity.SCALE, 0.7f);
        }

        @Override
        protected RomModels.Kind kind(KoopaEntity koopa) {
            return koopa.shelled() ? RomModels.Kind.KOOPA_WITH_SHELL : RomModels.Kind.KOOPA_WITHOUT_SHELL;
        }

        @Override
        protected int animation(KoopaEntity koopa) {
            return koopa.animation;
        }

        @Override
        protected float frame(KoopaEntity koopa, float partialTick) {
            return Mth.lerp(partialTick, koopa.lastAnimFrames, koopa.animFrames);
        }

        @Override
        protected int animState(KoopaEntity koopa) {
            return koopa.blinkTicks > 0 ? 1 : 0;
        }

        /** Squashed flat by a stomp, like the Goomba. */
        @Override
        protected float[] squash(KoopaEntity koopa, float partialTick) {
            return koopa.squished() ? new float[] {1.3f, 0.25f} : new float[] {1, 1};
        }
    }
}
