package com.nanobuntv.retro64plus.stars;

import com.nanobuntv.retro64plus.util.Saved;
import com.nanobuntv.retro64plus.server.ServerStars;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * A Power Star waiting to be collected (D63, D67): it floats where it appeared, belongs to one player (only their client
 * draws it, only they can touch it) and stands for one star id. It never despawns; it's removed when collected, or if
 * its owner already has that star.
 */
public final class PowerStarEntity extends Entity {
    private static final double PICKUP_REACH = 0.3;
    /** The owner's UUID as text, or "" (a string: the optional UUID serializer is gone from 26.1). */
    private static final EntityDataAccessor<String> OWNER =
            SynchedEntityData.defineId(PowerStarEntity.class, EntityDataSerializers.STRING);

    private String starId = "";

    public PowerStarEntity(EntityType<? extends PowerStarEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public PowerStarEntity(Level level, double x, double y, double z, UUID owner, String starId) {
        this(Stars.POWER_STAR.get(), level);
        setPos(x, y, z);
        entityData.set(OWNER, owner.toString());
        this.starId = starId;
    }

    public @Nullable UUID owner() {
        String owner = entityData.get(OWNER);
        if (owner.isEmpty()) return null;
        try {
            return UUID.fromString(owner);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String starId() {
        return starId;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, "");
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        UUID owner = owner();
        if (owner == null || starId.isEmpty()) {
            discard();
            return;
        }
        if (!(level.getPlayerByUUID(owner) instanceof ServerPlayer player)) return;
        if (ServerStars.has(player, starId)) {
            discard(); // collected another way (a second star with the same id)
            return;
        }
        if (player.isAlive() && !player.isSpectator()
                && player.getBoundingBox().intersects(getBoundingBox().inflate(PICKUP_REACH))) {
            ServerStars.collect(player, this);
            discard();
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
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPushable() {
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
        in.getUuid("Owner").ifPresent(owner -> entityData.set(OWNER, owner.toString()));
        starId = in.getString("Star").orElse("");
    }

    private void save(Saved.Out out) {
        UUID owner = owner();
        if (owner != null) out.putUuid("Owner", owner);
        out.putString("Star", starId);
    }
}
