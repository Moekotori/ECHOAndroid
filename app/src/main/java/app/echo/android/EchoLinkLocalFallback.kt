package app.echo.android

import app.echo.android.data.EchoLibrarySelectedSource
import app.echo.android.model.connect.EchoRemoteConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect

/** Keep disconnected PC controls and library selection out of the phone session. */
internal suspend fun observeEchoLinkLocalFallback(
    connectionStates: Flow<EchoRemoteConnectionState>,
    librarySources: Flow<String>,
    selectPhonePlayback: () -> Unit,
    selectLocalLibrary: suspend () -> Unit,
) {
    combine(connectionStates.distinctUntilChanged(), librarySources.distinctUntilChanged()) { state, source ->
        state to source
    }.collect { (state, source) ->
        if (state != EchoRemoteConnectionState.Connected) {
            selectPhonePlayback()
            if (source == EchoLibrarySelectedSource.PcEcho) selectLocalLibrary()
        }
    }
}
