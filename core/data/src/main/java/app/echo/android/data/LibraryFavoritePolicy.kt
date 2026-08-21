package app.echo.android.data

import org.json.JSONArray
import org.json.JSONObject

data class LibraryFavoriteSnapshot(
    val likedTrackIds: Set<String> = emptySet(),
)

object LibraryFavoritePolicy {
    fun toggle(snapshot: LibraryFavoriteSnapshot, trackId: String): LibraryFavoriteSnapshot {
        val id = normalizeTrackId(trackId) ?: return snapshot
        val liked = snapshot.likedTrackIds.toMutableSet()
        if (!liked.add(id)) {
            liked.remove(id)
        }
        return snapshot.copy(likedTrackIds = liked)
    }

    fun isLiked(snapshot: LibraryFavoriteSnapshot, trackId: String): Boolean {
        val id = normalizeTrackId(trackId) ?: return false
        return id in snapshot.likedTrackIds
    }

    fun serialize(snapshot: LibraryFavoriteSnapshot): String {
        val json = JSONObject()
        val ids = JSONArray()
        snapshot.likedTrackIds.sorted().forEach(ids::put)
        json.put(LikedTrackIdsKey, ids)
        return json.toString()
    }

    fun parse(raw: String?): LibraryFavoriteSnapshot {
        if (raw.isNullOrBlank()) return LibraryFavoriteSnapshot()
        val ids = runCatching {
            val root = JSONObject(raw)
            val array = root.optJSONArray(LikedTrackIdsKey) ?: JSONArray()
            buildSet {
                for (index in 0 until array.length()) {
                    normalizeTrackId(array.optString(index))?.let(::add)
                }
            }
        }.getOrElse { emptySet() }
        return LibraryFavoriteSnapshot(likedTrackIds = ids)
    }

    fun favoriteAlbumKeys(
        likedTrackIds: Collection<String>,
        albumKeyByTrackId: Map<String, String>,
        favoritedAtByTrackId: Map<String, Long> = emptyMap(),
        limit: Int = FavoriteAlbumLimit,
    ): List<String> {
        if (limit <= 0) return emptyList()
        val ranked = linkedMapOf<String, Long>()
        likedTrackIds.forEach { trackId ->
            val albumKey = albumKeyByTrackId[trackId]?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            val favoritedAt = favoritedAtByTrackId[trackId] ?: 0L
            val current = ranked[albumKey]
            if (current == null || favoritedAt > current) {
                ranked[albumKey] = favoritedAt
            }
        }
        return ranked.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(limit)
    }

    const val FavoriteAlbumLimit = 4

    private fun normalizeTrackId(trackId: String?): String? =
        trackId?.trim()?.takeIf { it.isNotEmpty() }

    private const val LikedTrackIdsKey = "likedTrackIds"
}
