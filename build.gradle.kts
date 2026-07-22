plugins {
    id("gg.meza.stonecraft")
}

group = "net.minegasm"
version = providers.gradleProperty("mod_version").get()

repositories {
    mavenCentral()
    maven("https://maven.neoforged.net/releases/")
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

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

// A loader's manifest (fabric.mod.json / neoforge.mods.toml) is identical across that loader's
// Minecraft lines — version-specific values come from `${...}` tokens resolved per variant — so it
// lives once in `loader-resources/<loader>` instead of one copy per `versions/<mc>-<loader>`
// (ADR-013). Adding it only to that loader's variants means no jar carries another loader's manifest.
// Forge keeps its own copy under `versions/26.2-forge` while unregistered.
sourceSets.named("main") {
    resources.srcDir(rootProject.file("loader-resources/${project.name.substringAfterLast('-')}"))
}

// Java 25 toolchain for all 26.x variants (brief §4.1, ADR-002). Independent of developer JAVA_HOME.
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
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

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// Standalone Intiface connectivity + safe-test-pulse harness (no Minecraft). Example:
//   ./gradlew intifaceProbe --args="--url ws://127.0.0.1:12345 --backend buttplug4j"
tasks.register<JavaExec>("intifaceProbe") {
    group = "verification"
    description = "Connect to a running Intiface server, list device features, and send a safe test pulse."
    mainClass.set("net.minegasm.tools.IntifaceProbe")
    classpath = sourceSets["main"].runtimeClasspath
}
