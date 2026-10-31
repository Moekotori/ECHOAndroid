package app.echo.android.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import app.echo.android.model.library.EchoMusicRelation
import app.echo.android.model.library.EchoMusicRelationKind
import app.echo.android.model.library.EchoMusicRelationSummary
import kotlinx.coroutines.flow.Flow

data class MusicRelationCountRow(val kind: String, val entityCount: Int)
data class MusicRelationTrackCounts(val trackCount: Int, val taggedTrackCount: Int)

@Dao
interface LibraryMusicRelationDao {
    @RawQuery(observedEntities = [LibraryTrackEntity::class, LibraryMusicRelationEntity::class, LibraryHiddenTrackEntity::class])
    fun counts(query: SupportSQLiteQuery): Flow<List<MusicRelationCountRow>>

    @RawQuery(observedEntities = [LibraryTrackEntity::class, LibraryMusicRelationEntity::class, LibraryHiddenTrackEntity::class])
    fun coverage(query: SupportSQLiteQuery): Flow<MusicRelationTrackCounts>

    @RawQuery(observedEntities = [LibraryTrackEntity::class, LibraryMusicRelationEntity::class, LibraryHiddenTrackEntity::class])
    fun entities(query: SupportSQLiteQuery): PagingSource<Int, EchoMusicRelationSummary>

    @RawQuery(observedEntities = [LibraryTrackEntity::class, LibraryMusicRelationEntity::class, LibraryHiddenTrackEntity::class])
    fun tracks(query: SupportSQLiteQuery): PagingSource<Int, LibraryTrackEntity>

    @Query("SELECT * FROM library_tracks WHERE source IN ('mediastore', 'saf') AND id > :afterId ORDER BY id LIMIT 64")
    suspend fun localBatch(afterId: String): List<LibraryTrackEntity>

    @Query("UPDATE library_tracks SET musicRelationsJson = :json WHERE id = :trackId")
    suspend fun setMetadata(trackId: String, json: String?)
}

internal object MusicRelationQueries {
    private fun scope(selection: List<EchoMusicRelation>, args: MutableList<Any>): MutableList<String> {
        val predicates = mutableListOf("t.source IN ('mediastore', 'saf')")
        selection.distinctBy { it.kind }.take(EchoMusicRelationKind.entries.size).forEach { relation ->
            predicates += "EXISTS (SELECT 1 FROM library_music_relations f WHERE f.trackId = t.id AND f.kind = ? AND f.entityKey = ?)"
            args += relation.kind.name; args += relation.key
        }
        return predicates
    }

    fun counts(selection: List<EchoMusicRelation>): SimpleSQLiteQuery {
        val args = mutableListOf<Any>()
        val where = scope(selection, args).joinToString(" AND ")
        return SimpleSQLiteQuery("SELECT r.kind, COUNT(DISTINCT r.entityKey) AS entityCount FROM library_music_relations r JOIN visible_library_tracks t ON t.id = r.trackId WHERE $where GROUP BY r.kind", args.toTypedArray())
    }

    fun coverage(selection: List<EchoMusicRelation>): SimpleSQLiteQuery {
        val args = mutableListOf<Any>()
        val where = scope(selection, args).joinToString(" AND ")
        return SimpleSQLiteQuery("""SELECT COUNT(*) AS trackCount, COALESCE(SUM(CASE WHEN EXISTS (
            SELECT 1 FROM library_music_relations c WHERE c.trackId = t.id
              AND c.kind != 'Contributor' AND (c.kind != 'CreditRole' OR c.name != 'artist')
            ) THEN 1 ELSE 0 END), 0) AS taggedTrackCount FROM visible_library_tracks t WHERE $where""", args.toTypedArray())
    }

    fun build(kind: EchoMusicRelationKind?, selection: List<EchoMusicRelation>, search: String): SimpleSQLiteQuery {
        val args = mutableListOf<Any>()
        val predicates = scope(selection, args)
        if (kind != null) { predicates += "r.kind = ?"; args += kind.name }
        val query = search.trim().take(200)
        if (query.isNotEmpty()) {
            val column = if (kind == null) "t.title || ' ' || t.artist || ' ' || COALESCE(t.album, '')" else "r.name || ' ' || r.context"
            predicates += "($column) LIKE ? ESCAPE '\\'"
            args += "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        }
        val where = predicates.joinToString(" AND ")
        val sql = if (kind == null) """SELECT t.* FROM visible_library_tracks t WHERE $where
            ORDER BY t.album COLLATE NOCASE, COALESCE(t.discNumber, 0), COALESCE(t.trackNumber, 0), t.title COLLATE NOCASE, t.id"""
        else """SELECT r.entityKey AS `key`, MIN(r.name) AS title, MIN(r.context) AS subtitle, COUNT(*) AS trackCount
            FROM library_music_relations r JOIN visible_library_tracks t ON t.id = r.trackId
            WHERE $where GROUP BY r.entityKey ORDER BY title COLLATE NOCASE, r.entityKey"""
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }
}
