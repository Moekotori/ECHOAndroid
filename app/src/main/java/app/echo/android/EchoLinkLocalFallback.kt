package app.echo.android

import app.echo.android.data.EchoLibrarySelectedSource
import app.echo.android.model.connect.EchoRemoteConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect

/** Terminal connection loss returns to the phone; transient reconnects keep the chosen destination. */
internal suspend fun observeEchoLinkLocalFallback(
    connectionStates: Flow<EchoRemoteConnectionState>,
    librarySources: Flow<String>,
    selectPhonePlayback: () -> Unit,
    selectLocalLibrary: suspend () -> Unit,
) {
    combine(connectionStates.distinctUntilChanged(), librarySources.distinctUntilChanged()) { state, source ->
        state to source
    }.collect { (state, source) ->
        // A temporary status failure must not replace the Remote dock and resize Home.
        // Connecting/pairing/reconnecting still belong to the selected PC session.
        if (state == EchoRemoteConnectionState.Disconnected || state == EchoRemoteConnectionState.Error) {
            selectPhonePlayback()
            if (source == EchoLibrarySelectedSource.PcEcho) selectLocalLibrary()
        }
    }
}
