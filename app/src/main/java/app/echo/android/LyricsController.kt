package app.echo.android

import android.net.Uri
import app.echo.android.data.EchoLibraryRepository
import app.echo.android.data.LibraryTrackEntity
import app.echo.android.data.parseNeteaseSongId
import app.echo.android.lyrics.EchoLyricsSearchRequest
import app.echo.android.lyrics.EchoLyricsParser
import app.echo.android.lyrics.ImportedLyricsStore
import app.echo.android.lyrics.LocalLyricsResolver
import app.echo.android.lyrics.withUserOffset
import app.echo.android.model.lyrics.EchoLyricsCandidate
import app.echo.android.lyrics.LyricsApplyPolicy
import app.echo.android.lyrics.OnlineLyricsCachePolicy
import app.echo.android.lyrics.OnlineLyricsResolver
import app.echo.android.model.connect.EchoRemoteLyrics
import app.echo.android.model.error.EchoErrorLog
import app.echo.android.model.error.EchoErrorSource
import app.echo.android.model.lyrics.EchoLyrics
import app.echo.android.model.lyrics.EchoLyricsLoadState
import app.echo.android.model.playback.EchoLinkPlaybackUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

@Suppress("SpellCheckingInspection", "ConstPropertyName")
internal class LyricsController(
    private val repository: EchoLibraryRepository,
    private val lyricsResolver: LocalLyricsResolver,
    private val onlineLyricsResolver: OnlineLyricsResolver,
    private val importedLyricsStore: ImportedLyricsStore,
    private val scope: CoroutineScope,
    private val subsonicLyricsLoader: (LibraryTrackEntity) -> EchoLyrics? = { null },
) {
    private val _lyricsState = MutableStateFlow<EchoLyricsLoadState>(EchoLyricsLoadState.Idle)
    val lyricsState: StateFlow<EchoLyricsLoadState> = _lyricsState.asStateFlow()

    private var lyricsJob: Job? = null
    private var importJob: Job? = null
    private var searchJob: Job? = null
    private var originalLyrics: EchoLyrics? = null
    private var searchTrackId: String? = null
    private var searchGeneration = 0L
    private val _candidates = MutableStateFlow<List<EchoLyricsCandidate>>(emptyList())
    val candidates = _candidates.asStateFlow()
    private val _searching = MutableStateFlow(false)
    val searching = _searching.asStateFlow()
    private val _managementError = MutableStateFlow<String?>(null)
    val managementError = _managementError.asStateFlow()
    private val onlineCacheLock = Any()
    private val offsetWriteMutex = Mutex()
    private var lastLyricsTrackId: String? = null
    private var currentLyricsUserOffsetMs: Long = 0L
    @Volatile
    private var onlineLyricsEnabled: Boolean = false
    private val onlineLyricsCache = object : LinkedHashMap<String, EchoLyrics>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, EchoLyrics>?): Boolean =
            size > MAX_ONLINE_LYRICS_CACHE_ENTRIES
    }
    private val echoLinkLyricsCache = object : LinkedHashMap<String, EchoLyrics>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, EchoLyrics>?): Boolean =
            size > MAX_ECHO_LINK_LYRICS_CACHE_ENTRIES
    }
    private val echoLinkLyricsLock = Any()
    @Volatile
    private var echoLinkLyricsFetcher: (suspend (String) -> EchoRemoteLyrics?)? = null

    fun importLyrics(
        uri: Uri,
        currentTrackId: String?,
        onImported: ((EchoLyrics) -> Unit)? = null,
    ) {
        val target = currentTrackId ?: return
        _managementError.value = null
        importJob?.cancel()
        importJob = scope.launch {
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val imported = lyricsResolver.importFromUri(uri)
                    importedLyricsStore.save(target, imported, selected = true)
                    importedLyricsStore.bindLyrics(target, uri)
                    imported
                }
                if (target == lastLyricsTrackId) updateLyricsForTrack(target, force = true)
                _managementError.value = null
                onImported?.invoke(parsed)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                reportManagementError(error.lyricsErrorMessage("Lyrics import failed"))
                if (target == lastLyricsTrackId && originalLyrics == null) {
                    _lyricsState.value = EchoLyricsLoadState.Error(_managementError.value!!)
                }
            }
        }
    }

    fun adjustLyricsOffset(deltaMs: Long, currentTrackId: String?) {
        val trackId = currentTrackId ?: return
        val original = originalLyrics ?: return
        if (trackId != lastLyricsTrackId) return
        val target = (currentLyricsUserOffsetMs + deltaMs).coerceIn(-30_000L, 30_000L)
        currentLyricsUserOffsetMs = target
        _lyricsState.value = EchoLyricsLoadState.Ready(original.withUserOffset(target))
        // Launch in the owner scope, preserving edit order before switching to I/O in DataStore.
        scope.launch {
            try { offsetWriteMutex.withLock {
                withContext(Dispatchers.IO) { importedLyricsStore.setLyricsOffset(trackId, target) }
            } } catch (error: Exception) {
                if (error is CancellationException) throw error
                reportManagementError(error.lyricsErrorMessage("Could not save timing adjustment"))
            }
        }
    }

    fun resetLyricsOffset(currentTrackId: String?) = adjustLyricsOffset(-currentLyricsUserOffsetMs, currentTrackId)

    fun searchLyrics(trackId: String?) {
        searchJob?.cancel()
        val generation = ++searchGeneration
        searchTrackId = trackId
        _candidates.value = emptyList()
        _managementError.value = null
        if (trackId == null) { _searching.value = false; return }
        _searching.value = true
        searchJob = scope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    val track = repository.trackForLyrics(trackId) ?: return@withContext emptyList()
                    val context = coroutineContext
                    onlineLyricsResolver.search(track.toLyricsSearchRequest()) { context.ensureActive() }
                }
                if (generation == searchGeneration && trackId == lastLyricsTrackId && searchTrackId == trackId) _candidates.value = results
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                reportManagementError(error.lyricsErrorMessage("Lyrics search failed"))
            } finally { if (generation == searchGeneration) _searching.value = false }
        }
    }

    fun cancelSearch() {
        searchGeneration++
        searchTrackId = null
        searchJob?.cancel()
        _searching.value = false
        _candidates.value = emptyList()
    }

    fun selectCandidate(id: String, trackId: String?) {
        if (trackId == null || trackId != lastLyricsTrackId || trackId != searchTrackId) return
        val candidate = _candidates.value.firstOrNull { it.id == id } ?: return
        lyricsJob?.cancel()
        lyricsJob = scope.launch {
            try {
                withContext(Dispatchers.IO) { importedLyricsStore.save(trackId, candidate.lyrics.copy(metadata = candidate.lyrics.metadata + ("selection_id" to id)), selected = true) }
                if (trackId == lastLyricsTrackId) updateLyricsForTrack(trackId, force = true)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                reportManagementError(error.lyricsErrorMessage("Could not save lyrics"))
            }
        }
    }

    fun removeSelection(trackId: String?) {
        if (trackId == null) return
        scope.launch {
            try {
                withContext(Dispatchers.IO) { importedLyricsStore.unbindLyrics(trackId) }
                if (trackId == lastLyricsTrackId) updateLyricsForTrack(trackId, force = true)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                reportManagementError(error.lyricsErrorMessage("Could not clear lyrics selection"))
            }
        }
    }

    fun refreshLyrics(trackId: String?) {
        if (trackId == null) return
        // Search is an explicit online request. Existing lyrics stay visible until a selection succeeds.
        searchLyrics(trackId)
    }

    fun setOnlineLyricsEnabled(enabled: Boolean, currentTrackId: String?) {
        if (onlineLyricsEnabled == enabled) return
        onlineLyricsEnabled = enabled
        if (enabled && currentTrackId != null && _lyricsState.value is EchoLyricsLoadState.Missing) {
            updateLyricsForTrack(currentTrackId, force = true)
        }
    }

    fun updateLyricsForTrack(trackId: String?, force: Boolean = false) {
        if (!force && trackId == lastLyricsTrackId) return
        if (lastLyricsTrackId != trackId) {
            cancelSearch()
            _managementError.value = null
        }
        lastLyricsTrackId = trackId
        originalLyrics = null
        lyricsJob?.cancel()
        if (trackId == null) {
            currentLyricsUserOffsetMs = 0L
            _lyricsState.value = EchoLyricsLoadState.Idle
            return
        }

        _lyricsState.value = EchoLyricsLoadState.Loading
        lyricsJob = scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val selected = importedLyricsStore.readSaved(trackId, selected = true)
                    val echoLinkLyrics = selected ?: cachedEchoLinkLyrics(trackId) ?: fetchEchoLinkLyrics(trackId)
                    if (echoLinkLyrics != null) {
                        val userOffsetMs = importedLyricsStore.lyricsOffsetForTrack(trackId)
                        LyricsLoadResult(
                            state = EchoLyricsLoadState.Ready(echoLinkLyrics),
                            userOffsetMs = userOffsetMs,
                        )
                    } else {
                        val track = repository.trackForLyrics(trackId)
                        if (track == null) {
                            LyricsLoadResult(EchoLyricsLoadState.Missing)
                        } else {
                            val userOffsetMs = importedLyricsStore.lyricsOffsetForTrack(trackId)
                            val importedLyrics = importedLyricsStore.lyricsUriForTrack(trackId)
                                ?.let { runCatching { lyricsResolver.loadFromUri(it) }.getOrNull() }
                            val localLyrics = importedLyrics ?: runCatching { lyricsResolver.loadForTrack(track) }.getOrNull()
                                ?.takeIf { it.lines.any { line -> line.text.isNotBlank() } }
                            val serverLyrics = if (localLyrics == null) {
                                runCatching { subsonicLyricsLoader(track) }.getOrNull()
                            } else {
                                null
                            }
                            val searchRequest = track.toLyricsSearchRequest()
                            val savedOnlineLyrics = if (localLyrics == null && serverLyrics == null) {
                                importedLyricsStore.readSaved(trackId, selected = false)
                                    ?.takeIf { OnlineLyricsCachePolicy.matches(it, searchRequest) }
                            } else null
                            val onlineLyrics = if (localLyrics == null && serverLyrics == null) {
                                savedOnlineLyrics ?: directNeteaseLyrics(track)
                                    ?: if (onlineLyricsEnabled) cachedOnlineLyrics(track) else null
                            } else {
                                null
                            }
                            if (onlineLyrics != null && savedOnlineLyrics == null) runCatching {
                                importedLyricsStore.save(trackId, OnlineLyricsCachePolicy.stamp(onlineLyrics, searchRequest), selected = false)
                            }
                            (localLyrics ?: serverLyrics ?: onlineLyrics)
                                ?.takeIf { it.lines.isNotEmpty() }
                                ?.let(EchoLyricsLoadState::Ready)
                                ?.let { LyricsLoadResult(state = it, userOffsetMs = userOffsetMs) }
                                ?: LyricsLoadResult(EchoLyricsLoadState.Missing)
                        }
                    }
                }.getOrElse { error ->
                    if (error is CancellationException) throw error
                    LyricsLoadResult(EchoLyricsLoadState.Error(error.lyricsErrorMessage("Lyrics load failed")))
                }
            }
            if (!isActive) return@launch
            if (!LyricsApplyPolicy.shouldApplyLyricsResult(trackId, lastLyricsTrackId)) {
                return@launch
            }
            currentLyricsUserOffsetMs = result.userOffsetMs
            originalLyrics = (result.state as? EchoLyricsLoadState.Ready)?.lyrics
            _lyricsState.value = originalLyrics?.withUserOffset(result.userOffsetMs)
                ?.let(EchoLyricsLoadState::Ready) ?: result.state
        }
    }

    fun setEchoLinkLyricsFetcher(fetcher: suspend (String) -> EchoRemoteLyrics?) {
        echoLinkLyricsFetcher = fetcher
    }

    fun setEchoLinkLyrics(trackId: String, rawText: String, sourceLabel: String?) {
        if (trackId.isBlank() || rawText.isBlank()) return
        scope.launch {
            val lyrics = withContext(Dispatchers.IO) {
                runCatching {
                    EchoLyricsParser.parse(
                        rawText = rawText,
                        sourceLabel = sourceLabel ?: "PC ECHO",
                    ).takeIf { it.lines.isNotEmpty() }
                }.getOrNull()
            } ?: return@launch

            synchronized(echoLinkLyricsLock) {
                echoLinkLyricsCache[trackId] = lyrics
            }
            if (lastLyricsTrackId == trackId) {
                updateLyricsForTrack(trackId, force = true)
            }
        }
    }

    fun clear() {
        lyricsJob?.cancel()
        importJob?.cancel()
        cancelSearch()
    }

    private data class LyricsLoadResult(
        val state: EchoLyricsLoadState,
        val userOffsetMs: Long = 0L,
    )

    private suspend fun cachedOnlineLyrics(track: LibraryTrackEntity): EchoLyrics? {
        val cacheKey = onlineLyricsCacheKey(track)
        synchronized(onlineCacheLock) { onlineLyricsCache[cacheKey] }?.let { return it }
        val context = coroutineContext
        return onlineLyricsResolver.loadForTrack(track.toLyricsSearchRequest()) { context.ensureActive() }
            ?.also { synchronized(onlineCacheLock) { onlineLyricsCache[cacheKey] = it } }
    }

    private fun cachedEchoLinkLyrics(trackId: String): EchoLyrics? =
        synchronized(echoLinkLyricsLock) {
            echoLinkLyricsCache[trackId]
        }

    private suspend fun fetchEchoLinkLyrics(trackId: String): EchoLyrics? {
        if (EchoLinkPlaybackUri.trackIdFromMediaId(trackId) == null) return null
        val remote = echoLinkLyricsFetcher?.invoke(trackId) ?: return null
        if (remote.rawText.isBlank()) return null
        val parsed = EchoLyricsParser.parse(
            rawText = remote.rawText,
            sourceLabel = remote.sourceLabel ?: "PC ECHO",
        ).takeIf { it.lines.isNotEmpty() } ?: return null
        synchronized(echoLinkLyricsLock) {
            echoLinkLyricsCache[trackId] = parsed
        }
        return parsed
    }

    private suspend fun directNeteaseLyrics(track: LibraryTrackEntity): EchoLyrics? {
        val songId = parseNeteaseSongId(track.id) ?: return null
        val cacheKey = "netease:$songId"
        synchronized(onlineCacheLock) { onlineLyricsCache[cacheKey] }?.let { return it }
        val context = coroutineContext
        return onlineLyricsResolver.loadFromNeteaseSongId(songId) { context.ensureActive() }
            ?.also { synchronized(onlineCacheLock) { onlineLyricsCache[cacheKey] = it } }
    }

    private fun onlineLyricsCacheKey(track: LibraryTrackEntity): String =
        listOf(track.id, track.title, track.artist, track.album.orEmpty(), track.durationMs).joinToString("|")

    private fun LibraryTrackEntity.toLyricsSearchRequest(): EchoLyricsSearchRequest =
        EchoLyricsSearchRequest(
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
        )

    private fun reportManagementError(message: String) {
        _managementError.value = message
        EchoErrorLog.record(EchoErrorSource.Lyrics, message)
    }

    private fun Throwable.lyricsErrorMessage(fallback: String): String =
        message?.takeIf { it.isNotBlank() }
            ?: javaClass.simpleName.takeIf { it.isNotBlank() }?.let { "$fallback: $it" }
            ?: fallback

    private companion object {
        const val MAX_ONLINE_LYRICS_CACHE_ENTRIES = 48
        const val MAX_ECHO_LINK_LYRICS_CACHE_ENTRIES = 32
    }
}
