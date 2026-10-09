package com.nanobuntv.retro64plus.net;

import com.nanobuntv.retro64plus.platform.NetworkHelper;
import com.nanobuntv.retro64plus.platform.Services;
import com.nanobuntv.retro64plus.server.ServerGroundPound;
import com.nanobuntv.retro64plus.server.ServerMarioAttacks;
import com.nanobuntv.retro64plus.server.ServerMarioHealth;
import com.nanobuntv.retro64plus.server.ServerMarios;

/**
 * Registers the payloads (common init, both sides). Client handlers go through {@code client.ClientNetwork}, named
 * only inside lambdas, so that class is loaded on a client the first time a payload arrives and never on a dedicated
 * server.
 */
public final class Retro64PlusNetwork {
    private Retro64PlusNetwork() {}

    public static void register() {
        NetworkHelper net = Services.NETWORK;
        net.registerServerbound(Payloads.MarioState.TYPE, Payloads.MarioState.CODEC, ServerMarios::onState);
        net.registerServerbound(Payloads.Attack.TYPE, Payloads.Attack.CODEC, ServerMarioAttacks::onAttack);
        net.registerServerbound(Payloads.SelfDamage.TYPE, Payloads.SelfDamage.CODEC, ServerMarioHealth::onSelfDamage);
        net.registerServerbound(Payloads.GroundPound.TYPE, Payloads.GroundPound.CODEC, ServerGroundPound::onLanding);
        net.registerServerbound(Payloads.ShopBuy.TYPE, Payloads.ShopBuy.CODEC,
                com.nanobuntv.retro64plus.server.ServerShop::onBuy);
        net.registerServerbound(Payloads.Hold.TYPE, Payloads.Hold.CODEC,
                (payload, player) -> {
                    if (!Float.isFinite(payload.yaw())) return; // a NaN would end up in the entity's position
                    if (payload.kind() == Payloads.Hold.RIDE) {
                        com.nanobuntv.retro64plus.enemies.KoopaShellEntity.onRide(payload, player);
                    } else {
                        com.nanobuntv.retro64plus.enemies.BobombEntity.onHold(payload, player);
                    }
                });
        net.registerClientbound(Payloads.Release.TYPE, Payloads.Release.CODEC,
                payload -> com.nanobuntv.retro64plus.client.ClientNetwork.onRelease(payload));
        net.registerClientbound(Payloads.ShopOffers.TYPE, Payloads.ShopOffers.CODEC,
                payload -> com.nanobuntv.retro64plus.client.ClientNetwork.onShopOffers(payload));
        net.registerClientbound(Payloads.RemoteMario.TYPE, Payloads.RemoteMario.CODEC,
                payload -> com.nanobuntv.retro64plus.client.ClientNetwork.onRemoteMario(payload));
        net.registerClientbound(Payloads.MarioRules.TYPE, Payloads.MarioRules.CODEC,
                payload -> com.nanobuntv.retro64plus.client.ClientNetwork.onRules(payload));
        net.registerClientbound(Payloads.CapState.TYPE, Payloads.CapState.CODEC,
                payload -> com.nanobuntv.retro64plus.client.ClientNetwork.onCaps(payload));
        net.registerClientbound(Payloads.CoinCount.TYPE, Payloads.CoinCount.CODEC,
                payload -> com.nanobuntv.retro64plus.client.ClientNetwork.onCoinCount(payload));
        net.registerClientbound(Payloads.Sm64Sound.TYPE, Payloads.Sm64Sound.CODEC,
                payload -> com.nanobuntv.retro64plus.client.ClientNetwork.onSm64Sound(payload));
        net.registerClientbound(Payloads.StarCount.TYPE, Payloads.StarCount.CODEC,
                payload -> com.nanobuntv.retro64plus.client.ClientNetwork.onStarCount(payload));
        net.registerClientbound(Payloads.StarCollected.TYPE, Payloads.StarCollected.CODEC,
                payload -> com.nanobuntv.retro64plus.client.ClientNetwork.onStarCollected(payload));
    }
}
