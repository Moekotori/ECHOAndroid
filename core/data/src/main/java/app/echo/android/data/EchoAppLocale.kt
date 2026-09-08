package app.echo.android.data

import android.content.Context
import app.echo.android.i18n.applyEchoAppLocale as applyLocale
import app.echo.android.i18n.wrapEchoAppLocale as wrapLocale

@Deprecated("Use core:i18n's platform locale adapter")
fun Context.wrapEchoAppLocale(languageId: String): Context = wrapLocale(languageId)

@Deprecated("Use core:i18n's platform locale adapter")
fun Context.applyEchoAppLocale(languageId: String) = applyLocale(languageId)
