package app.echo.android.model.settings

import java.util.Locale

object EchoAppLanguage {
    const val System = "system"
    const val Chinese = "zh"
    const val English = "en"
    const val Japanese = "ja"

    fun fromId(value: String?): String =
        when (value) {
            Chinese, English, Japanese, System -> value
            else -> System
        }

    fun localeOrNull(id: String?): Locale? =
        when (fromId(id)) {
            Chinese -> Locale.SIMPLIFIED_CHINESE
            English -> Locale.ENGLISH
            Japanese -> Locale.JAPANESE
            else -> null
        }
}
