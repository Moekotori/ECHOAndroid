package app.echo.android

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EchoLaunchActions {
    private val _nfcMusic = MutableStateFlow<String?>(null)
    val nfcMusic = _nfcMusic.asStateFlow()
    fun requestNfcMusic(token: String) { _nfcMusic.value = token }
    fun consumeNfcMusic() { _nfcMusic.value = null }

    private val _voiceSearch = MutableStateFlow<app.echo.android.model.playback.EchoMediaSearchRequest?>(null)
    val voiceSearch = _voiceSearch.asStateFlow()
    fun requestVoiceSearch(request: app.echo.android.model.playback.EchoMediaSearchRequest) { _voiceSearch.value = request }
    fun consumeVoiceSearch() { _voiceSearch.value = null }

    private val _libraryShortcut = MutableStateFlow<app.echo.android.library.EchoLibraryShortcutRequest?>(null)
    val libraryShortcut = _libraryShortcut.asStateFlow()
    fun requestLibraryShortcut(request: app.echo.android.library.EchoLibraryShortcutRequest) { _libraryShortcut.value = request }
    fun consumeLibraryShortcut() { _libraryShortcut.value = null }

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

    private val _openCast = MutableStateFlow(false)
    val openCast: StateFlow<Boolean> = _openCast.asStateFlow()

    fun requestOpenCast() {
        _openCast.value = true
    }

    fun consumeOpenCast() {
        _openCast.value = false
    }

    private val _listeningInvite = MutableStateFlow<String?>(null)
    val listeningInvite: StateFlow<String?> = _listeningInvite.asStateFlow()

    fun requestOpenListening(code: String) {
        val trimmed = code.trim()
        if (trimmed.isNotEmpty()) _listeningInvite.value = trimmed
    }

    fun consumeListeningInvite() {
        _listeningInvite.value = null
    }
}
