package com.nanobuntv.retro64plus.coins;

import net.minecraft.server.level.ServerLevel;
import com.nanobuntv.retro64plus.util.Saved;
import com.nanobuntv.retro64plus.server.ServerCoins;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;

/**
 * A yellow coin lying in the world (D50, D51): it pops out of a mob, a block or a chest, bounces like SM64's spawned
 * coins and is picked up by the first player to touch it ({@link ServerCoins#collect}). A coin can stand for more than
 * one (a death's scattered coins, D56) and is drawn the same. Despawns after 5 minutes, blinking for the last 3 seconds
 * as SM64's do. Moves on both sides, as experience orbs do; only the server picks up.
 */
public final class CoinEntity extends Entity {
    public static final int LIFETIME = 6000;
    public static final int BLINK_TICKS = 60;
    /** Ticks after spawning before it can be picked up (it would go straight into the player who broke the block). */
    private static final int PICKUP_DELAY = 10;
    /** Reach beyond the player's box: SM64's coin hitbox is generous (100 units across). */
    private static final double PICKUP_REACH = 0.25;

    /** The game time it despawns, so clients can blink it without their own age count. */
    private static final EntityDataAccessor<Long> DESPAWN_AT =
            SynchedEntityData.defineId(CoinEntity.class, EntityDataSerializers.LONG);

    private int value = 1;
    private int age;
    /** Scattered when a player died (D56): picking it up doesn't count toward a coin star (D61). */
    private boolean dropped;

    public CoinEntity(EntityType<? extends CoinEntity> type, Level level) {
        super(type, level);
    }

    public CoinEntity(Level level, double x, double y, double z, int value) {
        this(Coins.COIN.get(), level);
        setPos(x, y, z);
        this.value = Math.max(1, value);
        setYRot(random.nextFloat() * 360);
        entityData.set(DESPAWN_AT, level.getGameTime() + LIFETIME);
    }

    public int value() {
        return value;
    }

    public boolean dropped() {
        return dropped;
    }

    public CoinEntity markDropped() {
        dropped = true;
        return this;
    }

    public long despawnAt() {
        return entityData.get(DESPAWN_AT);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DESPAWN_AT, Long.MAX_VALUE);
    }

    @Override
    protected MovementEmission getMovementEmission() {
        return MovementEmission.NONE;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.04;
    }

    @Override
    public void tick() {
        super.tick();
        xo = getX();
        yo = getY();
        zo = getZ();
        if (isInWater()) {
            // Sinks slowly, as SM64's coins do under water.
            setDeltaMovement(getDeltaMovement().multiply(0.8, 0.8, 0.8).add(0, -0.005, 0));
        } else {
            applyGravity();
        }
        if (level().getFluidState(blockPosition()).is(FluidTags.LAVA)) {
            setDeltaMovement((random.nextFloat() - random.nextFloat()) * 0.2, 0.2,
                    (random.nextFloat() - random.nextFloat()) * 0.2);
        }
        if (!level().noCollision(getBoundingBox())) {
            moveTowardsClosestSpace(getX(), (getBoundingBox().minY + getBoundingBox().maxY) / 2, getZ());
        }
        boolean falling = getDeltaMovement().y < -0.1;
        move(MoverType.SELF, getDeltaMovement());
        float friction = onGround() ? 0.6f * 0.98f : 0.98f;
        setDeltaMovement(getDeltaMovement().multiply(friction, 0.98, friction));
        // SM64's spawned coins bounce a few times, each lower, then lie still.
        if (onGround() && falling) setDeltaMovement(getDeltaMovement().multiply(1, -0.5, 1));

        if (level() instanceof net.minecraft.server.level.ServerLevel) {
            age++;
            if (age >= LIFETIME) {
                discard();
                return;
            }
            if (age >= PICKUP_DELAY) tryPickup();
        }
    }

    private void tryPickup() {
        for (var player : level().getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(PICKUP_REACH))) {
            if (!player.isAlive() || player.isSpectator()) continue;
            ServerCoins.collect(player, this);
            discard();
            return;
        }
    }

    //? if >=1.21.2 {
    /*@Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return hurtBy(source, amount);
    }
    *///?} else {
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return hurtBy(source, amount);
    }
    //?}

    private boolean hurtBy(DamageSource source, float amount) {
        // Coins are only collected; lava and fire move them (above), nothing destroys them.
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    //? if >=1.21.6 {
    /*@Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
        load(Saved.in(input));
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
        save(Saved.out(output));
    }
    *///?} else {
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        load(Saved.in(tag));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        save(Saved.out(tag));
    }
    //?}

    private void load(Saved.In in) {
        value = Math.max(1, in.getInt("Value", 1));
        age = in.getInt("Age", 0);
        dropped = in.getBoolean("Dropped", false);
        entityData.set(DESPAWN_AT, level().getGameTime() + Math.max(0, LIFETIME - age));
    }

    private void save(Saved.Out out) {
        out.putInt("Value", value);
        out.putInt("Age", age);
        if (dropped) out.putBoolean("Dropped", true);
    }
}
