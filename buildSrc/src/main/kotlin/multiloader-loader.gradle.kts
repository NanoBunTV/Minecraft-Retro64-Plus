// Applied to the fabric and neoforge nodes: compiles the common sources into each loader's jar,
// so the shipped jars are self-contained and `common` itself is never published.
plugins {
    id("multiloader-common")
}

val commonJava = configurations.create("commonJava") { isCanBeConsumed = false }
val commonResources = configurations.create("commonResources") { isCanBeConsumed = false }

dependencies {
    compileOnly(project(commonPath))
    commonJava(project(path = commonPath, configuration = "commonJava"))
    commonResources(project(path = commonPath, configuration = "commonResources"))
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(commonJava)
    source(commonJava)
}

tasks.processResources {
    dependsOn(commonResources)
    from(commonResources)
    // The native library (D7), extracted on the client at runtime (M1): the committed builds in natives/ (D132,
    // `gradlew updateNatives` rebuilds them), with libsm64's license and the exact sources beside them.
    from(rootProject.file("natives")) { into("natives") }
    from(rootProject.file("native")) { include("LICENSE-libsm64.md", "SOURCE.md"); into("natives") }
}

tasks.named<Jar>("sourcesJar") {
    dependsOn(commonJava, commonResources)
    from(commonJava)
    from(commonResources)
}
