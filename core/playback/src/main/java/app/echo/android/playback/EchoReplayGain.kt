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

sealed class ReplayGainReadOutcome {
    data class Parsed(val trackGainDb: Float?) : ReplayGainReadOutcome()
    data object Failed : ReplayGainReadOutcome()
}

fun replayGainAfterMediaItemChange(
    mediaId: String?,
    cachedGains: Map<String, Float?>,
    previousGainDb: Float?,
): Float? {
    if (mediaId == null) return previousGainDb
    if (cachedGains.containsKey(mediaId)) return cachedGains.getValue(mediaId)
    return previousGainDb
}

fun replayGainReadOutcome(
    streamOpened: Boolean,
    parseResult: Result<Float?>,
): ReplayGainReadOutcome {
    if (!streamOpened) return ReplayGainReadOutcome.Failed
    val gain = parseResult.getOrElse { return ReplayGainReadOutcome.Failed }
    return ReplayGainReadOutcome.Parsed(gain)
}

fun shouldCacheReplayGainRead(outcome: ReplayGainReadOutcome): Boolean =
    outcome is ReplayGainReadOutcome.Parsed

fun shouldApplyReplayGainPlayerVolume(usbMuteInProgress: Boolean): Boolean = !usbMuteInProgress

private const val MIN_ATTENUATION_VOLUME = 0.01f
private const val MAX_ENHANCER_GAIN_MB = 3_000
