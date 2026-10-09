package com.nanobuntv.retro64plus.client;

import com.nanobuntv.retro64plus.MarioCharacter;
import com.nanobuntv.retro64plus.config.ClientSettings;
import com.nanobuntv.retro64plus.config.CoinHudPosition;
import com.nanobuntv.retro64plus.config.MarioMusic;
import com.nanobuntv.retro64plus.config.PowerMeterPosition;
import com.nanobuntv.retro64plus.config.Retro64PlusConfig;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import java.util.Locale;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Retro64 Plus's config screen (D23), built with YetAnotherConfigLib. Opened from the mod list (NeoForge) or Mod Menu
 * (Fabric). The client's settings only; the server's (D34) are a file, config/retro64plus-server.json5.
 */
public final class Retro64PlusConfigScreen {
    private static final String KEY = "retro64plus.configuration.";

    private Retro64PlusConfigScreen() {}

    public static Screen create(Screen parent) {
        ClientSettings c = Retro64PlusConfig.client();
        ClientSettings d = Retro64PlusConfig.clientDefaults();
        return YetAnotherConfigLib.createBuilder()
                .title(Component.translatable(KEY + "title"))
                .category(ConfigCategory.createBuilder()
                        .name(Component.translatable(KEY + "client"))
                        .option(Option.<MarioCharacter>createBuilder()
                                .name(Component.translatable(KEY + "character"))
                                .description(OptionDescription.of(Component.translatable(KEY + "character.description")))
                                .binding(d.characterChoice(), c::characterChoice, v -> c.character = v.key())
                                .controller(o -> EnumControllerBuilder.create(o).enumClass(MarioCharacter.class)
                                        .formatValue(v -> Component.translatable("retro64plus.character." + v.key())))
                                .build())
                        .option(Option.<MarioMusic>createBuilder()
                                .name(Component.translatable(KEY + "music"))
                                .description(OptionDescription.of(Component.translatable(KEY + "music.description")))
                                .binding(d.music, () -> c.music, v -> c.music = v)
                                .controller(o -> EnumControllerBuilder.create(o).enumClass(MarioMusic.class)
                                        .formatValue(v -> Component.translatable(
                                                "retro64plus.music." + v.name().toLowerCase(Locale.ROOT))))
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable(KEY + "power_meter_always_shown"))
                                .description(OptionDescription.of(
                                        Component.translatable(KEY + "power_meter_always_shown.description")))
                                .binding(d.powerMeterAlwaysShown, () -> c.powerMeterAlwaysShown,
                                        v -> c.powerMeterAlwaysShown = v)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<PowerMeterPosition>createBuilder()
                                .name(Component.translatable(KEY + "power_meter_position"))
                                .description(OptionDescription.of(
                                        Component.translatable(KEY + "power_meter_position.description")))
                                .binding(d.powerMeterPosition, () -> c.powerMeterPosition,
                                        v -> c.powerMeterPosition = v)
                                .controller(o -> EnumControllerBuilder.create(o).enumClass(PowerMeterPosition.class)
                                        .formatValue(v -> Component.translatable(
                                                "retro64plus.power_meter_position." + v.name().toLowerCase(Locale.ROOT))))
                                .build())
                        .option(Option.<CoinHudPosition>createBuilder()
                                .name(Component.translatable(KEY + "coin_counter_position"))
                                .description(OptionDescription.of(
                                        Component.translatable(KEY + "coin_counter_position.description")))
                                .binding(d.coinCounterPosition, () -> c.coinCounterPosition,
                                        v -> c.coinCounterPosition = v)
                                .controller(o -> EnumControllerBuilder.create(o).enumClass(CoinHudPosition.class)
                                        .formatValue(v -> Component.translatable(
                                                "retro64plus.coin_counter_position." + v.name().toLowerCase(Locale.ROOT))))
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable(KEY + "coin_counter_always_shown"))
                                .description(OptionDescription.of(
                                        Component.translatable(KEY + "coin_counter_always_shown.description")))
                                .binding(d.coinCounterAlwaysShown, () -> c.coinCounterAlwaysShown,
                                        v -> c.coinCounterAlwaysShown = v)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<String>createBuilder()
                                .name(Component.translatable(KEY + "rom_path"))
                                .description(OptionDescription.of(Component.translatable(KEY + "rom_path.description")))
                                .binding(d.romPath, () -> c.romPath, v -> c.romPath = v)
                                .controller(StringControllerBuilder::create)
                                .build())
                        .group(OptionGroup.createBuilder()
                                .name(Component.translatable(KEY + "controller_group"))
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable(KEY + "controller"))
                                        .description(OptionDescription.of(
                                                Component.translatable(KEY + "controller.description")))
                                        .binding(d.controller, () -> c.controller, v -> c.controller = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Double>createBuilder()
                                        .name(Component.translatable(KEY + "controller_deadzone"))
                                        .description(OptionDescription.of(
                                                Component.translatable(KEY + "controller_deadzone.description")))
                                        .binding(d.controllerDeadzone, () -> c.controllerDeadzone,
                                                v -> c.controllerDeadzone = v)
                                        .controller(o -> DoubleSliderControllerBuilder.create(o).range(0.0, 0.9)
                                                .step(0.05))
                                        .build())
                                .option(Option.<Double>createBuilder()
                                        .name(Component.translatable(KEY + "controller_look_speed"))
                                        .description(OptionDescription.of(
                                                Component.translatable(KEY + "controller_look_speed.description")))
                                        .binding(d.controllerLookSpeed, () -> c.controllerLookSpeed,
                                                v -> c.controllerLookSpeed = v)
                                        .controller(o -> DoubleSliderControllerBuilder.create(o).range(0.1, 4.0)
                                                .step(0.1))
                                        .build())
                                .build())
                        .build())
                .save(Retro64PlusConfig::saveClient)
                .build()
                .generateScreen(parent);
    }
}
