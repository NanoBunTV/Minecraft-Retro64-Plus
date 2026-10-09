package com.nanobuntv.retro64plus.config;

/**
 * SM64 music while you're Mario (D32): off by default. Each track is one of the ROM's sequences (the decomp's
 * {@code seq_ids.h}), played by libsm64's own sequence player through the same audio stream as Mario's voice.
 */
public enum MarioMusic {
    OFF(-1),
    BOB_OMB_BATTLEFIELD(0x03),
    INSIDE_THE_CASTLE_WALLS(0x04),
    DIRE_DIRE_DOCKS(0x05),
    LETHAL_LAVA_LAND(0x06),
    SNOW_MOUNTAIN(0x08),
    SLIDER(0x09),
    HAUNTED_HOUSE(0x0A),
    HAZY_MAZE_CAVE(0x0C),
    KOOPA_ROAD(0x11);

    /** The sequence id, or -1 for none. */
    public final int sequence;

    MarioMusic(int sequence) {
        this.sequence = sequence;
    }
}
