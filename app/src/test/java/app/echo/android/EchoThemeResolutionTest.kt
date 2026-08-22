package app.echo.android

import app.echo.android.data.EchoThemeMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoThemeResolutionTest {
    @Test
    fun darkModeIsAlwaysDark() {
        assertTrue(
            resolveEchoDarkTheme(
                systemDarkTheme = false,
                themeMode = EchoThemeMode.Dark,
                scheduledDarkModeEnabled = false,
                scheduledStartMinute = 22 * 60,
                scheduledEndMinute = 7 * 60,
                currentMinute = 12 * 60,
            ),
        )
    }

    @Test
    fun lightModeIsAlwaysLight() {
        assertFalse(
            resolveEchoDarkTheme(
                systemDarkTheme = true,
                themeMode = EchoThemeMode.Light,
                scheduledDarkModeEnabled = false,
                scheduledStartMinute = 22 * 60,
                scheduledEndMinute = 7 * 60,
                currentMinute = 12 * 60,
            ),
        )
    }

    @Test
    fun systemModeFollowsPhoneAppearance() {
        assertTrue(
            resolveEchoDarkTheme(
                systemDarkTheme = true,
                themeMode = EchoThemeMode.System,
                scheduledDarkModeEnabled = false,
                scheduledStartMinute = 22 * 60,
                scheduledEndMinute = 7 * 60,
                currentMinute = 12 * 60,
            ),
        )
        assertFalse(
            resolveEchoDarkTheme(
                systemDarkTheme = false,
                themeMode = EchoThemeMode.System,
                scheduledDarkModeEnabled = false,
                scheduledStartMinute = 22 * 60,
                scheduledEndMinute = 7 * 60,
                currentMinute = 12 * 60,
            ),
        )
    }

    @Test
    fun unknownModeDefaultsToDark() {
        assertTrue(
            resolveEchoDarkTheme(
                systemDarkTheme = false,
                themeMode = "",
                scheduledDarkModeEnabled = false,
                scheduledStartMinute = 22 * 60,
                scheduledEndMinute = 7 * 60,
                currentMinute = 12 * 60,
            ),
        )
    }
}
