package app.echo.android.playback

import kotlin.random.Random

/** Shuffle once per round, then interleave artist buckets in linear time. */
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
        val groups = ArrayDeque(buckets.entries)
        val result = ArrayList<Int>(indices.size)
        previous = artist(current)
        while (groups.isNotEmpty()) {
            var group = groups.removeFirst()
            if (group.key == previous && groups.isNotEmpty()) {
                val alternative = groups.removeFirst()
                groups.addFirst(group)
                group = alternative
            }
            result += group.value.removeFirst()
            previous = group.key
            if (group.value.isNotEmpty()) groups.addLast(group)
        }
        return result
    }
}
