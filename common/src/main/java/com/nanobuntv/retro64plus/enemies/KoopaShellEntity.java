package com.nanobuntv.retro64plus.enemies;

import com.nanobuntv.retro64plus.util.Saved;
import com.nanobuntv.retro64plus.util.Compat;
import com.nanobuntv.retro64plus.MarioHooks;
import com.nanobuntv.retro64plus.net.Payloads;
import com.nanobuntv.retro64plus.platform.Services;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A Koopa shell lying on the ground (D79, D88): knocked off a Koopa by Mario (D91) or put down from the item. It spins
 * and sparkles as SM64's does; Mario rides it by walking into it or landing on it (the client starts the ride,
 * {@link #onRide} checks it and takes the shell away, D89). Anyone can pick it up as the item: using it or hitting it.
 * A Koopa that lost it runs back and dives in ({@link KoopaEntity}). Lava takes it. Moves on both sides, like coins.
 */
public final class KoopaShellEntity extends Entity {
    /** SM64's lying shell turns 0x1000 a frame: 22.5°, so 33.75° a tick. */
    public static final float SPIN_PER_TICK = 33.75f;
    /** How far from the shell (blocks) the server accepts a ride, allowing for lag. */
    private static final double RIDE_REACH = 3;

    public KoopaShellEntity(EntityType<? extends KoopaShellEntity> type, Level level) {
        super(type, level);
    }

    public KoopaShellEntity(Level level, Vec3 pos) {
        this(Enemies.KOOPA_SHELL.get(), level);
        setPos(pos);
        setYRot(random.nextFloat() * 360);
    }

    /** A shell knocked off a Koopa is gone after this long if nobody takes it; one put down from the item stays. */
    private static final int KNOCKED_OFF_LIFETIME = 6000;
    private boolean knockedOff;
    private int age;

    /** Marks this a shell knocked off a Koopa (KNOCKED_OFF_LIFETIME). */
    public KoopaShellEntity knockedOff() {
        knockedOff = true;
        return this;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected MovementEmission getMovementEmission() {
        return MovementEmission.NONE;
    }

    /** SM64's shell: gravity 4 units a frame², like Mario's. */
    @Override
    protected double getDefaultGravity() {
        return 0.08;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && knockedOff && ++age >= KNOCKED_OFF_LIFETIME) {
            poof((ServerLevel) level(), position());
            discard();
            return;
        }
        if (isInWater()) {
            // SM64's shell floats (its buoyancy beats its gravity).
            setDeltaMovement(getDeltaMovement().multiply(0.8, 0.8, 0.8).add(0, 0.02, 0));
        } else {
            applyGravity();
        }
        if (!level().noCollision(getBoundingBox())) {
            moveTowardsClosestSpace(getX(), (getBoundingBox().minY + getBoundingBox().maxY) / 2, getZ());
        }
        move(MoverType.SELF, getDeltaMovement());
        float friction = onGround() ? 0.6f * 0.98f : 0.98f;
        setDeltaMovement(getDeltaMovement().multiply(friction, 0.98, friction));
        if (level().isClientSide()) {
            // SM64's koopa_shell_spawn_sparkles, thinned out.
            if (tickCount % 4 == 0) {
                level().addParticle(ParticleTypes.END_ROD, getX() + (random.nextDouble() - 0.5) * 0.8,
                        getY() + 0.1 + random.nextDouble() * 0.4, getZ() + (random.nextDouble() - 0.5) * 0.8, 0, 0.02, 0);
            }
        } else if (isInLava()) {
            poof((ServerLevel) level(), position());
            discard();
        }
    }

    /** Hit by a player: pops off as the item (nothing in creative). Blasts and the like just take it away. */
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
        if (!(level() instanceof ServerLevel level) || isRemoved()) return false;
        if (isInvulnerable() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (source.getEntity() instanceof Player player) {
            //? if >=1.21.2 {
            /*if (!player.getAbilities().instabuild) spawnAtLocation(level, new ItemStack(Enemies.KOOPA_SHELL_ITEM.get()));
            *///?} else {
            if (!player.getAbilities().instabuild) spawnAtLocation(new ItemStack(Enemies.KOOPA_SHELL_ITEM.get()));
            //?}
            discard();
            return true;
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_FIRE)) {
            poof(level, position());
            discard();
            return true;
        }
        return false;
    }

    /** Using it picks it up. */
    //? if >=26.1 {
    /*@Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        return pickUp(player);
    }
    *///?} else {
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return pickUp(player);
    }
    //?}

    private InteractionResult pickUp(Player player) {
        if (!level().isClientSide() && isAlive()) {
            ItemStack stack = new ItemStack(Enemies.KOOPA_SHELL_ITEM.get());
            if (!player.getInventory().add(stack)) Compat.drop(player, stack);
            discard();
        }
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Enemies.KOOPA_SHELL_ITEM.get());
    }

    /** SM64's spawn_mist_particles: the puff a shell leaves when it's gone. */
    public static void poof(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.2, at.z, 10, 0.3, 0.1, 0.3, 0.02);
    }

    /** A Mario player got on {@code payload.entityId()} (Payloads.Hold.RIDE): the shell goes; refused, they get off. */
    public static void onRide(Payloads.Hold payload, ServerPlayer player) {
        Entity entity = Compat.level(player).getEntity(payload.entityId());
        if (entity instanceof KoopaShellEntity shell && shell.isAlive() && player.isAlive()
                && MarioHooks.isServerMario(player) && shell.distanceToSqr(player) < RIDE_REACH * RIDE_REACH) {
            shell.discard();
        } else {
            Services.NETWORK.sendToPlayer(player, new Payloads.Release(payload.entityId()));
        }
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
        knockedOff = in.getBoolean("KnockedOff", false);
        age = in.getInt("Age", 0);
    }

    private void save(Saved.Out out) {
        if (knockedOff) {
            out.putBoolean("KnockedOff", true);
            out.putInt("Age", age);
        }
    }
}
