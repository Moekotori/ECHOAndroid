package app.echo.android.model.error

/** Deliberately accepts only enums and numbers: no titles, paths, URLs, lyrics or credentials. */
object EchoDiagnosticEvents {
    enum class Event { PlaybackState, MediaTransition, OutputChanged, AudioFocus, UsbOpen, UsbError, UsbTransport, UsbSupply, UsbAudioPackets, UsbFeedbackPackets, UsbUrbs, UsbFeedbackClock, SleepTimer, CaptureStarted, CaptureStopped }
    fun interface Sink { fun record(event: Event, value: Long, detail: Long) }
    @Volatile private var sink: Sink? = null
    val enabled: Boolean get() = sink != null
    fun install(value: Sink?) { sink = value }
    fun record(event: Event, value: Long = 0, detail: Long = 0) { sink?.record(event, value, detail) }
}
