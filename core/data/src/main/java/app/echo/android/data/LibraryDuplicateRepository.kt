package app.echo.android.data

import android.content.Context
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import app.echo.android.model.library.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.UUID

class LibraryDuplicateScanInProgressException : IllegalStateException("Library scan started during duplicate analysis")

/** Derived local version index. A completed generation is published atomically; cancellation preserves the old one. */
class LibraryDuplicateRepository(private val database: EchoLibraryDatabase, context: Context) {
    private val context = context.applicationContext
    private val dao = database.duplicateDao()
    fun observeSummary(): Flow<LibraryDuplicateSummary> = dao.observeSummary().distinctUntilChanged()
    suspend fun enabled(): Boolean = withContext(Dispatchers.IO) { dao.control()?.enabled == true }

    suspend fun setEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        preferenceLock.withLock {
            val control = dao.control() ?: LibraryDuplicateControl()
            require(!enabled || control.updatedAt > 0)
            dao.saveControl(control.copy(enabled = enabled))
        }
    }

    suspend fun analyze(shouldContinue: () -> Boolean = { true }, onProgress: (Int) -> Unit = {}) = withContext(Dispatchers.IO) {
        analysisLock.withLock {
            val active = dao.control()?.generation.orEmpty()
            dao.clearOthers(active)
            val generation = UUID.randomUUID().toString()
            var processed = 0
            var skipped = 0
            var last = LibraryDuplicateBucket("", "", "", "", -1, -1)
            val reader = LibraryDuplicateQualityReader(context)
            val preferences = TrackVersionPreferenceStore(context)
            fun checkpoint() { if (!shouldContinue()) throw LibraryDuplicateScanInProgressException() }
            try {
                while (true) {
                    currentCoroutineContext().ensureActive()
                    checkpoint()
                    val buckets = dao.buckets(last.title, last.artist, last.album, last.albumIdentity, last.start, last.end)
                    if (buckets.isEmpty()) break
                    for (bucket in buckets) {
                        currentCoroutineContext().ensureActive()
                        checkpoint()
                        val rows = dao.candidates(SimpleSQLiteQuery("""SELECT t.*, ${LibraryDuplicateSql.snapshot("t")} AS duplicateSnapshot
                            FROM visible_library_tracks t WHERE t.source IN ('mediastore','saf')
                            AND t.normalizedTitle=? AND t.normalizedArtist=? AND t.normalizedAlbum=? AND t.albumKey=?
                            AND t.clipStartMs=? AND t.clipEndMs=? ORDER BY t.durationMs,t.id LIMIT 257""",
                            arrayOf<Any>(bucket.title, bucket.artist, bucket.album, bucket.albumIdentity, bucket.start, bucket.end)))
                        if (rows.size > 256) { skipped++; continue }
                        val tracks = rows.map { it.track.toEchoTrack() }
                        val byId = rows.associateBy { it.track.id }
                        for (cluster in EchoDuplicateVersionPolicy.clusters(tracks)) {
                            val quality = mutableMapOf<String, EchoTrackAudioQuality>()
                            for (track in cluster) {
                                currentCoroutineContext().ensureActive()
                                checkpoint()
                                reader.read(track)?.let { quality[track.id] = it }
                                processed++; onProgress(processed)
                            }
                            val ranked = cluster.filter { it.id in quality }.sortedWith(
                                compareByDescending<EchoTrack> { quality.getValue(it.id).score(it) }.thenBy { it.id })
                            if (ranked.size < 2) continue
                            val versionKey = preferences.token(requireNotNull(preferences.group(ranked.first(), bucket.albumIdentity)))
                            val groupKey = digest(versionKey + ":" + ranked.minOf { it.durationMs } + ":" + ranked.minOf { it.id })
                            dao.insertMembers(ranked.mapIndexed { index, track ->
                                val value = quality.getValue(track.id)
                                LibraryDuplicateMember(generation, track.id, groupKey, versionKey, ranked.first().id,
                                    byId.getValue(track.id).duplicateSnapshot, index, value.score(track), value.sampleRateHz,
                                    value.bitDepth, value.bitrate, value.codecMime)
                            })
                        }
                    }
                    last = buckets.last()
                    yield()
                }
                preferenceLock.withLock {
                    val choices = preferences.snapshot()
                    currentCoroutineContext().ensureActive()
                    checkpoint()
                    database.withTransaction {
                        prune(generation)
                        // Scoped choices win. Legacy choices migrate only inside their actual matching group.
                        for ((key, id) in choices) {
                            val member = dao.member(generation, id) ?: continue
                            val scoped = key == member.versionKey
                            val row = if (!scoped && !choices.containsKey(member.versionKey)) database.trackDao().getTrackById(id) else null
                            val legacy = row?.let { listOfNotNull(EchoTrackVersionPolicy.key(it.toEchoTrack()),
                                preferences.previousGroup(it.toEchoTrack(), it.albumKey)).any { old -> preferences.token(old) == key } } == true
                            if (scoped || legacy) dao.setRepresentative(generation, member.groupKey, id)
                        }
                        val counts = dao.counts(generation)
                        checkpoint()
                        dao.saveControl(LibraryDuplicateControl(generation = generation, enabled = true,
                            updatedAt = System.currentTimeMillis(), processed = processed, skipped = skipped,
                            groupCount = counts.groups, hiddenCount = counts.hidden))
                        dao.clearOthers(generation)
                    }
                }
            } catch (failure: Throwable) {
                withContext(NonCancellable) {
                    try { if (dao.control()?.generation != generation) dao.discard(generation) }
                    catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
                }
                throw failure
            }
        }
    }

    private fun prune(generation: String) {
        val sql = database.openHelper.writableDatabase
        sql.execSQL("""DELETE FROM library_duplicate_members WHERE generation=? AND NOT EXISTS (
            SELECT 1 FROM visible_library_tracks t WHERE t.id=library_duplicate_members.trackId
            AND library_duplicate_members.snapshot=${LibraryDuplicateSql.snapshot("t")})""", arrayOf(generation))
        sql.execSQL("""DELETE FROM library_duplicate_members WHERE generation=? AND groupKey IN (
            SELECT groupKey FROM library_duplicate_members WHERE generation=? GROUP BY groupKey HAVING COUNT(*)<2)""", arrayOf(generation, generation))
        sql.execSQL("""UPDATE library_duplicate_members SET representativeId=(SELECT best.trackId
            FROM library_duplicate_members best WHERE best.generation=library_duplicate_members.generation
            AND best.groupKey=library_duplicate_members.groupKey ORDER BY best.rank,best.trackId LIMIT 1)
            WHERE generation=? AND NOT EXISTS (SELECT 1 FROM library_duplicate_members retained
                WHERE retained.generation=library_duplicate_members.generation
                AND retained.trackId=library_duplicate_members.representativeId
                AND retained.groupKey=library_duplicate_members.groupKey)""", arrayOf(generation))
    }

    /** Caller holds preferenceLock together with the DataStore preference write. */
    internal suspend fun refreshPreference(trackId: String, preferredId: String?) {
        val control = dao.control() ?: return
        val generation = control.generation
        val anchor = dao.member(generation, trackId) ?: return
        val members = dao.group(generation, anchor.groupKey)
        val selected = members.firstOrNull { it.trackId == preferredId } ?: members.firstOrNull() ?: return
        database.withTransaction {
            dao.setRepresentative(generation, anchor.groupKey, selected.trackId)
            // Only published changes invalidate pages; staging writes never churn the visible list.
            dao.saveControl(control)
        }
    }

    internal suspend fun membersForTracks(ids: List<String>): List<LibraryDuplicateMember> {
        if (ids.isEmpty()) return emptyList()
        require(ids.size <= 256)
        val seed = """SELECT seed.groupKey ${LibraryDuplicateSql.activeMembers("seed")}
            AND seed.trackId IN (${ids.joinToString(",") { "?" }})"""
        return dao.members(SimpleSQLiteQuery("SELECT m.* ${LibraryDuplicateSql.activeMembers()} AND m.groupKey IN ($seed) ORDER BY m.rank,m.trackId LIMIT 2048", ids.toTypedArray()))
    }

    internal suspend fun entities(rows: List<LibraryDuplicateMember>): List<LibraryTrackEntity> = rows.map { it.trackId }.distinct().chunked(256).flatMap { ids ->
        database.trackDao().queryTracks(SimpleSQLiteQuery("SELECT * FROM visible_library_tracks WHERE id IN (${ids.joinToString(",") { "?" }})", ids.toTypedArray()))
    }
    internal suspend fun tracks(rows: List<LibraryDuplicateMember>): List<EchoTrack> = entities(rows).map { it.toEchoTrack() }

    internal fun quality(rows: List<LibraryDuplicateMember>): Map<String, EchoTrackAudioQuality> = rows.associate {
        it.trackId to EchoTrackAudioQuality(it.sampleRateHz, it.bitDepth, it.bitrate, it.codecMime)
    }

    internal suspend fun invalidateUnavailable(rows: List<LibraryDuplicateMember>) {
        if (rows.isEmpty()) return
        preferenceLock.withLock {
            val control = dao.control() ?: return@withLock
            val missing = rows.filter { it.generation == control.generation }
            if (missing.isEmpty()) return@withLock
            database.withTransaction {
                missing.forEach { dao.removeMember(control.generation, it.trackId) }
                dao.saveControl(control)
            }
        }
    }

    fun displayTrackId(id: String?): Flow<String?> = if (id == null) flowOf(null) else dao.representative(SimpleSQLiteQuery("""
        SELECT m.representativeId ${LibraryDuplicateSql.activeMembers()}
        AND cfg.enabled=1 AND m.trackId=?
        AND EXISTS (SELECT 1 FROM library_duplicate_members r JOIN library_tracks t ON t.id=r.trackId
            WHERE r.generation=m.generation AND r.trackId=m.representativeId AND r.snapshot=${LibraryDuplicateSql.snapshot("t")}
            AND t.id NOT IN (SELECT trackId FROM library_hidden_tracks)) LIMIT 1
    """.trimIndent(), arrayOf(id))).map { it ?: id }.distinctUntilChanged().flowOn(Dispatchers.IO)

    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    companion object {
        private val analysisLock = Mutex()
        internal val preferenceLock = Mutex()
    }
}
