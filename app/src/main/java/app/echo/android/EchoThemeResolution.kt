package app.echo.android

import android.content.Context
import android.content.res.Configuration
import app.echo.android.data.EchoThemeMode
import java.time.LocalTime

internal fun Context.isEchoSystemDarkTheme(): Boolean =
    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

internal fun currentMinuteOfDayNow(): Int {
    val now = LocalTime.now()
    return now.hour * 60 + now.minute
}

internal fun resolveEchoDarkTheme(
    systemDarkTheme: Boolean,
    themeMode: String,
    scheduledDarkModeEnabled: Boolean,
    scheduledStartMinute: Int,
    scheduledEndMinute: Int,
    currentMinute: Int,
): Boolean {
    if (scheduledDarkModeEnabled && isMinuteInScheduledWindow(currentMinute, scheduledStartMinute, scheduledEndMinute)) {
        return true
    }
    return when (themeMode) {
        EchoThemeMode.Light -> false
        EchoThemeMode.Dark -> true
        else -> systemDarkTheme
    }
}

private fun isMinuteInScheduledWindow(
    currentMinute: Int,
    startMinute: Int,
    endMinute: Int,
): Boolean {
    val current = currentMinute.coerceIn(0, 23 * 60 + 59)
    val start = startMinute.coerceIn(0, 23 * 60 + 59)
    val end = endMinute.coerceIn(0, 23 * 60 + 59)
    return when {
        start == end -> false
        start < end -> current in start until end
        else -> current >= start || current < end
    }
}
