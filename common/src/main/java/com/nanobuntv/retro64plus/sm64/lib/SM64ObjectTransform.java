package com.nanobuntv.retro64plus.sm64.lib;

import com.sun.jna.Structure;

/** {@code struct SM64ObjectTransform}: a moving collision object's position and rotation. */
@Structure.FieldOrder({"position", "eulerRotation"})
public class SM64ObjectTransform extends Structure {
    public float[] position = new float[3];
    public float[] eulerRotation = new float[3];

    public static class ByValue extends SM64ObjectTransform implements Structure.ByValue {}
}
