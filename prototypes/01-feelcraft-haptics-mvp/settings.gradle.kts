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
    id("dev.kikugie.stonecutter") version "0.8+"
}

// Stonecraft's resource variable docs refer to mod.* properties from settings.
extra["mod.id"] = "feelcraft"
extra["mod.name"] = "Feelcraft Haptics"
extra["mod.group"] = "gg.meza.feelcraft"
extra["mod.description"] = "Client-side Minecraft haptic feedback MVP using Buttplug/Intiface."
extra["mod.version"] = "0.1.0"

gradle.beforeProject {
    extra["mod.id"] = "feelcraft"
    extra["mod.name"] = "Feelcraft Haptics"
    extra["mod.group"] = "gg.meza.feelcraft"
    extra["mod.description"] = "Client-side Minecraft haptic feedback MVP using Buttplug/Intiface."
    extra["mod.version"] = "0.1.0"
}

stonecutter {
    centralScript = "build.gradle.kts"
    kotlinController = true
    shared {
        fun mc(version: String, vararg loaders: String) {
            for (loader in loaders) vers("$version-$loader", version)
        }

        // MVP scope: NeoForge only, latest 2026 line plus latest 26.1 patch line.
        // 26.2 is beta at the time this pack was generated; see docs/BUILD_NOTES.md.
        mc("26.2", "neoforge")
        mc("26.1.2", "neoforge")
    }
    create(rootProject)
}

rootProject.name = "feelcraft-haptics"
