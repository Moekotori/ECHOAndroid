package app.echo.android

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.media3.common.util.UnstableApi
import app.echo.android.data.applyEchoAppLocale
import app.echo.android.data.readEchoStartupThemeSnapshot
import app.echo.android.data.readEchoStartupThemeSnapshotForLaunch
import app.echo.android.data.wrapEchoAppLocale

class MainActivity : ComponentActivity() {
    private var highRefreshRateRequested = false

    override fun attachBaseContext(newBase: Context) {
        val language = newBase.readEchoStartupThemeSnapshot().appLanguage
        super.attachBaseContext(newBase.wrapEchoAppLocale(language))
    }

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val startupThemeSnapshot = applicationContext.readEchoStartupThemeSnapshotForLaunch()
        applicationContext.applyEchoAppLocale(startupThemeSnapshot.appLanguage)
        val startupDarkTheme = resolveEchoDarkTheme(
            systemDarkTheme = applicationContext.isEchoSystemDarkTheme(),
            themeMode = startupThemeSnapshot.themeMode,
            scheduledDarkModeEnabled = startupThemeSnapshot.scheduledDarkModeEnabled,
            scheduledStartMinute = startupThemeSnapshot.scheduledDarkStartMinute,
            scheduledEndMinute = startupThemeSnapshot.scheduledDarkEndMinute,
            currentMinute = currentMinuteOfDayNow(),
        )
        setTheme(R.style.Theme_EchoAndroid_Splash)
        installSplashScreen()
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(startupWindowBackground(startupDarkTheme))
        applyEdgeToEdge(startupDarkTheme)
        setContent {
            EchoMobileApp()
        }
    }

    override fun onResume() {
        super.onResume()
        if (highRefreshRateRequested) {
            requestHighRefreshRate()
        } else {
            clearRefreshRatePreference()
        }
    }

    fun setHighRefreshRateRequested(enabled: Boolean) {
        if (highRefreshRateRequested == enabled) return
        highRefreshRateRequested = enabled
        if (enabled) {
            requestHighRefreshRate()
        } else {
            clearRefreshRatePreference()
        }
    }

    private fun applyEdgeToEdge(darkTheme: Boolean) {
        enableEdgeToEdge(
            statusBarStyle = if (darkTheme) {
                SystemBarStyle.dark(Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            },
            navigationBarStyle = if (darkTheme) {
                SystemBarStyle.dark(Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            },
        )
    }

    private fun startupWindowBackground(darkTheme: Boolean): Int =
        if (darkTheme) ECHO_DARK_WINDOW_BACKGROUND else ECHO_LIGHT_WINDOW_BACKGROUND

    private fun requestHighRefreshRate() {
        val preferredMode = bestSupportedHighRefreshMode() ?: return

        val attributes = window.attributes
        attributes.preferredDisplayModeId = preferredMode.modeId
        attributes.preferredRefreshRate = preferredMode.refreshRate
        window.attributes = attributes
    }

    private fun clearRefreshRatePreference() {
        val attributes = window.attributes
        attributes.preferredDisplayModeId = 0
        attributes.preferredRefreshRate = 0f
        window.attributes = attributes
    }

    @Suppress("DEPRECATION")
    private fun bestSupportedHighRefreshMode(): DisplayModePreference? {
        val activeDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            windowManager.defaultDisplay
        }
        val currentMode = activeDisplay?.mode ?: return null
        val preferredMode = activeDisplay
            .supportedModes
            .filter {
                it.refreshRate >= MIN_HIGH_REFRESH_RATE &&
                    it.physicalWidth == currentMode.physicalWidth &&
                    it.physicalHeight == currentMode.physicalHeight
            }
            .maxByOrNull { it.refreshRate }
            ?: return null
        return DisplayModePreference(
            modeId = preferredMode.modeId,
            refreshRate = preferredMode.refreshRate,
        )
    }

    private data class DisplayModePreference(
        val modeId: Int,
        val refreshRate: Float,
    )

    private companion object {
        const val MIN_HIGH_REFRESH_RATE = 90f
        val ECHO_DARK_WINDOW_BACKGROUND: Int = Color.rgb(8, 11, 18)
        val ECHO_LIGHT_WINDOW_BACKGROUND: Int = Color.rgb(241, 241, 243)
    }
}
