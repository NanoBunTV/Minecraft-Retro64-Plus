//! Builds libsm64 (the vendored libsm64-retro64, D7) for every supported platform (D5) in one go:
//!   zig build   -> zig-out/natives/<os>-<arch>/sm64.dll | libsm64.so
//! The natives/<os>-<arch>/ layout matches where the mod jars carry them (TechnicalArchitecture.md).
const std = @import("std");

/// D5: Windows x64 and Linux x64 only. glibc 2.28 keeps the Linux library loadable on older distros (as Cartridge).
const targets = [_]struct { triple: []const u8, dir: []const u8, name: []const u8 }{
    .{ .triple = "x86_64-windows-gnu", .dir = "windows-x86_64", .name = "sm64" },
    .{ .triple = "x86_64-linux-gnu.2.28", .dir = "linux-x86_64", .name = "sm64" },
};

/// Upstream's Makefile defines, plus flags that keep clang from optimizing on the decomp's undefined behavior.
/// The decomp is old C that clang warns about a lot; upstream built it with gcc and no -Werror either.
const c_flags = [_][]const u8{
    "-DSM64_LIB_EXPORT",
    "-DGBI_FLOATS",
    "-DVERSION_US",
    "-DNO_SEGMENTED_MEMORY",
    "-fno-strict-aliasing",
    "-fwrapv",
    "-fno-sanitize=undefined",
    "-w",
    // Errors in clang but only warnings in the gcc upstream used.
    "-Wno-error=implicit-function-declaration",
    "-Wno-error=incompatible-pointer-types",
    "-Wno-error=incompatible-function-pointer-types",
    "-Wno-error=int-conversion",
};

/// The sources upstream's Makefile compiles (its SRC_DIRS), relative to src/.
const common_sources = [_][]const u8{
    "debug_print.c",
    "errorHandler.c",
    "fake_interaction.c",
    "fpsLimitHelper.c",
    "gfx_adapter.c",
    "libsm64.c",
    "load_anim_data.c",
    "load_surfaces.c",
    "load_tex_data.c",
    "model_handler.c",
    "obj_pool.c",
    "decomp/global_state.c",
    "decomp/memory.c",
    "decomp/engine/geo_layout.c",
    "decomp/engine/graph_node_manager.c",
    "decomp/engine/graph_node.c",
    "decomp/engine/guMtxF2L.c",
    "decomp/engine/math_util.c",
    "decomp/engine/surface_collision.c",
    "decomp/game/behavior_actions.c",
    "decomp/game/config.c",
    "decomp/game/interaction.c",
    "decomp/game/mario_actions_airborne.c",
    "decomp/game/mario_actions_automatic.c",
    "decomp/game/mario_actions_cutscene.c",
    "decomp/game/mario_actions_moving.c",
    "decomp/game/mario_actions_object.c",
    "decomp/game/mario_actions_stationary.c",
    "decomp/game/mario_actions_submerged.c",
    "decomp/game/mario_misc.c",
    "decomp/game/mario_step.c",
    "decomp/game/mario.c",
    "decomp/game/object_stuff.c",
    "decomp/game/platform_displacement.c",
    "decomp/game/rendering_graph_node.c",
    "decomp/pc/alBnkfNew.c",
    "decomp/pc/configfile.c",
    "decomp/pc/dlmalloc.c",
    "decomp/pc/mixer.c",
    "decomp/pc/ultra_reimplementation.c",
    "decomp/mario/geo.inc.c",
    "decomp/tools/audioStructPrint.c",
    "decomp/tools/convUtils.c",
    "decomp/tools/displaylist_parser.c",
    "decomp/tools/geolayout_parser.c",
    "decomp/tools/libmio0.c",
    "decomp/tools/n64graphics.c",
    "decomp/tools/utils.c",
    "decomp/audio/data.c",
    "decomp/audio/effects.c",
    "decomp/audio/external.c",
    "decomp/audio/globals_start.c",
    "decomp/audio/heap.c",
    "decomp/audio/load_dat.c",
    "decomp/audio/load.c",
    "decomp/audio/playback.c",
    "decomp/audio/seqplayer.c",
    "decomp/audio/synthesis.c",
    // Mario is the only model: Luigi and the player's skin are drawn by the mod (D15, D28, D33).
};

pub fn build(b: *std.Build) void {
    const optimize = b.option(std.builtin.Optimize, "optimize", "Optimization mode (default: fast)") orelse .fast;

    for (targets) |t| {
        const query = std.Target.Query.parse(.{ .arch_os_abi = t.triple }) catch @panic("bad target triple");
        const lib = addLib(b, b.resolveTargetQuery(query), optimize, t.name);
        const install = b.addInstallArtifact(lib, .{
            .dest_dir = .{ .override = .{ .custom = b.fmt("natives/{s}", .{t.dir}) } },
            .pdb_dir = .disabled,
            .implib_dir = .disabled,
        });
        b.getInstallStep().dependOn(&install.step);
    }
}

fn addLib(b: *std.Build, target: std.Build.ResolvedTarget, optimize: std.builtin.Optimize, name: []const u8) *std.Build.Step.Compile {
    const windows = target.result.os.tag == .windows;
    const module = b.createModule(.{
        .target = target,
        .optimize = optimize,
        .link_libc = true,
        .strip = optimize != .debug,
        .pic = true,
    });
    // No system audio backends (D21): the mod pulls samples with sm64_audio_tick and plays them itself.
    module.addCSourceFiles(.{ .root = b.path("src"), .files = &common_sources, .flags = &c_flags });
    if (windows) {
        // Win7 API level: newer mingw headers otherwise pull MapViewOfFileNuma2 (onecore) into dlmalloc.
        module.addCMacro("_WIN32_WINNT", "0x0601");
        module.linkSystemLibrary("imagehlp", .{}); // errorHandler.c
    } else {
        module.linkSystemLibrary("pthread", .{});
        module.linkSystemLibrary("m", .{});
    }
    return b.addLibrary(.{ .name = name, .linkage = .dynamic, .root_module = module });
}
