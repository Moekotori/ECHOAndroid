package app.echo.android

import app.echo.android.connect.EchoRemoteClient
import app.echo.android.model.connect.EchoRemoteLyrics
import app.echo.android.model.connect.EchoRemoteTrack
import app.echo.android.model.library.EchoTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Session-owned destination selection; switching never replaces either playback queue. */
class EchoLinkPlaybackRouter(private val client: EchoRemoteClient) {
    private val selectedRemoteMode = MutableStateFlow(false)
    val remoteMode = selectedRemoteMode.asStateFlow()

    fun selectRemoteMode(remote: Boolean) {
        if (selectedRemoteMode.value == remote) return
        client.cancelPhonePlaybackRequest()
        selectedRemoteMode.value = remote
    }

    fun play(
        tracks: List<EchoRemoteTrack>,
        startIndex: Int,
        onPhoneQueueReady: (List<EchoTrack>, Int) -> Unit,
        onPhoneLyricsReady: (String, EchoRemoteLyrics) -> Unit,
    ) {
        if (selectedRemoteMode.value) {
            client.playQueueOnPc(tracks, startIndex)
        } else {
            client.playTracksOnPhone(
                tracks, startIndex,
                onQueueReady = { queue, index ->
                    if (!selectedRemoteMode.value) onPhoneQueueReady(queue, index)
                },
                onLyricsReady = { id, lyrics ->
                    if (!selectedRemoteMode.value) onPhoneLyricsReady(id, lyrics)
                },
            )
        }
    }
}
