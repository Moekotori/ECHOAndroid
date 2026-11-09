package app.echo.android

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun EchoMobileApp(startupDarkTheme: Boolean, onFirstScreenComposed: () -> Unit) {
    EchoOnboardingHost(darkTheme = startupDarkTheme, onFirstScreenComposed = onFirstScreenComposed) { openAddMusic ->
        EchoAppRoot(viewModel = viewModel(), openAddMusicInitially = openAddMusic)
    }
}
