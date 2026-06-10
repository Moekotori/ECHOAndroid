package app.echo.android

import android.net.Uri
import app.echo.android.data.EchoLibraryRepository
import app.echo.android.data.LibraryTrackEntity
import app.echo.android.data.parseNeteaseSongId
import app.echo.android.lyrics.EchoLyricsSearchRequest
import app.echo.android.lyrics.ImportedLyricsStore
import app.echo.android.lyrics.LocalLyricsResolver
import app.echo.android.lyrics.OnlineLyricsResolver
import app.echo.android.model.lyrics.EchoLyricLine
import app.echo.android.model.lyrics.EchoLyricWord
import app.echo.android.model.lyrics.EchoLyrics
import app.echo.android.model.lyrics.EchoLyricsLoadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

internal class LyricsController(
    private val repository: EchoLibraryRepository,
    private val lyricsResolver: LocalLyricsResolver,
    private val onlineLyricsResolver: OnlineLyricsResolver,
    private val importedLyricsStore: ImportedLyricsStore,
    private val scope: CoroutineScope,
) {
    private val _lyricsState = MutableStateFlow<EchoLyricsLoadState>(EchoLyricsLoadState.Idle)
    val lyricsState: StateFlow<EchoLyricsLoadState> = _lyricsState.asStateFlow()

    private var lyricsJob: Job? = null
    private var lastLyricsTrackId: String? = null
    private var currentLyricsUserOffsetMs: Long = 0L
    @Volatile
    private var onlineLyricsEnabled: Boolean = false
    private val onlineLyricsCache = object : LinkedHashMap<String, EchoLyrics>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, EchoLyrics>?): Boolean =
            size > MaxOnlineLyricsCacheEntries
    }

    fun importLyrics(uri: Uri, currentTrackId: String?) {
        val trackIdAtImport = currentTrackId ?: lastLyricsTrackId
        lastLyricsTrackId = trackIdAtImport
        lyricsJob?.cancel()
        _lyricsState.value = EchoLyricsLoadState.Loading
        lyricsJob = scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val userOffsetMs = trackIdAtImport?.let { importedLyricsStore.lyricsOffsetForTrack(it) } ?: 0L
                    lyricsResolver.importFromUri(uri)
                        .also {
                            trackIdAtImport?.let { trackId ->
                                runCatching { importedLyricsStore.bindLyrics(trackId, uri) }
                            }
                        }
                        .withUserOffset(userOffsetMs)
                        .let(EchoLyricsLoadState::Ready)
                        .let { LyricsLoadResult(state = it, userOffsetMs = userOffsetMs) }
                }.getOrElse { error ->
                    if (error is CancellationException) throw error
                    LyricsLoadResult(EchoLyricsLoadState.Error(error.lyricsErrorMessage("Lyrics import failed")))
                }
            }
            val latestTrackId = currentTrackId
            if (trackIdAtImport == null || latestTrackId == null || latestTrackId == trackIdAtImport) {
                val effectiveTrackId = trackIdAtImport ?: latestTrackId
                if (effectiveTrackId != null && result.state is EchoLyricsLoadState.Ready) {
                    scope.launch(Dispatchers.IO) {
                        runCatching { importedLyricsStore.bindLyrics(effectiveTrackId, uri) }
                    }
                    lastLyricsTrackId = effectiveTrackId
                }
                currentLyricsUserOffsetMs = result.userOffsetMs
                _lyricsState.value = result.state
            }
        }
    }

    fun adjustLyricsOffset(deltaMs: Long, currentTrackId: String?) {
        val trackId = currentTrackId ?: return
        val ready = _lyricsState.value as? EchoLyricsLoadState.Ready ?: return
        val targetOffset = (currentLyricsUserOffsetMs + deltaMs).coerceIn(-30_000L, 30_000L)
        val actualDelta = targetOffset - currentLyricsUserOffsetMs
        if (actualDelta == 0L) return

        currentLyricsUserOffsetMs = targetOffset
        _lyricsState.value = EchoLyricsLoadState.Ready(ready.lyrics.shiftedBy(actualDelta, userOffsetMs = targetOffset))
        scope.launch(Dispatchers.IO) {
            runCatching { importedLyricsStore.setLyricsOffset(trackId, targetOffset) }
        }
    }

    fun resetLyricsOffset(currentTrackId: String?) {
        val trackId = currentTrackId ?: return
        val ready = _lyricsState.value as? EchoLyricsLoadState.Ready ?: return
        val actualDelta = -currentLyricsUserOffsetMs
        if (actualDelta == 0L) return

        currentLyricsUserOffsetMs = 0L
        _lyricsState.value = EchoLyricsLoadState.Ready(ready.lyrics.shiftedBy(actualDelta, userOffsetMs = 0L))
        scope.launch(Dispatchers.IO) {
            runCatching { importedLyricsStore.setLyricsOffset(trackId, 0L) }
        }
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
        lastLyricsTrackId = trackId
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
                    val track = repository.trackForLyrics(trackId)
                    if (track == null) {
                        LyricsLoadResult(EchoLyricsLoadState.Missing)
                    } else {
                        val userOffsetMs = importedLyricsStore.lyricsOffsetForTrack(trackId)
                        val importedLyrics = importedLyricsStore.lyricsUriForTrack(trackId)
                            ?.let(lyricsResolver::loadFromUri)
                        val localLyrics = importedLyrics ?: lyricsResolver.loadForTrack(track)
                        val onlineLyrics = if (localLyrics == null) {
                            directNeteaseLyrics(track)
                                ?: if (onlineLyricsEnabled) cachedOnlineLyrics(track) else null
                        } else {
                            null
                        }
                        (localLyrics ?: onlineLyrics)
                            ?.takeIf { it.lines.isNotEmpty() }
                            ?.withUserOffset(userOffsetMs)
                            ?.let(EchoLyricsLoadState::Ready)
                            ?.let { LyricsLoadResult(state = it, userOffsetMs = userOffsetMs) }
                            ?: LyricsLoadResult(EchoLyricsLoadState.Missing)
                    }
                }.getOrElse { error ->
                    if (error is CancellationException) throw error
                    LyricsLoadResult(EchoLyricsLoadState.Error(error.lyricsErrorMessage("Lyrics load failed")))
                }
            }
            currentLyricsUserOffsetMs = result.userOffsetMs
            _lyricsState.value = result.state
        }
    }

    fun clear() {
        lyricsJob?.cancel()
    }

    private data class LyricsLoadResult(
        val state: EchoLyricsLoadState,
        val userOffsetMs: Long = 0L,
    )

    private fun cachedOnlineLyrics(track: LibraryTrackEntity): EchoLyrics? {
        val cacheKey = onlineLyricsCacheKey(track)
        onlineLyricsCache[cacheKey]?.let { return it }
        return onlineLyricsResolver.loadForTrack(track.toLyricsSearchRequest())
            ?.also { onlineLyricsCache[cacheKey] = it }
    }

    private fun directNeteaseLyrics(track: LibraryTrackEntity): EchoLyrics? {
        val songId = parseNeteaseSongId(track.id) ?: return null
        val cacheKey = "netease:$songId"
        onlineLyricsCache[cacheKey]?.let { return it }
        return onlineLyricsResolver.loadFromNeteaseSongId(songId)
            ?.also { onlineLyricsCache[cacheKey] = it }
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

    private fun EchoLyrics.withUserOffset(userOffsetMs: Long): EchoLyrics =
        if (userOffsetMs == 0L) {
            this
        } else {
            shiftedBy(userOffsetMs, userOffsetMs = userOffsetMs)
        }

    private fun EchoLyrics.shiftedBy(deltaMs: Long, userOffsetMs: Long): EchoLyrics =
        copy(
            lines = lines.map { it.shiftedBy(deltaMs) },
            offsetMs = offsetMs + deltaMs,
            metadata = metadata + ("user_offset_ms" to userOffsetMs.toString()),
        )

    private fun EchoLyricLine.shiftedBy(deltaMs: Long): EchoLyricLine =
        copy(
            startMs = shiftTimestamp(startMs, deltaMs),
            endMs = endMs?.let { shiftTimestamp(it, deltaMs) },
            words = words.map { it.shiftedBy(deltaMs) },
        )

    private fun EchoLyricWord.shiftedBy(deltaMs: Long): EchoLyricWord =
        copy(
            startMs = shiftTimestamp(startMs, deltaMs),
            endMs = endMs?.let { shiftTimestamp(it, deltaMs) },
        )

    private fun shiftTimestamp(valueMs: Long, deltaMs: Long): Long =
        if (valueMs < 0L) valueMs else (valueMs + deltaMs).coerceAtLeast(0L)

    private fun Throwable.lyricsErrorMessage(fallback: String): String =
        message?.takeIf { it.isNotBlank() }
            ?: javaClass.simpleName.takeIf { it.isNotBlank() }?.let { "$fallback: $it" }
            ?: fallback

    private companion object {
        const val MaxOnlineLyricsCacheEntries = 48
    }
}
