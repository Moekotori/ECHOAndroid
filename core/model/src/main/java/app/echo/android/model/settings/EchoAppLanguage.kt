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
    const val English = "en"
    const val Japanese = "ja"

    // Add supported languages here; checkLocalization verifies the Android locale declaration.
    val supported: List<EchoLanguage> = listOf(
        EchoLanguage("zh", "zh-CN", "简体中文"),
        EchoLanguage("en", "en", "English"),
        EchoLanguage("ja", "ja", "日本語"),
    )

    fun fromId(value: String?): String {
        if (value == null || value == System) return System
        val tag = value.trim().replace('_', '-')
        return supported.firstOrNull { it.id.equals(tag, true) || it.localeTag.equals(tag, true) }?.id
            ?: supported.firstOrNull { it.id == Locale.forLanguageTag(tag).language }?.id
            ?: System
    }

    fun languageOrNull(id: String?): EchoLanguage? = supported.firstOrNull { it.id == fromId(id) }

    fun localeOrNull(id: String?): Locale? = languageOrNull(id)?.let { Locale.forLanguageTag(it.localeTag) }
}
