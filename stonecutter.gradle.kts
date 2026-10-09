plugins {
    id("dev.kikugie.stonecutter")
}

// The version whose code lives in the shared src/ folders right now. Switch with the "Set active project" tasks.
stonecutter active "1.21.1"

// The native library (D7): `zig build` in native/ for every platform (D5); the loader jars carry the result under
// natives/<os>-<arch>/. Zig comes from PATH, or from the ZIG environment variable (full path to zig/zig.exe).
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

// The jars carry the committed builds in natives/ (D132), so a normal build needs no Zig. After changing native/,
// `gradlew updateNatives` rebuilds both platforms and copies them there; commit natives/ with the change.
tasks.register<Sync>("updateNatives") {
    group = "build"
    description = "Rebuilds libsm64 with Zig and copies it into natives/ (commit the result)."
    dependsOn("buildNative")
    from(native.resolve("zig-out/natives"))
    into(rootProject.file("natives"))
}

// Release jars: `gradlew release` builds every version and copies the loader jars (no sources jars) to
// _Release/<mod.version>/, as the other mod projects keep them (Publishing.md).
val modVersion = Regex("""(?m)^mod\.version\s*=\s*"([^"]+)"""")
    .find(rootProject.file("stonecutter.properties.toml").readText())!!.groupValues[1]
tasks.register<Copy>("release") {
    group = "build"
    description = "Builds all versions and copies the release jars to _Release/$modVersion."
    val loaders = listOf("fabric", "neoforge")
    dependsOn(loaders.flatMap { loader -> stonecutter.versions.map { ":$loader:${it.project}:build" } })
    loaders.forEach { loader ->
        stonecutter.versions.forEach { v ->
            from(rootProject.file("$loader/versions/${v.project}/build/libs")) {
                include("retro64plus-$loader-$modVersion+${v.project}.jar")
            }
        }
    }
    into(rootProject.file("_Release/$modVersion"))
}
