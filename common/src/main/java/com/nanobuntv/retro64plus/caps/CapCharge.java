package com.nanobuntv.retro64plus.caps;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A spent cap's recharge (D35), kept on the item: the game time it was used and when it's ready again. Game time is
 * the same in every dimension, and nothing has to tick: the bar and the server's check both just compare.
 */
public record CapCharge(long usedAt, long readyAt) {
    public static final Codec<CapCharge> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("used_at").forGetter(CapCharge::usedAt),
            Codec.LONG.fieldOf("ready_at").forGetter(CapCharge::readyAt)).apply(i, CapCharge::new));
    public static final StreamCodec<ByteBuf, CapCharge> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, CapCharge::usedAt, ByteBufCodecs.VAR_LONG, CapCharge::readyAt, CapCharge::new);

    /** The client's game time, for drawing the bar (items have no level there); set every client tick. */
    public static volatile long clientTime;

    /** Ready: its time has come, or it was used "in the future" (the item came from another world's clock). */
    public boolean ready(long now) {
        return now >= readyAt || now < usedAt;
    }

    /** 0 just used … 1 ready. */
    public float progress(long now) {
        if (readyAt <= usedAt || now < usedAt) return 1;
        return Math.clamp((float) (now - usedAt) / (readyAt - usedAt), 0f, 1f);
    }
}
