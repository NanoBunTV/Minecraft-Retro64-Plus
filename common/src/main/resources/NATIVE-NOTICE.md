# Native library notice (D111, D133)

Retro64 Plus is MIT-licensed (see LICENSE_retro64plus.md). This jar also carries a native library, `sm64`
(`natives/` in this jar: `sm64.dll` for Windows x64, `libsm64.so` for Linux x64), which plays Super Mario 64 itself.

- **Source:** the `native/` folder of https://github.com/NanoBunTV/Minecraft-Retro64-Plus, vendored from
  **libsm64-retro64** (https://github.com/Retro64Mod/libsm64-retro64, commit
  `7afa8ed60c539653d37ce99c987ac23a0f6e2ee3`), a fork of **libsm64** (https://github.com/libsm64/libsm64), with
  Retro64 Plus's changes listed in `native/UPSTREAM.md`. `natives/SOURCE.md` in this jar lists every part.
- **Licenses:** libsm64 is CC0 1.0 (`natives/LICENSE-libsm64.md` in this jar). The libsm64-retro64 fork and the code
  it takes from the Super Mario 64 decompilation project (https://github.com/n64decomp/sm64) and sm64-port state no
  license.
- **No Nintendo assets:** the library and this jar contain no game data. Mario's model, textures, animations, sounds
  and music are read at runtime from a Super Mario 64 (USA) ROM that you supply yourself.
