// Shared by the common, fabric and neoforge nodes (Jared's MultiLoader-Template layout, D8).
plugins {
    `java-library`
}

val modId = prop("mod.id")
val javaVersion = prop("java").toInt()

// Every node is named after its Minecraft version (":common:1.21.1", ":fabric:1.21.1"), so the group must differ
// per branch, or Gradle sees the three projects as one module and resolves the wrong one.
group = "${prop("mod.group")}.${sc.branch.id}"
version = "${prop("mod.version")}+$mcVersion"
base.archivesName = "$modId-$part"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = javaVersion
    options.compilerArgs.addAll(listOf("-Xmaxerrs", "10000"))
}

repositories {
    mavenCentral()
    exclusiveContent {
        forRepository { maven("https://repo.spongepowered.org/repository/maven-public") { name = "Sponge" } }
        filter { includeGroupAndSubgroups("org.spongepowered") }
    }
    exclusiveContent {
        forRepositories(
            maven("https://maven.parchmentmc.org/") { name = "ParchmentMC" },
            maven("https://maven.neoforged.net/releases") { name = "NeoForged" },
        )
        filter { includeGroup("org.parchmentmc.data") }
    }
    exclusiveContent {
        forRepository { maven("https://maven.terraformersmc.com/releases/") { name = "TerraformersMC" } }
        filter { includeGroup("com.terraformersmc") }
    }
    exclusiveContent {
        forRepository { maven("https://maven.isxander.dev/releases") { name = "isXander" } }
        filter { includeGroupAndSubgroups("dev.isxander") }
    }
    exclusiveContent {
        // YACL's JSON5 parser (D23)
        forRepository { maven("https://maven.quiltmc.org/repository/release/") { name = "QuiltMC" } }
        filter { includeGroup("org.quiltmc.parsers") }
    }
}

// D31: where there's no Controlify build to compile the compat mixin against, a compile-only stand-in of the one class
// it names (common/src/stubs), never packed into a jar.
if (optionalProp("deps.controlify_common") == null) {
    val stubs = sourceSets.create("controlifyStubs") {
        java.srcDir(rootProject.file("common/src/stubs/java"))
    }
    dependencies { "compileOnly"(stubs.output) }
}

tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE.md")) { rename { "LICENSE_$modId.md" } }
    manifest.attributes(
        "Specification-Title" to prop("mod.name"),
        "Specification-Vendor" to prop("mod.authors"),
        "Implementation-Title" to project.path,
        "Implementation-Version" to project.version,
        "Built-On-Minecraft" to mcVersion,
    )
}

// Renames between Minecraft versions that need no other change (Porting.md). src/ holds 1.21.1's names; Stonecutter
// rewrites them for other nodes, and back again when switching the active version. A rename also runs backwards on
// the versions where it is off, so the new name must never appear in src/ for any other reason. (Cartridge's list.)
fun rename(since: String, from: String, to: String) =
    sc.replacements.string(sc.eval(mcVersion, ">=$since")) { replace(from, to) }

rename("1.21.11", "ResourceLocation", "Identifier")
// GameProfile became a record (authlib 7, 1.21.9).
rename("1.21.9", "getGameProfile().getName()", "getGameProfile().name()")
rename("1.21.9", "getProfile().getName()", "getProfile().name()")
// A resource key's id.
rename("1.21.11", "dimension().location()", "dimension().identifier()")
// The window's GLFW handle.
rename("1.21.9", "getWindow().getWindow()", "getWindow().handle()")
rename("1.21.9", "window.getWindow()", "window.handle()")
// Util moved into the util package.
rename("1.21.11", "import net.minecraft.Util;", "import net.minecraft.util.Util;")
// 26.1: the GUI extracts render state; its context class was renamed.
sc.replacements.regex(sc.eval(mcVersion, ">=26.1")) {
    replace("""\bGuiGraphics\b""", "GuiGraphicsExtractor", """\bGuiGraphicsExtractor\b""", "GuiGraphics")
}
// 26.1: light coordinates' helpers left LightTexture.
sc.replacements.regex(sc.eval(mcVersion, ">=26.1")) {
    replace("""\bLightTexture\.""", "LightCoordsUtil.", """\bLightCoordsUtil\.""", "LightTexture.")
}
rename("26.1", "import net.minecraft.client.renderer.LightTexture;", "import net.minecraft.util.LightCoordsUtil;")
// 26.2: the open screen belongs to the GUI.
sc.replacements.regex(sc.eval(mcVersion, ">=26.2")) {
    replace("""\b(mc|minecraft|Minecraft\.getInstance\(\))\.screen\b(?!\()""", "$1.gui.screen()",
        """\b(mc|minecraft|Minecraft\.getInstance\(\))\.gui\.screen\(\)""", "$1.screen")
}
sc.replacements.regex(sc.eval(mcVersion, ">=26.2")) {
    replace("""\b(mc|minecraft|Minecraft\.getInstance\(\))\.setScreen\(""", "$1.gui.setScreen(",
        """\b(mc|minecraft|Minecraft\.getInstance\(\))\.gui\.setScreen\(""", "$1.setScreen(")
}
// 26.1: the camera's render state moved into the level states.
rename("26.1", "net.minecraft.client.renderer.state.CameraRenderState", "net.minecraft.client.renderer.state.level.CameraRenderState")
// 26.2: the game renderer's camera and main render target are plain accessors.
rename("26.2", "gameRenderer.getMainCamera()", "gameRenderer.mainCamera()")
rename("26.2", "minecraft.getMainRenderTarget()", "minecraft.gameRenderer.mainRenderTarget()")
rename("26.2", "mc.getMainRenderTarget()", "mc.gameRenderer.mainRenderTarget()")
// 26.3: the GPU abstraction moved out of blaze3d into renderpearl; keys are SDL's; small renames.
rename("26.3", "import com.mojang.blaze3d.textures.", "import com.mojang.renderpearl.api.textures.")
rename("26.3", "InputConstants.Type.KEYSYM", "InputConstants.Type.KEYBOARD")
rename("26.3", ".mulPose(ClientCompat.cameraRotation())", ".rotate(ClientCompat.cameraRotation())")
rename("26.3", "display.getType()", "display.type()")
// 26.3: PoseStack rotates by a quaternion itself.
rename("26.3", ".mulPose(Axis.", ".rotate(Axis.")
// Fabric API for 26.1 renamed its classes after Mojang's names.
rename("26.1", "import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;",
    "import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;")
rename("26.1", "KeyBindingHelper.registerKeyBinding(", "KeyMappingHelper.registerKeyMapping(")
rename("26.1", "import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;",
    "import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;")
rename("26.1", "ItemGroupEvents.modifyEntriesEvent(", "CreativeModeTabEvents.modifyOutputEvent(")
// Resource keys name their id with identifier() from 1.21.11; registries hand out values by getValue from 1.21.2.
rename("1.21.11", "registry.location()", "registry.identifier()")
rename("1.21.11", "table.location()", "table.identifier()")
rename("1.21.2", "BuiltInRegistries.REGISTRY.get(", "BuiltInRegistries.REGISTRY.getValue(")
rename("26.1", "itemgroup.v1.FabricItemGroup", "creativetab.v1.FabricCreativeModeTab")
rename("26.1", "FabricItemGroup.builder", "FabricCreativeModeTab.builder")
rename("26.1", "PayloadTypeRegistry.playC2S()", "PayloadTypeRegistry.serverboundPlay()")
rename("26.1", "PayloadTypeRegistry.playS2C()", "PayloadTypeRegistry.clientboundPlay()")
// One way (the reverse never matches): src/ is always 1.21.1's, and the active version never switches.
fun oneWay(since: String, from: String, to: String) = sc.replacements.regex(sc.eval(mcVersion, ">=$since")) {
    replace(from, to, "(?!)x", "x")
}
// 1.21.2: an entity's push flag, the lowest build height, spawn reasons, registry lookups.
rename("1.21.2", "hasImpulse = true", "needsSync = true")
rename("1.21.2", "getMinBuildHeight()", "getMinY()")
oneWay("1.21.2", """\bMobSpawnType\b""", "EntitySpawnReason")
rename("1.21.2", ".registryOrThrow(", ".lookupOrThrow(")
// 1.21.2: item and block use results lost their client/server split.
oneWay("1.21.2", """InteractionResult\.sidedSuccess\((level|level\(\))\.isClientSide(\(\))?\)""", "InteractionResult.SUCCESS")
// 1.21.2: registries hand out values by getValue (get gives a holder); loot parameters.
rename("1.21.2", "BuiltInRegistries.ITEM.get(key)", "BuiltInRegistries.ITEM.getValue(key)")
// A name that changed more than once: each step is (first version with that name, the name). Stonecutter needs one
// replacement per source name, so this registers only the step for this version.
fun renames(from: String, vararg steps: Pair<String, String>) {
    val to = steps.lastOrNull { sc.eval(mcVersion, ">=${it.first}") }?.second ?: return
    sc.replacements.string(true) { replace(from, to) }
}
renames(".getParamOrNull(", "1.21.2" to ".contextMap().getOptional(", "26.3" to ".contextMap().get(")
// 1.21.2: queueing a task for the next round of the client's loop.
rename("1.21.2", "minecraft.tell(", "minecraft.schedule(")
// 1.21.5: two effects renamed.
rename("1.21.5", "MobEffects.MOVEMENT_SPEED", "MobEffects.SPEED")
rename("1.21.5", "MobEffects.MOVEMENT_SLOWDOWN", "MobEffects.SLOWNESS")
// 1.21.11: the camera's position and axes.
rename("1.21.11", "camera.getPosition()", "camera.position()")
rename("1.21.11", "camera.getLookVector()", "camera.forwardVector()")
rename("1.21.11", "camera.getUpVector()", "camera.upVector()")
rename("1.21.11", "camera.getLeftVector()", "camera.leftVector()")
// 1.21.9: a player's skin moved to the entity package; its texture is the body asset, its arms a model type.
rename("1.21.9", "import net.minecraft.client.resources.PlayerSkin;", "import net.minecraft.world.entity.player.PlayerSkin;")
rename("1.21.9", "skin.texture()", "skin.body().texturePath()")
rename("1.21.9", "PlayerSkin.Model.SLIM", "net.minecraft.world.entity.player.PlayerModelType.SLIM")
// 1.21.5: an owned mob's owner is a reference.
rename("1.21.5", "pet.getOwnerUUID() != null", "pet.getOwnerReference() != null")
// 1.21.5: block entity-only blocks draw nothing themselves.
rename("1.21.5", "RenderShape.ENTITYBLOCK_ANIMATED", "RenderShape.INVISIBLE")
// 1.21.11: villagers moved into their own package.
rename("1.21.11", "world.entity.npc.AbstractVillager", "world.entity.npc.villager.AbstractVillager")
// Level.isClientSide became a method; every read of the field becomes a call.
sc.replacements.regex(sc.eval(mcVersion, ">=1.21.9")) {
    replace("""\.isClientSide\b(?!\()""", ".isClientSide()", """\.isClientSide\(\)""", ".isClientSide")
}

tasks.processResources {
    val props = mapOf(
        "mod_id" to modId,
        "mod_name" to prop("mod.name"),
        "mod_version" to project.version.toString(),
        "mod_authors" to prop("mod.authors"),
        "mod_license" to prop("mod.license"),
        "mod_description" to prop("mod.description"),
        "mod_slug" to prop("mod.slug"),
        "mod_sources" to prop("mod.sources"),
        "mod_icon" to "assets/$modId/icon.png", // D110
        "neoforge_icon_key" to (if (sc.eval(mcVersion, ">=26.2")) "iconFile" else "logoFile"), // logoFile deprecated from 26.2
        "java_version" to javaVersion.toString(),
        "minecraft_version" to mcVersion,
        "pack_meta" to prop("mc.pack_meta"),
        "mc_compat_fabric" to prop("mc.compat_fabric"),
        "mc_compat_neoforge" to prop("mc.compat_neoforge"),
        "fabric_loader_version" to prop("deps.fabric_loader"),
        "neoforge_range" to prop("deps.neoforge_range"),
        "yacl_id" to prop("deps.yacl_id"),
        "yacl_range" to prop("deps.yacl_range"),
        // Client mixins that exist on this version only (the others' class files are commented out by Stonecutter).
        "mixins_client" to prop("mixins.client").split(" ").joinToString(", ") { "\"client.$it\"" },
    )
    inputs.properties(props)
    filesMatching(listOf("pack.mcmeta", "fabric.mod.json", "META-INF/neoforge.mods.toml", "*.mixins.json")) {
        expand(props)
    }
    // Without format fields for this version, the mod ships no pack.mcmeta and the loaders describe the pack.
    if (prop("mc.pack_meta").isEmpty()) exclude("pack.mcmeta")
    // Item model definitions (assets/<mod>/items/) exist from 1.21.4 only, with the spawn eggs' own models (1.21.5+).
    if (sc.eval(mcVersion, "<1.21.4")) {
        exclude("assets/*/items/**")
        exclude("assets/*/models/item/*_spawn_egg_v2.json", "assets/*/textures/item/*_spawn_egg.png")
    }
    // Data is written in 1.21.1's formats; the build converts it for other versions (Porting.md).
    val formats = DataFormats(
        ingredients = sc.eval(mcVersion, ">=1.21.2"),
        entityTypeKey = sc.eval(mcVersion, ">=26.2"),
        singleConditions = sc.eval(mcVersion, ">=26.3"),
        plainFeatures = sc.eval(mcVersion, ">=26.3"),
        spawnCount = sc.eval(mcVersion, ">=26.3"),
        idRenames = if (sc.eval(mcVersion, ">=1.21.9")) mapOf("minecraft:chain" to "minecraft:iron_chain") else emptyMap(),
    )
    inputs.property("dataFormats", formats.toString())
    doLast {
        convertData(destinationDir, formats)
    }
}

// ModDevGradle's Minecraft setup is memory-hungry; never run two at once (common and neoforge both use it).
interface MinecraftArtifactsMutex : org.gradle.api.services.BuildService<org.gradle.api.services.BuildServiceParameters.None>

val mutex = gradle.sharedServices.registerIfAbsent("retro64plusMinecraftArtifactsMutex", MinecraftArtifactsMutex::class) {
    maxParallelUsages = 1
}
tasks.matching { it.name == "createMinecraftArtifacts" }.configureEach { usesService(mutex) }
