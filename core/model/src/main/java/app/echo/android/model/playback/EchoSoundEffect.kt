package app.echo.android.model.playback

/** IDs match ECHOSteam's sound-effect selector, independent of personal EQ. */
enum class EchoSoundEffectPreset(val id: String) {
    Vocal("vocal"), Bass("bass"), Warm("warm"), Wide("wide"), Live("live"), Hall("hall"),
    Tape("tape"), Night("night"), Headphones("headphones"), Echo("echo"), Tube("tube"), Vinyl("vinyl"),
    AutoPan("autopan"), Deess("deess"), Tremolo("tremolo"), Phaser("phaser"), Transient("transient"),
    Flanger("flanger"), Telephone("telephone"), Digital("digital"), AutoWah("autowah"), RingMod("ringmod");

    companion object {
        fun fromId(id: String?): EchoSoundEffectPreset = entries.firstOrNull { it.id == id } ?: Vocal
    }
}

data class EchoSoundEffectSettings(val enabled: Boolean = false, val preset: EchoSoundEffectPreset = EchoSoundEffectPreset.Vocal) {
    companion object {
        fun fromStored(enabled: Boolean, id: String?): EchoSoundEffectSettings {
            val known = EchoSoundEffectPreset.entries.firstOrNull { it.id == id }
            return EchoSoundEffectSettings(enabled && known != null, known ?: EchoSoundEffectPreset.Vocal)
        }
    }
}

enum class EchoSoundEffectState { Off, Preparing, Active, Bypassed, UnsupportedFormat, Failed }

/** Processing facts, published only on graph/format changes, never on every audio block. */
data class EchoSoundEffectRuntimeStatus(
    val state: EchoSoundEffectState = EchoSoundEffectState.Off,
    val preset: EchoSoundEffectPreset? = null,
    val sampleRateHz: Int = 0,
    val channels: Int = 0,
    val latencyFrames: Int = 0,
)

fun EchoPlaybackDiagnostics.withSoundEffectRuntime(runtime: EchoSoundEffectRuntimeStatus): EchoPlaybackDiagnostics =
    copy(soundEffect = if (usbBitPerfectEnabled) runtime.copy(state = EchoSoundEffectState.Bypassed) else runtime)
