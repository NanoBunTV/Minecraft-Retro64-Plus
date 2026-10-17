import net.neoforged.moddevgradle.dsl.NeoForgeExtension

// Loader-independent code, compiled against vanilla Minecraft (Mojang mappings + Parchment) through NeoForm.
plugins {
    id("multiloader-common")
    id("net.neoforged.moddev")
}

extensions.configure<NeoForgeExtension> {
    neoFormVersion = prop("deps.neoform")
    optionalProp("deps.parchment")?.let {
        parchment {
            minecraftVersion = prop("deps.parchment_mc")
            mappingsVersion = it
        }
    }
}

dependencies {
    compileOnly("org.spongepowered:mixin:${prop("deps.mixin")}")
    // Fabric and NeoForge both ship MixinExtras, so common code may use it.
    compileOnly("io.github.llamalad7:mixinextras-common:${prop("deps.mixinextras")}")
    annotationProcessor("io.github.llamalad7:mixinextras-common:${prop("deps.mixinextras")}")
    // D23: YetAnotherConfigLib draws the config screen. Its NeoForge build has Mojang names, as common does; each
    // loader ships its own build.
    compileOnly(prop("deps.yacl_common")) { isTransitive = false }
    // D31: the compat mixin that holds Controlify back while you're Mario. Never shipped or required.
    optionalProp("deps.controlify_common")?.let { compileOnly(it) { isTransitive = false } }
}

// Unit tests for the plain-Java parts (ROM detection, natives).
dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-api:2.0.9")
    // Minecraft 1.21.1 ships JNA 5.14.0 in game; tests need it on their own classpath.
    testImplementation("net.java.dev.jna:jna:5.14.0")
}

// The tests cover plain Java and the native library, the same on every Minecraft version: 1.21.1's node only.
tasks.matching { it.name in setOf("compileTestJava", "processTestResources", "test") }.configureEach {
    onlyIf { mcVersion == "1.21.1" }
}

tasks.test {
    useJUnitPlatform()
    // LibSM64RealRomTest runs the real native library on a real ROM, only when RETRO64_TEST_ROM points at one (the
    // ROM is never committed or copied into the repository).
    providers.environmentVariable("RETRO64_TEST_ROM").orNull?.let {
        systemProperty("retro64plus.testRom", it)
        inputs.property("testRom", it)
    }
}

// The native library built from native/ (natives/<os>-<arch>/, D135), in the same layout the loader jars carry.
tasks.processTestResources {
    dependsOn(":buildNative")
    from(rootProject.file("native/zig-out/natives")) { into("natives") }
}

// Hand the common sources and resources to the loader projects. For an inactive Stonecutter version these are
// the generated copies, so the loaders also wait for stonecutterGenerate.
val commonJava = configurations.consumable("commonJava")
val commonResources = configurations.consumable("commonResources")

afterEvaluate {
    sourceSets.main.get().java.sourceDirectories.files.forEach {
        artifacts.add(commonJava.name, it) { builtBy("stonecutterGenerate") }
    }
    sourceSets.main.get().resources.sourceDirectories.files.forEach {
        artifacts.add(commonResources.name, it) { builtBy("stonecutterGenerate") }
    }
}
