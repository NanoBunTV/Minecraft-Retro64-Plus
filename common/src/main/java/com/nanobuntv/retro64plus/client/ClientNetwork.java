package com.nanobuntv.retro64plus.client;

import com.nanobuntv.retro64plus.caps.StarBonus;
import com.nanobuntv.retro64plus.net.Payloads;
import com.nanobuntv.retro64plus.server.ServerSounds;
import com.nanobuntv.retro64plus.sm64.LibSM64;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Client-side payload handlers (net/Retro64PlusNetwork names this class only inside lambdas). Render thread. */
public final class ClientNetwork {
    private ClientNetwork() {}

    public static void onRemoteMario(Payloads.RemoteMario payload) {
        RemoteMarios.onUpdate(payload.entityId(), payload.update());
    }

    public static void onCaps(Payloads.CapState payload) {
        ClientCaps.set(payload.wing(), payload.metal(), payload.vanish());
    }

    public static void onRules(Payloads.MarioRules payload) {
        MarioRules.set(payload.forceMario(), payload.allowMario(), payload.allowedCharacters());
    }

    public static void onCoinCount(Payloads.CoinCount payload) {
        ClientCoins.set(payload.coins());
    }

    public static void onShopOffers(Payloads.ShopOffers payload) {
        Minecraft.getInstance().setScreen(new ShopScreen(payload.offers()));
    }

    public static void onStarCount(Payloads.StarCount payload) {
        ClientCoins.setStars(payload.stars());
        StarBonus.clientDuration = payload.capDuration();
        StarBonus.clientRecharge = payload.capRecharge();
    }

    /**
     * A star collected (D63): Mario does SM64's star dance (which plays the jingle); not as Mario, just the jingle
     * (Minecraft's challenge sound without the ROM).
     */
    public static void onRelease(Payloads.Release payload) {
        LocalMario.onRelease(payload.entityId());
    }

    public static void onStarCollected(Payloads.StarCollected payload) {
        ClientCoins.setStars(payload.total());
        Retro64PlusClient.actionBar(Component.translatable("message.retro64plus.star", payload.total())
                .withStyle(ChatFormatting.GOLD));
        LocalMario local = LocalMario.active();
        if (local != null) {
            local.starDance();
            return;
        }
        if (LibSM64.status() == LibSM64.Status.READY) {
            MarioAudio.playStarJingle();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1);
    }

    /** SM64's sound for one of the mod's events (D108); Minecraft's nearest stand-in without the ROM. */
    public static void onSm64Sound(Payloads.Sm64Sound payload) {
        Vec3 at = new Vec3(payload.x(), payload.y(), payload.z());
        boolean played = MarioAudio.playAt(at, payload.sound());
        DevAutoTest.onSound(payload.sound(), played);
        if (played) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        switch (payload.sound()) {
            case ServerSounds.COIN -> mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.PLAYERS, 0.3f, 1.6f, false);
            case ServerSounds.CAP -> mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                    SoundSource.PLAYERS, 1, 1, false);
            case ServerSounds.ONE_UP -> mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.PLAYER_LEVELUP,
                    SoundSource.PLAYERS, 0.6f, 1.6f, false);
            default -> { }
        }
    }
}
