package com.nanobuntv.retro64plus.enemies;

import com.nanobuntv.retro64plus.util.Compat;
import com.nanobuntv.retro64plus.coins.Coins;
import com.nanobuntv.retro64plus.combat.MarioAttack;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * SM64's Koopa Troopa (D72, D87): wanders slowly and never hurts anyone; a player who comes close (or hits it) sends it
 * running. Any Mario hit knocks it out of its shell (D91): it slides away, the shell lies where it was
 * ({@link KoopaShellEntity}), and the bare Koopa runs back and dives into it if it can. A Mario hit on a bare Koopa
 * finishes it (a stomp squashes it). Swords and arrows meet a small mob's health and leave the shell on (D91). One coin
 * (D76) and its loot table's little extra.
 */
public class KoopaEntity extends Monster implements Sm64Enemy {
    /** Twice SM64's object scale for a normal Koopa (the playtest: at SM64's size it looked too small). */
    public static final float SCALE = 2;
    /** States: walking about, knocked flat (sliding, then getting up), diving for the shell. */
    public static final int NORMAL = 0, LYING = 1, DIVING = 2;
    private static final byte EVENT_DAMAGE = 100, EVENT_SQUISH = 101;
    /** SM64: a shelled Koopa runs from Mario within 300 units, and stops once he's 800 away. */
    private static final double FLEE_START = 300 / 110.0, FLEE_STOP = 800 / 110.0;
    /**
     * How far a bare Koopa looks for a shell, and dives at it from: SM64's 600 units is too far for Minecraft's ground
     * friction, which stops a slide much sooner than SM64's.
     */
    private static final double SHELL_SEARCH = 16, DIVE_RANGE = 2.5;
    /** SM64's knock out of the shell: 20 units a frame, then lying still for a while. */
    private static final double KNOCK_SPEED = 20 * 1.5 / 110;
    private static final int LYING_TICKS = 40, DIVE_TICKS = 25, INTANGIBLE_TICKS = 7;

    private static final EntityDataAccessor<Boolean> SHELLED =
            SynchedEntityData.defineId(KoopaEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> STATE =
            SynchedEntityData.defineId(KoopaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SQUISHED =
            SynchedEntityData.defineId(KoopaEntity.class, EntityDataSerializers.BOOLEAN);

    private int stateTicks;
    /** SM64 makes it intangible for a moment after losing its shell. */
    private int intangible;

    /** Client: the animation playing, its frames played, and the blink. */
    public int animation = 7;
    public float animFrames, lastAnimFrames;
    public int blinkTicks;
    private int nextBlink = 40;

    public KoopaEntity(EntityType<? extends KoopaEntity> type, Level level) {
        super(type, level);
        xpReward = 3;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 10)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 0)
                .add(Attributes.FOLLOW_RANGE, 16);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new SeekShellGoal());
        goalSelector.addGoal(2, new FleeGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.4));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SHELLED, true);
        builder.define(STATE, NORMAL);
        builder.define(SQUISHED, false);
    }

    public boolean shelled() {
        return entityData.get(SHELLED);
    }

    public int state() {
        return entityData.get(STATE);
    }

    public boolean squished() {
        return entityData.get(SQUISHED);
    }

    private void setState(int state) {
        entityData.set(STATE, state);
        stateTicks = 0;
    }

    @Override
    public int coins() {
        return 1;
    }

    // ---- Server --------------------------------------------------------------------------------------------------

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || state() != NORMAL;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            tickClient();
            return;
        }
        if (intangible > 0) intangible--;
        stateTicks++;
        int state = state();
        if (state == LYING && stateTicks >= LYING_TICKS && onGround()) setState(NORMAL);
        if (state == DIVING) {
            if (!shelled()) tryTakeShell();
            if (stateTicks >= DIVE_TICKS && onGround()) setState(NORMAL);
        }
    }

    /** Diving: a shell within reach (and no one riding it, since a ridden one is gone) goes back on. */
    private void tryTakeShell() {
        List<KoopaShellEntity> shells = level().getEntitiesOfClass(KoopaShellEntity.class,
                getBoundingBox().inflate(0.3), KoopaShellEntity::isAlive);
        if (shells.isEmpty()) return;
        shells.get(0).discard();
        entityData.set(SHELLED, true);
        setState(LYING); // SM64 slides on in the shell and gets up
        stateTicks = LYING_TICKS / 2;
        setDeltaMovement(getDeltaMovement().multiply(0.5, 1, 0.5));
    }

    /**
     * Mario's attacks (D74, D91): with the shell, any of them knocks it off (sideways hits send the Koopa sliding away
     * from him); without it, they finish it, a stomp or ground pound squashing it flat.
     */
    @Override
    public boolean onMarioAttack(ServerPlayer player, MarioAttack attack) {
        if (intangible > 0) return true;
        boolean fromAbove = attack == MarioAttack.STOMP || attack == MarioAttack.GROUND_POUND;
        if (shelled()) {
            entityData.set(SHELLED, false);
            setState(LYING);
            intangible = INTANGIBLE_TICKS;
            getNavigation().stop();
            level().broadcastEntityEvent(this, EVENT_DAMAGE);
            KoopaShellEntity shell = new KoopaShellEntity(level(), position()).knockedOff();
            level().addFreshEntity(shell);
            Vec3 away = fromAbove ? Vec3.directionFromRotation(0, getYRot()) : position().subtract(player.position());
            away = away.multiply(1, 0, 1);
            if (away.lengthSqr() < 1e-6) away = new Vec3(0, 0, 1);
            away = away.normalize().scale(KNOCK_SPEED);
            setDeltaMovement(away.x, 0.2, away.z);
            setYRot((float) Math.toDegrees(Math.atan2(-away.x, away.z)));
            hasImpulse = true;
            player.setLastHurtMob(this);
            return true;
        }
        // Flat before the hit (death keeps the look), but only if the hit lands.
        if (fromAbove) entityData.set(SQUISHED, true);
        boolean hit = Compat.hurt(this, player.damageSources().playerAttack(player), getHealth() + 100);
        if (fromAbove && hit) level().broadcastEntityEvent(this, EVENT_SQUISH);
        if (fromAbove && !hit) entityData.set(SQUISHED, false);
        if (hit && !fromAbove) {
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

    //? if >=1.21.6 {
    /*@Override
    public void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Shelled", shelled());
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(SHELLED, input.getBooleanOr("Shelled", true));
    }
    *///?} else {
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Shelled", shelled());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Shelled")) entityData.set(SHELLED, tag.getBoolean("Shelled"));
    }
    //?}

    /** The nearest player who isn't a spectator, within {@code range}, or null. */
    private @Nullable Player nearbyPlayer(double range) {
        Player player = level().getNearestPlayer(this, range);
        return player != null && !player.isSpectator() && player.isAlive() ? player : null;
    }

    /**
     * Runs from players (SM64's koopa_shelled_act_run_from_mario): with the shell, from one who comes within 300
     * units; bare, from anyone nearer than 800. Anything that hurt it lately counts too.
     */
    private final class FleeGoal extends Goal {
        private @Nullable LivingEntity from;

        FleeGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            LivingEntity attacker = getLastHurtByMob();
            if (attacker != null && tickCount - getLastHurtByMobTimestamp() < 100 && distanceTo(attacker) < FLEE_STOP) {
                from = attacker;
            } else {
                from = nearbyPlayer(shelled() ? FLEE_START : FLEE_STOP);
            }
            return from != null && state() == NORMAL;
        }

        @Override
        public boolean canContinueToUse() {
            return from != null && from.isAlive() && state() == NORMAL && distanceTo(from) < FLEE_STOP;
        }

        @Override
        public void start() {
            runAway();
        }

        @Override
        public void tick() {
            if (getNavigation().isDone()) runAway();
        }

        private void runAway() {
            if (from == null) return;
            Vec3 to = DefaultRandomPos.getPosAway(KoopaEntity.this, 10, 5, from.position());
            if (to != null) getNavigation().moveTo(to.x, to.y, to.z, shelled() ? 1.6 : 1.9);
        }

        @Override
        public void stop() {
            from = null;
            getNavigation().stop();
        }
    }

    /** Bare: runs to the nearest shell and dives into it from a few blocks away (SM64's koopa_unshelled_act_run). */
    private final class SeekShellGoal extends Goal {
        /** Ticks it runs for a shell before giving up for as long (one it can't reach, say). */
        private static final int GIVE_UP_TICKS = 200;
        private @Nullable KoopaShellEntity shell;
        private int repath, seeking, restUntil;

        SeekShellGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (shelled() || state() != NORMAL || tickCount < restUntil) return false;
            List<KoopaShellEntity> shells = level().getEntitiesOfClass(KoopaShellEntity.class,
                    getBoundingBox().inflate(SHELL_SEARCH), KoopaShellEntity::isAlive);
            shell = null;
            double best = Double.MAX_VALUE;
            for (KoopaShellEntity s : shells) {
                double d = distanceToSqr(s);
                if (d < best) {
                    best = d;
                    shell = s;
                }
            }
            return shell != null;
        }

        @Override
        public boolean canContinueToUse() {
            return shell != null && shell.isAlive() && !shelled() && state() == NORMAL && seeking < GIVE_UP_TICKS;
        }

        @Override
        public void start() {
            seeking = 0;
        }

        @Override
        public void tick() {
            seeking++;
            if (shell == null) return;
            getLookControl().setLookAt(shell, 30, 30);
            double distance = distanceTo(shell);
            if (distance < DIVE_RANGE && onGround() && hasLineOfSight(shell)) {
                // SM64's dive: a low leap at the shell, sized to land on it.
                Vec3 to = shell.position().subtract(position()).multiply(1, 0, 1).normalize();
                double speed = Math.min(0.45, 0.12 + distance * 0.1);
                setDeltaMovement(to.x * speed, 0.3, to.z * speed);
                setYRot((float) Math.toDegrees(Math.atan2(-to.x, to.z)));
                yBodyRot = getYRot();
                hasImpulse = true;
                getNavigation().stop();
                setState(DIVING);
            } else if (--repath <= 0) {
                repath = 5;
                getNavigation().moveTo(shell, 1.9);
            }
        }

        @Override
        public void stop() {
            shell = null;
            if (seeking >= GIVE_UP_TICKS) restUntil = tickCount + GIVE_UP_TICKS; // flees and wanders a while first
        }
    }

    // ---- Client --------------------------------------------------------------------------------------------------

    /**
     * SM64's animations (the Koopa's table): 7 standing, 9 walking, 1 running, 5 sliding on its belly, 6 getting up;
     * bare, 3 running and 2 knocked flat. One SM64 frame per frame; footsteps on the walk's and runs' beats.
     */
    private void tickClient() {
        Vec3 v = getDeltaMovement();
        double unitsPerFrame = Math.sqrt(v.x * v.x + v.z * v.z) * 110 / 1.5;
        int state = state();
        int wanted;
        if (!isAlive()) {
            wanted = animation;
        } else if (state == DIVING) {
            wanted = 5;
        } else if (state == LYING) {
            wanted = shelled() ? (unitsPerFrame > 1 ? 5 : 6) : 2;
        } else if (!shelled()) {
            wanted = unitsPerFrame > 0.5 ? 3 : 7;
        } else {
            wanted = unitsPerFrame > 8 ? 1 : unitsPerFrame > 0.5 ? 9 : 7;
        }
        if (wanted != animation) {
            animation = wanted;
            animFrames = lastAnimFrames = 0;
        }
        lastAnimFrames = animFrames;
        float before = animFrames;
        if (isAlive()) animFrames += 1.5f;
        int[] beats = switch (animation) {
            case 9 -> new int[] {2, 17, 30};
            case 1 -> new int[] {0, 11, 22};
            case 3 -> new int[] {0, 6, 13};
            default -> null;
        };
        if (beats != null && (crossed(before, animFrames, beats[0], beats[2])
                || crossed(before, animFrames, beats[1], beats[2]))) {
            EnemyEffects.play(this, EnemyEffects.KOOPA_WALK);
        }
        // SM64's obj_update_blinking: 4 frames shut every 20 to 70 frames (bare: 3 every 10 to 25).
        if (blinkTicks > 0) {
            blinkTicks--;
        } else if (--nextBlink <= 0) {
            blinkTicks = shelled() ? 3 : 2;
            nextBlink = shelled() ? 13 + random.nextInt(34) : 7 + random.nextInt(10);
        }
    }

    private static boolean crossed(float before, float after, int frame, int length) {
        return Math.floor((before - frame) / length) != Math.floor((after - frame) / length);
    }

    @Override
    public void handleEntityEvent(byte id) {
        switch (id) {
            case EVENT_DAMAGE -> EnemyEffects.play(this, EnemyEffects.KOOPA_DAMAGE);
            case EVENT_SQUISH -> EnemyEffects.play(this, EnemyEffects.STOMPED);
            case 3 -> {
                EnemyEffects.play(this, EnemyEffects.KOOPA_DEATH);
                super.handleEntityEvent(id);
            }
            default -> super.handleEntityEvent(id);
        }
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
