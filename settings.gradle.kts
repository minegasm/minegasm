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

// Force Architectury Loom to 1.17.491 (overriding the version the stonecraft plugin resolves
// transitively). 1.17.487 — the newest at the time of ADR-011 — throws "convertAccessWideners is final
// and cannot be changed" when any Forge variant is registered, breaking Gradle config for every
// variant; 1.17.491 fixes it (docs/adr/ADR-013). Remove this force once the stonecraft plugin's own
// resolved Loom is >= 1.17.491, so it stops silently holding Loom back.
buildscript {
    configurations.all {
        resolutionStrategy.force("dev.architectury:architectury-loom:1.17.491")
    }
}

plugins {
    id("gg.meza.stonecraft") version "1.12.4"
    id("dev.kikugie.stonecutter") version "0.9.6"
    // Auto-provisions the Java 21 toolchain that 1.21.1 needs (build.gradle.kts) when it isn't already
    // installed locally — only Java 25 is present on this machine otherwise.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
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
        // NeoForge is the primary target (brief §4.1, ADR-002). Fabric (ADR-012) and Forge (ADR-011,
        // unblocked per ADR-013 by pinning Architectury Loom 1.17.491 in the buildscript block above)
        // are registered alongside it for both current Minecraft lines.
        mc("26.2", "neoforge", "fabric", "forge")
        mc("26.1.2", "neoforge", "fabric", "forge")
        mc("1.21.1", "neoforge", "fabric", "forge")
    }
    create(rootProject)
}

rootProject.name = "minegasm"
