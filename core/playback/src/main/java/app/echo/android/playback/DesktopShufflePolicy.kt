package app.echo.android.playback

import java.util.Locale
import kotlin.random.Random

internal data class ShuffleTrackTags(val artist: String?, val album: String?, val albumArtist: String?)

/** A draw stays locked until it is played, removed, or invalidated by an explicit mode change. */
internal class DesktopShufflePolicy(private val random: Random = Random.Default) {
    private val history = ArrayDeque<String>()
    private var currentKey: String? = null
    private var currentId: String? = null
    private var plannedKey: String? = null

    fun observe(key: String, id: String, manual: Boolean) {
        if (key == currentKey) return
        currentId?.let(history::addLast)
        if (history.size > 100) history.removeFirst()
        currentKey = key
        currentId = id
        if (!manual || key == plannedKey) plannedKey = null
    }

    fun recent(count: Int): List<String> = history.takeLast(count)

    fun preferNext(key: String?) { plannedKey = key }

    fun next(
        candidates: List<Int>, keys: List<String>, mediaIds: List<String>, current: Int,
        count: Int, tagsAt: (Int) -> ShuffleTrackTags,
    ): Int? {
        val eligible = candidates.filter { mediaIds[it] != mediaIds[current] }
        val locked = eligible.firstOrNull { keys[it] == plannedKey }
        if (locked != null) return locked
        val lastPlayed = recent(count).withIndex().associate { (index, id) -> id to index }
        val oldest = eligible.minOfOrNull { lastPlayed[mediaIds[it]] ?: -1 } ?: return null
        val available = eligible.filter { (lastPlayed[mediaIds[it]] ?: -1) == oldest }
        val preferred = if (count == 25 && available.size > 1) {
            val previous = tagsAt(current)
            val scores = available.groupBy { diversityScore(previous, tagsAt(it)) }
            scores.getValue(scores.keys.min())
        } else available
        return preferred.random(random).also { plannedKey = keys[it] }
    }

    private fun diversityScore(previous: ShuffleTrackTags, next: ShuffleTrackTags): Int {
        fun String?.key() = orEmpty().trim().lowercase(Locale.ROOT)
        val artist = previous.artist.key()
        val nextArtist = next.artist.key()
        if (artist.isNotEmpty() && artist == nextArtist) return 2
        val album = previous.album.key()
        val albumArtist = previous.albumArtist.key().ifEmpty { artist }
        return if (album.isNotEmpty() && album == next.album.key() &&
            albumArtist == next.albumArtist.key().ifEmpty { nextArtist }) 1 else 0
    }

    companion object {
        fun order(existing: List<Int>, current: Int, manual: Set<Int>, next: Int?, keepUpcoming: Boolean = false): List<Int> {
            val anchor = existing.indexOf(current)
            val rest = existing.filterNot { it == current || it in manual || it == next }
            val prefix = if (keepUpcoming && next != null) {
                existing.take(anchor.coerceAtLeast(0)).count { it !in manual && it != next }
            } else rest.size
            return rest.take(prefix) + current + manual.filterNot { it == current } +
                listOfNotNull(next) + rest.drop(prefix)
        }
    }
}
