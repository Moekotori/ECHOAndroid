package app.echo.android.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/** Compatibility only. New text belongs to its owning module's Android string resources. */
@Deprecated("Use stringResource with module-owned resources to support additional languages")
@Composable
fun echoString(en: String, zh: String, ja: String): String =
    when (LocalConfiguration.current.locales[0].language) {
        "zh" -> zh
        "ja" -> ja
        else -> en
    }
