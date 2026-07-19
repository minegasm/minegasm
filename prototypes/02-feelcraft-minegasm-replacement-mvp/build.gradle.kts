plugins {
    id("gg.meza.stonecraft")
}

group = "gg.meza.feelcraft"
version = "0.1.0"

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
        "id" to "feelcraft",
        "name" to "Feelcraft Haptics",
        "group" to "gg.meza.feelcraft",
        "description" to "Client-side Minecraft haptic feedback MVP using Buttplug/Intiface.",
        "version" to "0.1.0"
    )
}

dependencies {
    // Used for the MVP Buttplug v4 JSON line protocol.
    // Minecraft also ships Gson, but an explicit dependency keeps IDE import predictable.
    implementation("com.google.code.gson:gson:2.11.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}
