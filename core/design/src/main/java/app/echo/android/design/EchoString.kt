package app.echo.android.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import app.echo.android.model.i18n.echoText

@Composable
fun echoString(en: String, zh: String, ja: String): String {
    LocalConfiguration.current
    return echoText(en = en, zh = zh, ja = ja)
}
