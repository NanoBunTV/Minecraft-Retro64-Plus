package com.nanobuntv.retro64plus.sm64.lib;

import com.sun.jna.Structure;

/** {@code struct SM64MarioState}: what a tick reports back, in SM64 units. */
@Structure.FieldOrder({"position", "velocity", "faceAngle", "health", "flags", "action", "currentModel"})
public class SM64MarioState extends Structure {
    public float[] position = new float[3];
    public float[] velocity = new float[3];
    public float faceAngle;
    /** High byte: power meter wedges (0–8). */
    public short health;
    public int flags;
    public int action;
    public int currentModel;
}
