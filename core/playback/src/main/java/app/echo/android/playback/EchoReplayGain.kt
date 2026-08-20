package app.echo.android.playback

import kotlin.math.pow

data class EchoReplayGainOutput(
    val playerVolume: Float,
    val enhancerGainMb: Int,
)

fun echoReplayGainOutput(
    enabled: Boolean,
    preampDb: Float,
    trackGainDb: Float?,
): EchoReplayGainOutput {
    if (!enabled) {
        return EchoReplayGainOutput(playerVolume = 1f, enhancerGainMb = 0)
    }
    val gainDb = preampDb + (trackGainDb ?: 0f)
    return if (gainDb <= 0f) {
        EchoReplayGainOutput(
            playerVolume = 10.0.pow((gainDb / 20.0)).toFloat().coerceIn(MIN_ATTENUATION_VOLUME, 1f),
            enhancerGainMb = 0,
        )
    } else {
        EchoReplayGainOutput(
            playerVolume = 1f,
            enhancerGainMb = (gainDb * 100f).toInt().coerceIn(0, MAX_ENHANCER_GAIN_MB),
        )
    }
}

private const val MIN_ATTENUATION_VOLUME = 0.01f
private const val MAX_ENHANCER_GAIN_MB = 3_000
