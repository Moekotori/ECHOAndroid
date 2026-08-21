package app.echo.android.data

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import app.echo.android.model.settings.EchoAppLanguage
import java.util.Locale

fun Context.wrapEchoAppLocale(languageId: String): Context {
    if (Build.VERSION.SDK_INT >= 33) return this
    val locale = EchoAppLanguage.localeOrNull(languageId) ?: return this
    Locale.setDefault(locale)
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    config.setLocales(LocaleList(locale))
    return createConfigurationContext(config)
}

fun Context.applyEchoAppLocale(languageId: String) {
    val locale = EchoAppLanguage.localeOrNull(languageId)
    if (locale != null) {
        Locale.setDefault(locale)
    } else {
        val system = LocaleList.getDefault().get(0)
        if (system != null) Locale.setDefault(system)
    }
    if (Build.VERSION.SDK_INT < 33) return
    val manager = getSystemService(LocaleManager::class.java) ?: return
    val desired = if (locale == null) {
        LocaleList.getEmptyLocaleList()
    } else {
        LocaleList(locale)
    }
    if (manager.applicationLocales.toLanguageTags() != desired.toLanguageTags()) {
        manager.applicationLocales = desired
    }
}
