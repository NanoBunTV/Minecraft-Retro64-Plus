package com.nanobuntv.retro64plus.sm64.lib;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

/**
 * {@code struct SM64MarioGeometryBuffers}: where a tick writes the character's triangles. The four arrays are
 * allocated once, natively, for {@link Libsm64#GEO_MAX_TRIANGLES} triangles (3 vertices each).
 */
@Structure.FieldOrder({"position", "normal", "color", "uv", "numTrianglesUsed"})
public class SM64MarioGeometryBuffers extends Structure {
    public static final int VERTICES = Libsm64.GEO_MAX_TRIANGLES * 3;

    public Pointer position = new Memory(VERTICES * 3L * Float.BYTES);
    public Pointer normal = new Memory(VERTICES * 3L * Float.BYTES);
    public Pointer color = new Memory(VERTICES * 3L * Float.BYTES);
    public Pointer uv = new Memory(VERTICES * 2L * Float.BYTES);
    /** {@code uint16_t}. */
    public short numTrianglesUsed;

    public int triangles() {
        return Short.toUnsignedInt(numTrianglesUsed);
    }
}
