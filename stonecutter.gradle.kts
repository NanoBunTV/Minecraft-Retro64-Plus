plugins {
    id("dev.kikugie.stonecutter")
}

// The version whose code lives in the shared src/ folders right now. Switch with the "Set active project" tasks.
stonecutter active "1.21.1"

// The native library (D7): `zig build` in native/ for every platform (D5); the loader jars carry the result under
// natives/<os>-<arch>/. Zig comes from PATH, or from the ZIG environment variable (full path to zig/zig.exe).
// Every build compiles it from source, never from a committed binary (D135): release jars come from the GitHub
// Actions workflow (.github/workflows/build.yml), so anyone can check the library in a jar came from native/.
val native = rootProject.file("native")
tasks.register<Exec>("buildNative") {
    group = "build"
    description = "Builds libsm64 for all platforms with Zig."
    workingDir = native
    commandLine(providers.environmentVariable("ZIG").getOrElse("zig"), "build")
    inputs.dir(native.resolve("src"))
    inputs.files(native.resolve("build.zig"), native.resolve("build.zig.zon"))
    outputs.dir(native.resolve("zig-out/natives"))
}
