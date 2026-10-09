import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.Project
import java.io.File
import org.gradle.kotlin.dsl.getByType

val Project.sc: StonecutterBuildExtension get() = extensions.getByType()

/** A value from stonecutter.properties.toml (version tables resolved for this project). */
fun Project.prop(key: String): String = requireNotNull(findProperty(key)?.toString()) { "Missing property '$key' in $path" }

/** A value from stonecutter.properties.toml, or null when it is missing or empty (a dependency this version lacks). */
fun Project.optionalProp(key: String): String? = findProperty(key)?.toString()?.takeIf { it.isNotEmpty() }

/** The Minecraft version of this Stonecutter node. */
val Project.mcVersion: String get() = sc.current.version

/** The loader part of this node's branch: common, fabric or neoforge. */
val Project.part: String get() = sc.branch.id

/** The `common` node for the same Minecraft version as this loader node. */
val Project.commonPath: String get() = ":common:${sc.current.project}"

/** A dev run's folder: `runs/<branch>/<side>` for 1.21.1, `runs/<branch>/<version>/<side>` for any other version. */
fun Project.devRunDir(side: String): File =
    rootProject.file(if (mcVersion == "1.21.1") "runs/${sc.branch.id}/$side" else "runs/${sc.branch.id}/$mcVersion/$side")

/** Which of 1.21.1's data formats [convertData] changes for the version being built (as Cartridge's, Porting.md). */
data class DataFormats(
    /** 1.21.2+: recipe ingredients `{"item": "x"}` / `{"tag": "y"}` become `"x"` / `"#y"`. */
    val ingredients: Boolean,
    /** 26.2+: an entity predicate's `type` became the `minecraft:entity_type` sub-predicate. */
    val entityTypeKey: Boolean,
    /**
     * 26.3+: lists of loot conditions become one condition (several in an `all_of`), lists of loot functions become
     * a `modifier`, and both name their type with `type` instead of `condition` / `function`; the
     * `recipe_unlocked` trigger's `recipe` became `recipes`.
     */
    val singleConditions: Boolean,
    /** 26.3+: configured features became plain features (`worldgen/feature/`), their type and no `config`. */
    val plainFeatures: Boolean = false,
    /** 26.3+: a spawner's `minCount` / `maxCount` became `count`, an int provider (NeoForge's biome modifiers). */
    val spawnCount: Boolean = false,
    /** 1.21.9+: vanilla ids that were renamed, as `old` to `new` (the chain became `iron_chain`). */
    val idRenames: Map<String, String> = emptyMap(),
)

/**
 * Rewrites the data JSON in [resources] (written in 1.21.1's formats) for the version being built. Tags and world
 * generation are left alone.
 */
fun convertData(resources: File, formats: DataFormats) {
    val data = resources.resolve("data")
    data.walkTopDown()
        .filter { it.isFile && it.extension == "json" }
        .forEach { file ->
            val folder = file.relativeTo(data).invariantSeparatorsPath.split('/')[1]
            if (folder !in setOf("recipe", "loot_table", "advancement", "neoforge")) return@forEach
            var json = JsonSlurper().parse(file)
            if (formats.ingredients && folder == "recipe") json = recipeIngredients(json)
            if (formats.entityTypeKey) json = entityTypeKey(json)
            if (formats.singleConditions) json = singleConditions(json)
            if (formats.singleConditions && folder == "advancement") json = recipesUnlocked(json)
            if (formats.spawnCount && folder == "neoforge") json = spawnCount(json)
            if (formats.idRenames.isNotEmpty()) json = renameIds(json, formats.idRenames)
            if (json != JsonSlurper().parse(file)) file.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(json)))
        }
    if (formats.plainFeatures) {
        data.listFiles().orEmpty().forEach { namespace ->
            val configured = namespace.resolve("worldgen/configured_feature")
            if (!configured.isDirectory) return@forEach
            val target = namespace.resolve("worldgen/feature").apply { mkdirs() }
            configured.listFiles().orEmpty().filter { it.extension == "json" }.forEach { file ->
                @Suppress("UNCHECKED_CAST")
                val json = JsonSlurper().parse(file) as Map<String, Any?>
                val config = json["config"] as? Map<*, *>
                val plain = json.filterKeys { it != "config" } + (config.orEmpty() as Map<String, Any?>)
                target.resolve(file.name).writeText(JsonOutput.prettyPrint(JsonOutput.toJson(plain)))
                file.delete()
            }
            configured.delete()
        }
    }
}

private fun renameIds(value: Any?, renames: Map<String, String>): Any? = when (value) {
    is String -> renames[value] ?: value
    is Map<*, *> -> value.mapValues { renameIds(it.value, renames) }
    is List<*> -> value.map { renameIds(it, renames) }
    else -> value
}

/** A spawner's `minCount` / `maxCount` become `count`: the number itself, or a uniform range (26.3). */
private fun spawnCount(value: Any?): Any? = when (value) {
    is Map<*, *> -> {
        val min = value["minCount"]
        val max = value["maxCount"]
        if (min is Int && max is Int) {
            value.filterKeys { it != "minCount" && it != "maxCount" } + ("count" to
                if (min == max) min else mapOf("type" to "minecraft:uniform", "min_inclusive" to min, "max_inclusive" to max))
        } else {
            value.mapValues { spawnCount(it.value) }
        }
    }
    is List<*> -> value.map(::spawnCount)
    else -> value
}

private fun recipeIngredients(recipe: Any?): Any? {
    if (recipe !is Map<*, *>) return recipe
    return recipe.mapValues { (key, value) ->
        when (key) {
            "ingredient", "ingredients", "base", "addition", "template" -> ingredient(value)
            "key" -> (value as Map<*, *>).mapValues { ingredient(it.value) }
            else -> value
        }
    }
}

private fun ingredient(value: Any?): Any? = when (value) {
    is List<*> -> value.map(::ingredient)
    is Map<*, *> -> value["item"] ?: value["tag"]?.let { "#$it" } ?: value
    else -> value
}

private fun entityTypeKey(value: Any?): Any? = when (value) {
    is List<*> -> value.map(::entityTypeKey)
    is Map<*, *> -> value.entries.associate { (key, child) ->
        val converted = entityTypeKey(child)
        if (key == "predicate" && value["condition"] == "minecraft:entity_properties" && converted is Map<*, *>) {
            key to converted.mapKeys { if (it.key == "type") "minecraft:entity_type" else it.key }
        } else {
            key to converted
        }
    }
    else -> value
}

private fun singleConditions(value: Any?): Any? = when (value) {
    is List<*> -> value.map(::singleConditions)
    is Map<*, *> -> value.entries.associate { (key, child) ->
        val converted = singleConditions(child)
        when {
            key == "conditions" && converted is List<*> -> "condition" to allOf(converted)
            (key == "entity" || key == "player") && converted is List<*> -> key to allOf(converted)
            key == "functions" && converted is List<*> -> "modifier" to (converted.singleOrNull() ?: converted)
            (key == "condition" || key == "function") && converted is String -> "type" to converted
            else -> key to converted
        }
    }
    else -> value
}

/** An advancement's `recipe_unlocked` criteria: `{"recipe": x}` became `{"recipes": x}` (26.3). */
private fun recipesUnlocked(value: Any?): Any? = when (value) {
    is List<*> -> value.map(::recipesUnlocked)
    is Map<*, *> -> {
        val conditions = value["conditions"]
        if (value["trigger"] == "minecraft:recipe_unlocked" && conditions is Map<*, *>) {
            value + ("conditions" to conditions.mapKeys { if (it.key == "recipe") "recipes" else it.key })
        } else {
            value.mapValues { recipesUnlocked(it.value) }
        }
    }
    else -> value
}

private fun allOf(conditions: List<*>): Any? =
    conditions.singleOrNull() ?: mapOf("type" to "minecraft:all_of", "terms" to conditions)
