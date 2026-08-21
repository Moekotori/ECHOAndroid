package app.echo.android.data

import kotlin.math.exp
import kotlin.math.ln

data class LibraryAlbumListenSeed(
    val albumKey: String,
    val playCount: Int,
    val lastPlayedAtEpochMs: Long,
    val favoritedAtEpochMs: Long,
    val addedAtSeconds: Long,
)

object LibraryHomeRecommendationPolicy {
    const val DefaultLimit = 8
    private const val DayMs = 86_400_000.0

    fun rankAlbumKeys(
        seeds: List<LibraryAlbumListenSeed>,
        nowEpochMs: Long,
        refreshSalt: Int = 0,
        limit: Int = DefaultLimit,
    ): List<String> {
        if (limit <= 0) return emptyList()
        return seeds
            .asSequence()
            .filter { it.albumKey.isNotBlank() }
            .map { seed -> seed.albumKey to score(seed, nowEpochMs, refreshSalt) }
            .sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first })
            .map { it.first }
            .distinct()
            .take(limit)
            .toList()
    }

    private fun score(seed: LibraryAlbumListenSeed, nowEpochMs: Long, refreshSalt: Int): Double {
        val playCount = seed.playCount.coerceAtLeast(0)
        val favoriteBoost = if (seed.favoritedAtEpochMs > 0L) 100.0 else 0.0
        val playBoost = ln(1.0 + playCount) * 12.0
        val daysSincePlay = if (seed.lastPlayedAtEpochMs > 0L) {
            ((nowEpochMs - seed.lastPlayedAtEpochMs).coerceAtLeast(0L) / DayMs)
        } else {
            Double.POSITIVE_INFINITY
        }
        val recencyBoost = if (daysSincePlay.isFinite()) {
            30.0 * exp(-daysSincePlay / 14.0)
        } else {
            0.0
        }
        val neglectedBoost = if (playCount > 0 && daysSincePlay > 21.0) 25.0 else 0.0
        val daysSinceAdded = if (seed.addedAtSeconds > 0L) {
            ((nowEpochMs / 1000L - seed.addedAtSeconds).coerceAtLeast(0L) / 86_400.0)
        } else {
            Double.POSITIVE_INFINITY
        }
        val addedBoost = if (playCount == 0 && daysSinceAdded.isFinite()) {
            8.0 * exp(-daysSinceAdded / 45.0)
        } else {
            0.0
        }
        val noise = ((hash(seed.albumKey, refreshSalt) % 1_000) / 1_000.0) * 25.0
        return favoriteBoost + playBoost + recencyBoost + neglectedBoost + addedBoost + noise
    }

    private fun hash(albumKey: String, salt: Int): Int {
        var result = 17
        albumKey.forEach { ch -> result = 31 * result + ch.code }
        result = 31 * result + salt
        return result and Int.MAX_VALUE
    }
}
