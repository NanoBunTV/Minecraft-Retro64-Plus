package com.nanobuntv.retro64plus.client;

import com.nanobuntv.retro64plus.MarioCharacter;
import com.nanobuntv.retro64plus.MarioHooks;
import com.nanobuntv.retro64plus.Retro64Plus;
import com.nanobuntv.retro64plus.health.MarioHealth;
import com.nanobuntv.retro64plus.net.MarioUpdate;
import com.nanobuntv.retro64plus.net.Payloads;
import com.nanobuntv.retro64plus.platform.Services;
import com.nanobuntv.retro64plus.sm64.Actions;
import com.nanobuntv.retro64plus.sm64.LibSM64;
import com.nanobuntv.retro64plus.sm64.Units;
import com.nanobuntv.retro64plus.sm64.lib.SM64MarioInputs;
import com.nanobuntv.retro64plus.world.CollisionBuilder;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import com.nanobuntv.retro64plus.enemies.BobombEntity;
import com.nanobuntv.retro64plus.enemies.KoopaShellEntity;
import net.minecraft.world.phys.AABB;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/**
 * The local player as Mario (M1 checkpoint 2): turning on and off with the Mario key (D14), running libsm64 at SM64's
 * 30 Hz from the 20 Hz client tick (never from render events, unlike upstream), feeding it the controls, the blocks
 * around it and the water level, and keeping the player entity where Mario is. Checkpoint 4: the chosen character
 * (D27), a gamepad (D31), attacks (D29) and the sounds sent to other players (D32). M2: health is Minecraft's, shown
 * to SM64 every frame (D36); Minecraft's hits knock him back, SM64's own damage goes to the server, and dying plays
 * SM64's death before the death screen (D37). Render thread only.
 *
 * <p>Timing: after client tick {@code T} (counted since Mario appeared or was teleported) exactly
 * {@code floor(1.5 T)} SM64 frames have run, so the game runs at SM64's speed whatever the frame rate.
 */
public final class LocalMario {
    /** Collision box around Mario: blocks to each side, below and above (rebuilt when he moves to another block). */
    private static final int RADIUS = 6, BELOW = 8, ABOVE = 8;
    /** Rebuild the collision at least this often (SM64 frames), to pick up blocks placed or broken nearby. */
    private static final int REBUILD_FRAMES = 6;
    /** A server move of the player further than this (blocks) teleports Mario to them (/tp, respawn, portals). */
    private static final double TELEPORT_DISTANCE = 1.5;
    /** Ticks the death screen waits after dying, for SM64's death animation (D37). */
    static final int DEATH_SCREEN_DELAY = 50;
    /** A damage event waits this many ticks for its health update, to size the knockback. */
    private static final int HIT_WAIT_TICKS = 2;

    private static @Nullable LocalMario active;

    private final MarioInstance mario;
    private final CollisionBuilder collision = new CollisionBuilder();
    private final CameraType previousCamera;
    private LocalPlayer player;
    private Level level;
    private long ticks;
    private @Nullable BlockPos collisionCenter;
    private int framesSinceCollision;
    /** Where this controller last put the player; any other position means the server moved them. */
    private Vec3 placedAt;
    private boolean wasRiding, wasSleeping;
    private final MarioAttacks attacks = new MarioAttacks(this::tryGrab);
    /** The Bob-omb Mario carries (M4), its entity id, or -1. */
    private int heldBobomb = -1;
    /** Mario rides a Koopa shell (M4 checkpoint 3), and the shell entity he got on (its id until the ride ends). */
    private boolean ridingShell;
    private int rideShellId = -1;
    /** Pick-ups, throws, drops and rides to report after this tick's state. */
    private final List<Payloads.Hold> holds = new ArrayList<>();
    /** Sounds Mario made this tick, for the other players (D32). */
    private final List<Integer> tickSounds = new ArrayList<>();
    private final int[] frameSounds = new int[16];
    /** SM64's own damage not yet sent, in SM64 units, by kind (D36): falls and lava. */
    private int selfFallDamage, selfLavaDamage;
    /** A hit the server told us about, waiting for the health that says how hard it was. */
    private @Nullable DamageSource pendingHit;
    private int pendingHitAge;
    private float lastHealth = -1;
    /** The death screen, held while SM64's death plays (D37); null until the kill packet's arrives. */
    private @Nullable Screen heldDeathScreen;
    private boolean holdingDeathScreen;
    private int deadTicks;
    private static boolean openingDeathScreen;
    /** Knockbacks so far (dev diagnostics). */
    int knockbacks;
    /** Where Mario landed a ground pound this tick, for the server (D18); null if he didn't. */
    private @Nullable Vec3 groundPoundAt;
    /** Ground pound landings reported so far (dev diagnostics). */
    int groundPounds;

    private LocalMario(MarioInstance mario, LocalPlayer player, CameraType previousCamera) {
        this.mario = mario;
        this.player = player;
        this.level = player.level();
        this.previousCamera = previousCamera;
        this.placedAt = player.position();
    }

    public static boolean isActive() {
        return active != null;
    }

    static @Nullable LocalMario active() {
        return active;
    }

    /** The Mario key: on or off. libsm64 must be ready. */
    static void toggle() {
        if (active != null) {
            turnOff();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isSpectator()) return;
        MarioTexture.ensureRegistered();
        // libsm64 needs a floor under Mario before it will create him.
        CollisionBuilder spawnCollision = new CollisionBuilder();
        BlockPos at = player.blockPosition();
        spawnCollision.build(player.level(), at, RADIUS, BELOW, ABOVE, player.level().getMinBuildHeight());
        LibSM64.lib().sm64_static_surfaces_load(spawnCollision.toSurfaces(), spawnCollision.triangleCount());
        MarioInstance mario = MarioInstance.create(player.position());
        if (mario == null) {
            Retro64Plus.LOGGER.warn("libsm64 refused to create Mario at {}", player.position());
            return;
        }
        LocalMario local = new LocalMario(mario, player, mc.options.getCameraType());
        local.collisionCenter = at;
        mario.setAngle(Math.toRadians(-player.getYRot()));
        active = local;
        MarioHooks.setLocalMario(player);
        player.getAbilities().flying = false;
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); // D25
    }

    /** Back to the normal player: frees the native character and restores the camera (D25). */
    static void turnOff() {
        LocalMario local = active;
        if (local == null) return;
        local.release();
        Minecraft.getInstance().options.setCameraType(local.previousCamera);
    }

    /** Frees the native character and clears the hooks. */
    private void release() {
        if (heldBobomb >= 0 && Minecraft.getInstance().getConnection() != null) {
            Services.NETWORK.sendToServer(new Payloads.Hold(heldBobomb, Payloads.Hold.DROP, mario.state.faceAngle));
        }
        heldBobomb = -1;
        active = null;
        if (holdingDeathScreen && Minecraft.getInstance().level != null) openDeathScreen(heldDeathScreen);
        MarioHooks.setLocalMario(null);
        mario.delete();
        // Tell the server (it relays to the players nearby); only if still connected.
        if (Minecraft.getInstance().getConnection() != null) {
            Services.NETWORK.sendToServer(new Payloads.MarioState(Optional.empty()));
        }
        player.refreshDimensions();
    }

    /** End of every client tick. */
    static void tick() {
        LocalMario local = active;
        if (local == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            turnOff(); // left the world: the camera goes back too (D25)
            return;
        }
        if (mc.player.isSpectator()) {
            turnOff();
            return;
        }
        if (mc.isPaused()) return;
        local.update(mc);
    }

    private void update(Minecraft mc) {
        // Respawning or changing dimension makes a new player entity (and maybe a new level): follow it.
        if (mc.player != player || mc.level != level) {
            player = mc.player;
            level = mc.level;
            MarioHooks.setLocalMario(player);
            collisionCenter = null;
            placedAt = player.position();
            teleportTo(player.position());
            // Respawned (or changed dimension): back on his feet, whatever SM64's death left him doing. A Bob-omb he
            // carried stayed behind on the server, and a shell ride is over.
            if (heldBobomb >= 0) {
                mario.drop();
                heldBobomb = -1;
            }
            ridingShell = false;
            rideShellId = -1;
            wasRiding = false;
            wasSleeping = false;
            mario.setAction(Actions.ACT_IDLE);
            heldDeathScreen = null;
            holdingDeathScreen = false;
            deadTicks = 0;
            pendingHit = null;
            lastHealth = -1;
        } else if (player.position().distanceTo(placedAt) > TELEPORT_DISTANCE && !player.isPassenger()
                && !player.isSleeping()) {
            teleportTo(player.position());
        }

        player.getAbilities().flying = false;
        ticks++;
        long target = ticks * 3 / 2;
        while (mario.frameCount() < target) runFrame();

        if (player.isPassenger() || player.isSleeping()) {
            placedAt = player.position(); // the vehicle or the bed places the player
        } else {
            Vec3 pos = mario.position();
            player.setPos(pos.x, pos.y, pos.z);
            player.setDeltaMovement(Vec3.ZERO);
            // The server cancels vanilla fall damage for Mario (mixin/PlayerMixin); onGround is just the truth now.
            player.setOnGround(!Actions.has(mario.state.action, Actions.FLAG_AIR));
            player.resetFallDistance();
            placedAt = player.position();
        }
        if (player.getBbWidth() != MarioHooks.MARIO_DIMENSIONS.width()) player.refreshDimensions();
        updateHits();
        updateDeath(mc);
        if (selfFallDamage > 0) Services.NETWORK.sendToServer(new Payloads.SelfDamage(Payloads.SelfDamage.FALL, selfFallDamage));
        if (selfLavaDamage > 0) Services.NETWORK.sendToServer(new Payloads.SelfDamage(Payloads.SelfDamage.LAVA, selfLavaDamage));
        selfFallDamage = selfLavaDamage = 0;
        MarioUpdate state = new MarioUpdate(mario.pose(character().id()), List.copyOf(tickSounds));
        tickSounds.clear();
        Services.NETWORK.sendToServer(new Payloads.MarioState(Optional.of(state)));
        for (Payloads.Attack attack : attacks.drain()) Services.NETWORK.sendToServer(attack);
        updateHolding();
        for (Payloads.Hold hold : holds) Services.NETWORK.sendToServer(hold);
        holds.clear();
        // After the pose (the server checks its action) and the attacks (the mob under him takes the full hit first).
        if (groundPoundAt != null) {
            Services.NETWORK.sendToServer(new Payloads.GroundPound(groundPoundAt.x, groundPoundAt.y, groundPoundAt.z));
            groundPounds++;
            groundPoundAt = null;
        }
    }

    /**
     * A star collected (D63): SM64's star dance, the one that doesn't leave the level (its jingle and "Here we go!"
     * come from the native action). Under water the swimming one; in the air he falls first and dances on landing.
     */
    void starDance() {
        if (!player.isAlive()) return;
        int action = mario.state.action;
        if ((action & Actions.GROUP_MASK) == Actions.GROUP_SUBMERGED) {
            mario.setAction(Actions.ACT_STAR_DANCE_WATER, 1);
        } else if (Actions.has(action, Actions.FLAG_AIR)) {
            mario.setAction(Actions.ACT_FALL_AFTER_STAR_GRAB, 1);
        } else {
            mario.setAction(Actions.ACT_STAR_DANCE_NO_EXIT, 1);
        }
        starDances++;
    }

    /** For dev diagnostics. */
    int starDances;

    /** A punch reached {@code target}: picks it up if it's a free Bob-omb and Mario can (SM64's pick-up). */
    private boolean tryGrab(Entity target) {
        if (heldBobomb >= 0 || !(target instanceof BobombEntity bobomb) || !bobomb.grabbable()) return false;
        if (!mario.grab()) return false;
        heldBobomb = target.getId();
        holds.add(new Payloads.Hold(heldBobomb, Payloads.Hold.GRAB, mario.state.faceAngle));
        grabs++;
        return true;
    }

    /** Mario let go of the Bob-omb on his own: thrown (a throwing action, B) or dropped (hurt, fell, swam). */
    private void updateHolding() {
        if (heldBobomb < 0 || mario.holding()) return;
        boolean thrown = (mario.state.action & Actions.FLAG_THROWING) != 0;
        holds.add(new Payloads.Hold(heldBobomb, thrown ? Payloads.Hold.THROW : Payloads.Hold.DROP,
                mario.state.faceAngle));
        if (thrown) throwsMade++;
        heldBobomb = -1;
    }

    /** Pick-ups and throws so far (dev diagnostics). */
    int grabs, throwsMade;

    /** The server says let go of that Bob-omb (refused, or it's going off), or get off that shell (refused). */
    public static void onRelease(int entityId) {
        LocalMario local = active;
        if (local == null) return;
        if (local.ridingShell && local.rideShellId == entityId) {
            local.mario.setAction(Actions.has(local.mario.state.action, Actions.FLAG_AIR) ? Actions.ACT_FREEFALL
                    : Actions.ACT_IDLE);
            return;
        }
        if (local.heldBobomb != entityId) return;
        local.heldBobomb = -1;
        local.mario.drop();
    }

    /** Where the local Mario holds Bob-omb {@code entityId} this frame, or null if he doesn't hold it. */
    static @Nullable Vec3 heldBobombPosition(int entityId, float partialTick) {
        LocalMario local = active;
        if (local == null || local.heldBobomb != entityId) return null;
        return BobombEntity.heldPosition(local.player.getPosition(partialTick), local.mario.state.faceAngle);
    }

    /** The Bob-omb held (dev diagnostics), or -1. */
    int heldBobomb() {
        return heldBobomb;
    }

    /** The server hurt the local player (mixin/client/LivingEntityMixin). */
    public static void onDamaged(DamageSource source) {
        LocalMario local = active;
        if (local == null) return;
        local.pendingHit = source;
        local.pendingHitAge = 0;
    }

    /**
     * A hit becomes SM64's knockback, sized by the health it took (soft, normal or hard), away from what dealt it; one
     * without a position (fire, poison, a cactus) is just his voice. SM64's own damage already played its part, and
     * drowning and starving have Minecraft's own cues.
     */
    private void updateHits() {
        // Absorption counts: a hit it soaks up is still a hit of that size.
        float health = player.getHealth() + player.getAbsorptionAmount();
        if (pendingHit != null) {
            float taken = lastHealth - health;
            if (taken > 0 || ++pendingHitAge > HIT_WAIT_TICKS) {
                DamageSource source = pendingHit;
                pendingHit = null;
                boolean quiet = source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.LAVA)
                        || source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypes.STARVE);
                if (!quiet && player.isAlive()) {
                    mario.knockback(MarioHealth.toSegments(Math.max(taken, 0), player.getMaxHealth()),
                            source.getSourcePosition());
                    knockbacks++;
                }
            }
        }
        lastHealth = health;
    }

    /** Dead: SM64's death plays (its health says so), then the held death screen opens (D37). */
    private void updateDeath(Minecraft mc) {
        if (!player.isDeadOrDying()) {
            deadTicks = 0;
            return;
        }
        deadTicks++;
        // SM64 checks for death only on the ground and in water: flying, hanging or on a pole, let go so he falls.
        int action = mario.state.action;
        if (action == Actions.ACT_FLYING || (action & Actions.GROUP_MASK) == Actions.GROUP_AUTOMATIC) {
            mario.setAction(Actions.ACT_FREEFALL);
        }
        if (holdingDeathScreen && deadTicks >= DEATH_SCREEN_DELAY) {
            Screen screen = heldDeathScreen;
            heldDeathScreen = null;
            holdingDeathScreen = false;
            openDeathScreen(screen);
        }
    }

    /**
     * mixin/client/MinecraftMixin: true if Mario keeps {@code screen} back for now: a death screen, or null while the
     * player is dead (Minecraft then builds a death screen, or respawns at once with immediate respawn).
     */
    public static boolean holdDeathScreen(@Nullable Screen screen) {
        LocalMario local = active;
        if (local == null || openingDeathScreen) return false;
        if (screen == null) {
            if (!local.player.isDeadOrDying() || local.deadTicks >= DEATH_SCREEN_DELAY) return false;
            // Closing a screen opened meanwhile (pause, chat): let it close, though the death screen then comes early.
            // Held, it could never close: the pause menu stops the ticks that count the delay.
            if (Minecraft.getInstance().screen != null) {
                local.heldDeathScreen = null;
                local.holdingDeathScreen = false;
                local.deadTicks = DEATH_SCREEN_DELAY; // done holding
                return false;
            }
        } else {
            local.heldDeathScreen = screen;
        }
        local.holdingDeathScreen = true;
        return true;
    }

    /** Opens the held death screen, or (none came yet) lets Minecraft build one: {@code setScreen(null)} does. */
    private static void openDeathScreen(@Nullable Screen screen) {
        openingDeathScreen = true;
        try {
            Minecraft.getInstance().setScreen(screen);
        } finally {
            openingDeathScreen = false;
        }
    }

    /** Who this player plays (D27): their choice, if the server allows it (D34). */
    MarioCharacter character() {
        return MarioRules.character();
    }

    /** Hits so far (dev diagnostics). */
    int hits() {
        return attacks.hits;
    }

    /** Times Mario was moved to the player because something else moved them (dev diagnostics). */
    int teleports;

    private void teleportTo(Vec3 pos) {
        teleports++;
        refreshCollision(BlockPos.containing(pos), true);
        mario.teleport(pos);
        ticks = 0;
    }

    private void runFrame() {
        refreshCollision(BlockPos.containing(mario.position()), false);
        updateInputs();
        updateRiding();
        updateSleeping();
        mario.setWaterLevel(waterLevel());
        mario.setGasLevel(player.hasEffect(MobEffects.POISON) ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY);
        applyEffects();
        mario.setCaps(ClientCaps.flags(), ClientCaps.hiddenFlags(framesIntoTick()));
        int hurt = mario.setHealth(MarioHealth.sm64Health(player.getHealth(), player.getMaxHealth(),
                player.isDeadOrDying()));
        if (hurt > 0 && player.isAlive()) {
            if (mario.state.action == Actions.ACT_LAVA_BOOST) selfLavaDamage += hurt;
            else selfFallDamage += hurt;
        }
        int before = mario.state.action;
        mario.frame();
        if (mario.state.action == Actions.ACT_GROUND_POUND_LAND && before != Actions.ACT_GROUND_POUND_LAND) {
            groundPoundAt = mario.position();
        }
        int sounds = mario.lastFrameSounds(frameSounds);
        for (int i = 0; i < sounds && tickSounds.size() < MarioUpdate.MAX_SOUNDS; i++) tickSounds.add(frameSounds[i]);
        attacks.afterFrame(mario, player, level);
        updateShell();
    }

    /**
     * The Koopa shell (D88): SM64's interact_koopa_shell gets Mario on a shell he walks into or lands on; the server
     * then takes the shell entity away. The ride ends on SM64's terms (a wall, Z, a hit), and the shell is gone (D89).
     */
    private void updateShell() {
        if (ridingShell) {
            if (!mario.riding()) {
                ridingShell = false;
                rideShellId = -1;
            }
            return;
        }
        int action = mario.state.action;
        boolean walking = action == Actions.ACT_WALKING || action == Actions.ACT_HOLD_WALKING;
        boolean falling = Actions.has(action, Actions.FLAG_AIR) && mario.state.velocity[1] < 0;
        if (!walking && !falling) return;
        Vec3 pos = mario.position();
        // SM64's hitboxes: Mario's 37 units wide and 160 high, the shell's 50 and 50.
        double reach = (37 + 50) / (double) Units.PER_BLOCK, shellHeight = 50.0 / Units.PER_BLOCK;
        double marioHeight = 160.0 / Units.PER_BLOCK;
        for (KoopaShellEntity shell : level.getEntitiesOfClass(KoopaShellEntity.class,
                new AABB(pos, pos).inflate(reach + 0.5, marioHeight + 0.5, reach + 0.5), Entity::isAlive)) {
            double dx = shell.getX() - pos.x, dz = shell.getZ() - pos.z;
            if (dx * dx + dz * dz > reach * reach) continue;
            if (pos.y > shell.getY() + shellHeight || pos.y + marioHeight < shell.getY()) continue;
            if (!walking && pos.y <= shell.getY()) continue; // falling: only onto it, from above
            if (!mario.rideShell()) return;
            ridingShell = true;
            rideShellId = shell.getId();
            shellRides++;
            holds.add(new Payloads.Hold(shell.getId(), Payloads.Hold.RIDE, mario.state.faceAngle));
            return;
        }
    }

    /** Shell rides started (dev diagnostics). */
    int shellRides;

    /** Whether the local Mario rides a Koopa shell (its music, D90). */
    static boolean ridingShell() {
        LocalMario local = active;
        return local != null && local.ridingShell;
    }

    /** SM64 frames already run in the current tick (0 or 1), for the caps' flicker. */
    private int framesIntoTick() {
        return (int) Math.max(0, mario.frameCount() - (ticks - 1) * 3 / 2);
    }

    /** Rebuilds and uploads the collision box when Mario reaches another block, or every few frames. */
    private void refreshCollision(BlockPos center, boolean force) {
        framesSinceCollision++;
        if (!force && center.equals(collisionCenter) && framesSinceCollision < REBUILD_FRAMES) return;
        collision.build(level, center, RADIUS, BELOW, ABOVE, level.getMinBuildHeight());
        LibSM64.lib().sm64_static_surfaces_load(collision.toSurfaces(), collision.triangleCount());
        collisionCenter = center;
        framesSinceCollision = 0;
    }

    /**
     * Keyboard to the SM64 controller (D24): movement keys are the stick, relative to where the camera looks; Jump is
     * A, Sneak is Z, the action key is B. A gamepad's analog stick wins while it's tilted, and its buttons add to the
     * keys' (D31). libsm64 negates stickX, and its camera vector points from Mario towards the camera
     * (LibSM64RealRomTest checks this).
     */
    private void updateInputs() {
        SM64MarioInputs in = mario.inputs;
        var input = player.input;
        ControllerInput.poll();
        //? if >=1.21.2 {
        /*var keys = input.keyPresses;
        boolean up = keys.forward(), down = keys.backward(), keyLeft = keys.left(), keyRight = keys.right();
        boolean jumping = keys.jump(), sneaking = keys.shift();
        *///?} else {
        boolean up = input.up, down = input.down, keyLeft = input.left, keyRight = input.right;
        boolean jumping = input.jumping, sneaking = input.shiftKeyDown;
        //?}
        float forward = (up ? 1 : 0) - (down ? 1 : 0);
        float right = (keyRight ? 1 : 0) - (keyLeft ? 1 : 0);
        if (ControllerInput.stickForward != 0 || ControllerInput.stickRight != 0) {
            forward = ControllerInput.stickForward;
            right = ControllerInput.stickRight;
        }
        if (DevAutoTest.active) {
            forward = DevAutoTest.forward;
            right = DevAutoTest.right;
        }
        float length = (float) Math.sqrt(forward * forward + right * right);
        if (length > 1) {
            forward /= length;
            right /= length;
        }
        float speed = stickScale();
        in.stickX = -right * speed;
        in.stickY = forward * speed;
        double yaw = Math.toRadians(player.getYRot());
        // The direction the player faces is (-sin yaw, cos yaw); the camera sits behind, the other way.
        in.camLookX = (float) Math.sin(yaw);
        in.camLookZ = (float) -Math.cos(yaw);
        in.buttonA = (byte) (jumping || DevAutoTest.jump || ControllerInput.a ? 1 : 0);
        in.buttonB = (byte) (Retro64PlusClient.ACTION_KEY.isDown() || DevAutoTest.punch || ControllerInput.b ? 1 : 0);
        in.buttonZ = (byte) (sneaking || DevAutoTest.crouch || ControllerInput.z ? 1 : 0);
        Vec3 pos = mario.position();
        in.cameraPosition[0] = (float) (pos.x * Units.PER_BLOCK);
        in.cameraPosition[1] = (float) (pos.y * Units.PER_BLOCK);
        in.cameraPosition[2] = (float) (pos.z * Units.PER_BLOCK);
    }

    /** Speed and Slowness change how far the stick reaches, as upstream (10% faster, 15% slower per level). */
    private float stickScale() {
        float scale = 1;
        MobEffectInstance speed = player.getEffect(MobEffects.MOVEMENT_SPEED);
        if (speed != null) scale += 0.1f * (speed.getAmplifier() + 1);
        MobEffectInstance slow = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (slow != null) scale -= 0.15f * (slow.getAmplifier() + 1);
        return Math.max(0, scale);
    }

    /** Levitation lifts Mario and Slow Falling caps his fall speed, as upstream. */
    private void applyEffects() {
        MobEffectInstance levitation = player.getEffect(MobEffects.LEVITATION);
        if (levitation != null) {
            Vec3 v = mario.velocity();
            mario.setVelocity(new Vec3(v.x, Math.min(v.y + 0.04 * (levitation.getAmplifier() + 1), 0.2), v.z));
            if (!Actions.has(mario.state.action, Actions.FLAG_AIR)) mario.setAction(Actions.ACT_FREEFALL);
        } else if (player.hasEffect(MobEffects.SLOW_FALLING)) {
            Vec3 v = mario.velocity();
            if (v.y < -0.2) mario.setVelocity(new Vec3(v.x, -0.2, v.z));
        }
    }

    /** Riding a mob, boat or minecart: Mario sits where the player is, held in the "grabbed" pose. */
    private void updateRiding() {
        boolean riding = player.isPassenger();
        if (riding) {
            mario.teleportKeepingHistory(player.position());
            mario.setAngle(Math.toRadians(-player.getYRot()));
            if (mario.state.action != Actions.ACT_GRABBED) mario.setAction(Actions.ACT_GRABBED);
        } else if (wasRiding && mario.state.action == Actions.ACT_GRABBED) {
            mario.setAction(Actions.ACT_IDLE);
        }
        wasRiding = riding;
    }

    /** In bed: Mario lies down on it and sleeps. */
    private void updateSleeping() {
        boolean sleeping = player.isSleeping();
        if (sleeping && !wasSleeping) {
            player.getSleepingPos().ifPresent(head -> {
                Direction facing = player.getBedOrientation();
                Vec3 middle = Vec3.atBottomCenterOf(head).add(Vec3.atBottomCenterOf(head.relative(
                        facing != null ? facing.getOpposite() : Direction.NORTH))).scale(0.5).add(0, 0.5625, 0);
                mario.teleportKeepingHistory(middle);
                if (facing != null) mario.setAngle(Math.atan2(facing.getStepX(), facing.getStepZ()));
                mario.setAction(Actions.ACT_SLEEPING);
                mario.setActionState(1);
            });
        } else if (!sleeping && wasSleeping) {
            mario.setAction(Actions.ACT_WAKING_UP);
        }
        wasSleeping = sleeping;
    }

    /**
     * The water surface Mario should swim at: the top of the water column at or below him (searching a few blocks
     * down, so he knows the water is there before he lands in it), else none.
     */
    private double waterLevel() {
        BlockPos.MutableBlockPos pos = BlockPos.containing(mario.position()).mutable();
        pos.move(0, 1, 0);
        for (int down = 0; down < 8; down++, pos.move(0, -1, 0)) {
            if (!level.getFluidState(pos).is(FluidTags.WATER)) continue;
            // Found water: climb to the top of the column.
            while (level.getFluidState(pos.above()).is(FluidTags.WATER)) pos.move(0, 1, 0);
            FluidState top = level.getFluidState(pos);
            return pos.getY() + top.getHeight(level, pos);
        }
        return Double.NEGATIVE_INFINITY;
    }

    // --- Rendering access -----------------------------------------------------------------------------------------

    MarioInstance mario() {
        return mario;
    }

    /** The render time in frame indices, for {@link MarioInstance#bracket} (see the class comment). */
    double renderFrame(float partialTick) {
        return 1.5 * (ticks - 1 + partialTick) - 1;
    }
}
