package app.echo.android.connect

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class EchoRemoteClient {
    private val _status = MutableStateFlow(EchoRemoteStatus())
    val status: StateFlow<EchoRemoteStatus> = _status.asStateFlow()

    fun pair(endpoint: EchoRemoteEndpoint) {
        _status.value = EchoRemoteStatus(
            connectionState = EchoRemoteConnectionState.Connected,
            endpoint = endpoint,
            playback = EchoRemotePlaybackSnapshot(
                state = EchoRemotePlaybackState.Paused,
                track = EchoRemoteTrack(
                    id = "pc-demo-track",
                    title = "PC ECHO 已就绪",
                    artist = endpoint.name,
                    album = "远程会话",
                    artworkUrl = null,
                    durationMs = 240_000L,
                ),
                positionMs = 42_000L,
                durationMs = 240_000L,
                volume = 0.72f,
                outputMode = "WASAPI Shared",
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    fun disconnect() {
        _status.value = EchoRemoteStatus()
    }

    fun ingest(message: EchoRemoteMessage) {
        when (message) {
            is EchoRemoteMessage.StatusSnapshot -> _status.update {
                it.copy(
                    connectionState = EchoRemoteConnectionState.Connected,
                    playback = message.payload,
                    error = null,
                )
            }

            is EchoRemoteMessage.Error -> _status.update {
                it.copy(connectionState = EchoRemoteConnectionState.Error, error = message.message)
            }

            is EchoRemoteMessage.Command,
            EchoRemoteMessage.Ping,
            EchoRemoteMessage.Pong,
            -> Unit
        }
    }

    fun send(command: EchoRemoteCommand) {
        _status.update { current ->
            val playback = when (command) {
                EchoRemoteCommand.PlayPause -> current.playback.copy(
                    state = if (current.playback.state == EchoRemotePlaybackState.Playing) {
                        EchoRemotePlaybackState.Paused
                    } else {
                        EchoRemotePlaybackState.Playing
                    },
                    updatedAtEpochMs = System.currentTimeMillis(),
                )

                EchoRemoteCommand.Next -> current.playback.copy(
                    track = current.playback.track?.copy(title = "已请求下一首"),
                    positionMs = 0L,
                    updatedAtEpochMs = System.currentTimeMillis(),
                )

                EchoRemoteCommand.Previous -> current.playback.copy(
                    track = current.playback.track?.copy(title = "已请求上一首"),
                    positionMs = 0L,
                    updatedAtEpochMs = System.currentTimeMillis(),
                )

                EchoRemoteCommand.Stop -> current.playback.copy(
                    state = EchoRemotePlaybackState.Stopped,
                    positionMs = 0L,
                    updatedAtEpochMs = System.currentTimeMillis(),
                )

                is EchoRemoteCommand.SeekTo -> current.playback.copy(
                    positionMs = command.positionMs.coerceIn(0L, current.playback.durationMs.coerceAtLeast(0L)),
                    updatedAtEpochMs = System.currentTimeMillis(),
                )

                is EchoRemoteCommand.SetVolume -> current.playback.copy(
                    volume = command.volume.coerceIn(0f, 1f),
                    updatedAtEpochMs = System.currentTimeMillis(),
                )
            }
            current.copy(playback = playback, error = null)
        }
    }
}
