package com.nanobuntv.retro64plus.config;

import com.nanobuntv.retro64plus.MarioCharacter;

/** This player's own settings (D23): {@code config/retro64plus-client.json5}, on clients only. */
public final class ClientSettings {
    @SerialEntry(comment = "Path to your Super Mario 64 (USA) ROM, if you'd rather not copy it into the retro64plus\n"
            + "folder (D22). Leave empty to use the folder.")
    public String romPath = "";

    @SerialEntry(comment = "Who you play as (D27): \"mario\", \"luigi\" or \"skin\" (your own skin on Mario's skeleton).\n"
            + "The Character key (unbound by default, in Controls) cycles through them in game.")
    public String character = "mario";

    @SerialEntry(comment = "SM64 music while you're Mario (D32): OFF, or one of the game's tracks.\n"
            + "Follows the Players volume slider, as Mario's voice does.")
    public MarioMusic music = MarioMusic.OFF;

    @SerialEntry(comment = "Always show SM64's power meter while you're Mario (D45). Off: as in SM64, it slides in when\n"
            + "you're hurt or under water and away again at full power.")
    public boolean powerMeterAlwaysShown = false;

    @SerialEntry(comment = "Where the power meter sits: TOP (top middle, as in SM64) or HOTBAR (left of the hotbar).")
    public PowerMeterPosition powerMeterPosition = PowerMeterPosition.TOP;

    @SerialEntry(comment = "Where the coin counter sits (D57): TOP_LEFT (as in SM64), TOP_RIGHT or HIDDEN.")
    public CoinHudPosition coinCounterPosition = CoinHudPosition.TOP_LEFT;

    @SerialEntry(comment = "Show the coin counter when you're not Mario too.")
    public boolean coinCounterAlwaysShown = false;

    @SerialEntry(comment = "Play Mario with a gamepad (D31): left stick moves, A jumps, B punches, LT/LB is Z,\n"
            + "the right stick turns the camera. Only while you're Mario.")
    public boolean controller = true;

    @SerialEntry(comment = "How far a stick must move before it counts (0 to 0.9).")
    public double controllerDeadzone = 0.15;

    @SerialEntry(comment = "How fast the right stick turns the camera (0.1 to 4).")
    public double controllerLookSpeed = 1.0;

    public MarioCharacter characterChoice() {
        MarioCharacter c = MarioCharacter.byKey(character);
        return c != null ? c : MarioCharacter.MARIO;
    }

    /** A missing value in a hand-edited file reads as its default, not a crash. */
    public ClientSettings clean() {
        if (romPath == null) romPath = "";
        romPath = romPath.strip();
        if (character == null || MarioCharacter.byKey(character) == null) character = "mario";
        if (music == null) music = MarioMusic.OFF;
        if (powerMeterPosition == null) powerMeterPosition = PowerMeterPosition.TOP;
        if (coinCounterPosition == null) coinCounterPosition = CoinHudPosition.TOP_LEFT;
        if (!Double.isFinite(controllerDeadzone)) controllerDeadzone = 0.15;
        controllerDeadzone = Math.clamp(controllerDeadzone, 0, 0.9);
        if (!Double.isFinite(controllerLookSpeed)) controllerLookSpeed = 1.0;
        controllerLookSpeed = Math.clamp(controllerLookSpeed, 0.1, 4);
        return this;
    }
}
