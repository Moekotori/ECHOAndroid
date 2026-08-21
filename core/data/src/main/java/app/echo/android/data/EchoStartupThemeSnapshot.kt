package app.echo.android.data

import android.content.Context
import app.echo.android.model.settings.EchoAppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

data class EchoStartupThemeSnapshot(
    val themeMode: String = EchoThemeMode.System,
    val appLanguage: String = EchoAppLanguage.System,
    val scheduledDarkModeEnabled: Boolean = false,
    val scheduledDarkStartMinute: Int = DefaultScheduledDarkStartMinute,
    val scheduledDarkEndMinute: Int = DefaultScheduledDarkEndMinute,
) {
    fun toAppSettings(): EchoAppSettings =
        EchoAppSettings(
            themeMode = normalizeThemeMode(themeMode),
            appLanguage = EchoAppLanguage.fromId(appLanguage),
            scheduledDarkModeEnabled = scheduledDarkModeEnabled,
            scheduledDarkStartMinute = scheduledDarkStartMinute.coerceMinuteOfDay(),
            scheduledDarkEndMinute = scheduledDarkEndMinute.coerceMinuteOfDay(),
        )
}

fun Context.readEchoStartupThemeSnapshot(): EchoStartupThemeSnapshot {
    val preferences = applicationContext.getSharedPreferences(
        StartupThemePreferencesName,
        Context.MODE_PRIVATE,
    )
    return EchoStartupThemeSnapshot(
        themeMode = normalizeThemeMode(preferences.getString(KeyThemeMode, null)),
        appLanguage = EchoAppLanguage.fromId(preferences.getString(KeyAppLanguage, null)),
        scheduledDarkModeEnabled = preferences.getBoolean(KeyScheduledDarkModeEnabled, false),
        scheduledDarkStartMinute = preferences
            .getInt(KeyScheduledDarkStartMinute, DefaultScheduledDarkStartMinute)
            .coerceMinuteOfDay(),
        scheduledDarkEndMinute = preferences
            .getInt(KeyScheduledDarkEndMinute, DefaultScheduledDarkEndMinute)
            .coerceMinuteOfDay(),
    )
}

fun Context.readEchoStartupThemeSnapshotForLaunch(
    timeoutMillis: Long = StartupThemeDataStoreReadTimeoutMillis,
): EchoStartupThemeSnapshot {
    val cachedSnapshot = readEchoStartupThemeSnapshot()
    if (hasEchoStartupThemeSnapshot()) return cachedSnapshot

    val appContext = applicationContext
    return runBlocking(Dispatchers.IO) {
        withTimeoutOrNull(timeoutMillis) {
            EchoSettingsStore(appContext)
                .appSettings
                .first()
                .toStartupThemeSnapshot()
                .also { snapshot ->
                    appContext.writeEchoStartupThemeSnapshot(snapshot, synchronous = true)
                }
        }
    } ?: cachedSnapshot
}

internal fun Context.writeEchoStartupThemeSnapshot(
    snapshot: EchoStartupThemeSnapshot,
    synchronous: Boolean = false,
) {
    val safeSnapshot = snapshot.normalized()
    val editor = applicationContext
        .getSharedPreferences(StartupThemePreferencesName, Context.MODE_PRIVATE)
        .edit()
        .putString(KeyThemeMode, safeSnapshot.themeMode)
        .putString(KeyAppLanguage, safeSnapshot.appLanguage)
        .putBoolean(KeyScheduledDarkModeEnabled, safeSnapshot.scheduledDarkModeEnabled)
        .putInt(KeyScheduledDarkStartMinute, safeSnapshot.scheduledDarkStartMinute)
        .putInt(KeyScheduledDarkEndMinute, safeSnapshot.scheduledDarkEndMinute)

    if (synchronous) {
        editor.commit()
    } else {
        editor.apply()
    }
}

internal fun EchoAppSettings.toStartupThemeSnapshot(): EchoStartupThemeSnapshot =
    EchoStartupThemeSnapshot(
        themeMode = normalizeThemeMode(themeMode),
        appLanguage = EchoAppLanguage.fromId(appLanguage),
        scheduledDarkModeEnabled = scheduledDarkModeEnabled,
        scheduledDarkStartMinute = scheduledDarkStartMinute.coerceMinuteOfDay(),
        scheduledDarkEndMinute = scheduledDarkEndMinute.coerceMinuteOfDay(),
    )

internal fun normalizeThemeMode(value: String?): String =
    when (value) {
        EchoThemeMode.Light,
        EchoThemeMode.Dark,
        EchoThemeMode.System,
        -> value

        else -> EchoThemeMode.System
    }

private fun EchoStartupThemeSnapshot.normalized(): EchoStartupThemeSnapshot =
    copy(
        themeMode = normalizeThemeMode(themeMode),
        appLanguage = EchoAppLanguage.fromId(appLanguage),
        scheduledDarkStartMinute = scheduledDarkStartMinute.coerceMinuteOfDay(),
        scheduledDarkEndMinute = scheduledDarkEndMinute.coerceMinuteOfDay(),
    )

private fun Int.coerceMinuteOfDay(): Int = coerceIn(0, 23 * 60 + 59)

private fun Context.hasEchoStartupThemeSnapshot(): Boolean {
    val preferences = applicationContext.getSharedPreferences(
        StartupThemePreferencesName,
        Context.MODE_PRIVATE,
    )
    return preferences.contains(KeyThemeMode) ||
        preferences.contains(KeyScheduledDarkModeEnabled) ||
        preferences.contains(KeyScheduledDarkStartMinute) ||
        preferences.contains(KeyScheduledDarkEndMinute)
}

private const val StartupThemePreferencesName = "echo-startup-theme"
private const val KeyThemeMode = "theme_mode"
private const val KeyAppLanguage = "app_language"
private const val KeyScheduledDarkModeEnabled = "scheduled_dark_mode_enabled"
private const val KeyScheduledDarkStartMinute = "scheduled_dark_start_minute"
private const val KeyScheduledDarkEndMinute = "scheduled_dark_end_minute"
private const val DefaultScheduledDarkStartMinute = 22 * 60
private const val DefaultScheduledDarkEndMinute = 7 * 60
private const val StartupThemeDataStoreReadTimeoutMillis = 120L
