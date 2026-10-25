package app.echo.android.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.echo.android.model.podcast.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Independent bounded podcast database. Handles exist only while an operation owns the mutex. */
class PodcastStore(context: Context) {
    private val helper = object : SQLiteOpenHelper(context.applicationContext, "podcasts.sqlite", null, 1) {
        override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE feeds(id TEXT PRIMARY KEY, title TEXT NOT NULL, data TEXT NOT NULL, etag TEXT, modified TEXT)")
            db.execSQL("CREATE TABLE episodes(id TEXT PRIMARY KEY, feed_id TEXT NOT NULL REFERENCES feeds(id) ON DELETE CASCADE, title TEXT NOT NULL, description TEXT NOT NULL, published INTEGER NOT NULL, source_order INTEGER NOT NULL, data TEXT NOT NULL, position INTEGER NOT NULL DEFAULT 0, played INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE INDEX episodes_feed_order ON episodes(feed_id, published DESC, source_order ASC, id)")
        }
        override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) = Unit
    }
    private val mutex = Mutex()
    private suspend fun <T> access(block: (SQLiteDatabase) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock { try { block(helper.writableDatabase) } finally { helper.close() } }
    }

    suspend fun feeds(): List<EchoPodcastFeed> = access { db ->
        db.rawQuery("SELECT data FROM feeds ORDER BY title COLLATE NOCASE LIMIT ${PodcastLimits.Feeds}", null)
            .use { cursor -> buildList { while (cursor.moveToNext()) add(readFeed(cursor.getString(0))) } }
    }

    suspend fun validators(id: String): Pair<String?, String?> = access { db ->
        db.rawQuery("SELECT etag, modified FROM feeds WHERE id=?", arrayOf(id)).use {
            if (it.moveToFirst()) it.getString(0) to it.getString(1) else null to null
        }
    }

    suspend fun commit(parsed: ParsedPodcast, etag: String?, modified: String?, subscribing: Boolean) = access { db ->
        db.beginTransaction()
        try {
            val feed = parsed.feed
            val exists = db.rawQuery("SELECT 1 FROM feeds WHERE id=?", arrayOf(feed.id)).use { it.moveToFirst() }
            check(exists || subscribing) { "Subscription removed" }
            if (!exists) {
                val count = db.rawQuery("SELECT count(*) FROM feeds", null).use { it.moveToFirst(); it.getInt(0) }
                check(count < PodcastLimits.Feeds) { "Subscription limit" }
            }
            db.execSQL("UPDATE feeds SET title=?,data=?,etag=?,modified=? WHERE id=? AND (data!=? OR etag IS NOT ? OR modified IS NOT ?)",
                arrayOf(feed.title, encode(feed), etag, modified, feed.id, encode(feed), etag, modified))
            db.execSQL("INSERT OR IGNORE INTO feeds(id,title,data,etag,modified) VALUES(?,?,?,?,?)",
                arrayOf(feed.id, feed.title, encode(feed), etag, modified))
            parsed.episodes.take(PodcastLimits.Episodes).forEach { episode ->
                val data = encode(episode)
                db.execSQL("UPDATE episodes SET title=?,description=?,published=?,source_order=?,data=? WHERE id=? AND data!=?",
                    arrayOf<Any?>(episode.title, episode.description, episode.publishedMs, episode.sourceOrder, data, episode.id, data))
                db.execSQL("INSERT OR IGNORE INTO episodes(id,feed_id,title,description,published,source_order,data) VALUES(?,?,?,?,?,?,?)",
                    arrayOf<Any?>(episode.id, feed.id, episode.title, episode.description, episode.publishedMs, episode.sourceOrder, encode(episode)))
            }
            // Keep older episodes up to the cap; equal-date ties favor the current publisher window.
            val ids = parsed.episodes.take(PodcastLimits.Episodes).map { it.id }
            val currentWindow = if (ids.isEmpty()) "0" else "CASE WHEN id IN (${ids.joinToString(",") { "?" }}) THEN 0 ELSE 1 END"
            db.execSQL("DELETE FROM episodes WHERE feed_id=? AND id NOT IN (SELECT id FROM episodes WHERE feed_id=? ORDER BY published DESC,$currentWindow,source_order ASC,id LIMIT ${PodcastLimits.Episodes})",
                arrayOf(feed.id, feed.id, *ids.toTypedArray()))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    suspend fun unfollow(id: String) = access { it.delete("feeds", "id=?", arrayOf(id)); Unit }

    suspend fun page(feedId: String, query: String = "", filter: PodcastListenFilter = PodcastListenFilter.All, page: Int = 0): PodcastEpisodePage = access { db ->
        val predicate = when (filter) {
            PodcastListenFilter.All -> ""
            PodcastListenFilter.Unfinished -> " AND played=0"
            PodcastListenFilter.InProgress -> " AND played=0 AND position>0"
            PodcastListenFilter.Played -> " AND played=1"
        }
        val term = "%" + query.take(200).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        val where = "feed_id=?$predicate AND (title LIKE ? ESCAPE '\\' OR description LIKE ? ESCAPE '\\')"
        val args = arrayOf(feedId, term, term)
        val total = db.rawQuery("SELECT count(*) FROM episodes WHERE $where", args).use { it.moveToFirst(); it.getInt(0) }
        val clamped = page.coerceIn(0, ((total - 1) / PodcastLimits.PageSize).coerceAtLeast(0))
        val episodes = db.rawQuery("SELECT data,position,played FROM episodes WHERE $where ORDER BY published DESC,source_order ASC,id LIMIT ${PodcastLimits.PageSize} OFFSET ${clamped * PodcastLimits.PageSize}", args).use { cursor ->
            buildList { while (cursor.moveToNext()) add(readEpisode(cursor.getString(0)).copy(positionMs = cursor.getLong(1), played = cursor.getInt(2) == 1)) }
        }
        PodcastEpisodePage(episodes, clamped, total)
    }

    suspend fun episode(id: String): EchoPodcastEpisode? = access { db ->
        db.rawQuery("SELECT data,position,played FROM episodes WHERE id=?", arrayOf(id)).use {
            if (it.moveToFirst()) readEpisode(it.getString(0)).copy(positionMs = it.getLong(1), played = it.getInt(2) == 1) else null
        }
    }

    suspend fun continueEpisode(feedId: String): EchoPodcastEpisode? = access { db ->
        db.rawQuery("SELECT data,position,played FROM episodes WHERE feed_id=? ORDER BY CASE WHEN played=0 AND position>0 THEN 0 WHEN played=0 THEN 1 ELSE 2 END,published DESC,source_order ASC,id LIMIT 1", arrayOf(feedId)).use {
            if (it.moveToFirst()) readEpisode(it.getString(0)).copy(positionMs = it.getLong(1), played = it.getInt(2) == 1) else null
        }
    }

    suspend fun markPlayed(id: String, played: Boolean) = access { db ->
        db.execSQL("UPDATE episodes SET played=? WHERE id=? AND played!=?", arrayOf<Any?>(if (played) 1 else 0, id, if (played) 1 else 0))
    }

    suspend fun progress(id: String, positionMs: Long, ended: Boolean, replay: Boolean) = access { db ->
        val position = positionMs.coerceAtLeast(0)
        db.execSQL("UPDATE episodes SET position=?,played=CASE WHEN ?=1 THEN 1 WHEN ?=1 OR ?<position THEN 0 ELSE played END WHERE id=? AND (position!=? OR (?=1 AND played=0) OR (?=1 AND played=1))",
            arrayOf<Any?>(position, if (ended) 1 else 0, if (replay) 1 else 0, position, id, position, if (ended) 1 else 0, if (replay) 1 else 0))
    }

    private fun encode(feed: EchoPodcastFeed) = JSONObject().put("id", feed.id).put("url", feed.url).put("title", feed.title)
        .put("author", feed.author).put("description", feed.description).put("artwork", feed.artworkUrl).put("website", feed.website).toString()
    private fun readFeed(value: String) = JSONObject(value).let {
        EchoPodcastFeed(it.getString("id"), it.getString("url"), it.getString("title"), it.optString("author"), it.optString("description"), it.optional("artwork"), it.optional("website"))
    }
    private fun encode(episode: EchoPodcastEpisode) = JSONObject().put("id", episode.id).put("feed", episode.feedId)
        .put("title", episode.title).put("audio", episode.audioUrl).put("description", episode.description)
        .put("published", episode.publishedMs).put("duration", episode.durationMs).put("artwork", episode.artworkUrl)
        .put("website", episode.website).put("order", episode.sourceOrder).toString()
    private fun readEpisode(value: String) = JSONObject(value).let {
        EchoPodcastEpisode(it.getString("id"), it.getString("feed"), it.getString("title"), it.getString("audio"),
            it.optString("description"), it.optLong("published"), it.optLong("duration"), it.optional("artwork"), it.optional("website"), it.optInt("order"))
    }
    private fun JSONObject.optional(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}
