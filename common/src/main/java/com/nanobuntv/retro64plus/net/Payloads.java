package com.nanobuntv.retro64plus.net;

import com.nanobuntv.retro64plus.Retro64Plus;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Retro64 Plus's payloads (M1 checkpoints 3 and 4, M2). Registered in {@link Retro64PlusNetwork}. */
public final class Payloads {
    private Payloads() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String name) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Retro64Plus.MOD_ID, name));
    }

    private static final StreamCodec<FriendlyByteBuf, Optional<MarioUpdate>> OPTIONAL_UPDATE =
            ByteBufCodecs.optional(MarioUpdate.CODEC);

    /** Client → server, every tick while Mario (pose and sounds), and once when he turns off (empty). */
    public record MarioState(Optional<MarioUpdate> update) implements CustomPacketPayload {
        public static final Type<MarioState> TYPE = payloadType("mario_state");
        public static final StreamCodec<FriendlyByteBuf, MarioState> CODEC =
                OPTIONAL_UPDATE.map(MarioState::new, MarioState::update);

        @Override
        public Type<MarioState> type() {
            return TYPE;
        }
    }

    /** Server → the clients near a player: that player's Mario (pose and sounds), or empty when they turned back. */
    public record RemoteMario(int entityId, Optional<MarioUpdate> update) implements CustomPacketPayload {
        public static final Type<RemoteMario> TYPE = payloadType("remote_mario");
        public static final StreamCodec<FriendlyByteBuf, RemoteMario> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, RemoteMario::entityId, OPTIONAL_UPDATE, RemoteMario::update, RemoteMario::new);

        @Override
        public Type<RemoteMario> type() {
            return TYPE;
        }
    }

    /**
     * Server → client, on join and when the game rules change: the rules of D14 and the characters players may choose
     * (D34, as {@code MarioCharacter} bits).
     */
    public record MarioRules(boolean forceMario, boolean allowMario, int allowedCharacters)
            implements CustomPacketPayload {
        public static final Type<MarioRules> TYPE = payloadType("mario_rules");
        public static final StreamCodec<FriendlyByteBuf, MarioRules> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, MarioRules::forceMario, ByteBufCodecs.BOOL, MarioRules::allowMario,
                ByteBufCodecs.VAR_INT, MarioRules::allowedCharacters, MarioRules::new);

        @Override
        public Type<MarioRules> type() {
            return TYPE;
        }
    }

    /**
     * Server → the wearer: the game time each cap ends (D17, D44; 0 = off), on every change. Game time, not ticks
     * left, so the client's countdown follows the server's clock through lag and {@code /tick}. Others see caps in
     * the pose.
     */
    public record CapState(long wing, long metal, long vanish) implements CustomPacketPayload {
        public static final Type<CapState> TYPE = payloadType("cap_state");
        public static final StreamCodec<FriendlyByteBuf, CapState> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, CapState::wing, ByteBufCodecs.VAR_LONG, CapState::metal,
                ByteBufCodecs.VAR_LONG, CapState::vanish, CapState::new);

        @Override
        public Type<CapState> type() {
            return TYPE;
        }
    }

    /**
     * Client → server: damage SM64's own code dealt to the local Mario (D36): a fall or lava, in SM64 health units
     * (0x100 a power meter segment). The server turns it into Minecraft damage of that kind.
     */
    public record SelfDamage(int kind, int units) implements CustomPacketPayload {
        public static final int FALL = 0, LAVA = 1;
        public static final Type<SelfDamage> TYPE = payloadType("self_damage");
        public static final StreamCodec<FriendlyByteBuf, SelfDamage> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SelfDamage::kind, ByteBufCodecs.VAR_INT, SelfDamage::units, SelfDamage::new);

        @Override
        public Type<SelfDamage> type() {
            return TYPE;
        }
    }

    /**
     * Client → server: the local Mario landed a ground pound here (D18), in blocks. The server checks it's plausible,
     * then breaks the soft block under him, presses what he landed on and sends out the shockwave.
     */
    public record GroundPound(double x, double y, double z) implements CustomPacketPayload {
        public static final Type<GroundPound> TYPE = payloadType("ground_pound");
        public static final StreamCodec<FriendlyByteBuf, GroundPound> CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, GroundPound::x, ByteBufCodecs.DOUBLE, GroundPound::y,
                ByteBufCodecs.DOUBLE, GroundPound::z, GroundPound::new);

        @Override
        public Type<GroundPound> type() {
            return TYPE;
        }
    }

    /** Server → a player: their coin count (D50), on joining and on every change. */
    public record CoinCount(int coins) implements CustomPacketPayload {
        public static final Type<CoinCount> TYPE = payloadType("coin_count");
        public static final StreamCodec<FriendlyByteBuf, CoinCount> CODEC =
                ByteBufCodecs.VAR_INT.map(CoinCount::new, CoinCount::coins).cast();

        @Override
        public Type<CoinCount> type() {
            return TYPE;
        }
    }

    /**
     * Server → a player: how many Power Stars they own (D63), and what that does to their caps' duration and recharge
     * (D66, as multipliers), on joining and on every change.
     */
    public record StarCount(int stars, float capDuration, float capRecharge) implements CustomPacketPayload {
        public static final Type<StarCount> TYPE = payloadType("star_count");
        public static final StreamCodec<FriendlyByteBuf, StarCount> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, StarCount::stars, ByteBufCodecs.FLOAT, StarCount::capDuration,
                ByteBufCodecs.FLOAT, StarCount::capRecharge, StarCount::new);

        @Override
        public Type<StarCount> type() {
            return TYPE;
        }
    }

    /** Server → a player: they just collected this star (its id) and now own {@code total}: the star dance. */
    public record StarCollected(String id, int total) implements CustomPacketPayload {
        public static final Type<StarCollected> TYPE = payloadType("star_collected");
        public static final StreamCodec<FriendlyByteBuf, StarCollected> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(256), StarCollected::id, ByteBufCodecs.VAR_INT, StarCollected::total,
                StarCollected::new);

        @Override
        public Type<StarCollected> type() {
            return TYPE;
        }
    }

    /** One Coin Shop offer as the client sees it: what (with its count), its price in coins, the stars it needs. */
    public record Offer(ItemStack stack, int price, int stars) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Offer> CODEC = StreamCodec.composite(
                ItemStack.STREAM_CODEC, Offer::stack, ByteBufCodecs.VAR_INT, Offer::price, ByteBufCodecs.VAR_INT,
                Offer::stars, Offer::new);
    }

    /** Server → a player who used a Coin Shop (D58, D68): its offers; the client opens the shop screen. */
    public record ShopOffers(List<Offer> offers) implements CustomPacketPayload {
        public static final Type<ShopOffers> TYPE = payloadType("shop_offers");
        public static final StreamCodec<RegistryFriendlyByteBuf, ShopOffers> CODEC =
                Offer.CODEC.apply(ByteBufCodecs.list(256)).map(ShopOffers::new, ShopOffers::offers);

        @Override
        public Type<ShopOffers> type() {
            return TYPE;
        }
    }

    /** Client → server: buy offer {@code index} of the shop the player has open. */
    public record ShopBuy(int index) implements CustomPacketPayload {
        public static final Type<ShopBuy> TYPE = payloadType("shop_buy");
        public static final StreamCodec<FriendlyByteBuf, ShopBuy> CODEC =
                ByteBufCodecs.VAR_INT.map(ShopBuy::new, ShopBuy::index).cast();

        @Override
        public Type<ShopBuy> type() {
            return TYPE;
        }
    }

    /**
     * Client → server (M4): the local Mario picked up ({@link #GRAB}), threw ({@link #THROW}) or dropped
     * ({@link #DROP}) a Bob-omb, or got on a Koopa shell ({@link #RIDE}); {@code yaw} is Mario's facing (SM64's,
     * radians).
     */
    public record Hold(int entityId, int kind, float yaw) implements CustomPacketPayload {
        public static final int GRAB = 0, THROW = 1, DROP = 2, RIDE = 3;
        public static final Type<Hold> TYPE = payloadType("hold");
        public static final StreamCodec<FriendlyByteBuf, Hold> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Hold::entityId, ByteBufCodecs.VAR_INT, Hold::kind, ByteBufCodecs.FLOAT, Hold::yaw,
                Hold::new);

        @Override
        public Type<Hold> type() {
            return TYPE;
        }
    }

    /**
     * Server → client (M4): let go of that Bob-omb (the pick-up was refused, or it's going off in Mario's hands), or
     * get off that shell (the ride was refused).
     */
    public record Release(int entityId) implements CustomPacketPayload {
        public static final Type<Release> TYPE = payloadType("release");
        public static final StreamCodec<FriendlyByteBuf, Release> CODEC =
                ByteBufCodecs.VAR_INT.map(Release::new, Release::entityId).cast();

        @Override
        public Type<Release> type() {
            return TYPE;
        }
    }

    /**
     * Server → the players nearby: SM64's {@code sound} (bits) here (blocks), for the mod's own events (D108: a coin
     * picked up, a cap put on, a purchase, a Recharge Mushroom). The native side checks the bits.
     */
    public record Sm64Sound(double x, double y, double z, int sound) implements CustomPacketPayload {
        public static final Type<Sm64Sound> TYPE = payloadType("sm64_sound");
        public static final StreamCodec<FriendlyByteBuf, Sm64Sound> CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, Sm64Sound::x, ByteBufCodecs.DOUBLE, Sm64Sound::y,
                ByteBufCodecs.DOUBLE, Sm64Sound::z, ByteBufCodecs.INT, Sm64Sound::sound, Sm64Sound::new);

        @Override
        public Type<Sm64Sound> type() {
            return TYPE;
        }
    }

    /**
     * Client → server: the local Mario hit this entity with this attack (D29, a {@code combat.MarioAttack} ordinal).
     * The client decides by SM64's rules (and bounces off a stomp at once); the server checks it's plausible first.
     */
    public record Attack(int entityId, int attack) implements CustomPacketPayload {
        public static final Type<Attack> TYPE = payloadType("attack");
        public static final StreamCodec<FriendlyByteBuf, Attack> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Attack::entityId, ByteBufCodecs.VAR_INT, Attack::attack, Attack::new);

        @Override
        public Type<Attack> type() {
            return TYPE;
        }
    }
}
