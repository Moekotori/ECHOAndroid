package app.echo.android

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.echo.android.data.EchoOnboardingStore
import app.echo.android.data.EchoSettingsStore
import app.echo.android.data.readEchoStartupThemeSnapshot
import app.echo.android.design.EchoInteractionGate
import app.echo.android.design.EchoMobileTheme
import app.echo.android.design.EchoMotion
import app.echo.android.feature.home.FirstLaunchScreen
import app.echo.android.model.settings.EchoPerformanceMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException

/** Keep main navigation and its permission/update overlays out of the introduction. */
@Composable
internal fun EchoOnboardingHost(content: @Composable (openAddMusic: Boolean) -> Unit) {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { EchoOnboardingStore(context) }
    var completed by remember { mutableStateOf<Boolean?>(null) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var openAddMusic by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(store) {
        completed = try {
            store.completed.first()
        } catch (_: IOException) {
            false
        }
    }
    val snapshot = remember(context) { context.readEchoStartupThemeSnapshot() }
    val darkTheme = resolveEchoDarkTheme(
        systemDarkTheme = isSystemInDarkTheme(),
        themeMode = snapshot.themeMode,
        scheduledDarkModeEnabled = snapshot.scheduledDarkModeEnabled,
        scheduledStartMinute = snapshot.scheduledDarkStartMinute,
        scheduledEndMinute = snapshot.scheduledDarkEndMinute,
        currentMinute = currentMinuteOfDayNow(),
    )
    BackHandler(enabled = completed == false && (step > 0 || saving)) {
        if (!saving) step--
    }
    Box(Modifier.fillMaxSize()) {
        if (completed == true) content(openAddMusic)
        if (completed == null) {
            EchoMobileTheme(darkTheme = darkTheme) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            }
        }
        AnimatedVisibility(
            visible = completed == false,
            enter = EnterTransition.None,
            exit = ExitTransition.None,
            label = "onboarding-exit",
        ) {
            var mode by remember { mutableStateOf(EchoPerformanceMode.Auto) }
            LaunchedEffect(context) {
                mode = try {
                    EchoPerformanceMode.fromId(EchoSettingsStore(context).appSettings.first().performanceMode)
                } catch (_: IOException) {
                    EchoPerformanceMode.Auto
                }
            }
            val effectiveMode = mode.resolve(rememberSystemPowerSaveMode())
            EchoMobileTheme(darkTheme = darkTheme, effectivePerformanceMode = effectiveMode) {
                EchoInteractionGate(
                    active = completed == false,
                    modifier = Modifier.fillMaxSize()
                        .animateEnterExit(
                            enter = EnterTransition.None,
                            exit = fadeOut(EchoMotion.pageFadeOut(effectiveMode.isLightweight)),
                        )
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    FirstLaunchScreen(
                        step = step,
                        saving = saving || completed == true,
                        saveFailed = saveFailed,
                        onStepChange = { step = it },
                        onFinish = { openLibrary ->
                            if (completed == false && !saving) {
                                saving = true
                                saveFailed = false
                                scope.launch {
                                    try {
                                        store.complete()
                                        openAddMusic = openLibrary
                                        if (openLibrary) EchoLaunchActions.requestOpenLibrary()
                                        completed = true
                                    } catch (_: IOException) {
                                        saveFailed = true
                                    } finally {
                                        saving = false
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
