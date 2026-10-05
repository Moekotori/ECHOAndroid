package app.echo.android

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun EchoMobileApp() {
    EchoOnboardingHost { openAddMusic ->
        EchoAppRoot(viewModel = viewModel(), openAddMusicInitially = openAddMusic)
    }
}
