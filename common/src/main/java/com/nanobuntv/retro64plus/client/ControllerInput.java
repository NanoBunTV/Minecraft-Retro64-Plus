package com.nanobuntv.retro64plus.client;

import com.nanobuntv.retro64plus.util.Compat;
import com.nanobuntv.retro64plus.config.ClientSettings;
import com.nanobuntv.retro64plus.config.Retro64PlusConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
//? if >=26.3 {
/*import java.nio.IntBuffer;
import org.lwjgl.sdl.SDLGamepad;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDLStdinc;
*///?} else {
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;
//?}

/**
 * A gamepad for Mario (D31), read straight from GLFW (in Minecraft already), only while you're Mario and no screen is
 * open. The left stick is SM64's analog stick (so Mario walks, jogs or runs by how far it's pushed), A is A (jump),
 * B is B (punch), LT or LB is Z, the right stick turns the camera. A few Minecraft actions too, since Controllable and
 * Controlify are held back meanwhile (mixin/compat): RT attacks or mines, RB uses, the d-pad's left and right pick the
 * hotbar slot. Start and Y do nothing (D129). The first connected gamepad (with an SDL mapping) is used. Render thread. Read
 * from GLFW up to 26.2 and from SDL on 26.3 (where Minecraft moved to SDL), both into GLFW's standard layout.
 */
public final class ControllerInput {
    /** Full right-stick tilt turns the camera this many degrees a second (times the look speed setting). */
    private static final float LOOK_DEGREES_PER_SECOND = 200;
    private static final float TRIGGER = 0.5f;

    /** GLFW's standard gamepad layout: buttons and axes (sticks -1..1, triggers -1 resting to 1). */
    private static final int BUTTON_A = 0, BUTTON_B = 1, BUTTON_LB = 4, BUTTON_RB = 5, BUTTON_DPAD_RIGHT = 12,
            BUTTON_DPAD_LEFT = 14;
    private static final int AXIS_LEFT_X = 0, AXIS_LEFT_Y = 1, AXIS_RIGHT_X = 2, AXIS_RIGHT_Y = 3, AXIS_LT = 4, AXIS_RT = 5;
    private static final boolean[] BUTTONS = new boolean[15];
    private static final float[] AXES = new float[6];
    private static boolean connected;
    /** Left stick after the dead zone: right and forward, -1..1. */
    static float stickRight, stickForward;
    static boolean a, b, z;
    private static float lookX, lookY;
    private static boolean attack, use, left, right;
    private static long lastLookNanos;

    private ControllerInput() {}

    /** Reads the gamepad; once per client tick and before every SM64 frame. */
    static void poll() {
        ClientSettings settings = Retro64PlusConfig.client();
        connected = false;
        if (!settings.controller || !LocalMario.isActive() || Minecraft.getInstance().screen != null) {
            clear();
            return;
        }
        if (!readGamepad()) {
            clear();
            return;
        }
        connected = true;
        float dead = (float) settings.controllerDeadzone;
        float[] left = deadZone(AXES[AXIS_LEFT_X], -AXES[AXIS_LEFT_Y], dead);
        stickRight = left[0];
        stickForward = left[1];
        float[] look = deadZone(AXES[AXIS_RIGHT_X], AXES[AXIS_RIGHT_Y], dead);
        lookX = look[0];
        lookY = look[1];
        a = BUTTONS[BUTTON_A];
        b = BUTTONS[BUTTON_B];
        z = BUTTONS[BUTTON_LB] || AXES[AXIS_LT] > TRIGGER;
    }

    /** The Minecraft actions, on presses and releases; once per client tick, after {@link #poll}. */
    static void tickActions(Minecraft mc) {
        LocalPlayer player = mc.player;
        boolean nowAttack = connected && AXES[AXIS_RT] > TRIGGER;
        boolean nowUse = connected && BUTTONS[BUTTON_RB];
        attack = hold(mc.options.keyAttack, attack, nowAttack);
        use = hold(mc.options.keyUse, use, nowUse);
        boolean nowLeft = connected && BUTTONS[BUTTON_DPAD_LEFT];
        boolean nowRight = connected && BUTTONS[BUTTON_DPAD_RIGHT];
        if (player != null) {
            var inventory = player.getInventory();
            if (nowLeft && !left) Compat.setSelectedSlot(inventory, (Compat.selectedSlot(inventory) + 8) % 9);
            if (nowRight && !right) Compat.setSelectedSlot(inventory, (Compat.selectedSlot(inventory) + 1) % 9);
        }
        left = nowLeft;
        right = nowRight;
    }

    /** Every rendered frame (mixin/client/MouseHandlerMixin): the right stick turns the camera smoothly. */
    public static void turnCamera() {
        long now = System.nanoTime();
        double seconds = lastLookNanos == 0 ? 0 : Math.min((now - lastLookNanos) / 1e9, 0.1);
        lastLookNanos = now;
        LocalPlayer player = Minecraft.getInstance().player;
        if (!connected || player == null || (lookX == 0 && lookY == 0)) return;
        double degrees = LOOK_DEGREES_PER_SECOND * Retro64PlusConfig.client().controllerLookSpeed * seconds;
        // Entity.turn scales by 0.15 (mouse units).
        player.turn(lookX * degrees / 0.15, lookY * degrees / 0.15);
    }

    /** Presses or releases a Minecraft key while the button changes; leaves it alone otherwise (the mouse has it). */
    private static boolean hold(KeyMapping key, boolean was, boolean now) {
        if (now && !was) {
            click(key);
            key.setDown(true);
        } else if (!now && was) {
            key.setDown(false);
        }
        return now;
    }

    /** A press of {@code key} as if its own button were clicked (vanilla has no getter for the bound key). */
    private static void click(KeyMapping key) {
        KeyMapping.click(InputConstants.getKey(key.saveString()));
    }

    private static void clear() {
        stickRight = stickForward = lookX = lookY = 0;
        a = b = z = false;
        Minecraft mc = Minecraft.getInstance();
        if (attack) mc.options.keyAttack.setDown(false);
        if (use) mc.options.keyUse.setDown(false);
        attack = use = left = right = false;
    }

    //? if >=26.3 {
    /*/^* SDL's buttons in GLFW's order. ^/
    private static final int[] SDL_BUTTONS = {
            SDLGamepad.SDL_GAMEPAD_BUTTON_SOUTH, SDLGamepad.SDL_GAMEPAD_BUTTON_EAST, SDLGamepad.SDL_GAMEPAD_BUTTON_WEST,
            SDLGamepad.SDL_GAMEPAD_BUTTON_NORTH, SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_SHOULDER,
            SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER, SDLGamepad.SDL_GAMEPAD_BUTTON_BACK,
            SDLGamepad.SDL_GAMEPAD_BUTTON_START, SDLGamepad.SDL_GAMEPAD_BUTTON_GUIDE,
            SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_STICK, SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_STICK,
            SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_UP, SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_RIGHT,
            SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_DOWN, SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_LEFT};
    private static long sdlPad;

    /^* The first SDL gamepad, kept open (Minecraft starts SDL without its gamepads). False with none. ^/
    private static boolean readGamepad() {
        if ((SDLInit.SDL_WasInit(SDLInit.SDL_INIT_GAMEPAD) & SDLInit.SDL_INIT_GAMEPAD) == 0
                && !SDLInit.SDL_InitSubSystem(SDLInit.SDL_INIT_GAMEPAD)) {
            return false;
        }
        if (sdlPad != 0 && !SDLGamepad.SDL_GamepadConnected(sdlPad)) {
            SDLGamepad.SDL_CloseGamepad(sdlPad);
            sdlPad = 0;
        }
        if (sdlPad == 0) {
            IntBuffer ids = SDLGamepad.SDL_GetGamepads();
            if (ids == null) return false;
            try {
                if (ids.remaining() > 0) sdlPad = SDLGamepad.SDL_OpenGamepad(ids.get(0));
            } finally {
                SDLStdinc.SDL_free(ids);
            }
        }
        if (sdlPad == 0) return false;
        for (int i = 0; i < SDL_BUTTONS.length; i++) BUTTONS[i] = SDLGamepad.SDL_GetGamepadButton(sdlPad, SDL_BUTTONS[i]);
        // SDL's axes are 16-bit, in GLFW's order; its triggers rest at 0.
        for (int i = 0; i < 4; i++) AXES[i] = Math.max(-1F, SDLGamepad.SDL_GetGamepadAxis(sdlPad, i) / 32767F);
        for (int i = 4; i < 6; i++) AXES[i] = SDLGamepad.SDL_GetGamepadAxis(sdlPad, i) / 32767F * 2 - 1;
        return true;
    }
    *///?} else {
    private static final GLFWGamepadState STATE = GLFWGamepadState.create();

    /** The first connected gamepad (with an SDL mapping) into the arrays; false with none. */
    private static boolean readGamepad() {
        int pad = -1;
        for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_LAST && pad < 0; jid++) {
            if (GLFW.glfwJoystickPresent(jid) && GLFW.glfwJoystickIsGamepad(jid)) pad = jid;
        }
        if (pad < 0 || !GLFW.glfwGetGamepadState(pad, STATE)) return false;
        for (int i = 0; i < BUTTONS.length; i++) BUTTONS[i] = STATE.buttons(i) == GLFW.GLFW_PRESS;
        for (int i = 0; i < AXES.length; i++) AXES[i] = STATE.axes(i);
        return true;
    }
    //?}

    /** A radial dead zone: below it nothing, beyond it rescaled to 0..1 so small tilts still walk slowly. */
    static float[] deadZone(float x, float y, float dead) {
        float length = (float) Math.sqrt(x * x + y * y);
        if (length <= dead) return new float[] {0, 0};
        float scaled = Math.min(1, (length - dead) / (1 - dead));
        return new float[] {x / length * scaled, y / length * scaled};
    }
}
