import net.fabricmc.loom.api.LoomGradleExtensionAPI

plugins {
    id("multiloader-loader")
}

// Minecraft is obfuscated up to 1.21.11: fabric-loom-remap maps it (Mojang names + Parchment) and remaps mod
// dependencies. 26.1+ ships unobfuscated, so the plain fabric-loom plugin is used with ordinary configurations.
val obfuscated = sc.eval(mcVersion, "<26.1")
apply(plugin = if (obfuscated) "net.fabricmc.fabric-loom-remap" else "net.fabricmc.fabric-loom")
val loom = extensions.getByType<LoomGradleExtensionAPI>()
fun mod(configuration: String) = if (obfuscated) "mod" + configuration.replaceFirstChar(Char::uppercase) else configuration

dependencies {
    "minecraft"("com.mojang:minecraft:$mcVersion")
    if (obfuscated) {
        "mappings"(loom.layered {
            officialMojangMappings()
            optionalProp("deps.parchment")?.let {
                parchment("org.parchmentmc.data:parchment-${prop("deps.parchment_mc")}:$it@zip")
            }
        })
    }
    mod("implementation")("net.fabricmc:fabric-loader:${prop("deps.fabric_loader")}")
    mod("implementation")("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric_api")}")
    // D23: YetAnotherConfigLib, a required, separately installed dependency (not bundled).
    mod("implementation")(prop("deps.yacl_fabric"))
    // D31: compile only, for the compat mixin (common/.../mixin/compat); never shipped or required.
    optionalProp("deps.controlify_fabric")?.let { mod("compileOnly")(it) { isTransitive = false } }
    // Optional: Mod Menu's Config button opens our YACL screen; on the dev runtime to test it.
    mod("compileOnly")("com.terraformersmc:modmenu:${prop("deps.modmenu")}") { isTransitive = false }
    mod("localRuntime")("com.terraformersmc:modmenu:${prop("deps.modmenu")}")
}

loom.apply {
    runs {
        named("client") {
            client()
            displayName = "Fabric Client"
            generateRunConfig = true
            runDirectory = devRunDir("client")
            // -PquickPlay="World Name" opens that singleplayer world straight away (DevSetup).
            providers.gradleProperty("quickPlay").orNull?.let { programArgs("--quickPlaySingleplayer", it) }
        }
        named("server") {
            server()
            displayName = "Fabric Server"
            generateRunConfig = true
            runDirectory = devRunDir("server")
            programArgs("--nogui")
        }
    }
}
