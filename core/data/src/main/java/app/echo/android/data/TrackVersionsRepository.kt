package app.echo.android.data

import androidx.sqlite.db.SimpleSQLiteQuery
import app.echo.android.model.library.CueSheetPolicy
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoTrackVersionPolicy
import app.echo.android.model.library.EchoTrackVersions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lightweight preference resolver shared by UI and the background playback catalog. */
class TrackVersionsRepository(private val database: EchoLibraryDatabase, context: android.content.Context) {
    private val appContext = context.applicationContext
suspend fun trackVersions(track: EchoTrack): EchoTrackVersions = withContext(Dispatchers.IO) {
    val key = EchoTrackVersionPolicy.key(track)
    val candidates = if (key == null) emptyList() else database.trackDao().queryTracks(SimpleSQLiteQuery(
        "SELECT * FROM library_tracks WHERE normalizedTitle = ? ORDER BY id LIMIT 128",
        arrayOf(track.title.normalizedForSearch()),
    )).map { it.toEchoTrack() }.filter { EchoTrackVersionPolicy.matches(track, it) }
    val tracks = (listOf(track) + candidates).distinctBy { it.id }
    val store = TrackVersionPreferenceStore(appContext)
    EchoTrackVersions(track, tracks, key?.let { store.preferred(store.snapshot(), it) })
}

suspend fun setPreferredTrackVersion(track: EchoTrack, preferredId: String?) = withContext(Dispatchers.IO) {
    val key = EchoTrackVersionPolicy.key(track)
    if (key == null) {
        require(preferredId == null) { "This song has no safe version association" }
        return@withContext
    }
    require(preferredId == null || trackVersions(track).tracks.any { it.id == preferredId })
    TrackVersionPreferenceStore(appContext).save(key, preferredId)
}

/** Resolve queues in title-indexed batches, with one preferences read and no per-song database lookup. */
suspend fun resolvePreferredTrackVersions(
    tracks: List<EchoTrack>,
    isRemotePlayable: (EchoTrack) -> Boolean,
): List<EchoTrack> = withContext(Dispatchers.IO) {
    if (tracks.isEmpty()) return@withContext tracks
    val store = TrackVersionPreferenceStore(appContext)
    val preferences = store.snapshot()
    val result = tracks.toMutableList()
    for (chunk in tracks.withIndex().chunked(64)) {
        val titles = chunk.filter { EchoTrackVersionPolicy.key(it.value) != null }
            .map { it.value.title.normalizedForSearch() }.distinct()
        if (titles.isEmpty()) continue
        // A pathological repeated title must not cause unbounded memory or work.
        val candidates = database.trackDao().queryTracks(SimpleSQLiteQuery(
            "SELECT * FROM library_tracks WHERE normalizedTitle IN (${titles.joinToString(",") { "?" }}) ORDER BY id LIMIT 2048",
            titles.toTypedArray(),
        )).map { it.toEchoTrack() }
        val byTitle = candidates.groupBy { EchoTrackVersionPolicy.normalized(it.title) }
        val playable = HashSet<String>()
        val checked = HashSet<String>()
        for ((index, track) in chunk) {
            val key = EchoTrackVersionPolicy.key(track) ?: continue
            val related = (listOf(track) + byTitle[EchoTrackVersionPolicy.normalized(track.title)].orEmpty())
                .distinctBy { it.id }.filter { EchoTrackVersionPolicy.matches(track, it) }
            for (candidate in related) {
                if (checked.add(candidate.id) && if (candidate.source.isLocalAudioFile)
                    localUriExists(CueSheetPolicy.playbackUri(candidate.uri)) else isRemotePlayable(candidate)) playable += candidate.id
            }
            result[index] = EchoTrackVersionPolicy.choose(track, related, store.preferred(preferences, key), playable)
        }
    }
    result
}

    private fun localUriExists(uri: String): Boolean {
        if (uri.isBlank()) return false
        return try {
            val parsed = android.net.Uri.parse(uri)
            when (parsed.scheme) {
                "file" -> java.io.File(parsed.path.orEmpty()).exists()
                else -> appContext.contentResolver.openAssetFileDescriptor(parsed, "r")?.use { true } ?: false
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { false }
    }
}

suspend fun EchoLibraryRepository.trackVersions(track: EchoTrack): EchoTrackVersions =
    TrackVersionsRepository(database, appContext).trackVersions(track)

suspend fun EchoLibraryRepository.setPreferredTrackVersion(track: EchoTrack, preferredId: String?) =
    TrackVersionsRepository(database, appContext).setPreferredTrackVersion(track, preferredId)

suspend fun EchoLibraryRepository.resolvePreferredTrackVersions(tracks: List<EchoTrack>, isRemotePlayable: (EchoTrack) -> Boolean): List<EchoTrack> =
    TrackVersionsRepository(database, appContext).resolvePreferredTrackVersions(tracks, isRemotePlayable)
