# Native library sources

The `sm64` native library in Retro64 Plus's jars (`natives/windows-x86_64/sm64.dll` and
`natives/linux-x86_64/libsm64.so`) is built from the `native/` folder of
https://github.com/NanoBunTV/Minecraft-Retro64-Plus, with Zig 0.17.0 (`native/build.zig`).

| Part | Source | License |
|---|---|---|
| libsm64-retro64, the fork vendored here | https://github.com/Retro64Mod/libsm64-retro64, commit `7afa8ed60c539653d37ce99c987ac23a0f6e2ee3` | None stated |
| libsm64, the library it forks | https://github.com/libsm64/libsm64 (CC0 added in commit `83f68c8449`) | CC0 1.0, `LICENSE-libsm64.md` |
| Super Mario 64 decompilation code inside it | https://github.com/n64decomp/sm64 (Mario's geo layout as of commit `06ec56df7f951f88da05f468cdcacecba496145a`) | None stated |
| Audio code from sm64-port | https://github.com/sm64-port/sm64-port | None stated |
| dlmalloc (`src/decomp/pc/dlmalloc.c`) | Doug Lea | Public domain |
| stb_image, stb_image_write (`src/decomp/tools/stb/`) | Sean Barrett | Public domain or MIT, in the files |
| Retro64 Plus's changes | Every change is listed in `native/UPSTREAM.md` and marked `Retro64 Plus:` in the code | MIT, as the rest of Retro64 Plus |

The library contains **no Nintendo assets**. Mario's model, textures, animations, sounds and music are read at runtime
from a Super Mario 64 (USA) ROM that the player supplies.
