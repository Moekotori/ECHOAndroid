package app.echo.android.ui.discord

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.echo.android.model.connect.EchoMobileDiscordPresenceSnapshot
import kotlinx.coroutines.flow.Flow

@Composable
internal fun EchoDiscordPresenceBridge(
    enabled: Boolean,
    snapshots: Flow<EchoMobileDiscordPresenceSnapshot?>,
    publish: (EchoMobileDiscordPresenceSnapshot?) -> Unit,
) {
    LaunchedEffect(enabled) {
        if (!enabled) {
            publish(null)
            return@LaunchedEffect
        }
        snapshots.collect(publish)
    }
}
