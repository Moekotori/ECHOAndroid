package app.echo.android.model.playback

/** Scheduling strategy used only while shuffle is enabled. */
enum class EchoShuffleMode(val id: String) {
    PseudoRandom("pseudo_random"),
    PureRandom("pure_random");

    companion object {
        fun fromId(id: String?): EchoShuffleMode =
            entries.firstOrNull { it.id == id } ?: PseudoRandom
    }
}
