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
        // NeoForge only for the MVP; two current lines (brief §4.1, ADR-002).
        mc("26.2", "neoforge")
        mc("26.1.2", "neoforge")
    }
    create(rootProject)
}

rootProject.name = "minegasm"
