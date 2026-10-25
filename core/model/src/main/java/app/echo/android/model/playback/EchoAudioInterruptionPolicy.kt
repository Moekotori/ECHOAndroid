package app.echo.android.model.playback

/** Applies to transient focus loss only. Permanent loss always pauses playback. */
enum class EchoAudioInterruptionPolicy(val id: String) {
    Duck("duck"), Pause("pause");
    companion object {
        fun fromId(value: String?): EchoAudioInterruptionPolicy = entries.firstOrNull { it.id == value } ?: Duck
    }
}
