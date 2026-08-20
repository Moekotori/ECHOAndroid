plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

tasks.register("checkModules") {
    group = "verification"
    description = "Fail if project() dependencies violate gradle/allowed-module-graph.txt"
    notCompatibleWithConfigurationCache("Reads module build files to enforce the allowed graph")

    val graphFile = layout.projectDirectory.file("gradle/allowed-module-graph.txt")
    val settingsFile = layout.projectDirectory.file("settings.gradle.kts")
    val moduleBuildFiles = files(subprojects.map { it.layout.projectDirectory.file("build.gradle.kts") })
    inputs.file(graphFile)
    inputs.file(settingsFile)
    inputs.files(moduleBuildFiles)

    doLast {
        val includeRegex = Regex("""include\("([^"]+)"\)""")
        val projectDepRegex = Regex("""project\("([^"]+)"\)""")
        val featureAllowedCores = setOf(":core:model", ":core:design")

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
            val permitted = allowed[module].orEmpty()
            val unexpected = actual - permitted
            if (unexpected.isNotEmpty()) {
                errors += "$module has forbidden project dependencies: ${unexpected.sorted().joinToString()}. Allowed: ${permitted.sorted().joinToString().ifEmpty { "(none)" }}"
            }

            actual.filter { it == ":app" }.forEach {
                errors += "$module must not depend on :app"
            }
            if (module.startsWith(":feature:") && actual.any { it.startsWith(":feature:") }) {
                errors += "$module must not depend on another feature module"
            }
            if (module.startsWith(":core:") && actual.any { it.startsWith(":feature:") }) {
                errors += "$module must not depend on a feature module"
            }
            if (module.startsWith(":feature:")) {
                val illegalCores = actual.filter { it.startsWith(":core:") && it !in featureAllowedCores }
                if (illegalCores.isNotEmpty()) {
                    errors += "$module may only depend on ${featureAllowedCores.sorted().joinToString()} (not ${illegalCores.sorted().joinToString()})"
                }
            }
            if (module == ":core:model" && actual.isNotEmpty()) {
                errors += ":core:model must not depend on other project modules (${actual.sorted().joinToString()})"
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
                if (from.startsWith(":feature:") && to.startsWith(":core:") && to !in featureAllowedCores) {
                    errors += "allowed graph lets $from depend on $to; features may only use ${featureAllowedCores.sorted().joinToString()}"
                }
            }
        }

        if (errors.isNotEmpty()) {
            error("Module graph check failed:\n" + errors.joinToString("\n") { " - $it" })
        }
        logger.lifecycle("Module graph OK (${included.size} modules).")
    }
}
