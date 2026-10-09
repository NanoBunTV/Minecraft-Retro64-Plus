package com.nanobuntv.retro64plus.server;

import com.nanobuntv.retro64plus.platform.Services;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
//? if >=1.21.11 {
/*import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import net.minecraft.world.level.gamerules.GameRules;
*///?} else {
import net.minecraft.world.level.GameRules;
//?}

/**
 * The mod's game rules (D14) and the vanilla ones it reads, on every Minecraft version (Porting.md). Up to 1.21.10 a
 * rule is a key registered through the loader ({@code /gamerule retro64plusForceMario}); from 1.21.11 rules are a
 * registry and ours are {@code /gamerule retro64plus:force_mario}. Changes are noticed by polling ({@link ServerMarios}).
 */
public final class ModGameRules {
    private ModGameRules() {}

    /** A boolean rule's value on a server. */
    public interface Rule {
        boolean get(MinecraftServer server);
    }

    /** {@code legacyName} up to 1.21.10, {@code retro64plus:<id>} from 1.21.11. */
    public static Rule bool(String legacyName, String id, boolean defaultValue) {
        //? if >=1.21.11 {
        /*Supplier<GameRule<Boolean>> rule = Services.PLATFORM.register(Registries.GAME_RULE, id,
                () -> new GameRule<>(GameRuleCategory.PLAYER, GameRuleType.BOOL, BoolArgumentType.bool(),
                        GameRuleTypeVisitor::visitBoolean, Codec.BOOL, value -> value ? 1 : 0, defaultValue,
                        FeatureFlagSet.of()));
        return server -> server.overworld().getGameRules().get(rule.get());
        *///?} else {
        GameRules.Key<GameRules.BooleanValue> key = Services.PLATFORM.registerBooleanGameRule(legacyName,
                GameRules.Category.PLAYER, defaultValue, (server, value) -> {});
        return server -> server.getGameRules().getBoolean(key);
        //?}
    }

    public static boolean keepInventory(ServerLevel level) {
        //? if >=1.21.11 {
        /*return level.getGameRules().get(GameRules.KEEP_INVENTORY);
        *///?} else {
        return level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
        //?}
    }

    public static boolean mobLoot(ServerLevel level) {
        //? if >=1.21.11 {
        /*return level.getGameRules().get(GameRules.MOB_DROPS);
        *///?} else {
        return level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT);
        //?}
    }
}
