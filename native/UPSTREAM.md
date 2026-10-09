# Vendored native library (D7)

`native/` is the source of **libsm64-retro64**, vendored so Retro64 Plus can build and change it.

| | |
|---|---|
| Upstream | https://github.com/Retro64Mod/libsm64-retro64 |
| Commit | `7afa8ed60c539653d37ce99c987ac23a0f6e2ee3` (2025-02-24), `LIB_VER 5` |
| Itself a fork of | libsm64 (https://github.com/libsm64/libsm64), with code from the SM64 decompilation and sm64-port |
| License | **None stated upstream** (no license file). `src/decomp/pc/gfx/LICENSE.txt` covers that folder (not compiled). See OpenQuestions 2. |
| Copied | `src/`, `README.md`, `Makefile`, `import-mario-geo.py`. Not copied: `test/` (an SDL test program), `.github/`. |

The `Makefile` is upstream's and is kept for reference only; `build.zig` is what builds the library.

## Local changes
- **2026-10-07** — M6 review (`LIB_VER` stays 11, no API change):
  - `sm64_mChar_delete` stops and drops the sounds still playing or queued from Mario's position before freeing it.
    The sound engine kept the pointer, so the next audio tick read freed memory. New
    `forget_sound_requests_from_source` in `decomp/audio/external.c` / `external.h`.
  - Grab and drop with the Metal cap under water. `sm64_mChar_grab` refuses there, and `sm64_mChar_drop` leaves Metal
    Mario standing in the water. Before, `ACT_IDLE` there plunged him to the surface, through blocks.
  - `sm64_surface_object_delete` skips freed Mario slots.
  - `sm64_set_volume` returns 0.
  - The `sm64_mChar_knockback` comment now matches what it does.
- **2026-10-07** — M4 checkpoint 3, `LIB_VER` **11**: **`sm64_mChar_ride_shell`** and **`sm64_mChar_is_riding`**
  (`libsm64.c`): Mario rides a Koopa shell Minecraft owns with SM64's own riding actions (as `interact_koopa_shell`);
  a static stand-in `struct Object` fills `riddenObj`, and a ride left by any other way clears it.
- **2026-10-07** — M4 checkpoint 2, `LIB_VER` **10**: **`sm64_mChar_grab`**, **`sm64_mChar_is_holding`** and
  **`sm64_mChar_drop`** (`libsm64.c`): Mario picks up, carries and throws an object Minecraft owns (a Bob-omb) with
  SM64's own pick-up, hold and throw actions; a static stand-in `struct Object` fills `usedObj` / `heldObj`.
Every change to upstream files is listed here, newest first, and marked `Retro64 Plus:` in the code.

- **2026-10-07** — M3 checkpoint 2 (`LIB_VER` stays 9, no API change): **star dance restored**
  (`decomp/game/mario_actions_cutscene.c`, `general_star_dance_handler`). The fork had commented the whole handler
  out, so a star dance never ended. A minimal version runs again: the collect-star jingle (`play_music` on the
  environment sequence player; `audio/external.h` included for it), "Here we go!" at frame 42, the return animation
  at frame 80, then idle (water idle when swimming). Left out: the celebration star object, the level exit and the
  save dialog. SM64's original stays below it, commented.

- **2026-10-07** — M2 review (`LIB_VER` stays 9): `sm64_mChar_knockback` (`libsm64.c`) clears SM64's
  invulnerability for the call; Minecraft's own hurt cooldown already decided the hit counts.

- **2026-10-07** — M2 checkpoint 3 (`LIB_VER` stays 9, no API change):
  - **Water step wall radius** (`decomp/game/mario_actions_submerged.c`, `perform_water_full_step`): 50 units instead
    of SM64's 110, a whole block at 110 units per block. Walking off a ledge under water (Metal Mario) the ledge's side
    threw him a block forward in one frame; swimming Mario also didn't fit one-block tunnels.

- **2026-10-07** — M2 checkpoint 2, `LIB_VER` **9**:
  - **Caps owned by the mod** (`libsm64.c/.h`, `decomp/game/mario.c`, D17, D44): `sm64_mChar_set_caps(caps, hidden)`
    sets the special cap flags every frame (`capTimer` stays 0, so SM64 never removes a cap) and a mask
    (`gRetro64HiddenCaps`) that `update_and_return_cap_flags` takes out of the drawn flags (the flicker).
  - **Metal model re-enabled** (`decomp/game/mario_misc.c`, `geo_switch_mario_cap_effect`): the fork forced case 0
    ("temporarily disables metal cap"); all four cases are selected again. The mod does the environment mapping.
  - **Vanish cap walls** (`decomp/engine/surface_collision.c`): libsm64 had dropped SM64's rule; walls of type
    `SURFACE_VANISH_CAP_WALLS` are skipped while Mario wears the Vanish cap (D43).
  - **Master-list hack** (`decomp/game/rendering_graph_node.c`): skips exactly the first display list appended (the
    alpha / env-colour list with no real transform) instead of the first met walking the layers, which with SM64
    transparency skipped a real list and drew that one (the local Vanish cap).

- **2026-10-06** — M2 checkpoint 1, `LIB_VER` **8**:
  - **Minecraft owns Mario's health** (`decomp/game/mario.c` `update_mario_health`, `libsm64.c/.h`, D36): no drain,
    healing or hurt/heal counters in SM64 any more (only the almost-drowning alarm). `sm64_mChar_set_health` sets it
    every frame and returns the damage SM64's code put in `hurtCounter` (falls, lava), for the mod to apply.
  - **`sm64_mChar_knockback`**: SM64's knockback (the fork's `fake_damage_knock_back`) without touching health, or just
    the "attacked" voice without a source; its sounds are recorded for other players.
  - **Recorded sounds** are now cleared when read (`sm64_mChar_last_tick_sounds`), not at the start of a tick, so
    knockback sounds join the next frame's. `fake_interaction.h` included where it was implicitly declared.
  - Not bound by the mod any more: `sm64_mChar_apply_damage`, `sm64_mChar_heal` (they change health).

- **2026-10-06** — M1 checkpoint 4, `LIB_VER` **7**:
  - **Mario is the only model** (`model_handler.c/.h`, `load_tex_data.c`, `build.zig`): the folders `decomp/model_luigi`,
    `model_steve`, `model_alex`, `model_necoarc`, `model_vibri` and `model_sonic` are deleted (D15, D28, D33: their
    authorship was undocumented; the mod draws Luigi as a recolour and the player's skin itself). Every function's
    `model` argument is now ignored.
  - **Bones** (`gfx_adapter.c/.h`, `rendering_graph_node.c`, `libsm64.c/.h`): `geo_process_animated_part` records each
    animated part's matrix; `sm64_last_render_bones` returns those of the character drawn last (20 for Mario, in drawing
    order), for the skin character (D28).
  - **Sounds for other players** (`audio/external.c/.h`, `libsm64.c/.h`): `play_sound` calls an optional hook;
    `sm64_mChar_tick` uses it to record the sounds Mario's own code plays (not global ones), read with
    `sm64_mChar_last_tick_sounds`. `sm64_sound_source_move` / `sm64_sound_source_play` replay them from one of 64
    positions kept in the library (the sound engine keeps the position pointer while a sound plays, so the caller's
    memory can't be used, which is why upstream's `sm64_play_sound` isn't bound by the mod). Sound bits from the network
    are checked: a bank above 9 indexed 10-entry tables unchecked. Positions are sanitized (NaN, huge values).

- **2026-10-06** — `src/libsm64.c`, `src/libsm64.h`, `src/load_anim_data.c/.h` (M1 checkpoint 3): **drawing other
  players' Marios.** New `struct SM64MarioPose` (48 bytes: drawn offset, angles, animation id / frame / Y-translation,
  action and the body state: cap, eyes, hands, wing flutter, model state, grab, punch, torso and head angles),
  `sm64_mChar_get_pose`, `sm64_mChar_create_puppet` (a character that never runs physics, so it needs no floor) and
  `sm64_mChar_render_pose` (draws a puppet in a pose, holding that exact frame). Poses come from the network, so
  `render_pose` clamps every value: unknown animations fall back to the first (a fresh puppet with none crashed),
  frames to the animation's length, body-state values to their ranges, offsets to ±500 units (NaN → 0), and it drops
  SM64 transparency (`MODEL_STATE_ALPHA`): that path moves display lists to another layer, which libsm64's
  master-list hack (`rendering_graph_node.c`, "Mario ends up in the second master list") mishandles by skipping a real
  entry and drawing an uninitialized one, a non-deterministic crash. The local vanish cap uses the same path: fix it
  properly in M2. `mario_anim_count()` exposes how many animations the ROM has. Upstream's `sm64_mChar_animTick`
  (compared the frame with the animation id, advanced frames itself) is no longer used by the mod.
- **2026-10-06** — `src/libsm64.c`: **model numbers clamped** wherever `currentModel` is stored. `getModel()` falls
  back to Mario for an unknown model but `load_tex_data.c`'s tables return NULL for one, so a bad number crashed.
- **2026-10-06** — `src/gfx_adapter.c`: **triangle output bounded** to `SM64_GEO_MAX_TRIANGLES`. Upstream wrote on past
  the end of the caller's buffers whenever a display list drew more, corrupting the heap.
  Also found while fuzzing: the punch/kick scaler (`mario_misc.c`) indexes its 3 × 6 `gMarioAttackScaleAnimation`
  table with the punch timer unchecked, so `render_pose` clamps the timer to 0–5.
- **2026-10-06** — `src/decomp/engine/surface_collision.h`: `FLOOR_LOWER_LIMIT` −11000 → −1e9. `find_floor` starts its
  search at that height, so floors below it (100 blocks under y = 0) were never found; with no floor below him SM64
  treats Mario as out of bounds and he bonks off an invisible wall. Long-jumping off a cliff taller than the
  collision box hit it, because the only floor below him was the mod's safety floor, 70 blocks under the world's
  bottom. The fork made coordinates 32-bit but kept this 16-bit-era limit.
- **2026-10-06** — `src/decomp/game/mario_actions_submerged.c`, `mario_actions_airborne.c`: **stick axes restored to
  the SM64 decompilation's** in swimming (yaw from `stickX`, pitch from `stickY`), the jump out of water
  (`stickY < -60`) and wing-cap flying (yaw / pitch). The fork had swapped them, apparently to suit upstream's Java,
  which fed W into `stickX`; with standard libsm64 inputs (as this mod sends) swimming turned with forward/back and
  pitched with left/right. Checked against n64decomp/sm64 `master`.
- **2026-10-06** — `src/libsm64.c` (`sm64_static_surfaces_load`): **use-after-free fixed.** Loading static surfaces
  frees the previous list, but every character kept `floor` / `ceil` / `wall` pointers into it, so the next tick read
  freed memory (it crashed the game on the first frame after a collision refresh). After loading, each character's
  floor and ceiling are looked up again and its wall cleared. Upstream re-uploaded surfaces every frame and hit the
  same bug by luck of memory reuse.
- **2026-10-06** — `src/libsm64.c`, `src/libsm64.h` (D21): **no native audio output.** Upstream started a pthread in
  `sm64_global_init` that ran SM64's sound engine concurrently with the game's calls into the library (a data race)
  and played it through WASAPI / PulseAudio / ALSA / SDL. Loading it inside Minecraft crashed the game moments after
  init (access violation, both loaders), while the same init outside Minecraft survived. Removed: the thread, the
  backend selection, `audio_tick` / `audio_thread`. Added: `sm64_audio_tick(numQueuedSamples, numDesiredSamples,
  buffer)`, the same shape as original libsm64's, called by the mod at 30 Hz on the thread that ticks the
  characters. The audio backend sources (`src/decomp/pc/audio/`) stay in the tree, unmodified and not compiled.
  `LIB_VER` is now **6**.
- **2026-10-06** — `src/libsm64.c` (`sm64_mChar_tick`, `sm64_mChar_animTick`): removed the wall-clock frame
  limiter (`fpsLimit(30, lastTime)`), which silently skipped any call less than ~29 ms after the previous one. The
  mod runs SM64 frames at a fixed 30 Hz itself (sometimes two in one Minecraft tick), and the limiter made Mario
  slow down whenever the game ran below 30 FPS (Reference-Upstream, Timing).

## Build differences from upstream
- Zig (clang) instead of gcc. Clang errors that gcc only warned about are downgraded to warnings (see `c_flags` in
  `build.zig`); `-fno-strict-aliasing -fwrapv` keep the optimizer away from the decomp's undefined behavior.
- Windows: `_WIN32_WINNT=0x0601`, so newer mingw headers don't pull `MapViewOfFileNuma2` into dlmalloc.
- Linux: glibc 2.28 floor. Neither platform links an audio library (D21).
- Checked on 2026-10-06 (before the local changes): both libraries exported the same 34 `sm64_*` functions as upstream's prebuilt binaries.
