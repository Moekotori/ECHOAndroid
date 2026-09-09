package app.echo.android.playback

import app.echo.android.model.playback.EchoReplayGainMode
import app.echo.android.model.playback.EchoReplayGainTags
import java.io.InputStream
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
    data class Parsed(val tags: EchoReplayGainTags) : ReplayGainReadOutcome()
    data object Failed : ReplayGainReadOutcome()
}

fun replayGainAfterMediaItemChange(
    mediaId: String?,
    cachedTags: Map<String, EchoReplayGainTags>,
    mode: EchoReplayGainMode,
    previousGainDb: Float?,
): Float? {
    if (mediaId == null) return previousGainDb
    val tags = cachedTags[mediaId] ?: return null
    return tags.selectedGainDb(mode)
}

fun replayGainTagsLoaded(
    mediaId: String?,
    cachedTags: Map<String, EchoReplayGainTags>,
): Boolean = mediaId != null && cachedTags.containsKey(mediaId)

fun nextReplayGainPrefetchId(currentMediaId: String?, nextMediaId: String?): String? {
    val next = nextMediaId?.takeIf { it.isNotBlank() } ?: return null
    if (next == currentMediaId) return null
    return next
}

fun replayGainReadOutcome(
    streamOpened: Boolean,
    parseResult: Result<EchoReplayGainTags>,
): ReplayGainReadOutcome {
    if (!streamOpened) return ReplayGainReadOutcome.Failed
    val tags = parseResult.getOrElse { return ReplayGainReadOutcome.Failed }
    return ReplayGainReadOutcome.Parsed(tags)
}

fun shouldCacheReplayGainRead(outcome: ReplayGainReadOutcome): Boolean =
    outcome is ReplayGainReadOutcome.Parsed

fun echoReplayGainMakeupLinear(enhancerGainMb: Int): Float {
    if (enhancerGainMb <= 0) return 1f
    return 10.0.pow(enhancerGainMb / 2_000.0).toFloat().coerceAtLeast(1f)
}

fun shouldApplyReplayGainPlayerVolume(usbMuteInProgress: Boolean): Boolean = !usbMuteInProgress

enum class ReplayGainStreamKind {
    LocalContent,
    LocalFile,
    RemoteHttp,
}

fun replayGainStreamKind(uri: String): ReplayGainStreamKind? {
    val scheme = uri.substringBefore(':', missingDelimiterValue = "").lowercase()
    return when (scheme) {
        "content", "android.resource" -> ReplayGainStreamKind.LocalContent
        "file" -> ReplayGainStreamKind.LocalFile
        "http", "https" -> ReplayGainStreamKind.RemoteHttp
        else -> null
    }
}

fun canOpenReplayGainStream(
    uri: String,
    webDavAuthReadyForUri: Boolean,
    subsonicAuthReadyForUri: Boolean,
    jellyfinAuthReadyForUri: Boolean = true,
): Boolean {
    val kind = replayGainStreamKind(uri) ?: return false
    if (kind != ReplayGainStreamKind.RemoteHttp) return true
    if (webDavPlaybackUriRequiresCredential(uri) && !webDavAuthReadyForUri) return false
    if (subsonicPlaybackUriRequiresCredential(uri) && !subsonicAuthReadyForUri) return false
    if (jellyfinPlaybackUriRequiresCredential(uri) && !jellyfinAuthReadyForUri) return false
    return true
}

internal class LimitedInputStream(
    private val input: InputStream,
    maxBytes: Int,
) : InputStream() {
    private var remaining = maxBytes.coerceAtLeast(0)

    override fun read(): Int {
        if (remaining <= 0) return -1
        val value = input.read()
        if (value >= 0) remaining -= 1
        return value
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (remaining <= 0) return -1
        val toRead = minOf(len, remaining)
        if (toRead <= 0) return -1
        val read = input.read(b, off, toRead)
        if (read > 0) remaining -= read
        return read
    }

    override fun close() {
        input.close()
    }
}

internal const val ReplayGainRemoteReadMaxBytes = 2 * 1024 * 1024

private const val MIN_ATTENUATION_VOLUME = 0.01f
private const val MAX_ENHANCER_GAIN_MB = 3_000
