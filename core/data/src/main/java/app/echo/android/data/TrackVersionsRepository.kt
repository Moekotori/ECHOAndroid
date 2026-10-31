package app.echo.android.data

import androidx.sqlite.db.SimpleSQLiteQuery
import app.echo.android.model.library.CueSheetPolicy
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoTrackVersionPolicy
import app.echo.android.model.library.EchoTrackVersions
import app.echo.android.model.library.EchoDuplicateVersionPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock

/** Lightweight preference resolver shared by UI and the background playback catalog. */
class TrackVersionsRepository(private val database: EchoLibraryDatabase, context: android.content.Context) {
    private val appContext = context.applicationContext
suspend fun trackVersions(track: EchoTrack): EchoTrackVersions = withContext(Dispatchers.IO) {
    val currentRow = visibleTrack(track.id)
    require(currentRow != null || !track.source.isLocalAudioFile) { "This track is no longer visible" }
    val current = currentRow?.toEchoTrack() ?: track
    val key = EchoTrackVersionPolicy.key(current)
    val duplicates = LibraryDuplicateRepository(database, appContext)
    val indexed = duplicates.membersForTracks(listOf(current.id))
    val candidates = if (key == null) emptyList() else database.trackDao().queryTracks(SimpleSQLiteQuery(
        "SELECT * FROM visible_library_tracks WHERE normalizedTitle = ? ORDER BY id LIMIT 128",
        arrayOf(current.title.normalizedForSearch()),
    ))
    val rows = (duplicates.entities(indexed) + candidates).distinctBy { it.id }
    val anchor = currentRow
    val byId = rows.associateBy { it.id }
    val tracks = (listOf(current) + rows.map { it.toEchoTrack() }).distinctBy { it.id }
        .filter { it.id == current.id || EchoDuplicateVersionPolicy.matches(current, it) &&
            (!current.source.isLocalAudioFile || !it.source.isLocalAudioFile ||
                !anchor?.albumKey.isNullOrBlank() && byId[it.id]?.albumKey == anchor?.albumKey) }
    val store = TrackVersionPreferenceStore(appContext)
    val group = store.group(current, anchor?.albumKey)
    val quality = duplicates.quality(indexed)
    val ranked = tracks.sortedWith(compareByDescending<EchoTrack> { (quality[it.id] ?: app.echo.android.model.library.EchoTrackAudioQuality()).score(it) }.thenBy { it.id })
    EchoTrackVersions(current, ranked, if (key != null && group != null) store.choice(store.snapshot(), group, key,
        store.previousGroup(current, anchor?.albumKey))?.takeIf { id -> tracks.any { it.id == id } } else null, quality,
        indexed.minByOrNull { it.rank }?.trackId, rows.filter { it.source == "mediastore" || it.source == "saf" }.associate {
            it.id to listOfNotNull(it.relativePath?.trimEnd('/'), it.fileName).filter(String::isNotBlank).joinToString("/")
        }, duplicates.enabled() && indexed.any { it.trackId == current.id })
}

suspend fun setPreferredTrackVersion(track: EchoTrack, preferredId: String?) = withContext(Dispatchers.IO) {
    val row = visibleTrack(track.id)
    require(row != null || !track.source.isLocalAudioFile) { "This track is no longer visible" }
    val current = row?.toEchoTrack() ?: track
    val key = EchoTrackVersionPolicy.key(current)
    if (key == null) {
        require(preferredId == null) { "This song has no safe version association" }
        return@withContext
    }
    require(preferredId == null || trackVersions(current).tracks.any { it.id == preferredId })
    val store = TrackVersionPreferenceStore(appContext)
    val group = store.group(current, row?.albumKey) ?: key
    LibraryDuplicateRepository.preferenceLock.withLock {
        store.save(group, preferredId, keepAutomatic = group != key)
        LibraryDuplicateRepository(database, appContext).refreshPreference(track.id, preferredId)
    }
}

    private suspend fun visibleTrack(id: String): LibraryTrackEntity? = database.trackDao().queryTracks(
        SimpleSQLiteQuery("SELECT * FROM visible_library_tracks WHERE id=? LIMIT 1", arrayOf(id))).firstOrNull()

/** Resolve queues in title-indexed batches, with one preferences read and no per-song database lookup. */
suspend fun resolvePreferredTrackVersions(
    tracks: List<EchoTrack>,
    isRemotePlayable: (EchoTrack) -> Boolean,
): List<EchoTrack> = withContext(Dispatchers.IO) {
    if (tracks.isEmpty()) return@withContext tracks
    val store = TrackVersionPreferenceStore(appContext)
    val preferences = store.snapshot()
    val duplicates = LibraryDuplicateRepository(database, appContext)
    val preferQuality = duplicates.enabled()
    val result = tracks.toMutableList()
    for (chunk in tracks.withIndex().chunked(64)) {
        val titles = chunk.filter { EchoTrackVersionPolicy.key(it.value) != null }
            .map { it.value.title.normalizedForSearch() }.distinct()
        if (titles.isEmpty()) continue
        // A pathological repeated title must not cause unbounded memory or work.
        val indexed = if (preferQuality) duplicates.membersForTracks(chunk.map { it.value.id }) else emptyList()
        val groups = indexed.groupBy { it.groupKey }
        val membership = indexed.associateBy { it.trackId }
        val requested = chunk.map { it.value.id }.distinct()
        val requestedRows = database.trackDao().queryTracks(SimpleSQLiteQuery(
            "SELECT * FROM visible_library_tracks WHERE id IN (${requested.joinToString(",") { "?" }})", requested.toTypedArray()))
        val candidateRows = (database.trackDao().queryTracks(SimpleSQLiteQuery(
            "SELECT * FROM visible_library_tracks WHERE normalizedTitle IN (${titles.joinToString(",") { "?" }}) ORDER BY id LIMIT 2048",
            titles.toTypedArray(),
        )) + requestedRows + duplicates.entities(indexed)).distinctBy { it.id }
        val byId = candidateRows.associateBy { it.id }
        val candidates = candidateRows.map { it.toEchoTrack() }
        val byTitle = candidates.groupBy { EchoTrackVersionPolicy.normalized(it.title) }
        val playable = HashSet<String>()
        val checked = HashSet<String>()
        val unavailable = HashSet<String>()
        for ((index, track) in chunk) {
            val key = EchoTrackVersionPolicy.key(track) ?: continue
            val album = byId[track.id]?.albumKey
            val related = (listOf(track) + byTitle[EchoTrackVersionPolicy.normalized(track.title)].orEmpty())
                .distinctBy { it.id }.filter { EchoDuplicateVersionPolicy.matches(track, it) &&
                    (it.id == track.id || !track.source.isLocalAudioFile || !it.source.isLocalAudioFile ||
                        !album.isNullOrBlank() && byId[it.id]?.albumKey == album) }
            for (candidate in related) {
                if (checked.add(candidate.id)) {
                    val ready = if (candidate.source.isLocalAudioFile) localUriExists(CueSheetPolicy.playbackUri(candidate.uri)) else isRemotePlayable(candidate)
                    if (ready) playable += candidate.id else if (candidate.source.isLocalAudioFile) unavailable += candidate.id
                }
            }
            val group = membership[track.id]?.let { groups[it.groupKey] }
            val scoped = store.group(track, album) ?: key
            result[index] = EchoTrackVersionPolicy.choose(track, related, store.choice(preferences, scoped, key, store.previousGroup(track, album)), playable,
                group?.let(duplicates::quality).orEmpty(), preferQuality && group != null)
        }
        duplicates.invalidateUnavailable(indexed.filter { it.trackId in unavailable })
    }
    result
}

    private fun localUriExists(uri: String): Boolean {
        if (uri.isBlank()) return false
        return try {
            val parsed = android.net.Uri.parse(uri)
            when (parsed.scheme) {
                "file" -> java.io.File(parsed.path.orEmpty()).takeIf { it.isFile }?.inputStream()?.use { true } ?: false
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
