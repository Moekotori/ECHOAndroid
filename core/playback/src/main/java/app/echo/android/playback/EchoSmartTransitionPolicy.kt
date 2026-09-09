package app.echo.android.playback

import app.echo.android.model.library.LibrarySource
import app.echo.android.model.playback.EchoLinkPlaybackUri
import app.echo.android.model.playback.EchoSleepTimerMode
import app.echo.android.model.playback.EchoTrackTransitionOptions
import app.echo.android.model.settings.EchoEffectivePerformanceMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal object EchoSmartTransitionPolicy {
    const val MaxOverlapMs = 4_000
    const val MinOverlapMs = 1_500
    const val DefaultMaxOverlapMs = 3_000
    const val AnalyzeLeadMs = 25_000L
    const val WindowMs = 16_000
    const val AnalysisSampleRateHz = 11_025
    const val MaxMixBytes = (1.5 * 1024 * 1024).toInt()
    const val SilenceThresholdDb = -48f
    const val FrameMs = 100
    const val MemoryCacheLimit = 32
    const val DiskCacheMaxBytes = 2L * 1024 * 1024
    const val PredecodeLeadMs = 1_000
    const val MinRemainingAfterMixMs = 300
    const val MinTrackDurationMs = 4_000
    const val MaxSilenceSkipMs = 3_000
    const val DecodeTimeoutMs = 2_500L

    enum class BypassReason {
        Disabled,
        Lightweight,
        UsbExclusive,
        BitPerfect,
        Live,
        UnknownDuration,
        PlaybackSpeed,
        SkipSilence,
        RepeatOne,
        SleepEndOfTrack,
        Remote,
        AlbumAdjacent,
        ShortTrack,
        SampleRateMismatch,
        NextMissing,
        NotPlaying,
    }

    data class Candidate(
        val options: EchoTrackTransitionOptions,
        val performanceMode: EchoEffectivePerformanceMode,
        val usbExclusive: Boolean,
        val usbBitPerfect: Boolean,
        val live: Boolean,
        val currentDurationMs: Long,
        val currentPositionMs: Long,
        val nextDurationMs: Long,
        val playbackSpeed: Float,
        val skipSilence: Boolean,
        val repeatOne: Boolean,
        val sleepEndOfTrack: Boolean,
        val currentLocal: Boolean,
        val nextLocal: Boolean,
        val currentAlbum: String?,
        val nextAlbum: String?,
        val currentDisc: Int?,
        val nextDisc: Int?,
        val currentTrackNumber: Int?,
        val nextTrackNumber: Int?,
        val currentSampleRateHz: Int?,
        val nextSampleRateHz: Int?,
        val outputSampleRateHz: Int?,
        val outputChannelCount: Int,
        val hasNext: Boolean,
        val isPlaying: Boolean,
    )

    fun bypassReason(candidate: Candidate): BypassReason? {
        if (!candidate.options.smartEnabled) return BypassReason.Disabled
        if (candidate.performanceMode.isLightweight) return BypassReason.Lightweight
        if (candidate.usbBitPerfect) return BypassReason.BitPerfect
        if (candidate.usbExclusive) return BypassReason.UsbExclusive
        if (!candidate.isPlaying) return BypassReason.NotPlaying
        if (!candidate.hasNext) return BypassReason.NextMissing
        if (candidate.live) return BypassReason.Live
        if (candidate.currentDurationMs <= 0L || candidate.nextDurationMs <= 0L) return BypassReason.UnknownDuration
        if (kotlin.math.abs(candidate.playbackSpeed - 1f) > 0.0001f) return BypassReason.PlaybackSpeed
        if (candidate.skipSilence) return BypassReason.SkipSilence
        if (candidate.repeatOne) return BypassReason.RepeatOne
        if (candidate.sleepEndOfTrack) return BypassReason.SleepEndOfTrack
        if (!candidate.currentLocal || !candidate.nextLocal) return BypassReason.Remote
        if (isAlbumAdjacent(candidate)) return BypassReason.AlbumAdjacent
        val remaining = candidate.currentDurationMs - candidate.currentPositionMs
        if (remaining < MinOverlapMs + MinRemainingAfterMixMs ||
            candidate.currentDurationMs < MinTrackDurationMs ||
            candidate.nextDurationMs < MinTrackDurationMs
        ) {
            return BypassReason.ShortTrack
        }
        if (!ratesCompatible(candidate.outputSampleRateHz, candidate.currentSampleRateHz, candidate.nextSampleRateHz)) {
            return BypassReason.SampleRateMismatch
        }
        if (candidate.outputChannelCount > 2) return BypassReason.SampleRateMismatch
        return null
    }

    fun ratesCompatible(outputRateHz: Int?, currentRateHz: Int?, nextRateHz: Int?): Boolean {
        val left = outputRateHz ?: currentRateHz
        val right = nextRateHz
        if (left == null || right == null) return true
        return left == right
    }

    fun holdFrames(remainingMs: Long, overlapMs: Int, sampleRateHz: Int): Int {
        if (sampleRateHz <= 0 || overlapMs <= 0) return 0
        val holdMs = (remainingMs - overlapMs).coerceAtLeast(0L)
        return ((holdMs * sampleRateHz) / 1_000L).toInt().coerceAtLeast(0)
    }

    fun isAlbumAdjacent(candidate: Candidate): Boolean {
        val album = candidate.currentAlbum?.trim().orEmpty()
        if (album.isEmpty()) return false
        if (!album.equals(candidate.nextAlbum?.trim().orEmpty(), ignoreCase = true)) return false
        val currentDisc = candidate.currentDisc?.takeIf { it > 0 } ?: 1
        val nextDisc = candidate.nextDisc?.takeIf { it > 0 } ?: 1
        if (currentDisc != nextDisc) return false
        val currentTrack = candidate.currentTrackNumber ?: return false
        val nextTrack = candidate.nextTrackNumber ?: return false
        return nextTrack == currentTrack + 1
    }

    fun isLocalUri(uri: String, sourceId: String?): Boolean {
        if (EchoLinkPlaybackUri.trackIdFromPersistUri(uri) != null) return false
        if (EchoLinkPlaybackUri.isOneShotStreamUri(uri)) return false
        val source = sourceId?.let(::LibrarySource)
        if (source != null) {
            if (source == LibrarySource.Subsonic || source == LibrarySource.WebDav || source == LibrarySource.Netease) {
                return false
            }
            if (source.isLocalAudioFile) return true
        }
        val normalized = uri.trim().lowercase()
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) return false
        return normalized.startsWith("content:") || normalized.startsWith("file:")
    }

    fun maxOverlapMs(candidate: Candidate): Int {
        val userCap = if (candidate.options.fadeEnabled) {
            candidate.options.fadeDurationMs.coerceIn(500, 5_000)
        } else {
            DefaultMaxOverlapMs
        }
        val rate = (candidate.outputSampleRateHz ?: candidate.nextSampleRateHz ?: 48_000).coerceAtLeast(8_000)
        val channels = candidate.outputChannelCount.coerceIn(1, 2)
        val byteCapMs = (MaxMixBytes.toLong() * 1_000L) / (rate.toLong() * channels * 4L)
        return minOf(MaxOverlapMs, userCap, byteCapMs.toInt().coerceAtLeast(MinOverlapMs))
    }

    fun overlapMs(
        tailEnergy: Float,
        headEnergy: Float,
        remainingMs: Long,
        nextDurationMs: Long,
        maxMs: Int,
    ): Int? {
        val density = ((tailEnergy + headEnergy) / 2f).coerceIn(0f, 1f)
        val preferred = when {
            density >= 0.62f -> 1_500
            density >= 0.40f -> 2_500
            else -> 3_200
        }
        val remainingCap = remainingMs - MinRemainingAfterMixMs
        val nextCap = (nextDurationMs * 0.12).toLong()
        val cap = min(maxMs.toLong(), min(remainingCap, nextCap))
        if (cap < MinOverlapMs) return null
        return preferred.coerceIn(MinOverlapMs, cap.toInt())
    }

    fun nextStartMs(leadingSilenceMs: Int): Int =
        leadingSilenceMs.coerceIn(0, MaxSilenceSkipMs)

    fun analyzeLeadMs(mode: EchoEffectivePerformanceMode): Long =
        if (mode.isHighPerformance) Long.MAX_VALUE else AnalyzeLeadMs

    fun mixerPassthroughEnabled(
        options: EchoTrackTransitionOptions,
        mode: EchoEffectivePerformanceMode,
        usbExclusive: Boolean,
        usbBitPerfect: Boolean,
    ): Boolean =
        options.smartEnabled && !mode.isLightweight && !usbExclusive && !usbBitPerfect

    fun equalPowerOut(progress: Float): Float =
        cos((PI.toFloat() / 2f) * progress.coerceIn(0f, 1f))

    fun equalPowerIn(progress: Float): Float =
        sin((PI.toFloat() / 2f) * progress.coerceIn(0f, 1f))

    fun cacheKey(mediaId: String, uri: String, durationMs: Long): String =
        "$mediaId|$durationMs|$uri"
}
