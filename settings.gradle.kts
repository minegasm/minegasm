pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.kikugie.dev/snapshots")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev")
        maven("https://maven.minecraftforge.net")
        maven("https://maven.neoforged.net/releases/")
    }
}

plugins {
    id("gg.meza.stonecraft") version "1.12.2"
    id("dev.kikugie.stonecutter") version "0.9.6"
}

val modVersion = providers.gradleProperty("mod_version").get()
val modDescriptionShort = providers.gradleProperty("mod_description_short").get()
val modDescriptionLong = providers.gradleProperty("mod_description_long").get()
val modHomepage = providers.gradleProperty("mod_homepage").get()

// Mod identity. Working name "Minegasm" per the implementation brief; mod id `minegasm`.
extra["mod.id"] = "minegasm"
extra["mod.name"] = "Minegasm"
extra["mod.group"] = "net.minegasm"
extra["mod.description"] = modDescriptionShort
extra["mod.description.long"] = modDescriptionLong
extra["mod.homepage"] = modHomepage
extra["mod.version"] = modVersion

gradle.beforeProject {
    extra["mod.id"] = "minegasm"
    extra["mod.name"] = "Minegasm"
    extra["mod.group"] = "net.minegasm"
    extra["mod.description"] = modDescriptionShort
    extra["mod.description.long"] = modDescriptionLong
    extra["mod.homepage"] = modHomepage
    extra["mod.version"] = modVersion
}

stonecutter {
    centralScript = "build.gradle.kts"
    kotlinController = true
    shared {
        fun mc(version: String, vararg loaders: String) {
            for (loader in loaders) version("$version-$loader", version)
        }
        // NeoForge is the primary target (brief §4.1, ADR-002). Fabric is added alongside it
        // (docs/adr/ADR-012-add-fabric-loader.md) for the same two current lines. Forge is
        // scaffolded but NOT registered here: enabling it currently breaks Gradle configuration for
        // every variant, NeoForge included (docs/adr/ADR-011-add-forge-loader.md). Re-add "forge" to
        // these mc(...) calls once that is resolved.
        mc("26.2", "neoforge", "fabric")
        mc("26.1.2", "neoforge", "fabric")
    }
    create(rootProject)
}

rootProject.name = "minegasm"
