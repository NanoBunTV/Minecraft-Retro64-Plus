package com.nanobuntv.retro64plus.enemies;

import com.nanobuntv.retro64plus.util.Saved;
import com.nanobuntv.retro64plus.util.Compat;
import com.nanobuntv.retro64plus.coins.Coins;
import com.nanobuntv.retro64plus.combat.MarioAttack;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * SM64's Chain Chomp (D78, D94–D104): chained to its post ({@link ChompPostBlock}), it hops about within the chain's
 * reach (750 SM64 units, about 7 blocks), turns to face a player or hostile mob that comes near (D97), and lunges at
 * them (SM64's 140 units a frame, reaching 900 units from the post); touching it costs three power meter segments
 * (D100). Mario's attacks bounce off as in SM64, but weapons can kill it (60 HP, D94): 5 coins and some iron (D76,
 * D104). Three ground pounds on its post free it: it bounds away and is gone, leaving 5 coins (D95). Never spawns
 * naturally; world generation places it with its post. Not an {@link Enemy}, so iron golems leave it be.
 */
public final class ChainChompEntity extends PathfinderMob implements Sm64Enemy {
    /** SM64's chain: 750 units normally, 900 while lunging; how near a target must be (to the post) to be noticed. */
    public static final double CHAIN = 750 / 110.0, LUNGE_CHAIN = 900 / 110.0, NOTICE = LUNGE_CHAIN + 2;
    /** The model's middle sits this far above its feet (the model is centered on its origin). */
    public static final double MODEL_LIFT = 146 / 110.0;
    public static final int IDLE = 0, TURN = 1, LUNGE = 2, FREED = 3;
    private static final byte EVENT_HOP = 100, EVENT_LUNGE = 101, EVENT_POST = 102;
    /** SM64: turning hops 10 units a frame forwards and 20 up; the lunge 140 forwards (here capped for Minecraft). */
    private static final double HOP_FORWARD = 10 * 1.5 / 110, HOP_UP = 0.3, LUNGE_SPEED = 1.4;
    /** The turn before a lunge (at least), the lunge, going free; 4 seconds from one lunge to the next (D127). */
    private static final int TURN_TICKS = 27, LUNGE_TICKS = 12, FREED_TICKS = 60, LUNGE_EVERY = 80;

    private static final EntityDataAccessor<Optional<BlockPos>> POST =
            SynchedEntityData.defineId(ChainChompEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Integer> STATE =
            SynchedEntityData.defineId(ChainChompEntity.class, EntityDataSerializers.INT);

    private int stateTicks;
    private int lastLunge = -LUNGE_EVERY;
    private @Nullable LivingEntity target;

    /** Client: animation frames played. */
    public float animFrames, lastAnimFrames;

    public ChainChompEntity(EntityType<? extends ChainChompEntity> type, Level level) {
        super(type, level);
        xpReward = 10;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 60)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 7.5) // three power meter segments (D100)
                .add(Attributes.ATTACK_KNOCKBACK, 2.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 16);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(POST, Optional.empty());
        builder.define(STATE, IDLE);
    }

    public Optional<BlockPos> post() {
        return entityData.get(POST);
    }

    public void setPost(BlockPos pos) {
        entityData.set(POST, Optional.of(pos.immutable()));
    }

    public int state() {
        return entityData.get(STATE);
    }

    private void setState(int state) {
        entityData.set(STATE, state);
        stateTicks = 0;
    }

    @Override
    public int coins() {
        return 5;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** From a spawn egg (no post yet): its post goes where it was put, and it hops off beside it. */
    @Override
    @SuppressWarnings("deprecation") // vanilla's "override only" mark; overriding and calling super is the intended use
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            MobSpawnType reason, @Nullable SpawnGroupData data) {
        if (post().isEmpty() && reason != MobSpawnType.STRUCTURE && reason != MobSpawnType.CHUNK_GENERATION) {
            BlockPos at = blockPosition();
            if (level.getBlockState(at).canBeReplaced() && level.getBlockState(at.above()).canBeReplaced()) {
                level.setBlock(at, Enemies.CHOMP_POST.get().defaultBlockState(), 3);
                setPost(at);
                // Beside the post, on a side with room for it (else where it was put, over the post).
                for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    Vec3 beside = position().add(side.getStepX() * 2, 0, side.getStepZ() * 2);
                    if (level.noCollision(this, getBoundingBox().move(beside.subtract(position())))) {
                        setPos(beside);
                        break;
                    }
                }
            }
        }
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    // ---- Server --------------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            lastAnimFrames = animFrames;
            if (isAlive()) animFrames += 1.5f * (state() == LUNGE ? 2 : 1);
            return;
        }
        stateTicks++;
        if (state() == FREED) {
            tickFreed();
            return;
        }
        BlockPos post = post().orElse(null);
        if (post == null) return; // placed without one (a command): sits still
        if (!(level().getBlockState(post).getBlock() instanceof ChompPostBlock)
                || level().getBlockState(post).getValue(ChompPostBlock.POUNDS) >= ChompPostBlock.MAX_POUNDS) {
            free();
            return;
        }
        if (isNoAi()) return; // a frozen one (NoAI) still goes free, but never moves or bites
        if (tickCount % 10 == 0) findTarget(post);
        switch (state()) {
            case IDLE -> tickIdle(post);
            case TURN -> tickTurn();
            case LUNGE -> {
                if (stateTicks > LUNGE_TICKS && onGround()) setState(IDLE);
            }
            default -> { }
        }
        leash(post);
        bite();
    }

    /** The nearest player (not in creative) or hostile mob within reach of the chain, or none. */
    private void findTarget(BlockPos post) {
        Vec3 center = Vec3.atBottomCenterOf(post);
        target = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center)
                .inflate(NOTICE, 4, NOTICE), this::isTarget)) {
            double d = e.position().subtract(center).horizontalDistanceSqr();
            if (d < NOTICE * NOTICE && d < best && hasLineOfSight(e)) {
                best = d;
                target = e;
            }
        }
    }

    private boolean isTarget(LivingEntity e) {
        if (e == this || !e.isAlive() || e instanceof ChainChompEntity) return false;
        if (e instanceof Player player) return !player.isSpectator() && !player.isCreative();
        return e instanceof Enemy;
    }

    /** No one near: SM64's little hops about, now and then. */
    private void tickIdle(BlockPos post) {
        if (target != null) {
            setState(TURN);
            return;
        }
        if (onGround() && random.nextInt(50) == 0) {
            // Somewhere within the chain, a hop that way.
            double angle = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * CHAIN * 0.7;
            Vec3 to = Vec3.atBottomCenterOf(post).add(Math.sin(angle) * r, 0, Math.cos(angle) * r);
            face(to);
            hop(HOP_FORWARD * 3);
        }
    }

    /** SM64's chain_chomp_sub_act_turn: small hops round to face the target, a pause, then the lunge. */
    private void tickTurn() {
        if (target == null || !target.isAlive()) {
            setState(IDLE);
            return;
        }
        face(target.position());
        if (stateTicks < TURN_TICKS || tickCount - lastLunge < LUNGE_EVERY) {
            if (onGround() && stateTicks % 10 == 1) hop(HOP_FORWARD);
            return;
        }
        if (!onGround()) return;
        Vec3 to = target.position().add(0, target.getBbHeight() / 2, 0)
                .subtract(position().add(0, MODEL_LIFT, 0));
        Vec3 dir = to.lengthSqr() < 1e-6 ? Vec3.directionFromRotation(0, getYRot()) : to.normalize();
        setDeltaMovement(dir.x * LUNGE_SPEED, Math.max(0.1, dir.y * LUNGE_SPEED * 0.5 + 0.2), dir.z * LUNGE_SPEED);
        hasImpulse = true;
        level().broadcastEntityEvent(this, EVENT_LUNGE);
        lastLunge = tickCount;
        setState(LUNGE);
    }

    private void face(Vec3 to) {
        float yaw = (float) Math.toDegrees(Math.atan2(-(to.x - getX()), to.z - getZ()));
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private void hop(double forward) {
        double yaw = Math.toRadians(getYRot());
        setDeltaMovement(-Math.sin(yaw) * forward, HOP_UP, Math.cos(yaw) * forward);
        hasImpulse = true;
        level().broadcastEntityEvent(this, EVENT_HOP);
    }

    /**
     * The chain: never further from the post than it reaches (more while lunging); pulled up short, it stops. Fallen
     * below the post (off a ledge), the chain yanks it back up towards it now and then.
     */
    private void leash(BlockPos post) {
        if (onGround() && getY() < post.getY() - YANK_BELOW && stateTicks % YANK_EVERY == 0) {
            Vec3 to = Vec3.atBottomCenterOf(post).subtract(position()).multiply(1, 0, 1);
            Vec3 n = to.lengthSqr() > 1e-6 ? to.normalize() : Vec3.ZERO;
            setDeltaMovement(n.x * 0.3, YANK_UP, n.z * 0.3);
            hasImpulse = true;
            level().broadcastEntityEvent(this, EVENT_HOP);
        }
        double max = state() == LUNGE ? LUNGE_CHAIN : CHAIN;
        Vec3 center = Vec3.atBottomCenterOf(post);
        Vec3 off = position().subtract(center).multiply(1, 0, 1);
        double d = off.length();
        if (d <= max) return;
        Vec3 n = off.scale(1 / d);
        setPos(center.x + n.x * max, getY(), center.z + n.z * max);
        Vec3 v = getDeltaMovement();
        double outward = v.x * n.x + v.z * n.z;
        if (outward > 0) setDeltaMovement(v.x - n.x * outward * 1.3, Math.min(v.y, 0), v.z - n.z * outward * 1.3);
    }

    /** Touching a player or hostile mob bites them (SM64's three segments and a hard knock, D100). */
    private void bite() {
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.15),
                this::isTarget)) {
            Compat.doHurtTarget(this, e);
        }
    }

    /** Mario's attacks bounce off, as in SM64 (D94); only weapons and the post get past it. */
    @Override
    public boolean onMarioAttack(ServerPlayer player, MarioAttack attack) {
        return true;
    }

    /** Blocks below its post at which the chain pulls it up, how often (ticks), and the yank's upward speed. */
    private static final double YANK_BELOW = 1.5, YANK_UP = 0.62;
    private static final int YANK_EVERY = 20;

    /** Three ground pounds on the post (D95): 5 coins, then it bounds away. */
    public void free() {
        if (state() == FREED) return;
        setNoAi(false); // it bounds away even if it was frozen
        Coins.burst((ServerLevel) level(), position().add(0, 1, 0), coins());
        BlockPos post = post().orElse(blockPosition());
        Vec3 away = position().subtract(Vec3.atBottomCenterOf(post)).multiply(1, 0, 1);
        if (away.lengthSqr() < 1e-4) away = Vec3.directionFromRotation(0, getYRot());
        face(position().add(away));
        entityData.set(POST, Optional.empty());
        setState(FREED);
    }

    /** SM64's chain_chomp_released_jump_away: big bounds away, then gone in a puff. */
    private void tickFreed() {
        if (onGround() && stateTicks > 2) {
            double yaw = Math.toRadians(getYRot());
            setDeltaMovement(-Math.sin(yaw) * 0.45, 0.65, Math.cos(yaw) * 0.45);
            hasImpulse = true;
            level().broadcastEntityEvent(this, EVENT_HOP);
        }
        if (stateTicks >= FREED_TICKS) {
            ((ServerLevel) level()).sendParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 30, 0.8, 0.8, 0.8,
                    0.05);
            discard();
        }
    }

    /** The post was pounded (ChompPostBlock): SM64's thud comes from here, where the clients have a sound source. */
    public void postPounded() {
        level().broadcastEntityEvent(this, EVENT_POST);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        Coins.burst(level, position().add(0, 1, 0), coins());
        // Its post has nothing left to hold: gone a little later, as a pounded one (D107).
        post().ifPresent(p -> {
            if (level.getBlockState(p).getBlock() instanceof ChompPostBlock block) {
                level.scheduleTick(p, block, ChompPostBlock.REMOVE_DELAY_TICKS);
            }
        });
    }

    //? if >=1.21.5 {
    /*@Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        return false;
    }
    *///?} else {
    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }
    //?}

    //? if >=1.21.6 {
    /*@Override
    public void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
        super.addAdditionalSaveData(output);
        save(Saved.out(output));
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
        super.readAdditionalSaveData(input);
        load(Saved.in(input));
    }
    *///?} else {
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        save(Saved.out(tag));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        load(Saved.in(tag));
    }
    //?}

    private void save(Saved.Out out) {
        post().ifPresent(p -> out.putBlockPos("Post", p));
        if (state() == FREED) out.putBoolean("Freed", true);
    }

    private void load(Saved.In in) {
        in.getBlockPos("Post").ifPresent(this::setPost);
        if (in.getBoolean("Freed", false)) setState(FREED); // saved while bounding away: it goes on and is gone
    }

    // ---- Client --------------------------------------------------------------------------------------------------

    @Override
    public void handleEntityEvent(byte id) {
        switch (id) {
            case EVENT_HOP -> EnemyEffects.play(this, EnemyEffects.CHAIN_CHOMP_HOP);
            case EVENT_LUNGE -> EnemyEffects.play(this, EnemyEffects.CHAIN_CHOMP_LUNGE);
            case EVENT_POST -> EnemyEffects.play(this, EnemyEffects.POUND_WOOD_POST);
            default -> super.handleEntityEvent(id);
        }
    }

    /** The model's yaw for drawing, eased between ticks. */
    public float yaw(float partialTick) {
        return Mth.rotLerp(partialTick, yBodyRotO, yBodyRot);
    }

    @Override
    public boolean isPickable() {
        return state() != FREED && super.isPickable();
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

    /** No pushing it about, not even by its own target. */
    @Override
    protected void doPush(Entity entity) {}
}
