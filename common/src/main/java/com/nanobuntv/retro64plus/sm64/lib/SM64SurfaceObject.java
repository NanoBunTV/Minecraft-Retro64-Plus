package com.nanobuntv.retro64plus.sm64.lib;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;

/** {@code struct SM64SurfaceObject}: a moving collision object (boats, pistons, ...; later milestones). */
@Structure.FieldOrder({"transform", "surfaceCount", "surfaces"})
public class SM64SurfaceObject extends Structure {
    /** Embedded by value in the C struct. */
    public SM64ObjectTransform.ByValue transform = new SM64ObjectTransform.ByValue();
    /** {@code uint32_t}. */
    public int surfaceCount;
    /** {@code struct SM64Surface *}: the first element of a contiguous {@link SM64Surface#toArray} block. */
    public Pointer surfaces;
}
