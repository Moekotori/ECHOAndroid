package app.echo.android.playback

import java.io.File

internal class EchoSmartTransitionCache(private val directory: File) {
    private val memory = object : LinkedHashMap<String, EchoSmartTransitionAnalysis>(
        EchoSmartTransitionPolicy.MemoryCacheLimit + 1,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, EchoSmartTransitionAnalysis>?): Boolean =
            size > EchoSmartTransitionPolicy.MemoryCacheLimit
    }

    @Synchronized
    fun get(key: String): EchoSmartTransitionAnalysis? {
        memory[key]?.let { return it }
        val file = fileFor(key)
        if (!file.isFile) return null
        val parsed = runCatching { parse(file.readText()) }.getOrNull() ?: return null
        memory[key] = parsed
        return parsed
    }

    @Synchronized
    fun put(key: String, analysis: EchoSmartTransitionAnalysis) {
        memory[key] = analysis
        runCatching {
            directory.mkdirs()
            fileFor(key).writeText(encode(analysis))
            evictDisk()
        }
    }

    @Synchronized
    fun diskSizeBytes(): Long =
        directory.listFiles()?.sumOf { it.length() } ?: 0L

    @Synchronized
    fun memorySize(): Int = memory.size

    private fun fileFor(key: String): File {
        val digest = key.hashCode().toUInt().toString(16)
        return File(directory, "$digest.json")
    }

    private fun evictDisk() {
        val files = directory.listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= EchoSmartTransitionPolicy.DiskCacheMaxBytes) return
            total -= file.length()
            file.delete()
        }
    }

    private fun encode(analysis: EchoSmartTransitionAnalysis): String {
        val parts = mutableListOf(
            analysis.durationMs.toString(),
            analysis.leadingSilenceMs.toString(),
            analysis.trailingSilenceMs.toString(),
            analysis.headEnergy.toString(),
            analysis.tailEnergy.toString(),
            (analysis.sampleRateHz ?: 0).toString(),
            if (analysis.hasIntro) "1" else "0",
            if (analysis.hasOutro) "1" else "0",
            (analysis.bpm ?: 0f).toString(),
            analysis.bpmConfidence.toString(),
            (analysis.beatOffsetMs ?: -1).toString(),
            analysis.introVocal.confidence.toString(),
        )
        parts += analysis.introVocal.curve.map { it.toString() }
        parts += analysis.outroVocal.confidence.toString()
        parts += analysis.outroVocal.curve.map { it.toString() }
        return parts.joinToString(",")
    }

    private fun parse(raw: String): EchoSmartTransitionAnalysis {
        val parts = raw.split(',')
        require(parts.size >= 5)
        val durationMs = parts[0].toLong()
        val bpm = parts.getOrNull(8)?.toFloatOrNull()?.takeIf { it > 0f }
        val offset = parts.getOrNull(10)?.toIntOrNull()?.takeIf { it >= 0 }
        val windowMs = minOf(EchoSmartTransitionPolicy.WindowMs, durationMs.toInt().coerceAtLeast(0))
        return EchoSmartTransitionAnalysis(
            durationMs = durationMs,
            leadingSilenceMs = parts[1].toInt(),
            trailingSilenceMs = parts[2].toInt(),
            headEnergy = parts[3].toFloat().coerceIn(0f, 1f),
            tailEnergy = parts[4].toFloat().coerceIn(0f, 1f),
            sampleRateHz = parts.getOrNull(5)?.toIntOrNull()?.takeIf { it > 0 },
            hasIntro = parts.getOrNull(6) != "0",
            hasOutro = parts.getOrNull(7) != "0",
            bpm = bpm,
            bpmConfidence = parts.getOrNull(9)?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f,
            beatOffsetMs = offset,
            beatsMs = if (bpm != null) {
                EchoSmartTransitionTempoMath.beatTimesMs(0, windowMs, bpm, offset ?: 0)
            } else {
                intArrayOf()
            },
            introVocal = parseVocal(parts, 11),
            outroVocal = parseVocal(parts, 12 + EchoSmartTransitionVocal.Buckets),
        )
    }

    private fun parseVocal(parts: List<String>, start: Int): EchoSmartTransitionVocal {
        val confidence = parts.getOrNull(start)?.toFloatOrNull()?.coerceIn(0f, 1f) ?: return EchoSmartTransitionVocal()
        val curve = FloatArray(EchoSmartTransitionVocal.Buckets) { index ->
            parts.getOrNull(start + 1 + index)?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f
        }
        return EchoSmartTransitionVocal(curve, confidence)
    }
}
