package com.nanobuntv.retro64plus.client;

import com.nanobuntv.retro64plus.Retro64Plus;
import com.nanobuntv.retro64plus.platform.Services;
import com.nanobuntv.retro64plus.sm64.LibSM64;
import com.nanobuntv.retro64plus.sm64.NativePlatform;
import com.nanobuntv.retro64plus.sm64.Rom;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * The startup gate (D81, D82): shown after Minecraft's accessibility screen and before the title screen (or a quick
 * play). It finds the player's ROM (the config path or the retro64plus folder, D22) and starts the library; only once
 * it's loaded does the game go on. Without a ROM it asks for one: "Choose ROM..." opens the OS file dialog and copies
 * the file into the mod's folder (never moving it), "Open Folder" shows that folder, "Check Again" looks once more, and
 * "Quit Game" is the only other way out. On a system the native library doesn't support (D5) it explains that and
 * offers only quitting.
 */
public final class RomSetupScreen extends ModScreen {
    private enum State { SEARCHING, MISSING, CHECKING, LOADING, UNSUPPORTED, FAILED }

    private static final int TEXT_WIDTH = 320;

    private final Runnable next;
    private State state = State.SEARCHING;
    private Component status = Component.empty();
    private boolean done;
    /** Dev builds: a {@code retro64plus_dev/gate} file ("norom" or "unsupported") shows that state, screenshots it, quits. */
    private final @Nullable String devGate = devGate();
    private int ticks;

    public RomSetupScreen(Runnable next) {
        super(Component.translatable("screen.retro64plus.rom_setup.title"));
        this.next = next;
        if (LibSM64.status() == LibSM64.Status.UNSUPPORTED_SYSTEM || "unsupported".equals(devGate)) {
            state = State.UNSUPPORTED;
        } else if (LibSM64.status() == LibSM64.Status.READY) {
            state = State.LOADING; // already loaded (a reload): goes straight on
        } else if (LibSM64.status() != LibSM64.Status.NOT_STARTED) {
            state = State.LOADING;
        } else {
            search(false);
        }
    }

    private static @Nullable String devGate() {
        if (!Services.PLATFORM.isDevelopmentEnvironment()) return null;
        Path file = Services.PLATFORM.gameDir().resolve("retro64plus_dev").resolve("gate");
        try {
            return Files.exists(file) ? Files.readString(file).strip() : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** Looks for a ROM off the render thread (it may read several 8 MB files). */
    private void search(boolean again) {
        state = State.SEARCHING;
        rebuild();
        status = Component.empty();
        @Nullable Path configured = Retro64PlusClient.configuredRomPath();
        boolean hide = "norom".equals(devGate);
        CompletableFuture.supplyAsync(() -> hide ? Optional.<Rom.Loaded>empty() : Rom.find(configured,
                Retro64PlusClient.modDir())).whenComplete((rom, error) -> minecraft().execute(() -> {
                    if (rom != null && rom.isPresent()) {
                        start(rom.get());
                    } else {
                        if (again) {
                            status = Component.translatable("screen.retro64plus.rom_setup.not_found")
                                    .withStyle(ChatFormatting.GRAY);
                        }
                        state = State.MISSING;
                        rebuild();
                    }
                }));
    }

    private static net.minecraft.client.Minecraft minecraft() {
        return net.minecraft.client.Minecraft.getInstance();
    }

    private void start(Rom.Loaded rom) {
        state = State.LOADING;
        Retro64PlusClient.start(rom);
        rebuild();
    }

    @Override
    protected void init() {
        int y = height / 2 + 40;
        switch (state) {
            case MISSING -> {
                addRenderableWidget(Button.builder(Component.translatable("screen.retro64plus.rom_setup.choose"),
                        b -> choose()).bounds(width / 2 - 154, y, 100, 20).build());
                addRenderableWidget(Button.builder(Component.translatable("screen.retro64plus.rom_setup.open_folder"),
                        b -> openFolder()).bounds(width / 2 - 50, y, 100, 20).build());
                addRenderableWidget(Button.builder(Component.translatable("screen.retro64plus.rom_setup.check_again"),
                        b -> search(true)).bounds(width / 2 + 54, y, 100, 20).build());
                addRenderableWidget(quit(y + 24));
            }
            case UNSUPPORTED -> addRenderableWidget(quit(y));
            case FAILED -> {
                addRenderableWidget(Button.builder(Component.translatable("screen.retro64plus.rom_setup.continue"),
                        b -> proceed()).bounds(width / 2 - 154, y, 150, 20).build());
                addRenderableWidget(Button.builder(Component.translatable("menu.quit"), b -> minecraft.stop())
                        .bounds(width / 2 + 4, y, 150, 20).build());
            }
            default -> { }
        }
    }

    private Button quit(int y) {
        return Button.builder(Component.translatable("menu.quit"), b -> minecraft.stop())
                .bounds(width / 2 - 75, y, 150, 20).build();
    }

    private void rebuild() {
        if (minecraft != null) rebuildWidgets();
    }

    @Override
    public void tick() {
        ticks++;
        if (state == State.LOADING) {
            switch (LibSM64.status()) {
                case READY -> proceed();
                case FAILED -> {
                    state = State.FAILED;
                    status = Component.translatable("message.retro64plus.load_failed", LibSM64.error())
                            .withStyle(ChatFormatting.RED);
                    rebuild();
                }
                default -> { }
            }
        }
        if (devGate != null && ticks == 100) {
            //? if >=1.21.2 {
            /*Screenshot.grab(minecraft.gameDirectory, "retro64plus_gate_" + devGate + ".png",
                    minecraft.getMainRenderTarget(), 1, message -> Retro64Plus.LOGGER.info("Dev gate: {}",
                            message.getString()));
            *///?} else {
            Screenshot.grab(minecraft.gameDirectory, "retro64plus_gate_" + devGate + ".png",
                    minecraft.getMainRenderTarget(), message -> Retro64Plus.LOGGER.info("Dev gate: {}",
                            message.getString()));
            //?}
        } else if (devGate != null && ticks == 110) {
            Retro64Plus.LOGGER.info("Dev gate [{}]: state {}", devGate, state);
            minecraft.stop();
        }
    }

    /**
     * On to the title screen (or the quick play), once; queued rather than run here, since it replaces this screen
     * and Fabric's screen events still expect it after the tick.
     */
    private void proceed() {
        if (done) return;
        done = true;
        minecraft.tell(next);
    }

    private void choose() {
        if (state != State.MISSING || FilePicker.isOpen()) return;
        FilePicker.open(Component.translatable("screen.retro64plus.rom_setup.dialog_title").getString(),
                new String[] {"*.z64", "*.v64", "*.n64"},
                Component.translatable("screen.retro64plus.rom_setup.dialog_filter").getString(), this::importRom);
    }

    /** Checks and copies the picked file off the render thread (8 MB read and hash). */
    private void importRom(Path source) {
        state = State.CHECKING;
        rebuild();
        status = Component.translatable("screen.retro64plus.rom_setup.checking").withStyle(ChatFormatting.GRAY);
        Path folder = Retro64PlusClient.modDir();
        CompletableFuture.supplyAsync(() -> {
            try {
                return Rom.importRom(source, folder);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((rom, error) -> minecraft().execute(() -> {
            if (rom != null) {
                Retro64Plus.LOGGER.info("Imported ROM {} to {}", source, rom.path());
                status = Component.translatable("message.retro64plus.loading").withStyle(ChatFormatting.GRAY);
                start(rom);
            } else {
                state = State.MISSING;
                status = failure(error.getCause() != null ? error.getCause() : error);
                rebuild();
            }
        }));
    }

    private static Component failure(Throwable error) {
        Throwable cause = error instanceof RuntimeException && error.getCause() != null ? error.getCause() : error;
        String key = cause instanceof Rom.InvalidRomException invalid
                ? switch (invalid.reason) {
                    case NOT_N64_ROM -> "screen.retro64plus.rom_setup.not_n64_rom";
                    case WRONG_ROM -> "screen.retro64plus.rom_setup.wrong_rom";
                }
                : "screen.retro64plus.rom_setup.read_error";
        if (!(cause instanceof Rom.InvalidRomException)) Retro64Plus.LOGGER.warn("Couldn't import the ROM", cause);
        return Component.translatable(key).withStyle(ChatFormatting.RED);
    }

    private void openFolder() {
        Path folder = Retro64PlusClient.modDir();
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            Retro64Plus.LOGGER.warn("Couldn't create {}", folder, e);
        }
        //? if >=26.3 {
        /*com.mojang.blaze3d.Blaze3D.openPath(folder);
        *///?} else {
        Util.getPlatform().openFile(folder.toFile());
        //?}
    }

    @Override
    protected void drawOver(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int y = height / 2 - 80;
        Component heading = state == State.UNSUPPORTED
                ? Component.translatable("screen.retro64plus.rom_setup.unsupported_title") : title;
        GuiDraw.centered(graphics, font, heading, width / 2, y, 0xFFFFFFFF);
        y += 20;
        Component body = switch (state) {
            case UNSUPPORTED -> Component.translatable("screen.retro64plus.rom_setup.unsupported",
                    NativePlatform.describeCurrent());
            case SEARCHING, LOADING -> Component.translatable("message.retro64plus.loading");
            default -> Component.translatable("screen.retro64plus.rom_setup.explanation");
        };
        for (FormattedCharSequence line : font.split(body, TEXT_WIDTH)) {
            GuiDraw.centered(graphics, font, line, width / 2, y, 0xFFD0D0D0);
            y += font.lineHeight + 2;
        }
        int statusY = state == State.MISSING ? height / 2 + 92 : height / 2 + 68;
        for (FormattedCharSequence line : font.split(status, TEXT_WIDTH)) {
            GuiDraw.centered(graphics, font, line, width / 2, statusY, 0xFFFFFFFF);
            statusY += font.lineHeight + 2;
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // the ROM is required: choose one or quit
    }
}
