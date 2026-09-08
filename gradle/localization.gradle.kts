import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

// No Python or Android device required: runs with the repository's Gradle/JDK toolchain.
val languageRegistry = rootProject.file("core/model/src/main/java/app/echo/android/model/settings/EchoAppLanguage.kt")
val localeConfig = rootProject.file("app/src/main/res/xml/locales_config.xml")
fun declaredLanguageTags(): List<String> =
    Regex("""EchoLanguage\("[^"]+", "([^"]+)", "[^"]+"\)""")
        .findAll(languageRegistry.readText()).map { it.groupValues[1] }.toList()

fun parseXml(file: File) = DocumentBuilderFactory.newInstance().apply {
    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
}.newDocumentBuilder().parse(file)

tasks.register("generateEchoLocales") {
    group = "localization"
    description = "Update Android's locale declaration from EchoAppLanguage.supported"
    notCompatibleWithConfigurationCache("Updates the checked-in locale declaration on explicit request")
    doLast {
        val tags = declaredLanguageTags()
        require(tags.isNotEmpty() && tags.distinct().size == tags.size) { "Invalid language registry" }
        localeConfig.writeText(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<locale-config xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                tags.joinToString("") { "    <locale android:name=\"$it\" />\n" } +
                "</locale-config>\n",
        )
    }
}

tasks.register("checkLocalization") {
    group = "verification"
    description = "Check language registration, module-owned defaults, placeholders and translation coverage"
    notCompatibleWithConfigurationCache("Inspects module resource files")
    doLast {
        val errors = mutableListOf<String>()
        val locales = parseXml(localeConfig).getElementsByTagName("locale")
        val platformTags = (0 until locales.length).map {
            (locales.item(it) as Element).getAttribute("android:name")
        }
        if (platformTags.toSet() != declaredLanguageTags().toSet()) {
            errors += "Language registry and locales_config.xml differ; run generateEchoLocales"
        }
        fun placeholders(text: String): Map<String, String> {
            var implicitIndex = 0
            return Regex("""%(?:(\d+)\$)?[-#+ 0,(<]*\d*(?:\.\d+)?([a-zA-Z%])""")
                .findAll(text).filter { it.groupValues[2] !in listOf("%", "n") }
                .associate { match ->
                    (match.groupValues[1].ifEmpty { (++implicitIndex).toString() }) to match.groupValues[2]
                }
        }
        subprojects.sortedBy { it.path }.forEach { module ->
            val res = module.file("src/main/res")
            if (!res.isDirectory) return@forEach
            val catalogs = linkedMapOf<String, MutableMap<String, Element>>()
            res.listFiles().orEmpty().filter { it.isDirectory && it.name.startsWith("values") }.forEach { dir ->
                val strings = linkedMapOf<String, Element>()
                dir.listFiles().orEmpty().filter { it.extension == "xml" }.forEach { file ->
                    val nodes = parseXml(file).getElementsByTagName("string")
                    for (i in 0 until nodes.length) {
                        val element = nodes.item(i) as Element
                        val key = element.getAttribute("name")
                        if (strings.put(key, element) != null) errors += "${module.path}/${dir.name}: duplicate $key"
                    }
                }
                catalogs[dir.name] = strings
            }
            val defaults = catalogs["values"].orEmpty()
            catalogs.filterKeys { it != "values" }.forEach { (folder, translations) ->
                translations.forEach { (key, translated) ->
                    val base = defaults[key]
                    when {
                        base == null -> errors += "${module.path}/$folder: $key has no module-owned default"
                        base.getAttribute("formatted") != "false" &&
                            placeholders(base.textContent) != placeholders(translated.textContent) ->
                            errors += "${module.path}/$folder: $key has incompatible format arguments"
                    }
                }
                val translatable = defaults.filterValues { it.getAttribute("translatable") != "false" }.keys
                logger.lifecycle("${module.path}/$folder: ${translations.keys.intersect(translatable).size}/${translatable.size} strings; missing translations use values/ fallback")
            }
            module.file("src/main/java").walkTopDown().filter { it.extension == "kt" }.forEach { file ->
                if (file.name != "EchoString.kt" && Regex("""\bechoString\(""").containsMatchIn(file.readText())) {
                    errors += "${file.relativeTo(rootDir)}: inline UI translations are not extensible; use module string resources"
                }
            }
        }
        check(errors.isEmpty()) { "Localization check failed:\n" + errors.joinToString("\n") }
        logger.lifecycle("Localization checks passed.")
    }
}
