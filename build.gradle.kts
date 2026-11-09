import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

subprojects {
    pluginManager.withPlugin("com.android.application") {
        extensions.configure<ApplicationExtension>("android") {
            lint {
                lintConfig = rootProject.file("lint.xml")
            }
        }
    }
    pluginManager.withPlugin("com.android.library") {
        extensions.configure<LibraryExtension>("android") {
            lint {
                lintConfig = rootProject.file("lint.xml")
            }
        }
    }
}

tasks.register("checkModules") {
    group = "verification"
    description = "Validate project() dependencies and write the module map"
    notCompatibleWithConfigurationCache("Reads module build files to enforce the allowed graph")

    val graphFile = layout.projectDirectory.file("gradle/allowed-module-graph.txt")
    val settingsFile = layout.projectDirectory.file("settings.gradle.kts")
    val moduleBuildFiles = files(subprojects.map { it.layout.projectDirectory.file("build.gradle.kts") })
    val moduleMap = layout.buildDirectory.file("reports/module-map.md")
    inputs.file(graphFile)
    inputs.file(settingsFile)
    inputs.files(moduleBuildFiles)
    outputs.file(moduleMap)

    doLast {
        val includeRegex = Regex("""include\("([^"]+)"\)""")
        val projectDepRegex = Regex("""project\("([^"]+)"\)""")

        fun parseAllowedGraph(text: String): Map<String, Set<String>> {
            val graph = linkedMapOf<String, Set<String>>()
            text.lineSequence().forEach { raw ->
                val line = raw.substringBefore("#").trim()
                if (line.isEmpty()) return@forEach
                val parts = line.replace("->", " ").split(Regex("\\s+")).filter { it.isNotEmpty() }
                val from = parts.first()
                require(from.startsWith(":")) { "Module path must start with ':': $from" }
                graph[from] = parts.drop(1).toSet()
            }
            return graph
        }

        fun moduleDir(path: String): String = path.removePrefix(":").replace(':', '/')

        val rootDir = layout.projectDirectory.asFile
        val allowed = parseAllowedGraph(graphFile.asFile.readText())
        val included = includeRegex.findAll(settingsFile.asFile.readText())
            .map { it.groupValues[1] }
            .toSet()
        val errors = mutableListOf<String>()
        val actualGraph = sortedMapOf<String, Set<String>>()

        val missingFromGraph = included - allowed.keys
        if (missingFromGraph.isNotEmpty()) {
            errors += "settings.gradle.kts modules missing from gradle/allowed-module-graph.txt: ${missingFromGraph.sorted().joinToString()}"
        }
        val extraInGraph = allowed.keys - included
        if (extraInGraph.isNotEmpty()) {
            errors += "gradle/allowed-module-graph.txt lists modules not in settings.gradle.kts: ${extraInGraph.sorted().joinToString()}"
        }

        included.sorted().forEach { module ->
            val buildFile = rootDir.resolve(moduleDir(module)).resolve("build.gradle.kts")
            if (!buildFile.isFile) {
                errors += "$module is missing ${buildFile.relativeTo(rootDir)}"
                return@forEach
            }
            val actual = projectDepRegex.findAll(buildFile.readText())
                .map { it.groupValues[1] }
                .toSet()
            actualGraph[module] = actual
            val permitted = allowed[module].orEmpty()
            val unexpected = actual - permitted
            if (unexpected.isNotEmpty()) {
                errors += "$module has forbidden project dependencies: ${unexpected.sorted().joinToString()}. Allowed: ${permitted.sorted().joinToString().ifEmpty { "(none)" }}"
            }

            if (actual.any { it == ":app" }) {
                errors += "$module must not depend on :app"
            }
            if (module.startsWith(":feature:") && actual.any { it.startsWith(":feature:") }) {
                errors += "$module must not depend on another feature module"
            }
            if (module.startsWith(":core:") && actual.any { it.startsWith(":feature:") }) {
                errors += "$module must not depend on a feature module"
            }
        }

        allowed.forEach { (from, deps) ->
            deps.forEach { to ->
                if (to == ":app") {
                    errors += "allowed graph lets $from depend on :app"
                }
                if (from.startsWith(":feature:") && to.startsWith(":feature:")) {
                    errors += "allowed graph lets $from depend on $to"
                }
                if (from.startsWith(":core:") && (to.startsWith(":feature:") || to == ":app")) {
                    errors += "allowed graph lets $from depend on $to"
                }
            }
        }

        val report = moduleMap.get().asFile
        if (errors.isNotEmpty()) {
            report.delete()
            error("Module graph check failed:\n" + errors.joinToString("\n") { " - $it" })
        }

        report.parentFile.mkdirs()
        report.writeText(buildString {
            appendLine("# ECHOAndroid module map")
            appendLine()
            appendLine("Generated by `checkModules` from direct `project()` declarations.")
            appendLine()
            appendLine("| Module | Direct project dependencies |")
            appendLine("| --- | --- |")
            actualGraph.forEach { (module, dependencies) ->
                val direct = dependencies.sorted().joinToString(", ") { "`$it`" }.ifEmpty { "—" }
                appendLine("| `$module` | $direct |")
            }
        })
        logger.lifecycle("Module graph OK (${included.size} modules). Map: ${report.relativeTo(rootDir)}")
    }
}

apply(from = "gradle/localization.gradle.kts")
