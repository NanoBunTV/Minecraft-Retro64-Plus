package com.nanobuntv.retro64plus.sm64.lib;

import com.sun.jna.Structure;

/** {@code struct SM64MarioPose} (Retro64 Plus, native/src/libsm64.h): what drawing Mario reads. 48 bytes, no padding. */
@Structure.FieldOrder({"offset", "action", "angle", "animID", "animYTrans", "animFrame", "modelState", "torsoAngle",
        "headAngle", "capState", "eyeState", "handState", "wingFlutter", "grabPos", "punchState"})
public class SM64MarioPose extends Structure {
    public float[] offset = new float[3];
    public int action;
    public short[] angle = new short[3];
    public short animID;
    public short animYTrans;
    public short animFrame;
    public short modelState;
    public short[] torsoAngle = new short[3];
    public short[] headAngle = new short[3];
    public byte capState;
    public byte eyeState;
    public byte handState;
    public byte wingFlutter;
    public byte grabPos;
    /** {@code uint8_t}. */
    public byte punchState;
}
