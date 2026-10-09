package com.nanobuntv.retro64plus.net;

import com.nanobuntv.retro64plus.sm64.lib.SM64MarioPose;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * One frame of how a player's Mario looks (libsm64's {@code SM64MarioPose}) plus his model, as sent over the network
 * (M1 checkpoint 3). Plain Java so the server, which never loads native code (D13), can relay it. Receiving clients
 * hand it to {@code sm64_mChar_render_pose}, which clamps every value, so a bad pose can't crash them.
 */
public record MarioPose(float offsetX, float offsetY, float offsetZ, int action, short angleX, short angleY,
        short angleZ, short animId, short animYTrans, short animFrame, short modelState, short torsoX, short torsoY,
        short torsoZ, short headX, short headY, short headZ, byte capState, byte eyeState, byte handState,
        byte wingFlutter, byte grabPos, byte punchState, byte model) {

    public static final StreamCodec<FriendlyByteBuf, MarioPose> CODEC = StreamCodec.of(MarioPose::write, MarioPose::read);

    private static void write(FriendlyByteBuf buf, MarioPose p) {
        buf.writeFloat(p.offsetX).writeFloat(p.offsetY).writeFloat(p.offsetZ);
        buf.writeInt(p.action);
        buf.writeShort(p.angleX).writeShort(p.angleY).writeShort(p.angleZ);
        buf.writeShort(p.animId).writeShort(p.animYTrans).writeShort(p.animFrame).writeShort(p.modelState);
        buf.writeShort(p.torsoX).writeShort(p.torsoY).writeShort(p.torsoZ);
        buf.writeShort(p.headX).writeShort(p.headY).writeShort(p.headZ);
        buf.writeByte(p.capState).writeByte(p.eyeState).writeByte(p.handState).writeByte(p.wingFlutter);
        buf.writeByte(p.grabPos).writeByte(p.punchState).writeByte(p.model);
    }

    private static MarioPose read(FriendlyByteBuf buf) {
        return new MarioPose(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readInt(), buf.readShort(),
                buf.readShort(), buf.readShort(), buf.readShort(), buf.readShort(), buf.readShort(), buf.readShort(),
                buf.readShort(), buf.readShort(), buf.readShort(), buf.readShort(), buf.readShort(), buf.readShort(),
                buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte(),
                buf.readByte());
    }

    /** This pose with another model byte (the character, {@code MarioCharacter}). */
    public MarioPose withModel(byte newModel) {
        return new MarioPose(offsetX, offsetY, offsetZ, action, angleX, angleY, angleZ, animId, animYTrans, animFrame,
                modelState, torsoX, torsoY, torsoZ, headX, headY, headZ, capState, eyeState, handState, wingFlutter,
                grabPos, punchState, newModel);
    }

    /** From libsm64's struct (client side, after {@code sm64_mChar_get_pose}). */
    public static MarioPose of(SM64MarioPose s, int model) {
        return new MarioPose(s.offset[0], s.offset[1], s.offset[2], s.action, s.angle[0], s.angle[1], s.angle[2],
                s.animID, s.animYTrans, s.animFrame, s.modelState, s.torsoAngle[0], s.torsoAngle[1], s.torsoAngle[2],
                s.headAngle[0], s.headAngle[1], s.headAngle[2], s.capState, s.eyeState, s.handState, s.wingFlutter,
                s.grabPos, s.punchState, (byte) model);
    }

    /** Into libsm64's struct (client side, before {@code sm64_mChar_render_pose}). */
    public void into(SM64MarioPose s) {
        s.offset[0] = offsetX;
        s.offset[1] = offsetY;
        s.offset[2] = offsetZ;
        s.action = action;
        s.angle[0] = angleX;
        s.angle[1] = angleY;
        s.angle[2] = angleZ;
        s.animID = animId;
        s.animYTrans = animYTrans;
        s.animFrame = animFrame;
        s.modelState = modelState;
        s.torsoAngle[0] = torsoX;
        s.torsoAngle[1] = torsoY;
        s.torsoAngle[2] = torsoZ;
        s.headAngle[0] = headX;
        s.headAngle[1] = headY;
        s.headAngle[2] = headZ;
        s.capState = capState;
        s.eyeState = eyeState;
        s.handState = handState;
        s.wingFlutter = wingFlutter;
        s.grabPos = grabPos;
        s.punchState = punchState;
    }
}
