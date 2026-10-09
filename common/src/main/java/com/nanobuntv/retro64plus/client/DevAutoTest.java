package com.nanobuntv.retro64plus.client;

//? if <1.21.2 {
import com.nanobuntv.retro64plus.Retro64Plus;
import com.nanobuntv.retro64plus.config.Retro64PlusConfig;
import com.nanobuntv.retro64plus.platform.Services;
import com.nanobuntv.retro64plus.sm64.LibSM64;
import com.nanobuntv.retro64plus.sm64.Rom;
import java.nio.file.Files;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;

/**
 * Dev builds only: with a file {@code <gamedir>/retro64plus_dev/automario}, a scripted run once in a world, so a dev
 * run (DevSetup: {@code -PquickPlay}) can check Mario without anyone at the keyboard. It builds a test course next to
 * the player with commands (so run it in a throwaway copy of a world, with cheats on): a staircase going south and a
 * water pool with a squid that stays put. Mario walks up the stairs, then swims past the squid (strokes with A,
 * turning with the stick), with log lines and screenshots along the way.
 */
final class DevAutoTest {
    private static boolean enabled;
    private static int ticks = -1;
    private static BlockPos origin;
    /** Scripted controls, read by LocalMario instead of the keyboard while set. */
    static boolean active;
    static float forward, right;
    static boolean jump, punch, crouch;

    private DevAutoTest() {}

    /**
     * The trigger file's content: empty for the course, "mpmario" / "mpwatch" for the two players of the MP test,
     * "cp4" for checkpoint 4 (characters, attacks, sound), "m2health" for M2's health (D36–D38, D45), "m2caps" for
     * the caps (D17, D35, D40–D44), "m2pound" for the ground pound (D18, D39, D46–D49), "m3coins" for coins
     * (D50–D57), "m3stars" for Power Stars (D60–D67), "m3shop" for the Coin Shop (D58, D59, D68–D71), "m4models"
     * for M4's ROM models (D19, D79), "m4enemies" for the Goomba and Bob-omb (D72-D86), "m4koopa" for the Koopa
     * and its shell (D87-D93), "m4chomp" for the Chain Chomp (D94-D104).
     */
    private static String mode = "";

    static void init() {
        java.nio.file.Path trigger = Services.PLATFORM.gameDir().resolve("retro64plus_dev").resolve("automario");
        enabled = Services.PLATFORM.isDevelopmentEnvironment() && Files.exists(trigger);
        if (enabled) {
            try {
                mode = Files.readString(trigger).strip();
            } catch (java.io.IOException e) {
                mode = "";
            }
        }
    }

    /** An SM64 sound for one of the mod's events reached this client (D108); logged during dev runs. */
    static void onSound(int sound, boolean played) {
        if (enabled) Retro64Plus.LOGGER.info("Dev automario [sound]: 0x{} {}", Integer.toHexString(sound),
                played ? "played by SM64" : "not played (SM64's sound isn't running)");
    }

    static void tick() {
        if (!enabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || (mc.screen != null && !mode.equals("m2health") && !mode.startsWith("m3") && !mode.startsWith("m4"))) return;
        if (mode.equals("mpwatch")) {
            mpWatch(mc); // loads nothing itself: the remote Mario's first pose and the forced rule must do it
            return;
        }
        if (LibSM64.status() == LibSM64.Status.NOT_STARTED) {
            // As a key press would: the ROM from the config path or the retro64plus folder.
            Rom.find(Retro64PlusClient.configuredRomPath(), Retro64PlusClient.modDir()).ifPresentOrElse(
                    Retro64PlusClient::start, () -> {
                        Retro64Plus.LOGGER.warn("Dev automario: no ROM found");
                        enabled = false;
                    });
            return;
        }
        if (LibSM64.status() != LibSM64.Status.READY) return;
        if (mode.equals("mpmario")) {
            mpMario(mc);
            return;
        }
        if (mode.equals("cp4")) {
            checkpoint4(mc);
            return;
        }
        if (mode.equals("m2health")) {
            health(mc);
            return;
        }
        if (mode.equals("m2caps")) {
            caps(mc);
            return;
        }
        if (mode.equals("m2pound")) {
            pound(mc);
            return;
        }
        if (mode.equals("m3coins")) {
            coins(mc);
            return;
        }
        if (mode.equals("m3stars")) {
            stars(mc);
            return;
        }
        if (mode.equals("m3shop")) {
            shop(mc);
            return;
        }
        if (mode.equals("m4enemies")) {
            enemies(mc);
            return;
        }
        if (mode.equals("m4chomp") || mode.equals("m4chomp quick")) {
            villagesToVisit = mode.endsWith("quick") ? 0 : VILLAGES_FULL;
            chomp(mc);
            return;
        }
        if (mode.equals("m4koopa")) {
            koopa(mc);
            return;
        }
        if (mode.equals("m4models")) {
            models(mc);
            return;
        }
        ticks++;
        int t = ticks;
        if (t == 5) buildCourse(mc);
        // --- Stairs: walk south from the start, up two stair steps onto a 2-high platform.
        else if (t == 40) {
            LocalMario.toggle();
            active = true;
            log("on");
        } else if (t == 50) forward = 1;
        else if (t >= 50 && t <= 110 && t % 10 == 0) log("stairs walk");
        else if (t == 111) {
            forward = 0;
            screenshot("retro64plus_stairs");
        }
        // --- Water: into the pool at its north end, facing south towards the squid.
        else if (t == 120) command(mc, "tp @s %d %d %d 0 0", origin.getX() + 10, origin.getY() - 1, origin.getZ() + 1);
        else if (t == 135) log("in pool");
        else if (t >= 140 && t < 220) {
            // Swim strokes: tap A twice a second; hold forward a little (pitch down a touch).
            jump = t % 10 < 3;
            if (t % 10 == 0) log("swim");
        } else if (t == 220) {
            jump = false;
            right = 1;
            log("swim, now turning right");
        } else if (t == 240) {
            right = 0;
            log("turned");
            screenshot("retro64plus_swim");
        } else if (t == 250) {
            active = false;
            LocalMario.toggle();
            log("off");
            Retro64Plus.LOGGER.info("Dev automario: done");
            enabled = false;
        }
    }

    /**
     * MP test, the acting player ("Dev"): once "Dev2" is in, puts Dev2 a few blocks south facing this player, turns
     * Mario on, moves and jumps, then checks the game rules: not allowed (Mario must turn off), then forced (both
     * players must turn into Mario, Dev2 without pressing anything).
     */
    private static void mpMario(Minecraft mc) {
        if (origin == null) {
            boolean dev2Here = mc.level.players().stream().anyMatch(p -> p.getGameProfile().getName().equals("Dev2"));
            if (!dev2Here) return;
            origin = mc.player.blockPosition();
        }
        ticks++;
        int t = ticks;
        switch (t) {
            case 5 -> {
                command(mc, "time set day");
                command(mc, "tp @s ~ ~ ~ 0 0");
                command(mc, "execute at Dev run tp Dev2 ~ ~ ~5");
                command(mc, "execute as Dev2 at @s run tp @s ~ ~ ~ facing entity Dev feet");
            }
            case 40 -> {
                Retro64PlusConfig.client().character = "skin"; // checkpoint 4: the others must see the skin body
                LocalMario.toggle();
                active = true;
                log("mp on");
            }
            case 60 -> right = 1;
            case 80 -> {
                right = 0;
                jump = true;
                log("mp walked");
            }
            case 84 -> jump = false;
            case 110 -> log("mp jumped");
            case 130 -> command(mc, "gamerule retro64plusAllowMario false");
            case 150 -> Retro64Plus.LOGGER.info("Dev automario [mp not allowed]: Mario {}", LocalMario.isActive() ? "STILL ON" : "off");
            case 160 -> {
                active = false;
                command(mc, "gamerule retro64plusAllowMario true");
                command(mc, "gamerule retro64plusForceMario true");
            }
            case 200 -> Retro64Plus.LOGGER.info("Dev automario [mp forced]: Mario {}", LocalMario.isActive() ? "on" : "STILL OFF");
            case 300 -> {
                Retro64PlusConfig.client().character = "mario";
                command(mc, "gamerule retro64plusForceMario false");
                Retro64Plus.LOGGER.info("Dev automario: done");
                enabled = false;
                mc.stop();
            }
            default -> { }
        }
    }

    /**
     * MP test, the watching player ("Dev2"): presses nothing. Logs what it sees of "Dev" (Mario on this client?
     * a puppet to draw? Dev's hitbox) and screenshots the first time Dev appears as Mario; logs when the forced rule
     * makes this player Mario too.
     */
    private static void mpWatch(Minecraft mc) {
        ticks++;
        net.minecraft.world.entity.player.Player dev = mc.level.players().stream()
                .filter(p -> p.getGameProfile().getName().equals("Dev")).findFirst().orElse(null);
        boolean devMario = dev != null && com.nanobuntv.retro64plus.MarioHooks.isMario(dev);
        if (ticks % 20 == 0) {
            Retro64Plus.LOGGER.info(String.format(Locale.ROOT,
                    "Dev automario [watch %d]: Dev %s, Mario here %s, puppet %s, Dev hitbox %.2f x %.2f; me Mario %s, libsm64 %s; sound %s",
                    ticks, dev == null ? "absent" : "present", devMario, dev != null && RemoteMarios.puppet(dev) != null
                            ? RemoteMarios.puppet(dev).character() : "none",
                    dev == null ? 0 : dev.getBbWidth(), dev == null ? 0 : dev.getBbHeight(), LocalMario.isActive(),
                    LibSM64.status(), MarioAudio.debugState()));
        }
        if (devMario && dev != null && RemoteMarios.puppet(dev) != null && watchShots == 0) {
            watchShots = ticks;
        }
        if (watchShots > 0 && ticks == watchShots + 15) screenshot("retro64plus_mp_watch");
        if (LocalMario.isActive() && !watchSawForced) {
            watchSawForced = true;
            Retro64Plus.LOGGER.info("Dev automario [watch]: the forced rule made me Mario");
        }
        if (ticks == 1200) {
            Retro64Plus.LOGGER.info("Dev automario: done");
            enabled = false;
            mc.stop();
        }
    }

    /**
     * Checkpoint 4: an arena with husks that don't move (they don't burn by day). Mario punches the first three times
     * (punch, punch, kick), is dropped onto the second (stomp, must bounce) and ground-pounds the third; each husk's
     * health is logged. Then Luigi and the skin character, screenshot from the front, and the sound path's state.
     */
    private static void checkpoint4(Minecraft mc) {
        ticks++;
        int t = ticks;
        if (t == 5) {
            origin = mc.player.blockPosition();
            int x = origin.getX(), y = origin.getY(), z = origin.getZ();
            command(mc, "time set day");
            command(mc, "gamemode survival");
            command(mc, "fill %d %d %d %d %d %d stone", x - 6, y - 1, z - 6, x + 12, y - 1, z + 12);
            command(mc, "fill %d %d %d %d %d %d air", x - 6, y, z - 6, x + 12, y + 10, z + 12);
            command(mc, "kill @e[type=husk,distance=..40]");
            command(mc, "tp @s %d %d %d 0 0", x, y, z);
            command(mc, "summon husk %d %d %.1f {NoAI:1b,PersistenceRequired:1b,Tags:[\"r64punch\"]}", x, y, z + 1.6);
            command(mc, "summon husk %d %d %d {NoAI:1b,PersistenceRequired:1b,Tags:[\"r64stomp\"]}", x + 6, y, z);
            command(mc, "summon husk %d %d %d {NoAI:1b,PersistenceRequired:1b,Tags:[\"r64pound\"]}", x + 6, y, z + 6);
        } else if (t == 40) {
            Retro64PlusConfig.client().character = "mario";
            LocalMario.toggle();
            active = true;
            log("cp4 on");
        } else if (t >= 50 && t < 110) {
            // Three presses of B, about a third of a second apart: punch, punch, kick.
            punch = (t - 50) % 8 < 2;
            if (t % 10 == 0) logHusks(mc, "punching");
        } else if (t == 110) {
            punch = false;
            logHusks(mc, "after punches");
        } else if (t == 120) {
            command(mc, "tp @s %d %d %d 0 0", origin.getX() + 6, origin.getY() + 4, origin.getZ());
        } else if (t > 120 && t < 150) {
            if (t % 3 == 0) log("stomp fall");
        } else if (t == 150) {
            logHusks(mc, "after stomp");
        } else if (t == 160) {
            command(mc, "tp @s %d %d %d 0 0", origin.getX() + 6, origin.getY() + 5, origin.getZ() + 6);
        } else if (t == 166) {
            crouch = true; // Z in the air: ground pound
        } else if (t == 170) {
            crouch = false;
        } else if (t > 166 && t < 200 && t % 4 == 0) {
            log("ground pound");
        } else if (t == 200) {
            logHusks(mc, "after ground pound");
            Retro64Plus.LOGGER.info("Dev automario [cp4 sound]: {}", MarioAudio.debugState());
            command(mc, "tp @s %d %d %d 180 10", origin.getX() - 4, origin.getY(), origin.getZ() + 4);
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
        } else if (t == 220) {
            screenshot("retro64plus_cp4_mario");
            Retro64PlusClient.cycleCharacter();
        } else if (t == 235) {
            screenshot("retro64plus_cp4_luigi");
            Retro64PlusClient.cycleCharacter();
        } else if (t == 250) {
            screenshot("retro64plus_cp4_skin");
            Retro64PlusConfig.client().music = com.nanobuntv.retro64plus.config.MarioMusic.BOB_OMB_BATTLEFIELD;
            forward = 1;
        } else if (t == 270) {
            forward = 0;
            screenshot("retro64plus_cp4_skin_walk");
        } else if (t == 300) {
            Retro64Plus.LOGGER.info("Dev automario [cp4 music]: {}", MarioAudio.debugState());
            Retro64PlusConfig.client().music = com.nanobuntv.retro64plus.config.MarioMusic.OFF;
            Retro64PlusConfig.client().character = "mario";
            active = false;
            LocalMario.toggle();
            Retro64Plus.LOGGER.info("Dev automario [cp4]: hits {}", lastHits);
            Retro64Plus.LOGGER.info("Dev automario: done");
            enabled = false;
            mc.stop(); // unattended run: close the game, so the next run can open the world
        }
        if (LocalMario.active() != null) lastHits = LocalMario.active().hits();
    }

    private static int lastHits;

    /**
     * M2 checkpoint 1 (D36–D38, D45): a hit from a husk (knockback, meter shown), a long fall (SM64's own damage
     * reaching the server), swimming at a pool's surface (healing), then death (SM64's death first, the death screen
     * held back) and respawning (back on his feet). Health, meter and action are logged at each step.
     */
    private static void health(Minecraft mc) {
        ticks++;
        int t = ticks;
        int x = origin != null ? origin.getX() : 0, y = origin != null ? origin.getY() : 0, z = origin != null ? origin.getZ() : 0;
        if (t == 5) {
            origin = mc.player.blockPosition();
            x = origin.getX(); y = origin.getY(); z = origin.getZ();
            command(mc, "time set day");
            command(mc, "difficulty normal");
            command(mc, "gamemode survival");
            command(mc, "fill %d %d %d %d %d %d stone", x - 6, y - 1, z - 6, x + 12, y - 1, z + 12);
            command(mc, "fill %d %d %d %d %d %d air", x - 6, y, z - 6, x + 12, y + 30, z + 12);
            command(mc, "fill %d %d %d %d %d %d water", x + 6, y - 4, z + 6, x + 11, y - 1, z + 11);
            command(mc, "kill @e[type=husk,distance=..40]");
            command(mc, "tp @s %d %d %d 0 0", x, y, z);
            command(mc, "summon husk %d %d %d {NoAI:1b,PersistenceRequired:1b}", x, y, z + 3);
            command(mc, "effect clear @s");
        } else if (t == 40) {
            Retro64PlusConfig.client().character = "mario";
            LocalMario.toggle();
            active = true;
            healthLog(mc, "on");
        } else if (t == 90) {
            healthLog(mc, "full, meter should hide");
        } else if (t == 100) {
            command(mc, "damage @s 6 minecraft:mob_attack by @e[type=husk,limit=1,sort=nearest]");
        } else if (t > 100 && t <= 112 && t % 3 == 0) {
            healthLog(mc, "hit");
        } else if (t == 114) {
            screenshot("retro64plus_m2_meter");
            Retro64PlusConfig.client().powerMeterPosition = com.nanobuntv.retro64plus.config.PowerMeterPosition.HOTBAR;
            command(mc, "item replace entity @s weapon.offhand with shield"); // the meter must clear its slot
        } else if (t == 120) {
            screenshot("retro64plus_m2_meter_hotbar");
            Retro64PlusConfig.client().powerMeterPosition = com.nanobuntv.retro64plus.config.PowerMeterPosition.TOP;
            command(mc, "item replace entity @s weapon.offhand with air");
        } else if (t == 130) {
            command(mc, "tp @s %d %d %d 0 0", x - 3, y + 14, z - 3);
        } else if (t > 130 && t < 175 && t % 5 == 0) {
            healthLog(mc, "falling");
        } else if (t == 180) {
            healthLog(mc, "after the fall");
            command(mc, "tp @s %.1f %d %.1f 0 0", x + 8.5, y - 1, z + 8.5);
        } else if (t > 180 && t < 260 && t % 10 == 0) {
            healthLog(mc, "swimming");
        } else if (t == 270) {
            command(mc, "tp @s %d %d %d 0 0", x, y, z);
        } else if (t == 290) {
            command(mc, "kill @s");
        } else if (t > 290 && t < 360 && t % 5 == 0) {
            healthLog(mc, "dying");
            if (t == 310) screenshot("retro64plus_m2_death");
        } else if (t == 365) {
            healthLog(mc, "before respawn");
            mc.player.respawn();
        } else if (t == 390) {
            healthLog(mc, "respawned");
        } else if (t == 400) {
            active = false;
            LocalMario.toggle();
            Retro64Plus.LOGGER.info("Dev automario: done");
            enabled = false;
            mc.stop();
        }
    }

    /**
     * M2 checkpoint 2: the caps from the hotbar. Vanish (a husk loses interest; through a glass wall), then Metal on top
     * (a used cap refuses while recharging; sinks in a pool; a hit does nothing), then Wing (a scripted triple jump
     * into flight). Then the timers run out, the cap music fades, and chests' loot is sampled for caps.
     */
    private static void caps(Minecraft mc) {
        ticks++;
        int t = ticks;
        int x = origin != null ? origin.getX() : 0, y = origin != null ? origin.getY() : 0, z = origin != null ? origin.getZ() : 0;
        if (t == 5) {
            origin = mc.player.blockPosition();
            x = origin.getX(); y = origin.getY(); z = origin.getZ();
            command(mc, "time set day");
            command(mc, "difficulty normal");
            command(mc, "gamemode survival");
            command(mc, "effect clear @s");
            command(mc, "clear @s");
            command(mc, "fill %d %d %d %d %d %d stone", x - 8, y - 1, z - 8, x + 20, y - 1, z + 30);
            command(mc, "fill %d %d %d %d %d %d air", x - 8, y, z - 8, x + 20, y + 20, z + 30);
            command(mc, "fill %d %d %d %d %d %d glass", x - 4, y, z + 5, x + 4, y + 1, z + 5);
            command(mc, "fill %d %d %d %d %d %d water", x + 10, y - 4, z, x + 14, y - 1, z + 4);
            command(mc, "kill @e[type=husk,distance=..60]");
            command(mc, "summon husk %d %d %d {PersistenceRequired:1b}", x - 6, y, z - 6);
            command(mc, "tp @s %d %d %d 0 0", x, y, z);
            command(mc, "item replace entity @s hotbar.0 with retro64plus:vanish_cap");
            command(mc, "item replace entity @s hotbar.1 with retro64plus:metal_cap");
            command(mc, "item replace entity @s hotbar.2 with retro64plus:wing_cap");
            var server = mc.getSingleplayerServer();
            if (server != null) {
                for (String cap : new String[] {"wing_cap", "metal_cap", "vanish_cap"}) {
                    Retro64Plus.LOGGER.info("Dev automario [caps recipe {}]: {}", cap, server.getRecipeManager()
                            .byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("retro64plus", cap))
                            .isPresent() ? "loaded" : "MISSING");
                }
            }
        } else if (t == 40) {
            Retro64PlusConfig.client().character = "mario";
            LocalMario.toggle();
            active = true;
            capsLog(mc, "on");
        } else if (t == 60) {
            capsLog(mc, "husk should be after Mario");
        } else if (t == 65) {
            useCap(mc, 0);
        } else if (t == 75) {
            capsLog(mc, "vanish on, husk should ignore Mario");
            screenshot("retro64plus_m2_vanish");
        } else if (t == 80) {
            forward = 1;
        } else if (t > 80 && t < 120 && t % 8 == 0) {
            capsLog(mc, "walking at the glass");
        } else if (t == 120) {
            forward = 0;
            capsLog(mc, "past the glass wall (it's at z+5)");
        } else if (t == 125) {
            useCap(mc, 1);
        } else if (t == 128) {
            useCap(mc, 0); // recharging: must refuse
        } else if (t == 132) {
            capsLog(mc, "metal + vanish");
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
        } else if (t == 137) {
            screenshot("retro64plus_m2_metal");
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        } else if (t == 140) {
            command(mc, "tp @s %.1f %d %.1f 0 0", x + 12.5, y - 1, z + 2.5);
        } else if (t > 140 && t <= 165 && t % 5 == 0) {
            capsLog(mc, "metal in the pool");
        } else if (t == 166) {
            command(mc, "damage @s 6 minecraft:mob_attack");
        } else if (t == 172) {
            capsLog(mc, "metal after a hit");
        } else if (t == 175) {
            command(mc, "tp @s %d %d %d 0 0", x, y, z + 8);
        } else if (t == 185) {
            useCap(mc, 2);
            forward = 1;
        } else if (t > 185 && t < 265) {
            // Triple jump bot: A on the first tick, then again each time Mario lands from a jump.
            int action = LocalMario.active() != null ? LocalMario.active().mario().state.action : 0;
            jump = t == 187 || action == com.nanobuntv.retro64plus.sm64.Actions.ACT_JUMP_LAND
                    || action == com.nanobuntv.retro64plus.sm64.Actions.ACT_DOUBLE_JUMP_LAND;
            if (action == com.nanobuntv.retro64plus.sm64.Actions.ACT_FLYING && !sawFlying) {
                sawFlying = true;
                capsLog(mc, "FLYING");
            }
            if (t % 10 == 0) capsLog(mc, "wing");
            if (t == 240) screenshot("retro64plus_m2_wing");
        } else if (t == 265) {
            jump = false;
            forward = 0;
        } else if (t >= 300 && t <= 560 && t % 40 == 0) {
            capsLog(mc, "waiting for the timers");
        } else if (t == 565) {
            capsLog(mc, "metal and vanish over");
            var stack = mc.player.getInventory().getItem(0);
            var charge = stack.get(com.nanobuntv.retro64plus.caps.Retro64PlusItems.CAP_CHARGE.get());
            Retro64Plus.LOGGER.info("Dev automario [caps recharge]: vanish cap {}, bar visible {}, width {}",
                    charge, stack.isBarVisible(), stack.getBarWidth());
            for (int i = 0; i < 25; i++) command(mc, "loot give @s loot minecraft:chests/end_city_treasure");
        } else if (t == 580) {
            int wings = 0;
            for (var stack : mc.player.getInventory().items) {
                if (stack.getItem() == com.nanobuntv.retro64plus.caps.Retro64PlusItems.CAPS
                        .get(com.nanobuntv.retro64plus.caps.MarioCap.WING).get()) wings += stack.getCount();
            }
            Retro64Plus.LOGGER.info("Dev automario [caps loot]: {} wing caps in 25 end city chests (1 bought in hotbar)",
                    wings);
            command(mc, "clear @s");
            active = false;
            LocalMario.toggle();
            Retro64Plus.LOGGER.info("Dev automario: done");
            enabled = false;
            mc.stop();
        }
    }

    private static boolean sawFlying;

    /**
     * M2 checkpoint 3: ground pounds onto a floating glass block (must break, Mario falls through), a floating leaves
     * block, a poppy on grass (the poppy breaks, the grass stays), a floor button and a floor lever (both pressed), stone
     * among three husks (the two within 2.5 blocks are hurt and pushed, the far one not), and glass in adventure mode
     * (must stay). Block states and husk health are logged.
     */
    private static void pound(Minecraft mc) {
        ticks++;
        int t = ticks;
        if (t == 5) {
            origin = mc.player.blockPosition();
            int x = origin.getX(), y = origin.getY(), z = origin.getZ();
            command(mc, "time set day");
            command(mc, "gamemode survival");
            command(mc, "fill %d %d %d %d %d %d stone", x - 6, y - 1, z - 6, x + 16, y - 1, z + 16);
            command(mc, "fill %d %d %d %d %d %d air", x - 6, y, z - 6, x + 16, y + 12, z + 16);
            command(mc, "kill @e[type=husk,distance=..40]");
            command(mc, "kill @e[type=item,distance=..40]");
            command(mc, "setblock %d %d %d glass", x + 6, y + 2, z);
            command(mc, "setblock %d %d %d oak_leaves[persistent=true]", x + 12, y + 2, z + 4);
            command(mc, "setblock %d %d %d grass_block", x + 12, y - 1, z);
            command(mc, "setblock %d %d %d poppy", x + 12, y, z);
            command(mc, "setblock %d %d %d stone_button[face=floor]", x + 8, y, z + 8);
            command(mc, "setblock %d %d %d lever[face=floor]", x + 12, y, z + 8);
            command(mc, "setblock %d %d %d glass", x + 3, y + 2, z + 12);
            command(mc, "summon husk %.1f %d %.1f {NoAI:1b,PersistenceRequired:1b}", x + 2.0, y, z + 8.5);
            command(mc, "summon husk %.1f %d %.1f {NoAI:1b,PersistenceRequired:1b}", x + 0.5, y, z + 10.7);
            command(mc, "summon husk %.1f %d %.1f {NoAI:1b,PersistenceRequired:1b}", x + 4.5, y, z + 8.5);
            command(mc, "tp @s %d %d %d 0 0", x, y, z);
        } else if (t == 40) {
            Retro64PlusConfig.client().character = "mario";
            LocalMario.toggle();
            active = true;
            poundLog(mc, "on");
        }
        int[][] spots = { // x, top of what he lands on, z (relative), each pound 40 ticks from t = 60
                {6, 3, 0}, {12, 3, 4}, {12, 0, 0}, {8, 0, 8}, {12, 0, 8}, {0, 0, 8}, {3, 3, 12}};
        String[] names = {"glass", "leaves", "poppy", "button", "lever", "husks", "adventure glass"};
        int step = (t - 60) / 40, at = (t - 60) % 40;
        if (t >= 60 && step < spots.length) {
            int[] s = spots[step];
            if (at == 0) {
                if (step == 6) command(mc, "gamemode adventure");
                if (LocalMario.active() != null) {
                    // Teleported mid-landing he'd keep the pound's downward speed and land before the Z press.
                    LocalMario.active().mario().setVelocity(net.minecraft.world.phys.Vec3.ZERO);
                    LocalMario.active().mario().setAction(com.nanobuntv.retro64plus.sm64.Actions.ACT_IDLE);
                }
                command(mc, "tp @s %.1f %d %.1f 0 0", origin.getX() + s[0] + 0.5, origin.getY() + s[1] + 4,
                        origin.getZ() + s[2] + 0.5);
            } else if (at == 6) {
                crouch = true; // Z in the air: ground pound
            } else if (at == 10) {
                crouch = false;
            } else if (at == 14 && step == 0) {
                screenshot("retro64plus_m2_pound");
            } else if (at == 39) {
                poundLog(mc, "after " + names[step]);
            }
        } else if (t == 60 + 40 * spots.length) {
            command(mc, "gamemode survival");
            active = false;
            LocalMario.toggle();
            Retro64Plus.LOGGER.info("Dev automario: done");
            enabled = false;
            mc.stop();
        }
    }

    /**
     * M3 checkpoint 1: punches a 1 HP husk to death (1–3 coins), breaks three leaves blocks as Mario with the block
     * chance at 1 (3 coins), opens a dungeon chest (5–15 coins burst out), then, hurt to 10 HP, walks onto every coin:
     * the count must match and the coins heal him. Screenshot of the counter; then dies and must keep half, with the
     * other half lying where he died.
     */
    private static void coins(Minecraft mc) {
        ticks++;
        int t = ticks;
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        if (t == 5) {
            origin = mc.player.blockPosition();
            int x = origin.getX(), y = origin.getY(), z = origin.getZ();
            command(mc, "time set day");
            command(mc, "difficulty normal");
            command(mc, "gamemode survival");
            command(mc, "gamerule keepInventory false");
            command(mc, "effect clear @s");
            command(mc, "fill %d %d %d %d %d %d stone", x - 6, y - 1, z - 6, x + 16, y - 1, z + 16);
            command(mc, "fill %d %d %d %d %d %d air", x - 6, y, z - 6, x + 16, y + 12, z + 16);
            command(mc, "kill @e[type=husk,distance=..40]");
            command(mc, "kill @e[type=retro64plus:coin,distance=..60]");
            command(mc, "summon husk %d %d %.1f {NoAI:1b,PersistenceRequired:1b,Health:1f}", x, y, z + 1.6);
            command(mc, "fill %d %d %d %d %d %d oak_leaves[persistent=true]", x + 6, y, z, x + 6, y, z + 2);
            command(mc, "setblock %d %d %d chest[facing=west]{LootTable:\"minecraft:chests/simple_dungeon\"}",
                    x + 10, y, z);
            command(mc, "tp @s %d %d %d 0 0", x, y, z);
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                if (player != null) com.nanobuntv.retro64plus.server.ServerCoins.add(player,
                        -com.nanobuntv.retro64plus.server.ServerCoins.coins(player));
            });
            com.nanobuntv.retro64plus.config.Retro64PlusConfig.server().blockCoinChance = 1.0;
        } else if (t == 40) {
            Retro64PlusConfig.client().character = "mario";
            LocalMario.toggle();
            active = true;
            coinLog(mc, server, "on");
        } else if (t >= 50 && t < 74) {
            punch = (t - 50) % 8 < 2; // B a few times, as checkpoint 4's run
        } else if (t == 74) {
            punch = false;
        } else if (t == 80) {
            coinLog(mc, server, "after the husk");
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                if (player == null) return;
                for (int dz = 0; dz < 3; dz++) {
                    player.gameMode.destroyBlock(origin.offset(6, 0, dz));
                }
            });
        } else if (t == 100) {
            coinLog(mc, server, "after the leaves");
            command(mc, "tp @s %d %d %d -90 0", origin.getX() + 8, origin.getY(), origin.getZ());
        } else if (t == 110) {
            var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(
                    origin.offset(10, 0, 0)), net.minecraft.core.Direction.WEST, origin.offset(10, 0, 0), false);
            mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        } else if (t == 125) {
            Retro64Plus.LOGGER.info("Dev automario [coins chest]: screen {}", mc.screen == null ? "none"
                    : mc.screen.getClass().getSimpleName());
            mc.player.closeContainer();
        } else if (t == 128) {
            screenshot("retro64plus_m3_coin_world");
        } else if (t == 145) {
            coinLog(mc, server, "after the chest");
            command(mc, "damage @s 10 minecraft:generic");
        } else if (t >= 160 && t < 400 && t % 4 == 0) {
            // Onto the nearest coin still lying around.
            CoinEntityHolder.nearest(mc).ifPresent(coin -> command(mc, "tp @s %.2f %.2f %.2f", coin.getX(),
                    coin.getY(), coin.getZ()));
            if (t == 200) screenshot("retro64plus_m3_coins");
            if (t % 40 == 0) coinLog(mc, server, "collecting");
        } else if (t == 400) {
            coinLog(mc, server, "collected");
            command(mc, "tp @s %d %d %d 0 0", origin.getX() + 2, origin.getY(), origin.getZ() + 8);
        } else if (t == 410) {
            command(mc, "kill @s");
        } else if (t == 480) {
            coinLog(mc, server, "dead");
            mc.player.respawn();
        } else if (t == 520) {
            coinLog(mc, server, "respawned");
            com.nanobuntv.retro64plus.config.Retro64PlusConfig.loadServer();
            active = false;
            if (LocalMario.isActive()) LocalMario.toggle();
            Retro64Plus.LOGGER.info("Dev automario: done");
            enabled = false;
            mc.stop();
        }
    }

    /**
     * M3 checkpoint 2, with the coin star's threshold at 10: twelve coins collected (the Overworld's coin star appears
     * above Mario), a 1 HP Elder Guardian killed by the player (its star on the ground), a challenge advancement granted
     * (a star above), each star collected by walking into it (the star dance must play and end); then the count, the
     * Wing cap's longer timer and shorter recharge with 3 stars, and twelve more coins after a death (no second coin
     * star). Screenshots of a waiting star and of the dance.
     */
    /**
     * M4 checkpoint 2: punches a still Goomba (knocked away and gone), stomps another (squashed), picks up a Bob-omb
     * with a punch and throws it with B (it blows up where it lands), lets a live Bob-omb chase him and go off, pops one
     * with an arrow's damage (no blast), lets a live Goomba charge him; then waits at night for natural spawns. Logs
     * each step ({@code Dev automario [enemies ...]}) with screenshots ({@code retro64plus_m4_*.png}).
     */
    private static void enemies(Minecraft mc) {
        if (ticks < 5 && !mc.player.isAlive()) {
            mc.player.respawn(); // a world saved with the player dead (an earlier run)
            return;
        }
        ticks++;
        int t = ticks;
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        int x = origin == null ? 0 : origin.getX(), y = origin == null ? 0 : origin.getY(),
                z = origin == null ? 0 : origin.getZ();
        LocalMario local = LocalMario.active();
        if (t == 5) {
            origin = mc.player.blockPosition();
            x = origin.getX();
            y = origin.getY();
            z = origin.getZ();
            command(mc, "time set day");
            command(mc, "difficulty normal");
            command(mc, "gamemode survival");
            command(mc, "gamerule doMobSpawning false");
            command(mc, "gamerule mobGriefing true");
            command(mc, "effect clear @s");
            command(mc, "fill %d %d %d %d %d %d stone", x - 8, y - 2, z - 8, x + 16, y - 1, z + 16);
            command(mc, "fill %d %d %d %d %d %d air", x - 8, y, z - 8, x + 16, y + 12, z + 16);
            command(mc, "kill @e[type=!player,distance=..200]");
            command(mc, "tp @s %d %d %d 0 0", x, y, z);
            command(mc, "summon retro64plus:goomba %.1f %d %.1f {NoAI:1b,PersistenceRequired:1b}", x + 0.5, y, z + 2.0);
            command(mc, "summon retro64plus:goomba %.1f %d %.1f {NoAI:1b,PersistenceRequired:1b}", x + 8.5, y, z + 0.5);
            server.execute(() -> com.nanobuntv.retro64plus.server.PlayerProgress.get(server).reset(mc.player.getUUID()));
        } else if (t == 40) {
            Retro64PlusConfig.client().character = "mario";
            LocalMario.toggle();
            active = true;
            enemyLog(mc, server, "on");
        } else if (t == 48) {
            screenshot("retro64plus_m4_goomba");
        } else if (t >= 50 && t < 74) {
            punch = (t - 50) % 8 < 2;
        } else if (t == 74) {
            punch = false;
        } else if (t == 90) {
            enemyLog(mc, server, "punched the near Goomba");
            stopMario(local);
            command(mc, "tp @s %.1f %d %.1f 0 0", x + 8.5, y + 4, z + 0.5);
        } else if (t == 100) {
            screenshot("retro64plus_m4_squashed");
        } else if (t == 120) {
            enemyLog(mc, server, "stomped the far Goomba");
            command(mc, "kill @e[type=retro64plus:coin,distance=..80]");
            stopMario(local);
            command(mc, "tp @s %d %d %d 0 0", x, y, z);
            command(mc, "summon retro64plus:bobomb %.1f %d %.1f {NoAI:1b,PersistenceRequired:1b}", x + 0.5, y, z + 1.6);
        } else if (t == 130) {
            if (local != null) local.mario().setAngle(0);
        } else if (t >= 140 && t < 144) {
            punch = true;
        } else if (t == 144) {
            punch = false;
        } else if (t == 175) {
            enemyLog(mc, server, "punched the Bob-omb");
        } else if (t == 178) {
            screenshot("retro64plus_m4_carry");
        } else if (t >= 180 && t < 183) {
            punch = true; // B again: throw
        } else if (t == 183) {
            punch = false;
        } else if (t == 190) {
            enemyLog(mc, server, "threw it");
        } else if (t == 200) {
            screenshot("retro64plus_m4_throw");
        } else if (t == 240) {
            enemyLog(mc, server, "after the throw");
            command(mc, "kill @e[type=retro64plus:coin,distance=..80]");
            command(mc, "fill %d %d %d %d %d %d stone", x - 8, y - 2, z - 8, x + 16, y - 1, z + 16);
            stopMario(local);
            command(mc, "tp @s %d %d %d 0 0", x, y, z);
            command(mc, "summon retro64plus:bobomb %.1f %d %.1f {PersistenceRequired:1b}", x + 0.5, y, z + 8.5);
        } else if (t > 240 && t < 400 && t % 20 == 0) {
            enemyLog(mc, server, "a live Bob-omb");
            if (t == 300) screenshot("retro64plus_m4_fuse");
        } else if (t == 400) {
            command(mc, "kill @e[type=retro64plus:coin,distance=..80]");
            command(mc, "fill %d %d %d %d %d %d stone", x - 8, y - 2, z - 8, x + 16, y - 1, z + 16);
            command(mc, "effect give @s instant_health 1 5");
            stopMario(local);
            command(mc, "tp @s %d %d %d 0 0", x, y, z);
            command(mc, "summon retro64plus:bobomb %.1f %d %.1f {NoAI:1b,PersistenceRequired:1b}", x + 6.5, y, z + 6.5);
        } else if (t == 405) {
            command(mc, "damage @e[type=retro64plus:bobomb,limit=1,sort=nearest] 20 minecraft:arrow");
        } else if (t == 430) {
            enemyLog(mc, server, "popped one with an arrow's damage");
            command(mc, "summon retro64plus:goomba %.1f %d %.1f {PersistenceRequired:1b}", x + 0.5, y, z + 7.5);
        } else if (t > 430 && t < 530 && t % 20 == 0) {
            enemyLog(mc, server, "a live Goomba");
            if (t == 440) screenshot("retro64plus_m4_charge");
        } else if (t == 540) {
            active = false;
            LocalMario.toggle();
            command(mc, "kill @e[type=!player,distance=..80]");
            command(mc, "gamemode creative"); // the night's mobs mustn't kill the player before the counts
            command(mc, "time set 18000");
            command(mc, "gamerule doMobSpawning true");
        } else if (t > 540 && t <= 940 && t % 100 == 40) {
            enemyLog(mc, server, "night, natural spawns");
        } else if (t == 945) {
            command(mc, "time set day");
            Retro64Plus.LOGGER.info("Dev automario: done");
            enabled = false;
            mc.stop();
        }
    }

    /**
     * M4 checkpoint 3: punches a still Koopa out of its shell, stomps the bare Koopa, walks Mario into the shell (the
     * ride, with the music on), rams a still Goomba and bonks a wall (the ride ends, the shell is gone); a live Koopa
     * runs from him; a bare Koopa runs to a shell and dives in; the shell item is put down and picked up again. Logs
     * {@code Dev automario [koopa ...]}, screenshots {@code retro64plus_m4_koopa*.png}.
     */
    private static void koopa(Minecraft mc) {
        if (ticks < 5 && !mc.player.isAlive()) {
            mc.player.respawn(); // a world saved with the player dead (an earlier run)
            return;
        }
        mc.gui.getChat().clearMessages(false);
        ticks++;
        int t = ticks;
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        int x = origin == null ? 0 : origin.getX(), y = origin == null ? 0 : origin.getY(),
                z = origin == null ? 0 : origin.getZ();
        LocalMario local = LocalMario.active();
        if (t == 5) {
            origin = mc.player.blockPosition();
            x = origin.getX();
            y = origin.getY();
            z = origin.getZ();
            command(mc, "time set day");
            command(mc, "difficulty normal");
            command(mc, "gamemode survival");
            command(mc, "gamerule doMobSpawning false");
            command(mc, "effect clear @s");
            command(mc, "effect give @s instant_health 1 5");
            command(mc, "gamerule sendCommandFeedback false");
            command(mc, "fill %d %d %d %d %d %d stone", x - 8, y - 2, z - 8, x + 16, y - 1, z + 20);
            command(mc, "fill %d %d %d %d %d %d air", x - 8, y, z - 8, x + 16, y + 12, z + 20);
            command(mc, "kill @e[type=!player,distance=..80]");
            command(mc, "tp @s %d %d %d 0 0", x, y, z);
            command(mc, "summon retro64plus:koopa %.1f %d %.1f {NoAI:1b,PersistenceRequired:1b}", x + 0.5, y, z + 1.5);
        } else if (t == 40) {
            Retro64PlusConfig.client().character = "mario";
            LocalMario.toggle();
            active = true;
            koopaLog(mc, server, "on");
        } else if (t == 46) {
            koopaShot("retro64plus_m4_koopa");
        } else if (t >= 62 && t < 65) {
            punch = true; // once he's up from appearing
        } else if (t == 65) {
            punch = false;
        } else if (t == 80) {
            koopaLog(mc, server, "punched the Koopa");
            koopaShot("retro64plus_m4_koopa_bare");
        } else if (t == 86) {
            // The shell lies under the frozen Koopa (NoAI: it can't slide away); landing on it would be a ride.
            command(mc, "tp @e[type=retro64plus:koopa_shell] %.1f %d %.1f", x + 4.5, y, z + 3.5);
        } else if (t == 90) {
            stopMario(local);
            for (var e : server.overworld().getAllEntities()) {
                if (e instanceof com.nanobuntv.retro64plus.enemies.KoopaEntity k) {
                    command(mc, "tp @s %.2f %.2f %.2f 0 0", k.getX(), k.getY() + 4, k.getZ());
                }
            }
        } else if (t == 120) {
            koopaLog(mc, server, "stomped the bare Koopa");
            command(mc, "kill @e[type=retro64plus:coin,distance=..80]");
            stopMario(local);
            command(mc, "tp @s %d %d %d 0 0", x, y, z);
            command(mc, "tp @e[type=retro64plus:koopa_shell] %.1f %d %.1f", x + 0.5, y, z + 3.5);
            command(mc, "summon retro64plus:goomba %.1f %d %.1f {NoAI:1b,PersistenceRequired:1b}", x + 0.5, y, z + 10.5);
            command(mc, "fill %d %d %d %d %d %d stone", x - 8, y, z + 16, x + 16, y + 3, z + 16);
            musicBefore = Retro64PlusConfig.client().music;
            Retro64PlusConfig.client().music = com.nanobuntv.retro64plus.config.MarioMusic.BOB_OMB_BATTLEFIELD;
        } else if (t == 130) {
            if (local != null) local.mario().setAngle(0);
            forward = 1;
        } else if (t > 130 && t < 200 && t % 5 == 0) {
            koopaLog(mc, server, "riding");
            if (t == 155) koopaShot("retro64plus_m4_koopa_ride2");
            if (t == 150) {
                koopaShot("retro64plus_m4_koopa_ride");
                Retro64Plus.LOGGER.info("Dev automario [koopa sound]: " + MarioAudio.debugState());
            }
        } else if (t == 200) {
            forward = 0;
            koopaLog(mc, server, "after the wall");
            Retro64Plus.LOGGER.info("Dev automario [koopa sound]: " + MarioAudio.debugState());
            Retro64PlusConfig.client().music = musicBefore;
            command(mc, "kill @e[type=!player,distance=..80]");
            stopMario(local);
            command(mc, "tp @s %d %d %d 0 0", x, y, z);
            command(mc, "summon retro64plus:koopa %.1f %d %.1f {PersistenceRequired:1b}", x + 0.5, y, z + 2.0);
        } else if (t > 200 && t < 280 && t % 10 == 0) {
            koopaLog(mc, server, "a live Koopa near Mario");
            if (t == 220) koopaShot("retro64plus_m4_koopa_flee");
        } else if (t == 280) {
            command(mc, "kill @e[type=!player,distance=..80]");
            stopMario(local);
            command(mc, "tp @s %d %d %d 0 0", x + 12, y, z - 6);
            command(mc, "summon retro64plus:koopa %.1f %d %.1f {PersistenceRequired:1b,Shelled:0b}", x + 0.5, y, z + 6.5);
            command(mc, "summon retro64plus:koopa_shell %.1f %d %.1f", x + 5.5, y, z + 6.5);
        } else if (t > 280 && t < 400 && t % 10 == 0) {
            koopaLog(mc, server, "a bare Koopa and a shell");
            if (t == 300) koopaShot("retro64plus_m4_koopa_dive");
        } else if (t == 400) {
            command(mc, "kill @e[type=!player,distance=..80]");
            active = false;
            LocalMario.toggle();
            command(mc, "tp @s %d %d %d 0 0", x, y, z);
            command(mc, "clear @s retro64plus:koopa_shell");
            command(mc, "item replace entity @s hotbar.0 with retro64plus:koopa_shell 2");
        } else if (t == 410) {
            mc.player.getInventory().selected = 0;
            var hit = new net.minecraft.world.phys.BlockHitResult(
                    new net.minecraft.world.phys.Vec3(x + 0.5, y, z + 3.5), net.minecraft.core.Direction.UP,
                    new BlockPos(x, y - 1, z + 3), false);
            mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        } else if (t == 420) {
            koopaLog(mc, server, "put a shell down");
            koopaShot("retro64plus_m4_koopa_placed");
            for (var e : mc.level.entitiesForRendering()) {
                if (e instanceof com.nanobuntv.retro64plus.enemies.KoopaShellEntity) {
                    mc.gameMode.interact(mc.player, e, net.minecraft.world.InteractionHand.MAIN_HAND);
                    break;
                }
            }
        } else if (t == 430) {
            koopaLog(mc, server, "picked it up");
            command(mc, "gamerule sendCommandFeedback true");
            Retro64Plus.LOGGER.info("Dev automario: done");
            enabled = false;
            mc.stop();
        }
    }

    private static com.nanobuntv.retro64plus.config.MarioMusic musicBefore;

    /** A screenshot without the chat (cleared every tick of this run: commands that found nothing still print). */
    private static void koopaShot(String name) {
        screenshot(name);
    }

    private static void koopaLog(Minecraft mc, net.minecraft.server.MinecraftServer server, String step) {
        var level = server.overworld();
        StringBuilder out = new StringBuilder();
        int koopas = 0, shells = 0, goombas = 0, coins = 0;
        for (var e : level.getAllEntities()) {
            if (e.distanceToSqr(mc.player.position()) > 128 * 128) continue;
            if (e instanceof com.nanobuntv.retro64plus.enemies.KoopaEntity k) {
                koopas++;
                out.append(String.format(Locale.ROOT, " koopa[%.1f HP%s state %d at (%.1f, %.1f, %.1f), %.1f from the player]",
                        k.getHealth(), k.shelled() ? ", shelled" : ", bare", k.state(), k.getX() - origin.getX(),
                        k.getY() - origin.getY(), k.getZ() - origin.getZ(), k.distanceTo(mc.player)));
            } else if (e instanceof com.nanobuntv.retro64plus.enemies.KoopaShellEntity sh) {
                shells++;
                out.append(String.format(Locale.ROOT, " shell[at (%.1f, %.1f, %.1f)]", sh.getX() - origin.getX(),
                        sh.getY() - origin.getY(), sh.getZ() - origin.getZ()));
            } else if (e instanceof com.nanobuntv.retro64plus.enemies.GoombaEntity) {
                goombas++;
            } else if (e instanceof com.nanobuntv.retro64plus.coins.CoinEntity coin) {
                coins += coin.value();
            }
        }
        LocalMario local = LocalMario.active();
        net.minecraft.world.phys.Vec3 p = mc.player.position();
        Retro64Plus.LOGGER.info(String.format(Locale.ROOT,
                "Dev automario [koopa %s]: Mario %s action 0x%x at (%.1f, %.1f, %.1f), health %.1f, rides %d, riding %s, shell items %d; "
                        + "koopas %d, shells %d, goombas %d, coins lying %d;%s",
                step, local != null ? "on" : "off", local == null ? 0 : local.mario().state.action,
                p.x - origin.getX(), p.y - origin.getY(), p.z - origin.getZ(), mc.player.getHealth(),
                local == null ? 0 : local.shellRides,
                LocalMario.ridingShell(), mc.player.getInventory().countItem(
                        com.nanobuntv.retro64plus.enemies.Enemies.KOOPA_SHELL_ITEM.get()),
                koopas, shells, goombas, coins, out));
    }


    /**
     * M4 checkpoint 4: a Chain Chomp on its post lunges at Mario when he comes within reach (his health is logged); a
     * frozen one's post takes three ground pounds and it goes free (5 coins, then gone); another is killed with weapon
     * damage (coins, chains, nuggets); our yard is placed with {@code /place feature} (post, chest three blocks off,
     * Chomp); then up to ten villages not yet generated are visited and the Chomps placed in them counted. Logs
     * {@code Dev automario [chomp ...]}, screenshots {@code retro64plus_m4_chomp*.png}.
     */
    private static void chomp(Minecraft mc) {
        if (ticks < 5 && !mc.player.isAlive()) {
            mc.player.respawn();
            return;
        }
        mc.gui.getChat().clearMessages(false);
        ticks++;
        int t = ticks;
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        int x = origin == null ? 0 : origin.getX(), y = origin == null ? 0 : origin.getY(),
                z = origin == null ? 0 : origin.getZ();
        LocalMario local = LocalMario.active();
        if (t == 5) {
            origin = mc.player.blockPosition();
            x = origin.getX();
            y = origin.getY();
            z = origin.getZ();
            command(mc, "gamerule sendCommandFeedback false");
            command(mc, "time set day");
            command(mc, "difficulty normal");
            command(mc, "gamemode survival");
            command(mc, "gamerule doMobSpawning false");
            command(mc, "effect clear @s");
            command(mc, "effect give @s instant_health 1 5");
            command(mc, "fill %d %d %d %d %d %d stone", x - 6, y - 2, z - 6, x + 30, y - 1, z + 22);
            command(mc, "fill %d %d %d %d %d %d air", x - 6, y, z - 6, x + 30, y + 12, z + 22);
            command(mc, "kill @e[type=!player,distance=..80]");
            command(mc, "tp @s %d %d %d -90 0", x, y, z);
            command(mc, "setblock %d %d %d retro64plus:chomp_post", x + 12, y, z + 8);
            command(mc, "summon retro64plus:chain_chomp %d %d %d {Post:[I;%d,%d,%d]}", x + 14, y, z + 8, x + 12, y, z + 8);
            // A chest in the lunge's path too: the Chomp passes through it and its post.
            command(mc, "setblock %d %d %d chest", x + 9, y, z + 8);
        } else if (t == 40) {
            Retro64PlusConfig.client().character = "mario";
            LocalMario.toggle();
            active = true;
            chompLog(mc, server, "on, out of reach");
        } else if (t == 50) {
            screenshot("retro64plus_m4_chomp");
        } else if (t == 60) {
            stopMario(local);
            command(mc, "tp @s %d %d %d -90 0", x + 6, y, z + 8);
        } else if (t > 60 && t < 140 && t % 5 == 0) {
            chompLog(mc, server, "Mario in reach");
            if (t == 80) screenshot("retro64plus_m4_chomp_lunge");
            // One bite is enough (7.5 of his 20): out of reach after it, before the next.
            if (mc.player.getHealth() < 19 && t < 135) {
                stopMario(local);
                command(mc, "tp @s %d %d %d -90 0", x, y, z);
                ticks = 135;
            }
        } else if (t == 145) {
            command(mc, "setblock %d %d %d air", x + 9, y, z + 8);
            command(mc, "kill @e[type=retro64plus:chain_chomp]");
            command(mc, "kill @e[type=retro64plus:coin,distance=..80]");
            command(mc, "effect give @s instant_health 1 5");
            command(mc, "setblock %d %d %d retro64plus:chomp_post", x + 12, y, z + 8);
            command(mc, "summon retro64plus:chain_chomp %d %d %d {NoAI:1b,Post:[I;%d,%d,%d]}", x + 15, y, z + 8, x + 12, y, z + 8);
        } else if (t >= 160 && t < 280 && (t - 160) % 40 == 0) {
            stopMario(local);
            command(mc, "tp @s %.1f %d %.1f -90 0", x + 12.5, y + 5, z + 8.5); // above the post: pound onto it
        } else if (t >= 160 && t < 280 && (t - 160) % 40 == 8) {
            crouch = true; // Z in the air: ground pound
        } else if (t >= 160 && t < 280 && (t - 160) % 40 == 12) {
            crouch = false;
        } else if (t >= 160 && t < 290 && (t - 160) % 40 == 35) {
            chompLog(mc, server, "after a pound");
            if (t == 235) screenshot("retro64plus_m4_chomp_post");
        } else if (t == 300 || t == 330) {
            chompLog(mc, server, "freed");
            // Pounded all the way in, the post goes a few seconds later.
            Retro64Plus.LOGGER.info("Dev automario [chomp post]: {}", server.overworld().getBlockState(
                    new BlockPos(x + 12, y, z + 8)).getBlock() instanceof com.nanobuntv.retro64plus.enemies.ChompPostBlock
                    ? "still there" : "gone");
        } else if (t == 340) {
            command(mc, "kill @e[type=retro64plus:chain_chomp]");
            command(mc, "kill @e[type=retro64plus:coin,distance=..80]");
            command(mc, "kill @e[type=item,distance=..80]");
            command(mc, "fill %d %d %d %d %d %d air", x + 12, y, z + 8, x + 12, y, z + 8);
            command(mc, "setblock %d %d %d retro64plus:chomp_post", x + 12, y, z + 16);
            command(mc, "summon retro64plus:chain_chomp %d %d %d {NoAI:1b,Post:[I;%d,%d,%d]}", x + 14, y, z + 16, x + 12, y, z + 16);
        } else if (t == 345) {
            command(mc, "damage @e[type=retro64plus:chain_chomp,limit=1,sort=nearest] 30 minecraft:player_attack by @s");
        } else if (t == 350) {
            chompLog(mc, server, "hit once with a weapon");
            command(mc, "damage @e[type=retro64plus:chain_chomp,limit=1,sort=nearest] 100 minecraft:player_attack by @s");
        } else if (t == 380) {
            chompLog(mc, server, "killed");
            active = false;
            LocalMario.toggle();
            command(mc, "kill @e[type=!player,distance=..80]");
            command(mc, "fill %d %d %d %d %d %d grass_block", x + 18, y - 1, z - 6, x + 30, y - 1, z + 6);
            command(mc, "place feature retro64plus:chomp_yard %d %d %d", x + 24, y, z);
        } else if (t == 400) {
            chompLog(mc, server, "placed our yard");
            command(mc, "tp @s %d %d %d 90 20", x + 32, y + 3, z);
        } else if (t == 410) {
            screenshot("retro64plus_m4_chomp_yard");
        } else if (t >= 420) {
            villages(mc, server, t - 420);
        }
    }

    /** Villages to visit: the trigger file "m4chomp" visits ten, "m4chomp quick" none. */
    private static int villagesSeen, villageChomps;
    private static final int VILLAGES_FULL = 10;
    private static int villagesToVisit = VILLAGES_FULL;
    private static BlockPos villageAt;

    /** Visits villages not generated yet, one every 200 ticks, and counts the Chain Chomps placed in them. */
    private static void villages(Minecraft mc, net.minecraft.server.MinecraftServer server, int t) {
        var level = server.overworld();
        int step = t % 400;
        if (step == 0) {
            if (villagesSeen >= villagesToVisit) {
                Retro64Plus.LOGGER.info("Dev automario [chomp villages]: {} of {} villages have a Chomp", villageChomps,
                        villagesSeen);
                command(mc, "gamerule sendCommandFeedback true");
                Retro64Plus.LOGGER.info("Dev automario: done");
                enabled = false;
                mc.stop();
                return;
            }
            BlockPos from = villageAt != null ? villageAt : mc.player.blockPosition();
            // On the server thread: the search loads chunks, which only that thread may do.
            villageAt = server.submit(() -> level.findNearestMapStructure(net.minecraft.tags.StructureTags.VILLAGE,
                    from, 100, true)).join();
            if (villageAt == null) {
                Retro64Plus.LOGGER.info("Dev automario [chomp villages]: no more villages found");
                villagesSeen = 99;
                return;
            }
            command(mc, "gamemode spectator");
            command(mc, "tp @s %d 200 %d", villageAt.getX(), villageAt.getZ());
        } else if (step == 390 && villageAt != null) {
            villagesSeen++;
            int chomps = 0;
            StringBuilder out = new StringBuilder();
            for (var c : level.getEntitiesOfClass(com.nanobuntv.retro64plus.enemies.ChainChompEntity.class,
                    new net.minecraft.world.phys.AABB(villageAt).inflate(96, 200, 96))) {
                chomps++;
                out.append(String.format(Locale.ROOT, " chomp at %s, post %s", c.blockPosition(), c.post().orElse(null)));
            }
            if (chomps > 0) {
                villageChomps++;
                screenshot("retro64plus_m4_chomp_village" + villagesSeen);
            }
            Retro64Plus.LOGGER.info("Dev automario [chomp village {} at {}]: {} Chomps;{}", villagesSeen, villageAt, chomps,
                    out);
        } else if (step == 380 && villageAt != null) {
            // Look at the first Chomp, if any, for the picture.
            for (var c : level.getEntitiesOfClass(com.nanobuntv.retro64plus.enemies.ChainChompEntity.class,
                    new net.minecraft.world.phys.AABB(villageAt).inflate(96, 200, 96))) {
                command(mc, "tp @s %.1f %.1f %.1f facing %.1f %.1f %.1f", c.getX() + 8, c.getY() + 5, c.getZ() + 8,
                        c.getX(), c.getY() + 1, c.getZ());
                break;
            }
        }
    }

    private static void chompLog(Minecraft mc, net.minecraft.server.MinecraftServer server, String step) {
        var level = server.overworld();
        StringBuilder out = new StringBuilder();
        int chomps = 0, coins = 0, chains = 0, nuggets = 0;
        for (var e : level.getAllEntities()) {
            if (e.distanceToSqr(mc.player.position()) > 128 * 128) continue;
            if (e instanceof com.nanobuntv.retro64plus.enemies.ChainChompEntity c) {
                chomps++;
                String post = c.post().map(p -> {
                    var state = level.getBlockState(p);
                    return state.getBlock() instanceof com.nanobuntv.retro64plus.enemies.ChompPostBlock
                            ? "pounds " + state.getValue(com.nanobuntv.retro64plus.enemies.ChompPostBlock.POUNDS)
                            : "no post block";
                }).orElse("none");
                out.append(String.format(Locale.ROOT, " chomp[%.1f HP, state %d, post %s, at (%.1f, %.1f, %.1f)]",
                        c.getHealth(), c.state(), post, c.getX() - origin.getX(), c.getY() - origin.getY(),
                        c.getZ() - origin.getZ()));
            } else if (e instanceof com.nanobuntv.retro64plus.coins.CoinEntity coin) {
                coins += coin.value();
            } else if (e instanceof net.minecraft.world.entity.item.ItemEntity item) {
                if (item.getItem().is(net.minecraft.world.item.Items.CHAIN)) chains += item.getItem().getCount();
                if (item.getItem().is(net.minecraft.world.item.Items.IRON_NUGGET)) nuggets += item.getItem().getCount();
            }
        }
        // Our yard: a chest near (with its loot table, before anyone opened it).
        for (var pos : BlockPos.betweenClosed(origin.offset(16, -2, -8), origin.offset(32, 3, 8))) {
            // From the chunk: the level hands block entities only to its own thread.
            if (level.getChunkAt(pos).getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
                out.append(String.format(Locale.ROOT, " chest at (%d, %d, %d) loot %s", pos.getX() - origin.getX(),
                        pos.getY() - origin.getY(), pos.getZ() - origin.getZ(), chest.getLootTable()));
            }
            if (level.getBlockState(pos).getBlock() instanceof com.nanobuntv.retro64plus.enemies.ChompPostBlock) {
                out.append(String.format(Locale.ROOT, " post at (%d, %d, %d)", pos.getX() - origin.getX(),
                        pos.getY() - origin.getY(), pos.getZ() - origin.getZ()));
            }
        }
        LocalMario local = LocalMario.active();
        Retro64Plus.LOGGER.info(String.format(Locale.ROOT,
                "Dev automario [chomp %s]: Mario %s action 0x%x, health %.1f, pounds %d; chomps %d, coins lying %d, "
                        + "chains %d, nuggets %d;%s",
                step, local != null ? "on" : "off", local == null ? 0 : local.mario().state.action,
                mc.player.getHealth(), local == null ? 0 : local.groundPounds, chomps, coins, chains, nuggets, out));
    }

    private static void stopMario(@org.jetbrains.annotations.Nullable LocalMario local) {
        if (local == null) return;
        local.mario().setVelocity(net.minecraft.world.phys.Vec3.ZERO);
        local.mario().setAction(com.nanobuntv.retro64plus.sm64.Actions.ACT_IDLE);
    }

    private static void enemyLog(Minecraft mc, net.minecraft.server.MinecraftServer server, String step) {
        var level = server.overworld();
        StringBuilder out = new StringBuilder();
        int goombas = 0, bobombs = 0, coins = 0, gunpowder = 0;
        for (var e : level.getAllEntities()) {
            if (e.distanceToSqr(mc.player.position()) > 128 * 128) continue;
            if (e instanceof com.nanobuntv.retro64plus.enemies.GoombaEntity g) {
                goombas++;
                if (out.length() < 600) out.append(String.format(Locale.ROOT, " goomba[%.1f HP%s%s at (%.1f, %.1f, %.1f)]",
                        g.getHealth(), g.squished() ? ", squashed" : "", g.getTarget() != null ? ", chasing" : "",
                        g.getX() - origin.getX(), g.getY() - origin.getY(), g.getZ() - origin.getZ()));
            } else if (e instanceof com.nanobuntv.retro64plus.enemies.BobombEntity b) {
                bobombs++;
                if (out.length() < 600) out.append(String.format(Locale.ROOT, " bobomb[%.1f HP%s%s%s at (%.1f, %.1f, %.1f)]",
                        b.getHealth(), b.fuseLit() ? ", lit" : "", b.held() ? ", held" : "", b.swell() > 0 ? ", swelling" : "",
                        b.getX() - origin.getX(), b.getY() - origin.getY(), b.getZ() - origin.getZ()));
            } else if (e instanceof com.nanobuntv.retro64plus.coins.CoinEntity coin && e.distanceTo(mc.player) < 24) {
                coins += coin.value();
            } else if (e instanceof net.minecraft.world.entity.item.ItemEntity item
                    && item.getItem().is(net.minecraft.world.item.Items.GUNPOWDER)) {
                gunpowder += item.getItem().getCount();
            }
        }
        int floorHoles = 0;
        for (int dx = -8; dx <= 16; dx++) {
            for (int dz = -8; dz <= 16; dz++) {
                if (level.getBlockState(origin.offset(dx, -1, dz)).isAir()) floorHoles++;
            }
        }
        LocalMario local = LocalMario.active();
        Retro64Plus.LOGGER.info(String.format(Locale.ROOT,
                "Dev automario [enemies %s]: Mario %s at (%.1f, %.1f, %.1f) action 0x%x, health %.1f, coins %d, grabs %d, throws %d, holding %d; "
                        + "goombas %d, bobombs %d, coins lying %d, gunpowder %d, floor holes %d;%s",
                step, local != null ? "on" : "off", mc.player.getX() - origin.getX(), mc.player.getY() - origin.getY(),
                mc.player.getZ() - origin.getZ(), local == null ? 0 : local.mario().state.action,
                mc.player.getHealth(), ClientCoins.coins(), local == null ? 0 : local.grabs, local == null ? 0 : local.throwsMade,
                local == null ? -1 : local.heldBobomb(), goombas, bobombs, coins, gunpowder, floorHoles, out));
    }

    /**
     * M4's ROM models: a Power Star in the world (SM64's 3D star, D79), seen from beside and below, then every model
     * in {@link ModelViewerScreen}; screenshots of each, then the game closes.
     */
    private static void models(Minecraft mc) {
        ticks++;
        int t = ticks;
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        if (t == 5) {
            origin = mc.player.blockPosition();
            command(mc, "time set day");
            command(mc, "gamemode creative");
            command(mc, "tp @s %d %d %d 0 0", origin.getX(), origin.getY(), origin.getZ());
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                com.nanobuntv.retro64plus.server.PlayerProgress.get(server).reset(mc.player.getUUID());
                com.nanobuntv.retro64plus.server.ServerStars.spawn(player, "dev/m4models", server.overworld(),
                        net.minecraft.world.phys.Vec3.atCenterOf(origin.offset(0, 1, 3)));
            });
        } else if (t == 40) {
            Retro64Plus.LOGGER.info("Dev automario [models]: star model {}",
                    RomModelRenderer.model(com.nanobuntv.retro64plus.sm64.RomModels.Kind.STAR) != null ? "from the ROM"
                            : "MISSING");
            screenshot("retro64plus_m4_star");
        } else if (t == 50) {
            command(mc, "tp @s %d %d %d -45 -5", origin.getX() - 2, origin.getY(), origin.getZ() + 1);
        } else if (t == 70) {
            screenshot("retro64plus_m4_star_side");
            mc.setScreen(new ModelViewerScreen());
        } else if (t == 110) {
            screenshot("retro64plus_m4_models");
        } else if (t == 140) {
            screenshot("retro64plus_m4_models_turned");
            mc.setScreen(null);
            Retro64Plus.LOGGER.info("Dev automario: done");
            enabled = false;
            mc.stop();
        }
    }

    private static void stars(Minecraft mc) {
        ticks++;
        int t = ticks;
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        int x = origin == null ? 0 : origin.getX(), y = origin == null ? 0 : origin.getY(),
                z = origin == null ? 0 : origin.getZ();
        if (t == 5) {
            origin = mc.player.blockPosition();
            x = origin.getX();
            y = origin.getY();
            z = origin.getZ();
            command(mc, "time set day");
            command(mc, "difficulty normal");
            command(mc, "gamemode survival");
            command(mc, "gamerule keepInventory false");
            command(mc, "effect clear @s");
            command(mc, "clear @s");
            command(mc, "fill %d %d %d %d %d %d stone", x - 6, y - 1, z - 6, x + 16, y - 1, z + 16);
            command(mc, "fill %d %d %d %d %d %d air", x - 6, y, z - 6, x + 16, y + 12, z + 16);
            command(mc, "kill @e[type=!player,distance=..60]");
            command(mc, "advancement revoke @s only minecraft:adventure/kill_all_mobs");
            command(mc, "tp @s %d %d %d 0 0", x, y, z);
            server.execute(() -> com.nanobuntv.retro64plus.server.PlayerProgress.get(server).reset(mc.player.getUUID()));
            com.nanobuntv.retro64plus.config.Retro64PlusConfig.server().coinStarCoins = 10;
        } else if (t == 40) {
            Retro64PlusConfig.client().character = "mario";
            LocalMario.toggle();
            active = true;
            starLog(mc, server, "on");
        } else if (t == 50) {
            server.execute(() -> com.nanobuntv.retro64plus.coins.Coins.burst(server.overworld(),
                    net.minecraft.world.phys.Vec3.atCenterOf(origin.offset(0, 0, 2)), 12));
        } else if (t >= 60 && t < 100 && t % 4 == 0) {
            CoinEntityHolder.nearest(mc).ifPresent(coin -> command(mc, "tp @s %.2f %.2f %.2f", coin.getX(),
                    coin.getY(), coin.getZ()));
        } else if (t == 105) {
            command(mc, "tp @s %d %d %d 0 0", x, y, z);
            starLog(mc, server, "12 coins");
        } else if (t == 115) {
            screenshot("retro64plus_m3_star");
            collectNearestStar(mc);
        } else if (t == 125) {
            screenshot("retro64plus_m3_star_dance");
            starLog(mc, server, "dancing (coin star)");
        } else if (t == 230) {
            starLog(mc, server, "after the coin star");
            command(mc, "summon elder_guardian %d %d %d {NoAI:1b,Health:1f}", x + 6, y, z + 6);
        } else if (t == 240) {
            command(mc, "damage @e[type=elder_guardian,limit=1] 30 minecraft:player_attack by @s");
        } else if (t == 270) {
            starLog(mc, server, "guardian killed");
            collectNearestStar(mc);
        } else if (t == 380) {
            starLog(mc, server, "after the boss star");
            command(mc, "tp @s %d %d %d 0 0", x, y, z);
            command(mc, "advancement grant @s only minecraft:adventure/kill_all_mobs");
        } else if (t == 390) {
            starLog(mc, server, "challenge granted");
            collectNearestStar(mc);
        } else if (t == 500) {
            starLog(mc, server, "after the challenge star");
            command(mc, "item replace entity @s hotbar.0 with retro64plus:wing_cap");
        } else if (t == 505) {
            useCap(mc, 0);
        } else if (t == 510) {
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                if (player == null) return;
                var charge = player.getInventory().getItem(0)
                        .get(com.nanobuntv.retro64plus.caps.Retro64PlusItems.CAP_CHARGE.get());
                Retro64Plus.LOGGER.info("Dev automario [stars caps]: wing cap lasts {} ticks (base {}), charge {}",
                        com.nanobuntv.retro64plus.server.ServerCaps.duration(player,
                                com.nanobuntv.retro64plus.caps.MarioCap.WING),
                        com.nanobuntv.retro64plus.caps.MarioCap.WING.ticks, charge);
            });
        } else if (t == 520) {
            command(mc, "kill @s");
        } else if (t == 590) {
            mc.player.respawn();
        } else if (t == 620) {
            command(mc, "tp @s %d %d %d 0 0", x, y, z);
            server.execute(() -> com.nanobuntv.retro64plus.coins.Coins.burst(server.overworld(),
                    net.minecraft.world.phys.Vec3.atCenterOf(origin.offset(0, 0, 2)), 12));
        } else if (t >= 630 && t < 680 && t % 4 == 0) {
            CoinEntityHolder.nearest(mc).ifPresent(coin -> command(mc, "tp @s %.2f %.2f %.2f", coin.getX(),
                    coin.getY(), coin.getZ()));
        } else if (t == 700) {
            starLog(mc, server, "12 coins after dying (no new star)");
            command(mc, "clear @s");
            com.nanobuntv.retro64plus.config.Retro64PlusConfig.loadServer();
            active = false;
            if (LocalMario.isActive()) LocalMario.toggle();
            Retro64Plus.LOGGER.info("Dev automario: done");
            enabled = false;
            mc.stop();
        }
    }

    /**
     * M3 checkpoint 3: 300 coins and no stars; opens a Coin Shop, tries the Wing cap (refused: 1 star needed), buys a
     * diamond (200) and a Recharge Mushroom (40), tries a second diamond (refused: 60 coins); then gets a star and 200
     * coins and buys the Wing cap. As Mario, uses the cap (recharging) and the mushroom from the off hand (ready
     * again). Logs coins, inventory and the cap's charge; screenshot of the screen.
     */
    private static void shop(Minecraft mc) {
        ticks++;
        int t = ticks;
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        if (t == 5) {
            origin = mc.player.blockPosition();
            int x = origin.getX(), y = origin.getY(), z = origin.getZ();
            command(mc, "time set day");
            command(mc, "gamemode survival");
            command(mc, "clear @s");
            command(mc, "fill %d %d %d %d %d %d stone", x - 4, y - 1, z - 4, x + 8, y - 1, z + 8);
            command(mc, "fill %d %d %d %d %d %d air", x - 4, y, z - 4, x + 8, y + 6, z + 8);
            command(mc, "kill @e[type=!player,distance=..40]");
            command(mc, "setblock %d %d %d retro64plus:coin_shop[facing=north]", x, y, z + 2);
            command(mc, "tp @s %d %d %d 0 0", x, y, z);
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                if (player == null) return;
                com.nanobuntv.retro64plus.server.PlayerProgress.get(server).reset(player.getUUID());
                com.nanobuntv.retro64plus.server.ServerCoins.add(player, 300);
                com.nanobuntv.retro64plus.server.ServerStars.sync(player);
            });
        } else if (t == 30) {
            var pos = origin.offset(0, 0, 2);
            mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),
                            net.minecraft.core.Direction.NORTH, pos, false));
        } else if (t == 40) {
            shopLog(mc, server, "opened");
            screenshot("retro64plus_m3_shop");
        } else if (t == 45) {
            shopBuy(mc, 0); // Wing cap: needs a star
        } else if (t == 55) {
            shopLog(mc, server, "wing cap without a star");
            shopBuy(mc, 8); // diamond
        } else if (t == 65) {
            shopLog(mc, server, "diamond");
            shopBuy(mc, 3); // mushroom
        } else if (t == 75) {
            shopLog(mc, server, "mushroom");
            shopBuy(mc, 8);
        } else if (t == 85) {
            shopLog(mc, server, "second diamond (60 coins)");
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                if (player == null) return;
                com.nanobuntv.retro64plus.server.PlayerProgress.get(server).addStar(player.getUUID(), "dev/test");
                com.nanobuntv.retro64plus.server.ServerCoins.add(player, 200);
                com.nanobuntv.retro64plus.server.ServerStars.sync(player);
            });
        } else if (t == 95) {
            shopBuy(mc, 0);
        } else if (t == 105) {
            shopLog(mc, server, "wing cap with a star");
            screenshot("retro64plus_m3_shop_bought");
            mc.setScreen(null);
            command(mc, "tp @s %.1f %d %.1f 45 30", origin.getX() + 2.5, origin.getY(), origin.getZ() + 0.0);
        } else if (t == 112) {
            screenshot("retro64plus_m3_shop_block");
        } else if (t == 115) {
            Retro64PlusConfig.client().character = "mario";
            LocalMario.toggle();
            active = true;
        } else if (t == 130) {
            // The bought cap and mushroom moved: the cap to the first hotbar slot, the mushroom to the off hand.
            command(mc, "clear @s retro64plus:wing_cap");
            command(mc, "clear @s retro64plus:recharge_mushroom");
            command(mc, "item replace entity @s hotbar.0 with retro64plus:wing_cap");
            command(mc, "item replace entity @s weapon.offhand with retro64plus:recharge_mushroom");
        } else if (t == 145) {
            useCap(mc, 0);
        } else if (t == 150) {
            shopLog(mc, server, "cap used");
            var result = mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.OFF_HAND);
            Retro64Plus.LOGGER.info("Dev automario [shop mushroom]: {}", result);
        } else if (t == 160) {
            shopLog(mc, server, "mushroom used");
            active = false;
            if (LocalMario.isActive()) LocalMario.toggle();
            command(mc, "clear @s");
            Retro64Plus.LOGGER.info("Dev automario: done");
            enabled = false;
            mc.stop();
        }
    }

    private static void shopBuy(Minecraft mc, int index) {
        if (mc.screen instanceof ShopScreen screen) screen.devBuy(index);
        else Retro64Plus.LOGGER.info("Dev automario [shop]: no shop screen open ({})", mc.screen);
    }

    private static void shopLog(Minecraft mc, net.minecraft.server.MinecraftServer server, String step) {
        var player = server.getPlayerList().getPlayer(mc.player.getUUID());
        if (player == null) return;
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            items.append(' ').append(stack.getCount()).append(' ')
                    .append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
            var charge = stack.get(com.nanobuntv.retro64plus.caps.Retro64PlusItems.CAP_CHARGE.get());
            if (charge != null) items.append(charge.ready(player.level().getGameTime()) ? " (ready)" : " (recharging)");
        }
        Retro64Plus.LOGGER.info("Dev automario [shop {}]: coins {}, stars {}, screen {}; inventory:{}", step,
                com.nanobuntv.retro64plus.server.ServerCoins.coins(player),
                com.nanobuntv.retro64plus.server.ServerStars.count(player),
                mc.screen == null ? "none" : mc.screen.getClass().getSimpleName(), items);
    }

    /** Walks into the nearest waiting star: stands under it. */
    private static void collectNearestStar(Minecraft mc) {
        com.nanobuntv.retro64plus.stars.PowerStarEntity best = null;
        for (var e : mc.level.entitiesForRendering()) {
            if (e instanceof com.nanobuntv.retro64plus.stars.PowerStarEntity star
                    && (best == null || star.distanceTo(mc.player) < best.distanceTo(mc.player))) best = star;
        }
        if (best == null) {
            Retro64Plus.LOGGER.info("Dev automario [stars]: no star to collect");
            return;
        }
        if (LocalMario.active() != null) {
            LocalMario.active().mario().setVelocity(net.minecraft.world.phys.Vec3.ZERO);
            LocalMario.active().mario().setAction(com.nanobuntv.retro64plus.sm64.Actions.ACT_IDLE);
        }
        command(mc, "tp @s %.2f %.2f %.2f", best.getX(), best.getY() - 0.6, best.getZ());
    }

    private static void starLog(Minecraft mc, net.minecraft.server.MinecraftServer server, String step) {
        StringBuilder waiting = new StringBuilder();
        for (var e : server.overworld().getEntities(com.nanobuntv.retro64plus.stars.Stars.POWER_STAR.get(), s -> true)) {
            waiting.append(String.format(Locale.ROOT, " [%s at rel (%.1f, %.1f, %.1f)]", e.starId(),
                    e.getX() - origin.getX(), e.getY() - origin.getY(), e.getZ() - origin.getZ()));
        }
        var player = server.getPlayerList().getPlayer(mc.player.getUUID());
        LocalMario local = LocalMario.active();
        Retro64Plus.LOGGER.info(String.format(Locale.ROOT,
                "Dev automario [stars %s]: client %d, server %d; coins %d; dances %d, action 0x%x; waiting:%s",
                step, ClientCoins.stars(), player == null ? -1 : com.nanobuntv.retro64plus.server.ServerStars.count(player),
                ClientCoins.coins(), local == null ? -1 : local.starDances, local == null ? 0 : local.mario().state.action,
                waiting.length() == 0 ? " none" : waiting));
    }

    /** The coins lying in the client's world. */
    private static final class CoinEntityHolder {
        static java.util.Optional<com.nanobuntv.retro64plus.coins.CoinEntity> nearest(Minecraft mc) {
            com.nanobuntv.retro64plus.coins.CoinEntity best = null;
            for (var e : mc.level.entitiesForRendering()) {
                if (e instanceof com.nanobuntv.retro64plus.coins.CoinEntity coin && coin.isAlive()
                        && (best == null || coin.distanceTo(mc.player) < best.distanceTo(mc.player))) best = coin;
            }
            return java.util.Optional.ofNullable(best);
        }
    }

    private static void coinLog(Minecraft mc, net.minecraft.server.MinecraftServer server, String step) {
        int lying = 0, value = 0;
        for (var e : server.overworld().getEntities(com.nanobuntv.retro64plus.coins.Coins.COIN.get(), c -> true)) {
            lying++;
            value += e.value();
        }
        int husks = server.overworld().getEntities(net.minecraft.world.entity.EntityType.HUSK, h -> true).size();
        var player = server.getPlayerList().getPlayer(mc.player.getUUID());
        Retro64Plus.LOGGER.info(String.format(Locale.ROOT,
                "Dev automario [coins %s]: client %d, server %d; %d coin entities worth %d; HP %.1f; mario %s; art %s; %d husks",
                step, ClientCoins.coins(), player == null ? -1 : com.nanobuntv.retro64plus.server.ServerCoins.coins(player),
                lying, value, mc.player.getHealth(), LocalMario.isActive(), CoinArt.fromRom() ? "rom" : "own", husks));
    }

    private static void poundLog(Minecraft mc, String step) {
        LocalMario local = LocalMario.active();
        StringBuilder husks = new StringBuilder();
        var server = mc.getSingleplayerServer();
        String blocks = "?";
        if (server != null) {
            var level = server.overworld();
            for (var e : level.getEntities(net.minecraft.world.entity.EntityType.HUSK, h -> true)) {
                husks.append(String.format(Locale.ROOT, " [%.1f HP at (%.2f, %.2f)]",
                        ((net.minecraft.world.entity.LivingEntity) e).getHealth(), e.getX() - origin.getX(),
                        e.getZ() - origin.getZ()));
            }
            int x = origin.getX(), y = origin.getY(), z = origin.getZ();
            blocks = String.format(Locale.ROOT, "glass %s, leaves %s, poppy %s, grass %s, button %s, lever %s, adventure glass %s",
                    blockName(level, x + 6, y + 2, z), blockName(level, x + 12, y + 2, z + 4),
                    blockName(level, x + 12, y, z), blockName(level, x + 12, y - 1, z),
                    level.getBlockState(new BlockPos(x + 8, y, z + 8)), level.getBlockState(new BlockPos(x + 12, y, z + 8)),
                    blockName(level, x + 3, y + 2, z + 12));
        }
        Retro64Plus.LOGGER.info(String.format(Locale.ROOT,
                "Dev automario [pound %s]: pounds %d, action 0x%x, rel (%.2f, %.2f, %.2f); %s; husks%s",
                step, local == null ? 0 : local.groundPounds, local == null ? 0 : local.mario().state.action,
                mc.player.getX() - origin.getX(), mc.player.getY() - origin.getY(), mc.player.getZ() - origin.getZ(),
                blocks, husks));
    }

    private static String blockName(net.minecraft.world.level.Level level, int x, int y, int z) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(level.getBlockState(new BlockPos(x, y, z)).getBlock()).getPath();
    }

    private static void useCap(Minecraft mc, int slot) {
        mc.player.getInventory().selected = slot;
        var result = mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
        Retro64Plus.LOGGER.info("Dev automario [caps use slot {}]: {}", slot, result);
    }

    private static void capsLog(Minecraft mc, String step) {
        LocalMario local = LocalMario.active();
        String husk = "none";
        var server = mc.getSingleplayerServer();
        if (server != null) {
            for (var e : server.overworld().getEntities(net.minecraft.world.entity.EntityType.HUSK, h -> true)) {
                var target = e.getTarget();
                husk = String.format(Locale.ROOT, "husk %.1f away, target %s",
                        e.distanceTo(mc.player), target == null ? "none" : target.getName().getString());
            }
        }
        Retro64Plus.LOGGER.info(String.format(Locale.ROOT,
                "Dev automario [caps %s]: %s, flags 0x%x, action 0x%x, rel (%.2f, %.2f, %.2f), HP %.1f, %s; %s",
                step, ClientCaps.debugState(), local == null ? 0 : local.mario().state.flags,
                local == null ? 0 : local.mario().state.action,
                mc.player.getX() - origin.getX(), mc.player.getY() - origin.getY(), mc.player.getZ() - origin.getZ(),
                mc.player.getHealth(), husk, MarioAudio.debugState()));
    }

    private static void healthLog(Minecraft mc, String step) {
        LocalMario local = LocalMario.active();
        Retro64Plus.LOGGER.info(String.format(Locale.ROOT,
                "Dev automario [health %s]: HP %.1f/%.0f, alive %s, screen %s, %s, knockbacks %d, action 0x%x, y rel %.2f",
                step, mc.player.getHealth(), mc.player.getMaxHealth(), mc.player.isAlive(),
                mc.screen == null ? "none" : mc.screen.getClass().getSimpleName(), PowerMeterHud.debugState(),
                local == null ? -1 : local.knockbacks, local == null ? 0 : local.mario().state.action,
                mc.player.getY() - (origin != null ? origin.getY() : 0)));
    }

    private static void logHusks(Minecraft mc, String step) {
        StringBuilder out = new StringBuilder();
        for (var e : mc.level.entitiesForRendering()) {
            if (e instanceof net.minecraft.world.entity.monster.Husk husk) {
                // Entity tags aren't synced to clients: name them by where they stand.
                int rx = husk.getBlockX() - origin.getX(), rz = husk.getBlockZ() - origin.getZ();
                String name = rx < 3 ? "punch" : rz < 3 ? "stomp" : "pound";
                out.append(String.format(Locale.ROOT, " %s %.1f HP", name, husk.getHealth()));
                if (!husk.isAlive()) out.append(" (dead)");
            }
        }
        log(step);
        Retro64Plus.LOGGER.info("Dev automario [{}] husks:{}", step, out.length() == 0 ? " none" : out);
    }

    private static int watchShots;
    private static boolean watchSawForced;

    private static void buildCourse(Minecraft mc) {
        origin = mc.player.blockPosition();
        int x = origin.getX(), y = origin.getY(), z = origin.getZ();
        command(mc, "tp @s %d %d %d 0 0", x, y, z); // block centre-ish, facing south (+z)
        command(mc, "gamemode survival"); // the server's "moved wrongly" check only runs in survival
        command(mc, "effect give @s water_breathing 600 0 true");
        command(mc, "fill %d %d %d %d %d %d stone", x - 4, y - 6, z - 4, x + 14, y - 1, z + 16);
        command(mc, "fill %d %d %d %d %d %d air", x - 4, y, z - 4, x + 14, y + 8, z + 16);
        // Stairs ascending south: a step at z+4 (ground), stone + a step at z+5, a 2-high platform from z+6.
        command(mc, "setblock %d %d %d oak_stairs[facing=south]", x, y, z + 4);
        command(mc, "setblock %d %d %d stone", x, y, z + 5);
        command(mc, "setblock %d %d %d oak_stairs[facing=south]", x, y + 1, z + 5);
        command(mc, "fill %d %d %d %d %d %d stone", x - 1, y, z + 6, x + 1, y + 1, z + 10);
        // A slab beside the stairs too.
        command(mc, "setblock %d %d %d stone_slab", x + 2, y, z + 4);
        // The pool, 4 deep, with a squid in its path that doesn't move.
        command(mc, "fill %d %d %d %d %d %d water", x + 8, y - 5, z, x + 13, y - 1, z + 14);
        command(mc, "summon squid %d %d %d {NoAI:1b}", x + 10, y - 3, z + 6);
        Retro64Plus.LOGGER.info("Dev automario: course built at {}", origin);
    }

    private static void command(Minecraft mc, String format, Object... args) {
        mc.player.connection.sendCommand(String.format(Locale.ROOT, format, args));
    }

    private static void log(String step) {
        LocalMario local = LocalMario.active();
        Minecraft mc = Minecraft.getInstance();
        BlockPos o = origin != null ? origin : BlockPos.ZERO;
        if (local == null) {
            Retro64Plus.LOGGER.info("Dev automario [{}]: Mario off, player at {}", step, mc.player.position());
            return;
        }
        MarioInstance m = local.mario();
        Retro64Plus.LOGGER.info(String.format(Locale.ROOT,
                "Dev automario [%s]: Mario rel (%.2f, %.2f, %.2f) action 0x%x face %.0f° frames %d snapped back %d; player rel (%.2f, %.2f, %.2f) camera %s",
                step, m.position().x - o.getX(), m.position().y - o.getY(), m.position().z - o.getZ(), m.state.action,
                Math.toDegrees(m.state.faceAngle), m.frameCount(), local.teleports, mc.player.getX() - o.getX(),
                mc.player.getY() - o.getY(), mc.player.getZ() - o.getZ(), mc.gameRenderer.getMainCamera().getPosition()));
    }

    private static void screenshot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(),
                message -> Retro64Plus.LOGGER.info("Dev automario: {}", message.getString()));
    }
}
//?} else {
/*import com.nanobuntv.retro64plus.Retro64Plus;
import com.nanobuntv.retro64plus.platform.Services;
import com.nanobuntv.retro64plus.sm64.LibSM64;
import com.nanobuntv.retro64plus.sm64.Rom;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;

// Dev builds on the newer Minecraft versions (Porting.md): the scripted runs above are written against 1.21.1's API,
// so these versions get one smoke test instead: with the trigger file (any content) the ROM loads, Mario turns on,
// walks, jumps and punches, the state is logged ("Dev automario [smoke ...]"), Mario turns off and the game closes.
public final class DevAutoTest {
    static boolean active;
    static float forward, right;
    static boolean jump, punch, crouch;
    private static boolean enabled;
    private static int ticks, waiting;

    private DevAutoTest() {}

    static void onSound(int sound, boolean played) {
        if (enabled) Retro64Plus.LOGGER.info("Dev automario [sound]: 0x{} {}", Integer.toHexString(sound),
                played ? "played by SM64" : "not played (SM64's sound isn't running)");
    }

    static void init() {
        java.nio.file.Path trigger = Services.PLATFORM.gameDir().resolve("retro64plus_dev").resolve("automario");
        enabled = Services.PLATFORM.isDevelopmentEnvironment() && Files.exists(trigger);
    }

    static void tick() {
        if (!enabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            // NeoForge's warnings screen (deprecated mod properties): continue to the game.
            if (mc.screen != null && mc.screen.getClass().getName().endsWith("LoadingErrorScreen")) {
                String quit = net.minecraft.network.chat.Component.translatable("menu.quit").getString();
                for (var child : mc.screen.children()) {
                    if (!(child instanceof net.minecraft.client.gui.components.Button button)) continue;
                    String label = button.getMessage().getString();
                    if (label.equals(quit) || label.startsWith("Open")) continue;
                    Retro64Plus.LOGGER.info("Dev automario [smoke]: pressing \"{}\" on the warnings screen", label);
                    button.onPress(new net.minecraft.client.input.KeyEvent(0, 0, 0));
                    return;
                }
            }
            // The test world comes from 1.21.1: upgrade it without the backup Minecraft offers first.
            if (mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen backup) {
                try {
                    var field = net.minecraft.client.gui.screens.BackupConfirmScreen.class.getDeclaredField("onProceed");
                    field.setAccessible(true);
                    ((net.minecraft.client.gui.screens.BackupConfirmScreen.Listener) field.get(backup)).proceed(false, false);
                } catch (ReflectiveOperationException e) {
                    Retro64Plus.LOGGER.warn("Dev automario: couldn't answer the backup screen", e);
                }
                return;
            }
            // ... and join it once it's upgraded.
            if (mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen confirm) {
                try {
                    var field = net.minecraft.client.gui.screens.ConfirmScreen.class.getDeclaredField("callback");
                    field.setAccessible(true);
                    ((it.unimi.dsi.fastutil.booleans.BooleanConsumer) field.get(confirm)).accept(true);
                } catch (ReflectiveOperationException e) {
                    Retro64Plus.LOGGER.warn("Dev automario: couldn't answer the screen", e);
                }
                return;
            }
            if (++waiting % 100 == 0) {
                Retro64Plus.LOGGER.info("Dev automario [smoke]: no world yet, screen {}: {}",
                        mc.screen == null ? "none" : mc.screen.getClass().getName(),
                        mc.screen == null ? "" : mc.screen.getNarrationMessage().getString());
            }
            return;
        }
        if (LibSM64.status() == LibSM64.Status.NOT_STARTED) {
            Rom.find(Retro64PlusClient.configuredRomPath(), Retro64PlusClient.modDir()).ifPresentOrElse(
                    Retro64PlusClient::start, () -> {
                        Retro64Plus.LOGGER.warn("Dev automario: no ROM found");
                        enabled = false;
                    });
            return;
        }
        if (LibSM64.status() != LibSM64.Status.READY) return;
        ticks++;
        switch (ticks) {
            case 20 -> {
                LocalMario.toggle();
                active = true;
                log("on");
            }
            case 30 -> forward = 1;
            case 60 -> net.minecraft.client.Screenshot.grab(mc.gameDirectory, "retro64plus_smoke.png",
                    mc.getMainRenderTarget(), 1, message -> Retro64Plus.LOGGER.info("Dev automario: {}", message.getString()));
            case 70 -> {
                log("walked");
                forward = 0;
                jump = true;
            }
            case 76 -> jump = false;
            case 90 -> {
                log("jumped");
                punch = true;
            }
            case 96 -> punch = false;
            case 110 -> log("punched");
            case 120 -> {
                active = false;
                if (LocalMario.isActive()) LocalMario.toggle();
                log("off");
                Retro64Plus.LOGGER.info("Dev automario: done");
                enabled = false;
                mc.stop();
            }
            default -> { }
        }
    }

    private static void log(String step) {
        Minecraft mc = Minecraft.getInstance();
        LocalMario local = LocalMario.active();
        Retro64Plus.LOGGER.info("Dev automario [smoke {}]: Mario {}, action 0x{}, at {}, health {}", step,
                local != null ? "on" : "off", local != null ? Integer.toHexString(local.mario().state.action) : "-",
                mc.player.position(), mc.player.getHealth());
    }
}
*///?}
