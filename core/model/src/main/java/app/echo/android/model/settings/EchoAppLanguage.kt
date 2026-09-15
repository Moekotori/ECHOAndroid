package app.echo.android.model.settings

import java.util.Locale

/** Language metadata shared by settings, persistence and the platform locale adapter. */
data class EchoLanguage(
    val id: String,
    val localeTag: String,
    val nativeName: String,
)

object EchoAppLanguage {
    const val System = "system"
    const val Chinese = "zh"
    const val TraditionalChinese = "zh-Hant"
    const val English = "en"
    const val Japanese = "ja"
    const val Korean = "ko"
    const val Spanish = "es"
    const val German = "de"
    const val French = "fr"
    const val Russian = "ru"

    // Add supported languages here; checkLocalization verifies the Android locale declaration.
    val supported: List<EchoLanguage> = listOf(
        EchoLanguage("zh", "zh-CN", "简体中文"),
        EchoLanguage("zh-Hant", "zh-Hant", "繁體中文"),
        EchoLanguage("en", "en", "English"),
        EchoLanguage("ja", "ja", "日本語"),
        EchoLanguage("ko", "ko", "한국어"),
        EchoLanguage("es", "es", "Español"),
        EchoLanguage("de", "de", "Deutsch"),
        EchoLanguage("fr", "fr", "Français"),
        EchoLanguage("ru", "ru", "Русский"),
    )

    fun fromId(value: String?): String {
        if (value == null || value == System) return System
        val tag = value.trim().replace('_', '-')
        return supported.firstOrNull { it.id.equals(tag, true) || it.localeTag.equals(tag, true) }?.id
            ?: matchSupportedLocale(tag)
            ?: System
    }

    private fun matchSupportedLocale(tag: String): String? {
        val locale = Locale.forLanguageTag(tag)
        val language = locale.language
        if (language.equals("zh", ignoreCase = true)) {
            val script = locale.script
            val region = locale.country
            val traditional = script.equals("Hant", ignoreCase = true) ||
                region.equals("TW", ignoreCase = true) ||
                region.equals("HK", ignoreCase = true) ||
                region.equals("MO", ignoreCase = true)
            return if (traditional) TraditionalChinese else Chinese
        }
        return supported.firstOrNull { it.id.equals(language, ignoreCase = true) }?.id
    }

    fun languageOrNull(id: String?): EchoLanguage? = supported.firstOrNull { it.id == fromId(id) }

    fun localeOrNull(id: String?): Locale? = languageOrNull(id)?.let { Locale.forLanguageTag(it.localeTag) }
}
