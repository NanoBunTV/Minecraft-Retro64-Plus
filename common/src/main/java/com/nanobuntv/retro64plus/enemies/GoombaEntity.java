package com.nanobuntv.retro64plus.enemies;

import com.nanobuntv.retro64plus.util.Compat;
import com.nanobuntv.retro64plus.coins.Coins;
import com.nanobuntv.retro64plus.combat.MarioAttack;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * SM64's Goomba (D72, normal size only, D83): wanders and now and then hops; when it spots a player it hops in
 * surprise and charges; after a hit it hops again (SM64's {@code goomba_act_attacked_mario}). Mario's attacks finish it
 * at once, a stomp or ground pound squashing it flat (D74); swords and arrows meet a small mob's health. It goes after
 * any player (D75), stays out by day (D85), and drops one coin (D76) and its loot table's little extra.
 */
public class GoombaEntity extends Monster implements Sm64Enemy {
    /** SM64's object scale for a normal Goomba. */
    public static final float SCALE = 1.5f;
    private static final byte EVENT_ALERT = 100, EVENT_SQUISH = 101;
    private static final EntityDataAccessor<Boolean> SQUISHED =
            SynchedEntityData.defineId(GoombaEntity.class, EntityDataSerializers.BOOLEAN);

    /** Client: animation frames played (SM64 speeds the walk up with the Goomba's speed), and the blink. */
    public float animFrames, lastAnimFrames;
    public int blinkTicks;
    private int nextBlink = 40;

    public GoombaEntity(EntityType<? extends GoombaEntity> type, Level level) {
        super(type, level);
        xpReward = 3;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 10)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 2.5) // one power meter segment (D36)
                .add(Attributes.FOLLOW_RANGE, 20);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.5, false));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SQUISHED, false);
    }

    public boolean squished() {
        return entityData.get(SQUISHED);
    }

    @Override
    public int coins() {
        return 1;
    }

    /** Spotting a player: SM64's surprised hop and "!" sound before the charge. */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        boolean spotted = getTarget() == null && target != null;
        super.setTarget(target);
        if (spotted && !level().isClientSide() && onGround()) {
            jumpFromGround();
            level().broadcastEntityEvent(this, EVENT_ALERT);
        }
    }

    //? if >=1.21.2 {
    /*@Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && onGround()) jumpFromGround(); // SM64 hops back after hurting Mario
        return hit;
    }
    *///?} else {
    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && onGround()) jumpFromGround(); // SM64 hops back after hurting Mario
        return hit;
    }
    //?}

    @Override
    public void aiStep() {
        super.aiStep();
        // SM64's wandering Goomba hops now and then.
        if (!level().isClientSide() && getTarget() == null && onGround() && random.nextInt(400) == 0) jumpFromGround();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) tickClient();
    }

    private void tickClient() {
        lastAnimFrames = animFrames;
        // SM64: animation speed = forward speed / scale × 0.4, at least 1, in frames per 30 Hz frame.
        Vec3 v = getDeltaMovement();
        double unitsPerFrame = Math.sqrt(v.x * v.x + v.z * v.z) * 110 / 1.5;
        float speed = (float) Math.max(1, unitsPerFrame / SCALE * 0.4);
        if (!isAlive()) speed = 0;
        float before = animFrames;
        animFrames += 1.5f * speed;
        // Footsteps while hurrying, at the walk's frames 2 and 17 (of 30).
        if (unitsPerFrame > 4 / 3.0 * SCALE * 2 && isAlive()
                && (crossed(before, animFrames, 2) || crossed(before, animFrames, 17))) {
            EnemyEffects.play(this, EnemyEffects.GOOMBA_WALK);
        }
        // SM64's obj_update_blinking: eyes shut for a moment every second or two.
        if (blinkTicks > 0) {
            blinkTicks--;
        } else if (--nextBlink <= 0) {
            blinkTicks = 3;
            nextBlink = 20 + random.nextInt(34);
        }
    }

    private static boolean crossed(float before, float after, int frame) {
        return Math.floor((before - frame) / 30) != Math.floor((after - frame) / 30);
    }

    /** Mario's attacks (D74): a stomp or ground pound squashes it, anything else knocks it away; both finish it. */
    @Override
    public boolean onMarioAttack(ServerPlayer player, MarioAttack attack) {
        boolean squash = attack == MarioAttack.STOMP || attack == MarioAttack.GROUND_POUND;
        // Flat before the hit (death keeps the look), but only if the hit lands.
        if (squash) entityData.set(SQUISHED, true);
        boolean hit = Compat.hurt(this, player.damageSources().playerAttack(player), getHealth() + 100);
        if (squash && hit) level().broadcastEntityEvent(this, EVENT_SQUISH);
        if (squash && !hit) entityData.set(SQUISHED, false);
        if (hit && !squash) {
            Vec3 away = position().subtract(player.position()).multiply(1, 0, 1);
            if (away.lengthSqr() > 1e-6) {
                away = away.normalize();
                Compat.knockback(this, attack.knockback * 1.5, -away.x, -away.z);
            }
        }
        player.setLastHurtMob(this);
        return true;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        Coins.burst(level, position().add(0, 0.5, 0), coins());
    }

    @Override
    public void handleEntityEvent(byte id) {
        switch (id) {
            case EVENT_ALERT -> EnemyEffects.play(this, EnemyEffects.GOOMBA_ALERT);
            case EVENT_SQUISH -> EnemyEffects.play(this, EnemyEffects.STOMPED);
            case 3 -> { // death
                EnemyEffects.play(this, EnemyEffects.ENEMY_DEATH_HIGH);
                super.handleEntityEvent(id);
            }
            default -> super.handleEntityEvent(id);
        }
    }

    // SM64's sounds come from the client (EnemyEffects); no vanilla ones.
    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return null;
    }
}
