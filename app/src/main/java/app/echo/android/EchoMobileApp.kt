package app.echo.android

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi

@UnstableApi
@Composable
fun EchoMobileApp(viewModel: EchoAndroidViewModel = viewModel()) {
    EchoAppRoot(viewModel = viewModel)
}
