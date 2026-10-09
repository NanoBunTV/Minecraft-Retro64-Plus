package com.nanobuntv.retro64plus.sm64.lib;

import com.sun.jna.Structure;

/** {@code struct SM64Surface}: one collision triangle, in SM64 units. */
@Structure.FieldOrder({"type", "force", "terrain", "vertices"})
public class SM64Surface extends Structure {
    public short type;
    public short force;
    /** {@code uint16_t}. */
    public short terrain;
    /** {@code int32_t vertices[3][3]}, flattened: x0 y0 z0 x1 y1 z1 x2 y2 z2. */
    public int[] vertices = new int[9];
}
