package com.nanobuntv.retro64plus.sm64.lib;

import com.sun.jna.Structure;

/** {@code struct SM64MarioInputs}: one frame's controller state. */
@Structure.FieldOrder({"camLookX", "camLookZ", "cameraPosition", "stickX", "stickY", "buttonA", "buttonB", "buttonZ"})
public class SM64MarioInputs extends Structure {
    public float camLookX, camLookZ;
    /** Used for sound positioning. */
    public float[] cameraPosition = new float[3];
    public float stickX, stickY;
    public byte buttonA, buttonB, buttonZ;
}
