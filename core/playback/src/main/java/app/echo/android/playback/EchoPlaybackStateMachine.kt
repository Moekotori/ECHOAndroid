package app.echo.android.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EchoPlaybackStateMachine {
    private val _status = MutableStateFlow(EchoPlaybackStatus())
    val status: StateFlow<EchoPlaybackStatus> = _status.asStateFlow()

    private var requestToken = 0L

    fun beginLoading(track: EchoTrackRef, commandName: String): Long {
        val token = ++requestToken
        _status.value = EchoPlaybackStatus(
            state = EchoPlaybackState.Loading,
            track = track,
            positionMs = 0L,
            durationMs = track.durationMs,
            diagnostics = _status.value.diagnostics.copy(
                requestToken = token,
                lastCommand = commandName,
                lastError = null,
            ),
        )
        return token
    }

    fun markPlaying(token: Long, positionMs: Long, durationMs: Long) {
        mutateIfCurrent(token) { current ->
            current.copy(
                state = EchoPlaybackState.Playing,
                isPlaying = true,
                positionMs = positionMs.coerceAtLeast(0L),
                durationMs = durationMs.coerceAtLeast(0L),
            )
        }
    }

    fun markPaused(positionMs: Long) {
        _status.value = _status.value.copy(
            state = EchoPlaybackState.Paused,
            isPlaying = false,
            positionMs = positionMs.coerceAtLeast(0L),
            diagnostics = _status.value.diagnostics.copy(lastCommand = "pause"),
        )
    }

    fun markBuffering(positionMs: Long) {
        _status.value = _status.value.copy(
            state = EchoPlaybackState.Buffering,
            isPlaying = false,
            positionMs = positionMs.coerceAtLeast(0L),
        )
    }

    fun markEnded() {
        _status.value = _status.value.copy(
            state = EchoPlaybackState.Ended,
            isPlaying = false,
            positionMs = _status.value.durationMs,
        )
    }

    fun markStopped() {
        _status.value = _status.value.copy(
            state = EchoPlaybackState.Stopped,
            isPlaying = false,
            diagnostics = _status.value.diagnostics.copy(lastCommand = "stop"),
        )
    }

    fun markError(error: EchoPlaybackError) {
        _status.value = _status.value.copy(
            state = EchoPlaybackState.Error,
            isPlaying = false,
            diagnostics = _status.value.diagnostics.copy(lastError = error),
        )
    }

    fun updatePosition(positionMs: Long, bufferedMs: Long) {
        val current = _status.value
        _status.value = current.copy(
            positionMs = positionMs.coerceAtLeast(0L),
            diagnostics = current.diagnostics.copy(bufferedMs = bufferedMs.coerceAtLeast(0L)),
        )
    }

    private inline fun mutateIfCurrent(token: Long, transform: (EchoPlaybackStatus) -> EchoPlaybackStatus) {
        val current = _status.value
        if (current.diagnostics.requestToken == token) {
            _status.value = transform(current)
        }
    }
}
