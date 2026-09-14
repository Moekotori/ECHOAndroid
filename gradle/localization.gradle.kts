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
        fun isLanguageValuesDir(name: String): Boolean =
            name == "values" || name.matches(Regex("^values(?:-[a-z]{2}(?:-r[A-Z]{2})?|-b\\+.+)$"))
        subprojects.sortedBy { it.path }.forEach { module ->
            val res = module.file("src/main/res")
            if (!res.isDirectory) return@forEach
            val catalogs = linkedMapOf<String, MutableMap<String, Element>>()
            val pluralCatalogs = linkedMapOf<String, MutableMap<String, Element>>()
            fun visibleText(element: Element): String = element.textContent.trim().removeSurrounding("\"")
            res.listFiles().orEmpty().filter { it.isDirectory && isLanguageValuesDir(it.name) }.forEach { dir ->
                val strings = linkedMapOf<String, Element>()
                val plurals = linkedMapOf<String, Element>()
                dir.listFiles().orEmpty().filter { it.extension == "xml" }.forEach { file ->
                    val document = parseXml(file)
                    val stringNodes = document.getElementsByTagName("string")
                    for (i in 0 until stringNodes.length) {
                        val element = stringNodes.item(i) as Element
                        val key = element.getAttribute("name")
                        if (strings.put(key, element) != null) errors += "${module.path}/${dir.name}: duplicate $key"
                        if (element.getAttribute("translatable") != "false" && visibleText(element).isEmpty()) {
                            errors += "${module.path}/${dir.name}: $key is empty"
                        }
                    }
                    val pluralNodes = document.getElementsByTagName("plurals")
                    for (i in 0 until pluralNodes.length) {
                        val element = pluralNodes.item(i) as Element
                        val key = element.getAttribute("name")
                        if (plurals.put(key, element) != null) errors += "${module.path}/${dir.name}: duplicate plural $key"
                    }
                }
                catalogs[dir.name] = strings
                pluralCatalogs[dir.name] = plurals
            }
            val defaults = catalogs["values"].orEmpty()
            val defaultPlurals = pluralCatalogs["values"].orEmpty()
            catalogs.filterKeys { it != "values" }.forEach { (folder, translations) ->
                translations.forEach { (key, translated) ->
                    val base = defaults[key]
                    when {
                        base == null -> errors += "${module.path}/$folder: $key has no module-owned default"
                        placeholders(base.textContent) != placeholders(translated.textContent) ->
                            errors += "${module.path}/$folder: $key has incompatible format arguments"
                    }
                }
                val translatable = defaults.filterValues { it.getAttribute("translatable") != "false" }.keys
                logger.lifecycle("${module.path}/$folder: ${translations.keys.intersect(translatable).size}/${translatable.size} strings; missing translations use values/ fallback")
            }
            pluralCatalogs.filterKeys { it != "values" }.forEach { (folder, translations) ->
                translations.forEach { (key, translated) ->
                    val base = defaultPlurals[key]
                    if (base == null) {
                        errors += "${module.path}/$folder: plural $key has no module-owned default"
                        return@forEach
                    }
                    val baseItems = base.getElementsByTagName("item")
                    val translatedItems = translated.getElementsByTagName("item")
                    val baseByQuantity = (0 until baseItems.length).associate {
                        val item = baseItems.item(it) as Element
                        item.getAttribute("quantity") to item
                    }
                    val translatedByQuantity = (0 until translatedItems.length).associate {
                        val item = translatedItems.item(it) as Element
                        item.getAttribute("quantity") to item
                    }
                    if ("other" !in translatedByQuantity) {
                        errors += "${module.path}/$folder: plural $key is missing other"
                    }
                    translatedByQuantity.forEach { (quantity, item) ->
                        val defaultItem = baseByQuantity[quantity] ?: baseByQuantity["other"]
                        if (defaultItem != null && placeholders(defaultItem.textContent) != placeholders(item.textContent)) {
                            errors += "${module.path}/$folder: plural $key/$quantity has incompatible format arguments"
                        }
                        if (visibleText(item).isEmpty()) {
                            errors += "${module.path}/$folder: plural $key/$quantity is empty"
                        }
                    }
                }
                if (defaultPlurals.isNotEmpty()) {
                    logger.lifecycle("${module.path}/$folder: ${translations.keys.intersect(defaultPlurals.keys).size}/${defaultPlurals.size} plurals; missing translations use values/ fallback")
                }
            }
            module.file("src/main/java").walkTopDown().filter { it.extension == "kt" }.forEach { file ->
                val source = file.readText()
                if (file.name != "EchoString.kt" && Regex("""\bechoString\(""").containsMatchIn(source)) {
                    errors += "${file.relativeTo(rootDir)}: inline UI translations are not extensible; use module string resources"
                }
                if (file.name != "EchoText.kt" && Regex("""\bechoText\(""").containsMatchIn(source)) {
                    errors += "${file.relativeTo(rootDir)}: inline translations are not extensible; use module string resources"
                }
            }
        }
        check(errors.isEmpty()) { "Localization check failed:\n" + errors.joinToString("\n") }
        logger.lifecycle("Localization checks passed.")
    }
}
