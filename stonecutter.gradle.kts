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
