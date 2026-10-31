package app.echo.android.playback

import kotlin.random.Random
import java.util.PriorityQueue

/** Shuffle once per round, then interleave the largest remaining artist buckets. */
internal object PseudoRandomQueuePolicy {
    fun order(
        indices: List<Int>,
        current: Int,
        artists: List<String?>,
        spreadArtists: Boolean,
        random: Random = Random.Default,
    ): List<Int> {
        val shuffled = indices.filter { it != current }.shuffled(random)
        if (!spreadArtists) return listOf(current) + shuffled
        return listOf(current) + spread(shuffled, current, artists)
    }

    fun spread(indices: List<Int>, current: Int, artists: List<String?>): List<Int> {
        // Missing artist tags get independent buckets, rather than one large "unknown" artist.
        fun artist(index: Int): String = artists.getOrNull(index)?.trim()?.takeIf { it.isNotEmpty() }
            ?.lowercase(java.util.Locale.ROOT) ?: "\u0000$index"
        var previous = artist(current)
        val needsSpreading = indices.any {
            val next = artist(it)
            val adjacent = previous == next
            previous = next
            adjacent
        }
        if (!needsSpreading) return indices
        val buckets = LinkedHashMap<String, ArrayDeque<Int>>()
        indices.forEach { buckets.getOrPut(artist(it)) { ArrayDeque() }.addLast(it) }
        // Schedule larger buckets first so smaller ones can separate their final songs.
        // Mutate a bucket only while it is outside the heap; ties retain shuffled order.
        val entries = buckets.entries.toList()
        val groups = PriorityQueue<Int>(compareByDescending<Int> { entries[it].value.size }.thenBy { it })
        groups.addAll(entries.indices)
        val result = ArrayList<Int>(indices.size)
        previous = artist(current)
        while (groups.isNotEmpty()) {
            var group = groups.remove()
            if (entries[group].key == previous && groups.isNotEmpty()) {
                val alternative = groups.remove()
                groups.add(group)
                group = alternative
            }
            val entry = entries[group]
            result += entry.value.removeFirst()
            previous = entry.key
            if (entry.value.isNotEmpty()) groups.add(group)
        }
        return result
    }
}
