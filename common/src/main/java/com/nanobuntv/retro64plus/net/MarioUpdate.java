package com.nanobuntv.retro64plus.net;

import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * One tick of a player's Mario as sent over the network: his pose, and the SM64 sounds his own code played during the
 * tick's frames (D32), which the players around replay from where he is. At most {@link #MAX_SOUNDS} sounds.
 */
public record MarioUpdate(MarioPose pose, List<Integer> sounds) {
    public static final int MAX_SOUNDS = 32;

    public static final StreamCodec<FriendlyByteBuf, MarioUpdate> CODEC = StreamCodec.composite(
            MarioPose.CODEC, MarioUpdate::pose,
            ByteBufCodecs.INT.apply(ByteBufCodecs.list(MAX_SOUNDS)), MarioUpdate::sounds,
            MarioUpdate::new);
}
