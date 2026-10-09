package com.nanobuntv.retro64plus.enemies;

import com.nanobuntv.retro64plus.util.Compat;
import com.nanobuntv.retro64plus.MarioHooks;
import com.nanobuntv.retro64plus.coins.Coins;
import com.nanobuntv.retro64plus.combat.MarioAttack;
import com.nanobuntv.retro64plus.net.Payloads;
import com.nanobuntv.retro64plus.platform.Services;
import com.nanobuntv.retro64plus.server.ServerMarios;
import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * SM64's Bob-omb (D72, D77, D86): patrols slowly; when it sees a player it lights its fuse and chases them. It blows
 * up when the fuse runs out (SM64's 150 frames, 5 seconds), when it touches someone with the fuse lit, when another
 * blast reaches it, or when it lands after being thrown or knocked flying. The blast breaks a few blocks when
 * mobGriefing allows (D77) and hurts like SM64's (two power meter segments at most). Killed any other way it just
 * pops (D86). Mario picks it up with a punch, carries it and throws it, with SM64's own actions (native); his other
 * attacks knock it flying. One coin either way (D76).
 */
public class BobombEntity extends Monster implements Sm64Enemy {
    /** Loaded from a save: nobody holds it any more (holding isn't saved), so it falls again. */
    //? if >=1.21.6 {
    /*@Override
    public void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
        super.readAdditionalSaveData(input);
        setNoGravity(false);
    }
    *///?} else {
    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setNoGravity(false);
    }
    //?}

    /** SM64's fuse: 150 frames. */
    public static final int FUSE_TICKS = 100;
    /** SM64 swells for 5 frames before the blast. */
    public static final int SWELL_TICKS = 3;
    /** The blast: smaller than a Creeper's 3 (D77), and its damage capped at two power meter segments. */
    private static final float BLAST_POWER = 2f, MAX_BLAST_DAMAGE = 5f;
    private static final double SIGHT_RANGE = 10;
    private static final byte EVENT_EXPLODE = 100;

    private static final EntityDataAccessor<Boolean> FUSE_LIT =
            SynchedEntityData.defineId(BobombEntity.class, EntityDataSerializers.BOOLEAN);
    /** The entity id of the player carrying it, or -1. */
    private static final EntityDataAccessor<Integer> HOLDER =
            SynchedEntityData.defineId(BobombEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SWELL =
            SynchedEntityData.defineId(BobombEntity.class, EntityDataSerializers.INT);

    private static final ExplosionDamageCalculator BLAST = new ExplosionDamageCalculator() {
        //? if >=1.21.2 {
        /*@Override
        public float getEntityDamageAmount(Explosion explosion, Entity entity, float seen) {
            return Math.min(MAX_BLAST_DAMAGE, super.getEntityDamageAmount(explosion, entity, seen));
        }
        *///?} else {
        @Override
        public float getEntityDamageAmount(Explosion explosion, Entity entity) {
            return Math.min(MAX_BLAST_DAMAGE, super.getEntityDamageAmount(explosion, entity));
        }
        //?}
    };

    private int fuse;
    private boolean launched;
    private int launchTicks;
    /** Who threw or knocked it last: the blast is theirs (kill credit). */
    private @Nullable UUID thrower;

    /** Client: animation frames played, the blink. */
    public float animFrames, lastAnimFrames;
    public int blinkTicks;

    public BobombEntity(EntityType<? extends BobombEntity> type, Level level) {
        super(type, level);
        xpReward = 3;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 8)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.ATTACK_DAMAGE, 0)
                .add(Attributes.FOLLOW_RANGE, 16);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new ChaseGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FUSE_LIT, false);
        builder.define(HOLDER, -1);
        builder.define(SWELL, 0);
    }

    public boolean fuseLit() {
        return entityData.get(FUSE_LIT);
    }

    public int holderId() {
        return entityData.get(HOLDER);
    }

    public boolean held() {
        return holderId() >= 0;
    }

    public int swell() {
        return entityData.get(SWELL);
    }

    /** Mario can pick it up: alive, not carried, not going off. */
    public boolean grabbable() {
        return isAlive() && !held() && swell() == 0;
    }

    @Override
    public int coins() {
        return 1;
    }

    // ---- Server --------------------------------------------------------------------------------------------------

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || held() || launched || swell() > 0;
    }

    @Override
    public boolean isPushable() {
        return !held() && super.isPushable();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            tickClient();
            return;
        }
        int swell = swell();
        if (swell > 0) {
            if (swell >= SWELL_TICKS) detonate();
            else entityData.set(SWELL, swell + 1);
            return;
        }
        if (held()) followHolder();
        if (fuseLit() && ++fuse >= FUSE_TICKS) {
            startExplosion();
            return;
        }
        if (launched) {
            launchTicks++;
            if (launchTicks > 2 && (onGround() || horizontalCollision)) {
                startExplosion();
                return;
            }
        }
        if (fuseLit() && !held() && !launched) {
            // Touching anyone with the fuse lit sets it off (SM64's INT_STATUS_TOUCHED_BOB_OMB); not in flight, or a
            // throw would set it off in Mario's face.
            for (Player player : level().players()) {
                if (player.isAlive() && !player.isSpectator() && !player.isCreative() && touching(player)) {
                    startExplosion();
                    return;
                }
            }
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide() || fuseLit() || held()) return;
        LivingEntity target = getTarget();
        if (target != null && distanceTo(target) < SIGHT_RANGE && hasLineOfSight(target)) lightFuse();
    }

    /** Within touching distance: the two widths side by side (plus a little), overlapping in height. */
    private boolean touching(Entity other) {
        double dx = other.getX() - getX(), dz = other.getZ() - getZ();
        double reach = (getBbWidth() + other.getBbWidth()) / 2 + 0.3;
        return dx * dx + dz * dz < reach * reach && other.getY() < getY() + getBbHeight()
                && other.getY() + other.getBbHeight() > getY();
    }

    private void lightFuse() {
        if (!fuseLit()) entityData.set(FUSE_LIT, true);
    }

    /** Begins SM64's swell; the blast follows a few ticks later. */
    public void startExplosion() {
        if (swell() > 0 || !isAlive()) return;
        Entity holder = holder();
        if (holder instanceof ServerPlayer player) Services.NETWORK.sendToPlayer(player, new Payloads.Release(getId()));
        entityData.set(HOLDER, -1);
        setNoGravity(false);
        entityData.set(SWELL, 1);
        level().broadcastEntityEvent(this, EVENT_EXPLODE);
    }

    private void detonate() {
        ServerLevel level = (ServerLevel) level();
        Entity cause = thrower != null ? level.getEntity(thrower) : null;
        level.explode(this, damageSources().explosion(this, cause), BLAST, getX(), getY() + 0.5, getZ(), BLAST_POWER,
                false, Level.ExplosionInteraction.MOB);
        Coins.burst(level, position().add(0, 0.5, 0), coins());
        discard();
    }

    /** Another blast reaching it sets it off rather than killing it. */
    //? if >=1.21.2 {
    /*@Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            if (source.getDirectEntity() != this) startExplosion();
            return false;
        }
        return super.hurtServer(level, source, amount);
    }
    *///?} else {
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_EXPLOSION) && !level().isClientSide()) {
            if (source.getDirectEntity() != this) startExplosion();
            return false;
        }
        return super.hurt(source, amount);
    }
    //?}

    /** Mario's attacks other than the grab knock it flying, to blow up where it lands (SM64's launched action). */
    @Override
    public boolean onMarioAttack(ServerPlayer player, MarioAttack attack) {
        if (!grabbable()) return true;
        launch(player, ServerMarios.yaw(player), 25, 30);
        return true;
    }

    /**
     * Flies off along SM64's yaw {@code yaw} (radians, 0 = +z) at {@code forward} and {@code up} SM64 units a frame,
     * lit, to explode on landing.
     */
    private void launch(ServerPlayer by, double yaw, double forward, double up) {
        thrower = by.getUUID();
        setNoAi(false);
        entityData.set(HOLDER, -1);
        setNoGravity(false);
        lightFuse();
        launched = true;
        launchTicks = 0;
        double perTick = 1.5 / 110; // SM64 units a frame to blocks a tick
        setDeltaMovement(Math.sin(yaw) * forward * perTick, up * perTick, Math.cos(yaw) * forward * perTick);
        hasImpulse = true;
    }

    private @Nullable Entity holder() {
        int id = holderId();
        return id >= 0 ? level().getEntity(id) : null;
    }

    /** Carried: in front of the holder's chest; let go if they're gone, dead or no longer Mario. */
    private void followHolder() {
        if (!(holder() instanceof ServerPlayer player) || !player.isAlive() || !MarioHooks.isServerMario(player)
                || player.level() != level()) {
            entityData.set(HOLDER, -1);
            setNoGravity(false);
            return;
        }
        setPos(heldPosition(player, ServerMarios.yaw(player)));
        setDeltaMovement(Vec3.ZERO);
        fallDistance = 0;
    }

    /** Where a carried Bob-omb sits: SM64 holds it in front of Mario, at chest height. */
    public static Vec3 heldPosition(Entity holder, double marioYaw) {
        return heldPosition(holder.position(), marioYaw);
    }

    /** As {@link #heldPosition(Entity, double)}, from the holder's feet. */
    public static Vec3 heldPosition(Vec3 feet, double marioYaw) {
        return feet.add(Math.sin(marioYaw) * 0.45, 0.55, Math.cos(marioYaw) * 0.45);
    }

    /** A Mario player's pick-up, throw or drop (Payloads.Hold), checked here. */
    public static void onHold(Payloads.Hold payload, ServerPlayer player) {
        Entity entity = Compat.level(player).getEntity(payload.entityId());
        if (!(entity instanceof BobombEntity bobomb)) {
            if (payload.kind() == Payloads.Hold.GRAB) Services.NETWORK.sendToPlayer(player, new Payloads.Release(payload.entityId()));
            return;
        }
        switch (payload.kind()) {
            case Payloads.Hold.GRAB -> {
                boolean holdingAnother = !Compat.level(player).getEntitiesOfClass(BobombEntity.class,
                        player.getBoundingBox().inflate(4), b -> b.holderId() == player.getId()).isEmpty();
                if (!MarioHooks.isServerMario(player) || !player.isAlive() || !bobomb.grabbable() || holdingAnother
                        || bobomb.distanceToSqr(player) > 3.5 * 3.5) {
                    Services.NETWORK.sendToPlayer(player, new Payloads.Release(bobomb.getId()));
                    return;
                }
                bobomb.entityData.set(HOLDER, player.getId());
                bobomb.setNoAi(false); // a placed, frozen one comes alive once picked up
                bobomb.setNoGravity(true);
                bobomb.launched = false;
                bobomb.lightFuse(); // SM64 lights it when picked up
                bobomb.getNavigation().stop();
            }
            case Payloads.Hold.THROW -> {
                if (bobomb.holderId() != player.getId()) return;
                bobomb.setPos(heldPosition(player, payload.yaw()));
                bobomb.launch(player, payload.yaw(), 25, 20); // SM64's bobomb_thrown_loop
            }
            case Payloads.Hold.DROP -> {
                if (bobomb.holderId() != player.getId()) return;
                bobomb.entityData.set(HOLDER, -1);
                bobomb.setNoGravity(false);
                bobomb.setPos(heldPosition(player, payload.yaw()).subtract(0, 0.5, 0));
            }
            default -> { }
        }
    }

    /** Chases its target with the fuse lit (SM64's chase: 20 units a frame, turning towards Mario). */
    private final class ChaseGoal extends Goal {
        private int repath;

        ChaseGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            return fuseLit() && target != null && target.isAlive() && !held() && !launched;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) return;
            getLookControl().setLookAt(target, 30, 30);
            if (distanceToSqr(target) < 3 * 3 && hasLineOfSight(target)) {
                // Close by, straight at them as SM64's does (a path would stop a block short).
                getNavigation().stop();
                getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1.9);
            } else if (--repath <= 0) {
                repath = 5;
                getNavigation().moveTo(target, 1.9);
            }
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        Coins.burst(level, position().add(0, 0.5, 0), coins()); // popped (D86)
    }

    // ---- Client --------------------------------------------------------------------------------------------------

    private void tickClient() {
        lastAnimFrames = animFrames;
        float before = animFrames;
        Vec3 v = getDeltaMovement();
        boolean moving = v.x * v.x + v.z * v.z > 1e-4;
        // SM64 plays the walk at double speed while chasing (bobomb_act_chase_mario advances it an extra frame).
        animFrames += isAlive() ? 1.5f * (fuseLit() && moving ? 2 : 1) : 0;
        if (fuseLit() && moving && !held() && (crossed(before, animFrames, 5) || crossed(before, animFrames, 16))) {
            EnemyEffects.play(this, EnemyEffects.BOBOMB_WALK);
        }
        if (fuseLit() && isAlive()) {
            // SM64's fuse: smoke every 8 frames, every other frame near the end, and its hiss.
            if (tickCount % (fuse() > FUSE_TICKS * 0.8 ? 1 : 5) == 0) {
                float yaw = (float) Math.toRadians(-getYRot());
                level().addParticle(ParticleTypes.SMOKE, getX() - Math.sin(yaw) * 0.05, getY() + getBbHeight() + 0.1,
                        getZ() - Math.cos(yaw) * 0.05, 0, 0.03, 0);
            }
            if (tickCount % 2 == 0) EnemyEffects.play(this, EnemyEffects.BOBOMB_LIT_FUSE);
            clientFuse++;
        }
        // SM64's curr_obj_random_blink: a double blink now and then.
        if (blinkTicks > 0) {
            blinkTicks++;
            if (blinkTicks > 10) blinkTicks = 0;
        } else if (random.nextInt(66) == 0) {
            blinkTicks = 1;
        }
    }

    private int clientFuse;

    /** How long the fuse has burned (ticks; the client's own count). */
    private int fuse() {
        return level().isClientSide() ? clientFuse : fuse;
    }

    /** Client: the eyes shut (the two blinks of SM64's double blink). */
    public boolean eyesShut() {
        return blinkTicks > 0 && (blinkTicks <= 3 || (blinkTicks > 6 && blinkTicks <= 9));
    }

    private static boolean crossed(float before, float after, int frame) {
        return Math.floor((before - frame) / 25) != Math.floor((after - frame) / 25);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_EXPLODE) EnemyEffects.play(this, EnemyEffects.BOBOMB_EXPLOSION);
        else super.handleEntityEvent(id);
    }

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
