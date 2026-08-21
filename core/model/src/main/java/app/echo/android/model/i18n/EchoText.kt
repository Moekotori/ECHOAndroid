package app.echo.android.model.i18n

import java.util.Locale

fun echoText(en: String, zh: String, ja: String): String =
    when (Locale.getDefault().language) {
        "zh" -> zh
        "ja" -> ja
        else -> en
    }
