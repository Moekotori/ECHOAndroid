package app.echo.android.playback

import app.echo.android.model.playback.PlaybackShuffleResume

/** Tracks visits independently of the Media3 permutation, whose prefix can contain unplayed items. */
internal class ShufflePlaybackHistory {
    private val previous = ArrayDeque<String>()
    private var current: String? = null
    private var currentIsManual = false
    private var currentPlayed = false

    fun observe(entry: String?, manual: Boolean, playing: Boolean) {
        if (current == entry) {
            currentPlayed = currentPlayed || playing
            return
        }
        if (currentPlayed && !currentIsManual) current?.let(previous::addLast)
        if (previous.size > PlaybackShuffleResume.HistoryLimit) previous.removeFirst()
        current = entry
        currentIsManual = manual
        currentPlayed = playing
    }

    fun entries(): List<String> = previous.toList()

    fun rewindTo(entry: String, manual: Boolean) {
        while (previous.isNotEmpty() && previous.removeLast() != entry) { }
        current = entry
        currentIsManual = manual
        currentPlayed = true
    }

    fun restore(entry: String?, manual: Boolean, entries: List<String>, played: Boolean = false) {
        previous.clear()
        previous.addAll(entries.takeLast(PlaybackShuffleResume.HistoryLimit))
        current = entry
        currentIsManual = manual
        currentPlayed = played
    }
}
