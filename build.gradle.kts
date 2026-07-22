plugins {
    id("gg.meza.stonecraft")
}

group = "net.minegasm"
version = providers.gradleProperty("mod_version").get()

repositories {
    mavenCentral()
    maven("https://maven.neoforged.net/releases/")
    // ModMenu (Fabric-only config-screen entry), from Modrinth's maven. Content-filtered to the
    // maven.modrinth group so this repo is never queried for other artifacts — otherwise a transient
    // 5xx here would disable the repo and break unrelated dynamic-version resolutions (e.g. Forge's
    // dev.architectury:mixin-patched). Modrinth is used rather than ModMenu's own Terraformers maven,
    // which has proven flaky (502s); both serve the same distributed jar.
    maven("https://api.modrinth.com/maven") {
        content { includeGroup("maven.modrinth") }
    }
}

modSettings {
    runDirectory = rootProject.layout.projectDirectory.dir("run")
    clientOptions {
        narrator = false
        musicVolume = 0.0
        guiScale = 3
    }
    variableReplacements = mapOf(
        "id" to "minegasm",
        "name" to "Minegasm",
        "group" to "net.minegasm",
        "description" to providers.gradleProperty("mod_description_short").get(),
        "description_long" to providers.gradleProperty("mod_description_long").get(),
        "homepage" to providers.gradleProperty("mod_homepage").get(),
        "version" to project.version.toString(),
    )
}

dependencies {
    // Primary Buttplug v4 client: buttplug4j (feature-based spec), via its Jetty WebSocket connector.
    // Pinned to the latest release; pulls in Jetty + Jackson. Shade/jar-in-jar so it does not collide
    // with other mods (brief §4.2, §9.2, ADR-006).
    implementation("io.github.blackspherefollower:buttplug4j.connectors.jetty.websocket.client:4.0.278")

    // Fallback native provider (net.minegasm.buttplug): raw v4 over the JDK WebSocket + Gson, no extra
    // runtime deps. Also the in-process test backend.
    implementation("com.google.code.gson:gson:2.11.0")

    // Loom's include configuration is intentionally non-transitive. Keep this list aligned with
    // the resolved buttplug4j connector graph so release jars contain every private runtime class.
    // Gson is deliberately not included: Minecraft/NeoForge already supplies it at runtime.
    include("io.github.blackspherefollower:buttplug4j.connectors.jetty.websocket.client:4.0.278")
    include("io.github.blackspherefollower:buttplug4j:4.0.278")
    include("com.fasterxml.jackson.core:jackson-annotations:2.20")
    include("com.fasterxml.jackson.core:jackson-core:2.20.1")
    include("com.fasterxml.jackson.core:jackson-databind:2.20.1")
    include("org.eclipse.jetty.websocket:websocket-client:9.4.58.v20250814")
    include("org.eclipse.jetty.websocket:websocket-common:9.4.58.v20250814")
    include("org.eclipse.jetty.websocket:websocket-api:9.4.58.v20250814")
    include("org.eclipse.jetty:jetty-client:9.4.58.v20250814")
    include("org.eclipse.jetty:jetty-http:9.4.58.v20250814")
    include("org.eclipse.jetty:jetty-io:9.4.58.v20250814")
    include("org.eclipse.jetty:jetty-util:9.4.58.v20250814")

    // ModMenu (Fabric only) gives the config screen a mods-list entry (net.minegasm.fabric.
    // ModMenuIntegration, wired via the `modmenu` entrypoint in fabric.mod.json). Compile-only: that
    // entrypoint is invoked only when ModMenu is installed, so the mod neither bundles nor requires it
    // at runtime. ModMenu is published per Minecraft line, so the version tracks the variant; the
    // ModMenuApi/ConfigScreenFactory surface used here is identical across 11.0.4, 18.0.0 and 20.0.0,
    // so the shared source needs no version guard. Kept as an explicit map (not a deps-file property)
    // so the wiring is self-contained and cannot silently no-op.
    val modmenuVersion = when (project.name) {
        "26.2-fabric" -> "20.0.0"
        "26.1.2-fabric" -> "18.0.0"
        "1.21.1-fabric" -> "11.0.4"
        "1.20.1-fabric" -> "7.2.2"
        else -> null
    }
    if (modmenuVersion != null) {
        // ModMenu ends up on the compile classpath only: NOT bundled (only `include` bundles), NOT on
        // the runtime classpath, and adds no `depends` entry to fabric.mod.json — so ModMenu stays
        // optional for end users.
        //
        // The 26.x builds are published mojmap-native and resolve against this project's mappings as-is
        // via plain `compileOnly`. The older builds for 1.21.1 (11.0.4) and 1.20.1 (7.2.2) are published
        // against intermediary mappings, so they must go through Loom's `modCompileOnly`, which remaps
        // the mod jar from intermediary to the project's mappings at compile time (plain `compileOnly`
        // skips that remap, leaving intermediary names like `class_437` on the classpath and failing
        // compilation).
        if (project.name == "1.21.1-fabric" || project.name == "1.20.1-fabric") {
            "modCompileOnly"("maven.modrinth:modmenu:$modmenuVersion")
        } else {
            compileOnly("maven.modrinth:modmenu:$modmenuVersion")
        }
    }

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    // Forge's test executor does not auto-provision the JUnit Platform launcher the way the Fabric/
    // NeoForge test tasks do; declare it explicitly so `:*-forge:test` can start. Harmless for the
    // other loaders, which already resolve it transitively.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// NeoForge's userdev "junit" run config auto-registers net.neoforged.fancymodloader:junit-fml's
// LauncherSessionListener (ServiceLoader, via META-INF/services), which tries to boot a Minecraft
// transforming classloader before any test runs. That bootstrap works on the 26.x lines but is broken
// under this project's pinned Architectury Loom for NeoForge 21.1.x (1.21.1): it fails reading its
// launch-args file regardless of content. The engine/config test suite is pure JVM code with no
// net.minecraft/net.neoforged imports (see src/test), so it never needed that bootstrap — excluding the
// jar here removes the auto-registered listener entirely rather than working around its bootstrap.
if (project.name == "1.21.1-neoforge") {
    configurations.named("testRuntimeClasspath") {
        exclude(group = "net.neoforged.fancymodloader", module = "junit-fml")
    }
}

// A loader's manifest (fabric.mod.json / neoforge.mods.toml) is identical across that loader's
// Minecraft lines — version-specific values come from `${...}` tokens resolved per variant — so it
// lives once in `loader-resources/<loader>` instead of one copy per `versions/<mc>-<loader>`
// (ADR-013). Adding it only to that loader's variants means no jar carries another loader's manifest.
// Forge keeps its own copy under `versions/26.2-forge` while unregistered.
sourceSets.named("main") {
    resources.srcDir(rootProject.file("loader-resources/${project.name.substringAfterLast('-')}"))
}

// Java 25 for the 26.x lines (brief §4.1, ADR-002); Minecraft 1.21.1 requires Java 21 and 1.20.1
// requires Java 17. Independent of developer JAVA_HOME.
java {
    val minecraftVersion = project.name.substringBeforeLast('-')
    val javaVersion = when (minecraftVersion) {
        "1.20.1" -> 17
        "1.21.1" -> 21
        else -> 25
    }
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.named<Jar>("jar") {
    val minecraftVersion = project.name.substringBeforeLast('-')
    val loader = project.name.substringAfterLast('-')
    archiveFileName.set("minegasm-${project.version}+mc${minecraftVersion}-${loader}.jar")
    from(rootProject.file("LICENSE")) {
        into("META-INF")
        rename { "LICENSE-minegasm" }
    }
}

// Loom's remapJar defaults to build/libs/minegasm-<loader>-<version>.jar on the 1.21.1 line instead of
// reusing the `jar` task's archiveFileName above the way it does on the 26.x lines (root cause not
// pinned down — likely stonecraft's own naming falling back to a default it doesn't special-case for a
// Minecraft version it doesn't otherwise recognize). Force it explicitly so every variant's final
// artifact name matches stonecutter.gradle.kts's `installJars`, which expects this exact pattern.
tasks.matching { it.name == "remapJar" }.configureEach {
    val minecraftVersion = project.name.substringBeforeLast('-')
    val loader = project.name.substringAfterLast('-')
    (this as org.gradle.jvm.tasks.Jar).archiveFileName.set(
        "minegasm-${project.version}+mc${minecraftVersion}-${loader}.jar")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// The stonecraft plugin's `generatePackMCMetaJson` (registered only on the Forge variant) writes the
// generated pack.mcmeta into `build/resources/main`, which is on the test source set's compile
// classpath, but the plugin doesn't declare that task relationship. Gradle's strict task-output
// validation then fails `compileTestJava`. Wire it explicitly, empty-safe so the non-Forge variants
// (which have no such task) are unaffected. (Latent until Architectury Loom 1.17.491 made pack.mcmeta
// generation actually run for the 26.x lines.)
tasks.matching { it.name == "compileTestJava" }.configureEach {
    dependsOn(tasks.matching { it.name == "generatePackMCMetaJson" })
}

// Standalone Intiface connectivity + safe-test-pulse harness (no Minecraft). Example:
//   ./gradlew intifaceProbe --args="--url ws://127.0.0.1:12345 --backend buttplug4j"
tasks.register<JavaExec>("intifaceProbe") {
    group = "verification"
    description = "Connect to a running Intiface server, list device features, and send a safe test pulse."
    mainClass.set("net.minegasm.tools.IntifaceProbe")
    classpath = sourceSets["main"].runtimeClasspath
}
