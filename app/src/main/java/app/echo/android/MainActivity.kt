package app.echo.android

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
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
import app.echo.android.i18n.initializeEchoAppLocale
import app.echo.android.data.readEchoStartupThemeSnapshot
import app.echo.android.data.readEchoStartupThemeSnapshotForLaunch
import app.echo.android.design.echoStartupWindowColor
import app.echo.android.model.settings.EchoColorTheme
import app.echo.android.i18n.wrapEchoAppLocale
import app.echo.android.playback.EchoPlaybackIntents

class MainActivity : ComponentActivity() {
    private var highRefreshRateRequested = false
    internal var desktopShortcutHandler: ((Int) -> Boolean)? = null

    override fun attachBaseContext(newBase: Context) {
        val language = newBase.readEchoStartupThemeSnapshot().appLanguage
        super.attachBaseContext(newBase.wrapEchoAppLocale(language))
    }

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val startupThemeSnapshot = applicationContext.readEchoStartupThemeSnapshotForLaunch()
        applicationContext.initializeEchoAppLocale(startupThemeSnapshot.appLanguage)
        val startupDarkTheme = resolveEchoDarkTheme(
            systemDarkTheme = applicationContext.isEchoSystemDarkTheme(),
            themeMode = startupThemeSnapshot.themeMode,
            scheduledDarkModeEnabled = startupThemeSnapshot.scheduledDarkModeEnabled,
            scheduledStartMinute = startupThemeSnapshot.scheduledDarkStartMinute,
            scheduledEndMinute = startupThemeSnapshot.scheduledDarkEndMinute,
            currentMinute = currentMinuteOfDayNow(),
        )
        setTheme(R.style.Theme_EchoAndroid_Splash)
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.installEchoExitTransition(
            activity = this,
            restored = savedInstanceState != null,
            startupBackgroundUri = startupThemeSnapshot.startupBackgroundUri,
        )
        window.decorView.setBackgroundColor(
            echoStartupWindowColor(
                EchoColorTheme.fromId(startupThemeSnapshot.colorTheme),
                startupDarkTheme,
                startupThemeSnapshot.customColors,
            ),
        )
        applyEdgeToEdge(startupDarkTheme)
        setContent {
            EchoMobileApp()
        }
        consumeLaunchIntent(intent)
    }

    /** 投送到 DLNA / Chromecast 时，音量键调的是远端设备，不是手机。 */
    @OptIn(UnstableApi::class)
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event?.isCtrlPressed == true && event.repeatCount == 0 && !event.isAltPressed && !event.isShiftPressed) {
            val command = when (keyCode) {
                KeyEvent.KEYCODE_1 -> 0
                KeyEvent.KEYCODE_2 -> 1
                KeyEvent.KEYCODE_3 -> 2
                KeyEvent.KEYCODE_4 -> 3
                KeyEvent.KEYCODE_COMMA -> 4
                KeyEvent.KEYCODE_SPACE -> 5
                else -> -1
            }
            if (command >= 0 && desktopShortcutHandler?.invoke(command) == true) return true
        }
        val delta = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> CAST_VOLUME_STEP
            KeyEvent.KEYCODE_VOLUME_DOWN -> -CAST_VOLUME_STEP
            else -> return super.onKeyDown(keyCode, event)
        }
        val session = (application as? EchoApplication)?.echoLinkSession
        if (session != null && session.adjustCastVolume(delta)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeLaunchIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (highRefreshRateRequested) {
            requestHighRefreshRate()
        } else {
            clearRefreshRatePreference()
        }
    }

    override fun onPause() {
        clearRefreshRatePreference()
        super.onPause()
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

    private fun requestHighRefreshRate() {
        // ARR negotiates per-layer hints from Compose; a fixed window mode defeats that policy.
        if (Build.VERSION.SDK_INT >= 36 && display?.hasArrSupport() == true) {
            clearRefreshRatePreference()
            return
        }
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

    private fun consumeLaunchIntent(intent: Intent?) {
        if (intent == null) return
        val openLyrics = intent.action == EchoPlaybackIntents.ACTION_OPEN_LYRICS ||
            intent.getBooleanExtra(EchoPlaybackIntents.EXTRA_OPEN_LYRICS, false)
        if (openLyrics) {
            intent.action = null
            intent.removeExtra(EchoPlaybackIntents.EXTRA_OPEN_LYRICS)
            EchoLaunchActions.requestOpenLyrics()
        }
        val listeningCode = intent.dataString?.takeIf { it.startsWith("echo-listen:") }
        if (listeningCode != null) {
            EchoLaunchActions.requestOpenListening(listeningCode)
            intent.action = Intent.ACTION_MAIN
            intent.data = null
            return
        }
        val incoming = EchoIncomingAudio.urisFromIntent(intent)
        if (incoming.isNotEmpty()) {
            EchoLaunchActions.requestPlayIncoming(incoming.map { it.toString() })
            intent.action = Intent.ACTION_MAIN
            intent.data = null
            intent.clipData = null
            intent.removeExtra(Intent.EXTRA_STREAM)
            return
        }
        when {
            EchoPlaybackIntents.isPlayLast(intent.action) -> {
                EchoLaunchActions.requestPlayLast()
                intent.action = Intent.ACTION_MAIN
            }
            EchoPlaybackIntents.isOpenLibrary(intent.action) -> {
                EchoLaunchActions.requestOpenLibrary()
                intent.action = Intent.ACTION_MAIN
            }
        }
        if (intent.getBooleanExtra(EchoLinkCastService.ExtraOpenCast, false)) {
            intent.removeExtra(EchoLinkCastService.ExtraOpenCast)
            EchoLaunchActions.requestOpenCast()
        }
    }

    private data class DisplayModePreference(
        val modeId: Int,
        val refreshRate: Float,
    )

    private companion object {
        const val MIN_HIGH_REFRESH_RATE = 90f
    }
}

private const val CAST_VOLUME_STEP = 0.05f
