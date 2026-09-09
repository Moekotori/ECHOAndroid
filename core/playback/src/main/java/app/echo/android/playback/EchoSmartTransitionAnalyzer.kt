package app.echo.android.playback

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class EchoSmartTransitionAnalyzer(
    private val decoder: EchoSmartTransitionDecoder,
    private val cache: EchoSmartTransitionCache,
) {
    suspend fun analysisFor(
        mediaId: String,
        uri: String,
        durationMs: Long,
        needIntro: Boolean,
        needOutro: Boolean,
    ): EchoSmartTransitionAnalysis? {
        val key = EchoSmartTransitionPolicy.cacheKey(mediaId, uri, durationMs)
        cache.get(key)?.takeIf { it.covers(needIntro, needOutro) }?.let { return it }
        return withContext(Dispatchers.IO) {
            val cached = cache.get(key)
            if (cached != null && cached.covers(needIntro, needOutro)) return@withContext cached
            val computed = compute(uri, durationMs, needIntro, needOutro, cached) ?: return@withContext cached
            val merged = cached?.merge(computed) ?: computed
            cache.put(key, merged)
            merged
        }
    }

    private fun compute(
        uri: String,
        durationMs: Long,
        needIntro: Boolean,
        needOutro: Boolean,
        cached: EchoSmartTransitionAnalysis?,
    ): EchoSmartTransitionAnalysis? {
        val window = EchoSmartTransitionPolicy.WindowMs.toLong().coerceAtMost(durationMs.coerceAtLeast(0L))
        if (window <= 0L) return null
        val decodeIntro = needIntro && cached?.hasIntro != true
        val decodeOutro = needOutro && cached?.hasOutro != true && durationMs > EchoSmartTransitionPolicy.WindowMs
        var intro: EchoSmartTransitionAnalysis? = null
        var outro: EchoSmartTransitionAnalysis? = null
        if (decodeIntro) {
            val pcm = decoder.decodeAnalysisMono(uri, 0L, window) ?: return cached
            intro = EchoSmartTransitionAnalysisMath.fromMono(
                samples = pcm.samples,
                sampleRate = EchoSmartTransitionPolicy.AnalysisSampleRateHz,
                durationMs = durationMs,
                nativeSampleRateHz = pcm.nativeSampleRateHz,
                hasIntro = true,
                hasOutro = durationMs <= EchoSmartTransitionPolicy.WindowMs,
                vocal = pcm.vocal,
            )
            if (durationMs <= EchoSmartTransitionPolicy.WindowMs) return intro
        }
        if (decodeOutro) {
            val pcm = decoder.decodeAnalysisMono(uri, (durationMs - window).coerceAtLeast(0L), window)
            if (pcm != null) {
                outro = EchoSmartTransitionAnalysisMath.fromMono(
                    samples = pcm.samples,
                    sampleRate = EchoSmartTransitionPolicy.AnalysisSampleRateHz,
                    durationMs = durationMs,
                    nativeSampleRateHz = pcm.nativeSampleRateHz,
                    hasIntro = false,
                    hasOutro = true,
                    vocal = pcm.vocal,
                )
            }
        }
        return when {
            intro != null && outro != null -> intro.merge(outro)
            intro != null -> intro
            outro != null -> outro
            else -> null
        }
    }
}
