package app.echo.android.data

import androidx.room.Dao
import androidx.room.Query

@Dao
interface LibraryLyricsIndexDao {
    @Query("""
        SELECT (SELECT COUNT(*) FROM library_tracks WHERE source IN ('mediastore', 'saf')) AS totalCount,
            COALESCE(SUM(CASE WHEN i.status = 1 THEN 1 ELSE 0 END), 0) AS foundCount,
            COALESCE(SUM(CASE WHEN i.status = 0 THEN 1 ELSE 0 END), 0) AS missingCount,
            COALESCE(SUM(CASE WHEN i.status = -1 THEN 1 ELSE 0 END), 0) AS unverifiedCount,
            COALESCE(MAX(i.checkedAtEpochMs), 0) AS checkedAtEpochMs
        FROM library_lyrics_index i JOIN library_tracks t ON t.id = i.trackId
        WHERE t.source IN ('mediastore', 'saf') AND i.trackFingerprint IS t.fingerprint
            AND i.contentUri = t.contentUri AND i.sizeBytes = t.sizeBytes AND i.dateModifiedSeconds = t.dateModifiedSeconds
    """)
    suspend fun summary(): LibraryLyricsIndexSummary

    // INSERT ... SELECT avoids racing a removed track or an updated scan fingerprint.
    // REPLACE is supported by Android 8 SQLite; newer UPSERT syntax is not required.
    @Query("""
        INSERT OR REPLACE INTO library_lyrics_index
            (trackId, trackFingerprint, contentUri, sizeBytes, dateModifiedSeconds, status, checkedAtEpochMs)
        SELECT id, fingerprint, contentUri, sizeBytes, dateModifiedSeconds, :status, :checkedAt
        FROM library_tracks t WHERE id = :id AND source IN ('mediastore', 'saf')
            AND fingerprint IS :fingerprint AND contentUri = :uri
            AND sizeBytes = :size AND dateModifiedSeconds = :modified
            AND NOT EXISTS(SELECT 1 FROM library_lyrics_index i WHERE i.trackId = t.id AND i.checkedAtEpochMs >= :checkedAt)
    """)
    suspend fun recordInspected(id: String, fingerprint: String?, uri: String, size: Long, modified: Long,
        status: Int, checkedAt: Long)

    @Query("""
        INSERT OR REPLACE INTO library_lyrics_index
            (trackId, trackFingerprint, contentUri, sizeBytes, dateModifiedSeconds, status, checkedAtEpochMs)
        SELECT id, fingerprint, contentUri, sizeBytes, dateModifiedSeconds, :status, :checkedAt
        FROM library_tracks WHERE id = :id AND source IN ('mediastore', 'saf')
    """)
    suspend fun recordSelection(id: String, status: Int, checkedAt: Long)
}

data class LibraryLyricsIndexSummary(val totalCount: Int, val foundCount: Int, val missingCount: Int,
    val unverifiedCount: Int, val checkedAtEpochMs: Long)
