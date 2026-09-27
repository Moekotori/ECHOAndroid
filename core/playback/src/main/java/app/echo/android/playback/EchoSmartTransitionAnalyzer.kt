package app.echo.android.playback

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

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
        return withContext(Dispatchers.IO) {
            val key = EchoSmartTransitionPolicy.cacheKey(mediaId, uri, durationMs) + "|" + decoder.sourceRevision(uri)
            val cached = cache.get(key)
            if (cached != null && cached.covers(needIntro, needOutro)) return@withContext cached
            val computed = compute(uri, durationMs, needIntro, needOutro, cached) ?: return@withContext cached
            coroutineContext.ensureActive()
            val merged = cached?.merge(computed) ?: computed
            cache.put(key, merged)
            merged
        }
    }

    private suspend fun compute(
        uri: String,
        durationMs: Long,
        needIntro: Boolean,
        needOutro: Boolean,
        cached: EchoSmartTransitionAnalysis?,
    ): EchoSmartTransitionAnalysis? {
        val window = EchoSmartTransitionPolicy.WindowMs.toLong().coerceAtMost(durationMs.coerceAtLeast(0L))
        if (window <= 0L) return null
        val decodeIntro = (needIntro || (needOutro && durationMs <= EchoSmartTransitionPolicy.WindowMs)) && cached?.hasIntro != true
        val decodeOutro = needOutro && cached?.hasOutro != true && durationMs > EchoSmartTransitionPolicy.WindowMs
        var intro: EchoSmartTransitionAnalysis? = null
        var outro: EchoSmartTransitionAnalysis? = null
        if (decodeIntro) {
            val pcm = decoder.decodeAnalysisMono(uri, 0L, window) ?: return cached
            intro = withContext(Dispatchers.Default) { EchoSmartTransitionAnalysisMath.fromMono(
                samples = pcm.samples,
                sampleRate = EchoSmartTransitionPolicy.AnalysisSampleRateHz,
                durationMs = durationMs,
                nativeSampleRateHz = pcm.nativeSampleRateHz,
                hasIntro = true,
                hasOutro = durationMs <= EchoSmartTransitionPolicy.WindowMs,
                vocal = pcm.vocal,
            ) }
            if (durationMs <= EchoSmartTransitionPolicy.WindowMs) return intro
        }
        if (decodeOutro) {
            val pcm = decoder.decodeAnalysisMono(uri, (durationMs - window).coerceAtLeast(0L), window)
            if (pcm != null) {
                outro = withContext(Dispatchers.Default) { EchoSmartTransitionAnalysisMath.fromMono(
                    samples = pcm.samples,
                    sampleRate = EchoSmartTransitionPolicy.AnalysisSampleRateHz,
                    durationMs = durationMs,
                    nativeSampleRateHz = pcm.nativeSampleRateHz,
                    hasIntro = false,
                    hasOutro = true,
                    vocal = pcm.vocal,
                ) }
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
