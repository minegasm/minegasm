plugins {
    id("dev.kikugie.stonecutter")
    id("gg.meza.stonecraft")
}
stonecutter active "26.2-neoforge" /* [SC] DO NOT EDIT */

// An unqualified `intifaceProbe` invocation selects the task in every Stonecutter subproject.
// Intiface may reject simultaneous clients, so serialize those tasks while preserving explicit
// per-variant invocations such as `:26.2-neoforge:intifaceProbe`.
gradle.projectsEvaluated {
    val probes = rootProject.subprojects
        .mapNotNull { it.tasks.findByName("intifaceProbe") }
        .sortedBy { it.path }
    probes.zipWithNext().forEach { (before, after) ->
        after.mustRunAfter(before)
    }
}

// `installJars`: build every variant (via `chiseledBuild`) and copy each freshly built jar into the
// local Minecraft instance's `mods` folder, for hands-on testing across loaders/versions. Targets are
// read from `mods-install.env` (gitignored; copy `mods-install.env.example`), one `<variant>=<mods
// folder>` line per instance you test; blank/absent lines are skipped. Everything below is pure path
// construction resolved at configuration time, so nothing here reaches into subproject state.
run {
    val modVersion = providers.gradleProperty("mod_version").get()
    val envFile = rootProject.file("mods-install.env")
    // Jar path per variant mirrors build.gradle.kts's `jar` naming and Stonecutter's per-variant
    // build dir (`versions/<variant>/build/libs`); derived from the variant id, not subproject access.
    val jarByVariant = stonecutter.versions.associate { it.project to it.project }
        .mapValues { (variant, _) ->
            val mc = variant.substringBeforeLast('-')
            val loader = variant.substringAfterLast('-')
            rootProject.file("versions/$variant/build/libs/minegasm-$modVersion+mc$mc-$loader.jar")
        }

    tasks.register("installJars") {
        group = "minegasm"
        description = "Build all variants (chiseledBuild) and copy each jar into the mods folder set in mods-install.env."
        dependsOn("chiseledBuild")
        doLast {
            if (!envFile.exists()) {
                throw org.gradle.api.GradleException(
                    "Missing ${envFile.name}. Copy mods-install.env.example to ${envFile.name} and set your instances' mods folders."
                )
            }
            // Parse env-style `key=value` lines ourselves (not java.util.Properties) so raw Windows
            // paths with backslashes paste in without escaping. Value is the verbatim remainder, with
            // optional surrounding quotes stripped.
            val targets = LinkedHashMap<String, String>()
            envFile.readLines().forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEach
                val eq = line.indexOf('=')
                if (eq <= 0) return@forEach
                val key = line.substring(0, eq).trim()
                val value = line.substring(eq + 1).trim().trim('"')
                targets[key] = value
            }

            var installed = 0
            jarByVariant.forEach { (variant, jar) ->
                val dest = targets[variant].orEmpty()
                if (dest.isEmpty()) {
                    logger.lifecycle("installJars: skip $variant (no target in ${envFile.name})")
                    return@forEach
                }
                val modsDir = file(dest)
                if (!modsDir.isDirectory) {
                    throw org.gradle.api.GradleException("installJars: $variant target is not a folder: $dest")
                }
                if (!jar.isFile) {
                    throw org.gradle.api.GradleException("installJars: built jar not found for $variant: ${jar.absolutePath}")
                }
                // Remove prior Minegasm jars so a version bump never leaves two of our jars side by
                // side (a duplicate-mod load error). Scoped to our own `minegasm-*.jar` filenames.
                modsDir.listFiles { f -> f.isFile && f.name.startsWith("minegasm-") && f.name.endsWith(".jar") }
                    ?.forEach { old ->
                        if (old.delete()) logger.lifecycle("installJars: removed old ${old.name} from $dest")
                    }
                jar.copyTo(File(modsDir, jar.name), overwrite = true)
                logger.lifecycle("installJars: installed ${jar.name} -> $dest")
                installed++
            }
            logger.lifecycle("installJars: installed $installed jar(s).")
        }
    }
}
