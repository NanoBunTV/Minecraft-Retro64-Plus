plugins {
    id("multiloader-loader")
    id("net.neoforged.moddev")
}

val modId = prop("mod.id")

neoForge {
    version = prop("deps.neoforge_version")
    optionalProp("deps.parchment")?.let {
        parchment {
            minecraftVersion = prop("deps.parchment_mc")
            mappingsVersion = it
        }
    }

    runs {
        register("client") {
            client()
            gameDirectory = devRunDir("client")
            // -PquickPlay="World Name" opens that singleplayer world straight away; -PquickPlayServer=host:port joins
            // that server (DevSetup).
            providers.gradleProperty("quickPlay").orNull?.let { programArguments.addAll("--quickPlaySingleplayer", it) }
            providers.gradleProperty("quickPlayServer").orNull?.let { programArguments.addAll("--quickPlayMultiplayer", it) }
        }
        // A second player for multiplayer tests, with its own game folder and name (DevSetup).
        register("client2") {
            client()
            gameDirectory = devRunDir("client2")
            programArguments.addAll("--username", "Dev2")
            providers.gradleProperty("quickPlayServer").orNull?.let { programArguments.addAll("--quickPlayMultiplayer", it) }
        }
        register("server") {
            server()
            gameDirectory = devRunDir("server")
            programArgument("--nogui")
        }
        configureEach {
            ideName = "NeoForge ${name.replaceFirstChar(Char::uppercase)}"
        }
    }

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    // D23: YetAnotherConfigLib, a required dependency.
    implementation(prop("deps.yacl_neoforge"))
    // D31: compile only, for the compat mixin (common/.../mixin/compat); never shipped or required.
    optionalProp("deps.controlify_common")?.let { compileOnly(it) { isTransitive = false } }
}
