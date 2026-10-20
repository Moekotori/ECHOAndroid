package app.echo.android.feature.library

import app.echo.android.model.connect.EchoRemoteTrack

/** Screen-owned. Keep existing rows in place and shuffle only newly appended rows. */
internal class LinkedLibraryRandomOrder {
    private var keys: List<String> = emptyList()

    @Synchronized
    fun order(tracks: List<EchoRemoteTrack>, checkActive: () -> Unit = {}): List<EchoRemoteTrack> {
        val remaining = tracks.associateByTo(LinkedHashMap()) { track ->
            checkActive()
            track.id ?: "${track.title}-${track.artist}-${track.album.orEmpty()}"
        }
        val result = ArrayList<EchoRemoteTrack>(tracks.size)
        val nextKeys = ArrayList<String>(tracks.size)
        keys.forEach { key ->
            checkActive()
            remaining.remove(key)?.let { result.add(it); nextKeys.add(key) }
        }
        val additions = remaining.keys.toMutableList()
        // Fisher-Yates with cancellation points; cancelled builds never commit their order.
        for (index in additions.lastIndex downTo 1) {
            checkActive()
            val other = kotlin.random.Random.nextInt(index + 1)
            val key = additions[index]
            additions[index] = additions[other]
            additions[other] = key
        }
        additions.forEach { key ->
            checkActive()
            result.add(remaining.getValue(key))
            nextKeys.add(key)
        }
        checkActive()
        keys = nextKeys
        return result
    }
}
