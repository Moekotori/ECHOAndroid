package app.echo.android

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EchoLaunchActions {
    private val _openLyrics = MutableStateFlow(false)
    val openLyrics: StateFlow<Boolean> = _openLyrics.asStateFlow()

    private val _incomingAudioUris = MutableStateFlow<List<String>>(emptyList())
    val incomingAudioUris: StateFlow<List<String>> = _incomingAudioUris.asStateFlow()

    fun requestOpenLyrics() {
        _openLyrics.value = true
    }

    fun consumeOpenLyrics() {
        _openLyrics.value = false
    }

    fun requestPlayIncoming(uris: List<String>) {
        _incomingAudioUris.value = uris.filter { it.isNotBlank() }
    }

    fun consumeIncomingAudio() {
        _incomingAudioUris.value = emptyList()
    }

    private val _playLast = MutableStateFlow(false)
    val playLast: StateFlow<Boolean> = _playLast.asStateFlow()

    private val _openLibrary = MutableStateFlow(false)
    val openLibrary: StateFlow<Boolean> = _openLibrary.asStateFlow()

    fun requestPlayLast() {
        _playLast.value = true
    }

    fun consumePlayLast() {
        _playLast.value = false
    }

    fun requestOpenLibrary() {
        _openLibrary.value = true
    }

    fun consumeOpenLibrary() {
        _openLibrary.value = false
    }
}
