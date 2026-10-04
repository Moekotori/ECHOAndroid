package app.echo.android.model.playback

/** Stable order for persisted values. Each bipolar control ranges from -100 to +100. */
enum class EchoMsebParameter {
    Temperature, BassExtension, BassTexture, NoteThickness, Voice,
    FemaleOvertones, SibilanceLf, SibilanceHf, ImpulseResponse, Air,
}

data class EchoMsebState(
    val enabled: Boolean = false,
    val values: List<Float> = List(EchoMsebParameter.entries.size) { 0f },
) {
    fun value(parameter: EchoMsebParameter): Float =
        values.getOrElse(parameter.ordinal) { 0f }.let { if (it.isFinite()) it.coerceIn(-100f, 100f) else 0f }

    val normalized: EchoMsebState
        get() = copy(values = EchoMsebParameter.entries.map(::value))

    val active: Boolean
        get() = enabled && EchoMsebParameter.entries.any { kotlin.math.abs(value(it)) >= 0.5f }

    fun withValue(parameter: EchoMsebParameter, value: Float): EchoMsebState =
        copy(values = EchoMsebParameter.entries.map {
            if (it == parameter) value.let { v -> if (v.isFinite()) v.coerceIn(-100f, 100f) else 0f } else value(it)
        })
}

enum class EchoMsebPreset(val values: List<Float>) {
    Neutral(List(10) { 0f }),
    Warm(listOf(30f, 20f, 15f, 25f, 10f, -10f, -20f, -25f, -15f, 0f)),
    Vocal(listOf(0f, -10f, -10f, 10f, 40f, 20f, -20f, -15f, 10f, 15f)),
    Bass(listOf(10f, 50f, 35f, 15f, 0f, 0f, -10f, -10f, 10f, 10f)),
    Clear(listOf(-25f, 0f, -20f, -15f, 15f, 20f, 0f, -10f, 25f, 35f)),
    ;

    fun applyTo(state: EchoMsebState): EchoMsebState = state.copy(values = values)
}
