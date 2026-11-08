package app.echo.android.feature.settings

import androidx.compose.runtime.Composable
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.PlaybackPositionState
import kotlinx.coroutines.flow.StateFlow

/** Detailed readouts are mounted after opening them from the signal path. */
@Composable
internal fun SignalDiagnosticDetails(
    status: EchoPlaybackStatus,
    positionFlow: StateFlow<PlaybackPositionState>,
    bluetoothCodecNeedsPermission: Boolean,
    onRequestBluetoothCodecPermission: () -> Unit,
) {
    HealthPanel(status, bluetoothCodecNeedsPermission, onRequestBluetoothCodecPermission)
    UsbOutputPanel(status)
    CurrentStreamPanel(status, positionFlow)
}
