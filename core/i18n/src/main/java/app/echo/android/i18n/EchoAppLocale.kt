package app.echo.android.i18n

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import app.echo.android.model.settings.EchoAppLanguage
import java.util.Locale

/** Android 13+ owns the selected language; never overwrite a system-settings change on launch. */
fun Context.echoAppLanguage(savedLanguage: String): String {
    if (Build.VERSION.SDK_INT < 33) return EchoAppLanguage.fromId(savedLanguage)
    val locales = getSystemService(LocaleManager::class.java)?.applicationLocales
        ?: return EchoAppLanguage.fromId(savedLanguage)
    return if (locales.isEmpty) EchoAppLanguage.System else EchoAppLanguage.fromId(locales[0].toLanguageTag())
}

private fun Context.systemLocales(): LocaleList =
    if (Build.VERSION.SDK_INT >= 33) {
        getSystemService(LocaleManager::class.java)?.systemLocales ?: Resources.getSystem().configuration.locales
    } else {
        Resources.getSystem().configuration.locales
    }

/** Context wrapping is needed only before Android 13. Does not mutate the incoming Configuration. */
fun Context.wrapEchoAppLocale(languageId: String): Context {
    if (Build.VERSION.SDK_INT >= 33) return this
    val locales = EchoAppLanguage.localeOrNull(languageId)?.let { LocaleList(it) } ?: systemLocales()
    val config = Configuration(resources.configuration)
    config.setLocales(locales)
    config.setLayoutDirection(locales[0])
    return createConfigurationContext(config)
}

/** Refresh the existing window on Android 8–12; Android 13+ dispatches this itself. */
@Suppress("DEPRECATION")
fun Activity.refreshEchoAppLocale(languageId: String) {
    if (Build.VERSION.SDK_INT >= 33) return
    val config = wrapEchoAppLocale(languageId).resources.configuration
    // This Activity owns a configuration Context on these versions. Updating its resources
    // keeps Activity lookup, dialogs and stringResource on the same localized Context.
    resources.updateConfiguration(config, resources.displayMetrics)
    onConfigurationChanged(config)
    window.decorView.dispatchConfigurationChanged(config)
}

/** Called only for an explicit user selection (or the one-time legacy preference migration). */
fun Context.applyEchoAppLocale(languageId: String) {
    val locale = EchoAppLanguage.localeOrNull(languageId)
    if (Build.VERSION.SDK_INT >= 33) {
        val manager = getSystemService(LocaleManager::class.java) ?: return
        val desired = locale?.let { LocaleList(it) } ?: LocaleList.getEmptyLocaleList()
        if (manager.applicationLocales != desired) manager.applicationLocales = desired
    }
    // Compatibility for non-UI legacy messages. Resource-based UI reads its own Context instead.
    Locale.setDefault(locale ?: systemLocales()[0])
}

/** Migrate old stored IDs once, then treat the platform setting as authoritative. */
fun Context.initializeEchoAppLocale(savedLanguage: String) {
    if (Build.VERSION.SDK_INT >= 33) {
        val preferences = getSharedPreferences("echo_locale_migration", Context.MODE_PRIVATE)
        if (!preferences.getBoolean("platform_locale_initialized", false)) {
            val manager = getSystemService(LocaleManager::class.java)
            if (manager != null && manager.applicationLocales.isEmpty && savedLanguage != EchoAppLanguage.System) {
                applyEchoAppLocale(savedLanguage)
            }
            preferences.edit().putBoolean("platform_locale_initialized", true).apply()
        }
        val selected = echoAppLanguage(savedLanguage)
        Locale.setDefault(EchoAppLanguage.localeOrNull(selected) ?: systemLocales()[0])
    } else {
        applyEchoAppLocale(savedLanguage)
    }
}
