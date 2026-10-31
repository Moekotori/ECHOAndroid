package app.echo.android

import android.net.Uri
import app.echo.android.data.LibraryTrackEntity
import app.echo.android.data.parseNeteaseSongId
import app.echo.android.lyrics.EchoLyricsSearchRequest
import app.echo.android.lyrics.EchoLyricsParser
import app.echo.android.lyrics.ImportedLyricsStore
import app.echo.android.lyrics.LocalLyricsResolver
import app.echo.android.lyrics.LyricsSelectionWrites
import app.echo.android.lyrics.withUserOffset
import app.echo.android.model.lyrics.EchoLyricsCandidate
import app.echo.android.lyrics.LyricsApplyPolicy
import app.echo.android.lyrics.LyricsContentPolicy
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

@Suppress("SpellCheckingInspection")
internal class LyricsController(
    private val trackForLyrics: suspend (String) -> LibraryTrackEntity?,
    private val lyricsResolver: LocalLyricsResolver,
    private val onlineLyricsResolver: OnlineLyricsResolver,
    private val importedLyricsStore: ImportedLyricsStore,
    private val scope: CoroutineScope,
    private val subsonicLyricsLoader: (LibraryTrackEntity) -> EchoLyrics? = { null },
    private val onSelectionChanged: suspend (String, Boolean) -> Unit = { _, _ -> },
    private val onLoaded: suspend (LibraryTrackEntity, Long) -> Unit = { _, _ -> },
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
    private val selectionWrites = LyricsSelectionWrites()
    fun beginSelectionChange(trackId: String): Long = selectionWrites.begin(trackId)
    fun selectionVersion(trackId: String): Long = selectionWrites.current(trackId)
    fun isCurrentSelection(trackId: String, version: Long): Boolean = selectionWrites.isCurrent(trackId, version)
    suspend fun withCurrentSelection(trackId: String, version: Long, block: suspend () -> Unit): Boolean =
        selectionWrites.runIfCurrent(trackId, version, block)
    private var lastLyricsTrackId: String? = null
    val currentTrackId: String? get() = lastLyricsTrackId
    suspend fun saveEditedLyrics(trackId: String, lyrics: EchoLyrics, version: Long = beginSelectionChange(trackId)) {
        withContext(Dispatchers.IO) {
            withCurrentSelection(trackId, version) {
                importedLyricsStore.save(trackId, lyrics, selected = true)
                recordSelectionAvailability(trackId, LyricsContentPolicy.readable(lyrics) != null)
                importedLyricsStore.setLyricsOffset(trackId, 0)
            }
        }
        if (isCurrentSelection(trackId, version) && lastLyricsTrackId == trackId) updateLyricsForTrack(trackId, force = true)
    }

    suspend fun applyEmbeddedLyricsEdit(trackId: String, text: String, version: Long = beginSelectionChange(trackId)) {
        val lyrics = withContext(Dispatchers.Default) {
            EchoLyricsParser.parse(text, sourceLabel = "Embedded tags")
        }
        // The explicit tag edit becomes the selected source, including an intentional clear.
        // Keep the old selection intact until the audio file has been written successfully.
        saveEditedLyrics(trackId, lyrics, version)
    }
    suspend fun backupSelections(lookup: suspend (Set<String>) -> Map<String,String>) = withContext(Dispatchers.IO) {
        importedLyricsStore.selectedForBackup(lookup)
    }
    suspend fun restoreSelection(trackId: String, json: String?, offset: Long) {
        val version = beginSelectionChange(trackId)
        withContext(Dispatchers.IO) {
            withCurrentSelection(trackId, version) {
                if (json != null) {
                    val restored = app.echo.android.lyrics.EchoLyricsJson.decode(json)
                    importedLyricsStore.save(trackId, restored, selected = true)
                    recordSelectionAvailability(trackId, LyricsContentPolicy.readable(restored) != null)
                }
                importedLyricsStore.setLyricsOffset(trackId, offset)
            }
        }
        if (isCurrentSelection(trackId, version) && lastLyricsTrackId == trackId) updateLyricsForTrack(trackId, force = true)
    }
    private var currentLyricsUserOffsetMs: Long = 0L
    @Volatile
    private var onlineLyricsEnabled: Boolean = false
    @Volatile
    private var sourceOrder = app.echo.android.model.settings.EchoLyricsSource.entries.toList()

    fun setSourceOrder(value: List<app.echo.android.model.settings.EchoLyricsSource>, trackId: String?) {
        val normalized = app.echo.android.model.settings.EchoLyricsOptions(sourceOrder = value).normalized.sourceOrder
        if (sourceOrder == normalized) return
        sourceOrder = normalized
        updateLyricsForTrack(trackId, force = true)
    }
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

    suspend fun previewEncodedLyrics(uri: Uri, charsetName: String?): EchoLyrics = withContext(Dispatchers.IO) {
        lyricsResolver.importFromUri(uri, charsetName)
    }

    suspend fun importEncodedLyrics(uri: Uri, target: String, charsetName: String?): Boolean {
        val version = beginSelectionChange(target)
        importJob?.cancel()
        val parsed = withContext(Dispatchers.IO) {
            val imported = lyricsResolver.importFromUri(uri, charsetName)
            if (!withCurrentSelection(target, version) {
                importedLyricsStore.save(target, imported, selected = true)
                recordSelectionAvailability(target, true)
            }) return@withContext null
            imported
        } ?: return false
        if (!isCurrentSelection(target, version)) return false
        if (target == lastLyricsTrackId) updateLyricsForTrack(target, force = true)
        _managementError.value = null
        return true
    }

    fun importLyrics(
        uri: Uri,
        currentTrackId: String?,
        onImported: ((EchoLyrics, Long) -> Unit)? = null,
    ) {
        val target = currentTrackId ?: return
        val version = beginSelectionChange(target)
        _managementError.value = null
        importJob?.cancel()
        importJob = scope.launch {
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val imported = lyricsResolver.importFromUri(uri)
                    // The atomic selected file is self-contained; URI bindings are legacy fallback only.
                    if (!withCurrentSelection(target, version) {
                        importedLyricsStore.save(target, imported, selected = true)
                        recordSelectionAvailability(target, true)
                    }) return@withContext null
                    imported
                } ?: return@launch
                if (!isCurrentSelection(target, version)) return@launch
                if (target == lastLyricsTrackId) updateLyricsForTrack(target, force = true)
                _managementError.value = null
                onImported?.invoke(parsed, version)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (!isCurrentSelection(target, version)) return@launch
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
                    val track = trackForLyrics(trackId) ?: return@withContext emptyList()
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
        _managementError.value = null
        val version = beginSelectionChange(trackId)
        lyricsJob?.cancel()
        scope.launch { saveCandidate(candidate, trackId, version) }
    }

    suspend fun selectCandidateAndAwait(id: String, trackId: String?): Boolean {
        if (trackId == null || trackId != lastLyricsTrackId || trackId != searchTrackId) return false
        val candidate = _candidates.value.firstOrNull { it.id == id } ?: return false
        _managementError.value = null
        val version = beginSelectionChange(trackId)
        lyricsJob?.cancel()
        return saveCandidate(candidate, trackId, version)
    }

    private suspend fun saveCandidate(candidate: app.echo.android.model.lyrics.EchoLyricsCandidate, trackId: String, version: Long): Boolean {
        return try {
            withContext(Dispatchers.IO) { withCurrentSelection(trackId, version) {
                importedLyricsStore.save(trackId, candidate.lyrics.copy(metadata = candidate.lyrics.metadata + ("selection_id" to candidate.id)), selected = true)
                recordSelectionAvailability(trackId, LyricsContentPolicy.readable(candidate.lyrics) != null)
            } }
            if (!isCurrentSelection(trackId, version) || trackId != lastLyricsTrackId) return false
            updateLyricsForTrack(trackId, force = true)
            true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            if (isCurrentSelection(trackId, version)) reportManagementError(error.lyricsErrorMessage("Could not save lyrics"))
            false
        }
    }

    fun removeSelection(trackId: String?) {
        if (trackId == null) return
        val version = beginSelectionChange(trackId)
        scope.launch {
            try {
                withContext(Dispatchers.IO) { withCurrentSelection(trackId, version) {
                    importedLyricsStore.unbindLyrics(trackId)
                    recordSelectionAvailability(trackId, false)
                } }
                if (isCurrentSelection(trackId, version) && trackId == lastLyricsTrackId) updateLyricsForTrack(trackId, force = true)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (!isCurrentSelection(trackId, version)) return@launch
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
        val indexVersion = selectionVersion(trackId)
        lyricsJob = scope.launch {
            val result = withContext(Dispatchers.IO) {
                var inspectedTrack: LibraryTrackEntity? = null
                runCatching {
                    val selected = LyricsContentPolicy.readable(importedLyricsStore.readSaved(trackId, selected = true))
                    val echoLinkLyrics = selected ?: LyricsContentPolicy.readable(cachedEchoLinkLyrics(trackId))
                        ?: LyricsContentPolicy.readable(fetchEchoLinkLyrics(trackId))
                    if (echoLinkLyrics != null) {
                        val userOffsetMs = importedLyricsStore.lyricsOffsetForTrack(trackId)
                        LyricsLoadResult(
                            state = if (echoLinkLyrics.lines.any { it.text.isNotBlank() }) {
                                EchoLyricsLoadState.Ready(echoLinkLyrics)
                            } else EchoLyricsLoadState.Missing,
                            userOffsetMs = userOffsetMs,
                        )
                    } else {
                        val track = trackForLyrics(trackId)
                        inspectedTrack = track
                        if (selected != null) {
                            LyricsLoadResult(EchoLyricsLoadState.Ready(selected), importedLyricsStore.lyricsOffsetForTrack(trackId))
                        } else if (track == null) {
                            LyricsLoadResult(EchoLyricsLoadState.Missing)
                        } else {
                            val userOffsetMs = importedLyricsStore.lyricsOffsetForTrack(trackId)
                            val importedLyrics = importedLyricsStore.lyricsUriForTrack(trackId)
                                ?.let { runCatching { lyricsResolver.loadFromUri(it) }.getOrNull() }
                            val localLyrics = selected ?: importedLyrics ?: runCatching { lyricsResolver.loadForTrack(track, sourceOrder) }.getOrNull()
                                ?.takeIf { it.lines.any { line -> line.text.isNotBlank() } }
                            val serverLyrics = if (localLyrics == null) {
                                LyricsContentPolicy.readable(runCatching { subsonicLyricsLoader(track) }.getOrNull())
                            } else {
                                null
                            }
                            val searchRequest = track.toLyricsSearchRequest()
                            val savedOnlineLyrics = if (localLyrics == null && serverLyrics == null) {
                                importedLyricsStore.readSaved(trackId, selected = false)
                                    ?.takeIf { OnlineLyricsCachePolicy.matches(it, searchRequest) }
                                    ?.let(LyricsContentPolicy::readable)
                            } else null
                            val onlineLyrics = if (localLyrics == null && serverLyrics == null) {
                                savedOnlineLyrics ?: LyricsContentPolicy.readable(directNeteaseLyrics(track))
                                    ?: if (onlineLyricsEnabled) LyricsContentPolicy.readable(cachedOnlineLyrics(track)) else null
                            } else {
                                null
                            }
                            if (onlineLyrics != null && savedOnlineLyrics == null) runCatching {
                                importedLyricsStore.save(trackId, OnlineLyricsCachePolicy.stamp(onlineLyrics, searchRequest), selected = false)
                            }
                            (localLyrics ?: serverLyrics ?: onlineLyrics)
                                ?.let(LyricsContentPolicy::readable)
                                ?.let(EchoLyricsLoadState::Ready)
                                ?.let { LyricsLoadResult(state = it, userOffsetMs = userOffsetMs) }
                                ?: LyricsLoadResult(EchoLyricsLoadState.Missing)
                        }
                    }
                }.getOrElse { error ->
                    if (error is CancellationException) throw error
                    LyricsLoadResult(EchoLyricsLoadState.Error(error.lyricsErrorMessage("Lyrics load failed")))
                }.also { loaded ->
                    if (loaded.state is EchoLyricsLoadState.Ready) updateLyricsIndex {
                        val evidence = inspectedTrack ?: trackForLyrics(trackId)
                        if (evidence != null) withCurrentSelection(trackId, indexVersion) {
                            // This source may have just been downloaded; its evidence is newer
                            // than an inspection that started before that download completed.
                            onLoaded(evidence, System.currentTimeMillis())
                        }
                    }
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
        LyricsContentPolicy.readable(synchronized(onlineCacheLock) { onlineLyricsCache[cacheKey] })?.let { return it }
        val context = coroutineContext
        return onlineLyricsResolver.loadForTrack(track.toLyricsSearchRequest()) { context.ensureActive() }
            ?.let(LyricsContentPolicy::readable)
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
        ).let(LyricsContentPolicy::readable) ?: return null
        synchronized(echoLinkLyricsLock) {
            echoLinkLyricsCache[trackId] = parsed
        }
        return parsed
    }

    private suspend fun directNeteaseLyrics(track: LibraryTrackEntity): EchoLyrics? {
        val songId = parseNeteaseSongId(track.id) ?: return null
        val cacheKey = "netease:$songId"
        LyricsContentPolicy.readable(synchronized(onlineCacheLock) { onlineLyricsCache[cacheKey] })?.let { return it }
        val context = coroutineContext
        return onlineLyricsResolver.loadFromNeteaseSongId(songId) { context.ensureActive() }
            ?.let(LyricsContentPolicy::readable)
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

    private suspend fun recordSelectionAvailability(trackId: String, present: Boolean) =
        updateLyricsIndex { onSelectionChanged(trackId, present) }

    private suspend fun updateLyricsIndex(write: suspend () -> Unit) {
        try { write() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            // Secondary index failure cannot turn a successful lyric import/load into a failure.
            EchoErrorLog.record(EchoErrorSource.Lyrics, error.lyricsErrorMessage("Could not update lyrics index"))
        }
    }

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
