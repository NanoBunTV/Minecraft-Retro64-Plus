pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie" }
    }
}

plugins {
    // https://stonecutter.kikugie.dev — keep in sync with buildSrc/build.gradle.kts
    id("dev.kikugie.stonecutter") version "0.9.8"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// D8: MultiLoader-style common / fabric / neoforge projects, each a Stonecutter branch. D4, D123: 1.21.1, 1.21.11 and the
// 26.x line, Fabric and NeoForge on each. Gradle paths are :<branch>:<version>, e.g. :fabric:26.1.2.
val all = arrayOf("1.21.1", "1.21.11", "26.1.2", "26.2", "26.3")
stonecutter {
    create(rootProject) {
        branch("common") { versions(*all) }
        branch("fabric") { versions(*all) }
        branch("neoforge") { versions(*all) }
        vcsVersion = "1.21.1"
    }
}

rootProject.name = "Retro64Plus"
