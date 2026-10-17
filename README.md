# Retro64 Plus

Play as our favorite plumber with Super Mario 64's real moveset, caps, coins, stars and enemies. Uses your own SM64
ROM.

> [!NOTE]
> **Download the mod from Modrinth:** [Retro64 Plus](https://modrinth.com/mod/retro64-plus). This repository is the
> source code. Jars you build yourself are not releases. Please don't share them as releases.

Retro64 Plus is a rewrite of [Retro64](https://github.com/Retro64Mod/Retro64Mod) by dylanpdx and the Retro64
contributors. Mario's physics come from [libsm64](https://github.com/libsm64/libsm64), which runs Super Mario 64's own
movement code. On top of that it adds working caps, the power meter, coins, Power Stars, a Coin Shop, and Goombas,
Bob-ombs, Koopa Troopas and Chain Chomps.

It's an unofficial fan project, not affiliated with or endorsed by Nintendo. Super Mario 64 is Nintendo's.

- **No game files are included.** Mario's model, textures, animations, sounds and music are read at runtime from a
  Super Mario 64 (USA) ROM that you supply yourself. Please don't ask for ROMs in issues; such requests will be
  closed.
- **Windows x64 and Linux x64.** The mod's native library (`sm64`) is built for those two platforms only. macOS and
  ARM aren't supported.
- **Minecraft versions:** 1.21.1, 1.21.11, 26.1.2, 26.2 and 26.3, each on Fabric and NeoForge.
- **Dependencies:** [YetAnotherConfigLib](https://modrinth.com/mod/yacl) on both loaders, and
  [Fabric API](https://modrinth.com/mod/fabric-api) on Fabric.
- **Not compatible with the original Retro64.** The game won't load with both installed.
- **Bugs and ideas:** [Issues](https://github.com/NanoBunTV/Minecraft-Retro64-Plus/issues). Please include your
  Minecraft version, your loader, your operating system, and `logs/latest.log`. If the game crashed, include the
  crash report from `crash-reports/` too.

NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.

## Building from source

### Requirements
- **Git.** No Git LFS is needed.
- **A JDK, 17 or newer,** to run Gradle. Gradle's toolchains download the JDKs each Minecraft version compiles with
  (21 and 25) by themselves.
- **[Zig](https://ziglang.org) 0.17.0,** which builds the native library from [`native/`](native) as part of every
  build. Put it on `PATH`, or set `ZIG` to the full path of `zig`/`zig.exe`. Zig cross-compiles both platforms from
  either OS, so no other C compiler is needed.
- The Gradle wrapper (9.8.0) comes with the repository.

### Build
```sh
git clone https://github.com/NanoBunTV/Minecraft-Retro64-Plus.git
cd Minecraft-Retro64-Plus
./gradlew build            # Windows: gradlew build
```
This builds both loaders on every Minecraft version and runs the unit tests. The first build sets up each Minecraft
version, which takes a while and a few GB of disk space.

The jars land in `<loader>/versions/<minecraft>/build/libs/`, e.g. `fabric/versions/1.21.1/build/libs/`. Use the
jar without `-sources` in its name.

To build one loader on one version, name its Gradle project, `:<loader>:<minecraft>`:
```sh
./gradlew :fabric:1.21.1:build
./gradlew :neoforge:26.3:build
```
The loaders are `fabric` and `neoforge`.

### Run in a dev client
```sh
./gradlew :fabric:1.21.1:runClient
./gradlew :neoforge:26.3:runServer
```
Game folders are under `runs/` (git-ignored): `runs/<loader>/client/` for 1.21.1 and
`runs/<loader>/<minecraft>/client/` for the other versions. To play as Mario, choose your ROM when the game asks for
it, or set `romPath` in the client's `config/retro64plus-client.json5`. The mod reads the ROM where it is.

The unit tests run on the 1.21.1 project. One of them runs the real native library on a real ROM, and only runs when
the environment variable `RETRO64_TEST_ROM` holds the full path to your ROM:
```sh
RETRO64_TEST_ROM="/path/to/Super Mario 64 (USA).z64" ./gradlew :common:1.21.1:test
```

### The native library
The native library is the C code in [`native/`](native), a fork of libsm64-retro64 (see
[`native/SOURCE.md`](native/SOURCE.md)). Every build compiles it from that source: the `buildNative` task runs
`zig build`, which makes `sm64.dll` (Windows x64) and `libsm64.so` (Linux x64, glibc 2.28) in `native/zig-out/`,
and the jars carry them. No compiled library is ever committed. Every change made to the upstream code is listed in
[`native/UPSTREAM.md`](native/UPSTREAM.md).

### Releases
Release jars are built by GitHub Actions ([`build.yml`](.github/workflows/build.yml)) on GitHub's machines, from
this repository's source, native library included. Every run lists the SHA-256 of both native libraries and every
jar in its summary, and signs a build provenance attestation for each file. Pushing a tag `v<version>` also attaches
the jars to a [GitHub Release](https://github.com/NanoBunTV/Minecraft-Retro64-Plus/releases); those are the files
on Modrinth and CurseForge. To check a jar you downloaded:
```sh
gh attestation verify retro64plus-fabric-1.0.0+1.21.1.jar --repo NanoBunTV/Minecraft-Retro64-Plus
```

## Repository layout
| Folder | What's there |
|---|---|
| `common/` | The shared code and assets. Sources are written for Minecraft 1.21.1; [Stonecutter](https://stonecutter.kikugie.dev) generates each version's variant. |
| `fabric/`, `neoforge/` | Each loader's entry points and platform code. |
| `buildSrc/` | The shared Gradle build logic, including the conversion of data files for newer Minecraft versions. |
| `stonecutter.properties.toml` | Mod metadata and dependency versions for each Minecraft version. |
| `native/` | The native library's C source and Zig build, with its license and sources. |
| `.github/workflows/` | The GitHub Actions build that makes the release jars. |

## Licenses
Retro64 Plus is under the [MIT license](LICENSE.md). It's a rewrite of Retro64, which is also MIT; Retro64's copyright
notice is kept in `LICENSE.md`.

The native library keeps its own notices. `native/SOURCE.md` lists every part, and the jars carry it with the
licenses beside the library in `natives/`:

| Part | License |
|---|---|
| libsm64 | [CC0 1.0](native/LICENSE-libsm64.md) |
| libsm64-retro64, the fork it's built from | None stated |
| Code from the Super Mario 64 decompilation and sm64-port | None stated |
| dlmalloc | Public domain |
| stb_image, stb_image_write | Public domain or MIT |
| Retro64 Plus's changes to it | MIT |
