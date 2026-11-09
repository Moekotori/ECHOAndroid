package app.echo.android.model.playback

/** Scheduling strategy used only while shuffle is enabled. */
enum class EchoShuffleMode(val id: String, val desktopRecentCount: Int? = null) {
    QueueRandom("queue_random", 0),
    AvoidRecent("avoid_recent", 25),
    AvoidRecentLong("avoid_recent_long", 100),
    LibraryWide("library_wide", 25),
    PseudoRandom("pseudo_random"),
    PureRandom("pure_random"),
    Albums("albums");

    companion object {
        fun fromId(id: String?): EchoShuffleMode =
            if (id == null) AvoidRecent else entries.firstOrNull { it.id == id } ?: PseudoRandom
    }
}
