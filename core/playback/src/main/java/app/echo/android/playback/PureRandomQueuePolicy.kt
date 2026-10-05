package app.echo.android.playback

import kotlin.random.Random
import app.echo.android.model.playback.EchoShuffleOptions

/** One stable next draw at a time; memory is bounded by the active playlist. */
internal class PureRandomQueuePolicy(private val random: Random = Random.Default) {
    private var baseKeys = emptySet<String>()
    private var currentKey: String? = null
    private var nextKey: String? = null
    private var remaining = 0
    private var looping = false
    private var currentMediaId: String? = null
    private val recent = ArrayDeque<String>()

    fun reset() {
        baseKeys = emptySet()
        currentKey = null
        nextKey = null
        remaining = 0
        currentMediaId = null
        recent.clear()
    }

    fun preferNext(key: String?) {
        nextKey = key
    }

    fun order(
        existing: List<Int>,
        keys: List<String>,
        mediaIds: List<String>,
        current: Int,
        manual: Set<Int>,
        repeatAll: Boolean,
        recentTrackCount: Int = 0,
    ): List<Int> {
        if (current !in keys.indices) return existing
        val base = existing.filterNot { it in manual }
        val membership = base.mapTo(HashSet()) { keys[it] }
        val key = keys[current]
        val pending = manual.sorted().filter { it != current }
        val newQueue = membership != baseKeys
        if (newQueue) recent.clear()
        if (!newQueue && currentKey != key) {
            currentMediaId?.let(recent::addLast)
            while (recent.size > EchoShuffleOptions.MaxRecentTracks) recent.removeFirst()
        }
        if (newQueue || looping != repeatAll) {
            baseKeys = membership
            remaining = (base.size - if (current in manual) 0 else 1).coerceAtLeast(0)
            nextKey = null
        } else if (currentKey != key && current !in manual) {
            if (key == nextKey) remaining = (remaining - 1).coerceAtLeast(0)
            else remaining = (base.size - 1).coerceAtLeast(0) // Explicit seek starts a new round.
            nextKey = null
        }
        currentKey = key
        currentMediaId = mediaIds[current]
        looping = repeatAll

        // Repeat OFF still ends after one playlist-sized round, even if draws revisit songs.
        if (!repeatAll && remaining == 0) {
            nextKey = null
            return existing.filterNot { it == current || it in manual } + current + pending
        }
        val eligible = base.filter { it != current && mediaIds[it] != mediaIds[current] }
        var window = recentTrackCount.coerceIn(0, EchoShuffleOptions.MaxRecentTracks).coerceAtMost(recent.size)
        var candidates: List<Int>
        do {
            val excluded = recent.takeLast(window).toHashSet()
            candidates = eligible.filter { mediaIds[it] !in excluded }
            window-- // Relax the oldest exclusions first when the playlist is small.
        } while (candidates.isEmpty() && window >= 0)
        val next = candidates.firstOrNull { keys[it] == nextKey }
            ?: candidates.takeIf { it.isNotEmpty() }?.let { it[random.nextInt(it.size)] }
            ?: return existing.filterNot { it == current || it in manual } + current + pending
        nextKey = keys[next]
        val rest = existing.filterNot { it == next || it in manual && it != current }
        val at = rest.indexOf(current) + 1
        return rest.take(at) + pending + next + rest.drop(at)
    }
}
