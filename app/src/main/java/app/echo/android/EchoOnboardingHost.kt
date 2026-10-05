package app.echo.android

import androidx.activity.compose.BackHandler
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
import app.echo.android.data.readEchoStartupThemeSnapshot
import app.echo.android.design.EchoMobileTheme
import app.echo.android.feature.home.FirstLaunchScreen
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
    if (completed == true) {
        content(openAddMusic)
        return
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
    EchoMobileTheme(darkTheme = darkTheme) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (completed == false) {
                FirstLaunchScreen(
                    step = step,
                    saving = saving,
                    saveFailed = saveFailed,
                    onStepChange = { step = it },
                    onFinish = { openLibrary ->
                        if (!saving) {
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
