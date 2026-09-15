package app.echo.android.model.playback

object EchoEqualizerApoCodec {
    private const val MaxFilters = 32
    private val FilterHeader = Regex("""^\s*Filter\s*\d*\s*:\s*(ON|OFF)\s+([A-Za-z]+)\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val PreampLine = Regex("""^\s*Preamp:\s*([+-]?\d+(?:\.\d+)?)\s*dB""", RegexOption.IGNORE_CASE)
    private val GraphicLine = Regex("""^\s*GraphicEQ:\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val Frequency = Regex("""Fc\s+([+-]?\d+(?:\.\d+)?)\s*Hz""", RegexOption.IGNORE_CASE)
    private val Gain = Regex("""Gain\s+([+-]?\d+(?:\.\d+)?)\s*dB""", RegexOption.IGNORE_CASE)
    private val Quality = Regex("""(?:^|[\s])Q\s+([+-]?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private val Comment = Regex("""^\s*#\s*(.+)$""")

    fun parse(text: String, id: String): EchoEqualizerUserPreset? {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.isEmpty()) return null
        var preamp = 0f
        var name: String? = null
        val filters = ArrayList<OpraEqBand>(8)
        var graphic = emptyList<OpraEqBand>()
        for (line in lines) {
            val comment = Comment.matchEntire(line)?.groupValues?.get(1)?.trim()
            if (comment != null) {
                if (name == null && comment.isNotEmpty() && !comment.startsWith("http", ignoreCase = true)) {
                    name = comment
                }
                continue
            }
            val preampMatch = PreampLine.matchEntire(line)
            if (preampMatch != null) {
                preamp = preampMatch.groupValues[1].toFloatOrNull() ?: preamp
                continue
            }
            val graphicMatch = GraphicLine.matchEntire(line)
            if (graphicMatch != null) {
                graphic = parseGraphic(graphicMatch.groupValues[1])
                continue
            }
            val filter = parseFilter(line) ?: continue
            if (filters.size < MaxFilters) filters.add(filter)
        }
        val bands = filters.ifEmpty { graphic }
        if (bands.isEmpty() && kotlin.math.abs(preamp) < 0.05f) return null
        return EchoEqualizerUserPresets.normalize(
            EchoEqualizerUserPreset(
                id = id,
                name = name ?: "Imported EQ",
                parametric = bands.isNotEmpty(),
                graphicPresetId = EchoEqualizerPreset.Custom,
                preampDb = preamp,
                filters = bands,
                sourceLabel = name,
            ),
        )
    }

    fun encode(preset: EchoEqualizerUserPreset): String? {
        val normalized = EchoEqualizerUserPresets.normalize(preset.copy(id = preset.id.ifBlank { "share" })) ?: return null
        return buildString {
            append("# ").append(normalized.name).append('\n')
            append("Preamp: ").append(formatApoNumber(normalized.preampDb)).append(" dB\n")
            if (normalized.parametric) {
                normalized.filters.take(MaxFilters).forEachIndexed { index, band ->
                    append("Filter ").append(index + 1).append(": ").append(encodeFilter(band)).append('\n')
                }
            } else {
                append("GraphicEQ: ")
                append(
                    EchoEqualizerPresets.defaultFrequenciesHz.mapIndexed { index, frequency ->
                        "$frequency ${formatApoNumber(normalized.gainsDb.getOrElse(index) { 0f })}"
                    }.joinToString("; "),
                )
                append('\n')
            }
        }.trimEnd()
    }

    private fun parseFilter(line: String): OpraEqBand? {
        val match = FilterHeader.matchEntire(line) ?: return null
        if (!match.groupValues[1].equals("ON", ignoreCase = true)) return null
        val type = apoType(match.groupValues[2]) ?: return null
        val rest = match.groupValues[3]
        val frequency = Frequency.find(rest)?.groupValues?.get(1)?.toFloatOrNull() ?: return null
        if (!frequency.isFinite() || frequency <= 0f) return null
        val gain = Gain.find(rest)?.groupValues?.get(1)?.toFloatOrNull()?.takeIf { it.isFinite() } ?: 0f
        val q = Quality.find(rest)?.groupValues?.get(1)?.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f }
        return OpraEqBand(
            type = type,
            frequencyHz = frequency,
            gainDb = gain,
            q = q,
            slope = if (type == EchoEqFilterType.LowPass || type == EchoEqFilterType.HighPass) {
                if (q == null) 12f else null
            } else {
                null
            },
        )
    }

    private fun parseGraphic(raw: String): List<OpraEqBand> {
        val pairs = Regex("""([+-]?\d+(?:\.\d+)?)\s+([+-]?\d+(?:\.\d+)?)""")
            .findAll(raw)
            .map { match -> match.groupValues[1].toFloat() to match.groupValues[2].toFloat() }
            .filter { (frequency, gain) -> frequency.isFinite() && frequency > 0f && gain.isFinite() }
            .take(MaxFilters)
            .toList()
        return pairs.map { (frequency, gain) ->
            OpraEqBand(EchoEqFilterType.PeakDip, frequency, gain, 1.2f, null)
        }
    }

    private fun encodeFilter(band: OpraEqBand): String {
        val type = when (band.normalizedType()) {
            EchoEqFilterType.LowShelf -> "LSC"
            EchoEqFilterType.HighShelf -> "HSC"
            EchoEqFilterType.LowPass -> if (band.q != null) "LPQ" else "LP"
            EchoEqFilterType.HighPass -> if (band.q != null) "HPQ" else "HP"
            EchoEqFilterType.BandStop -> "NO"
            EchoEqFilterType.BandPass -> "BP"
            else -> "PK"
        }
        return buildString {
            append("ON ").append(type).append(" Fc ").append(formatApoNumber(band.frequencyHz)).append(" Hz")
            if (band.normalizedType() != EchoEqFilterType.LowPass && band.normalizedType() != EchoEqFilterType.HighPass || kotlin.math.abs(band.gainDb) >= 0.05f) {
                append(" Gain ").append(formatApoNumber(band.gainDb)).append(" dB")
            }
            band.q?.let { append(" Q ").append(formatApoNumber(it)) }
        }
    }

    private fun apoType(raw: String): String? =
        when (raw.uppercase()) {
            "PK", "PEQ", "PEAKING", "EQ", "PEAK" -> EchoEqFilterType.PeakDip
            "LS", "LSC", "LCB", "LOW_SHELF", "LOWSHELF" -> EchoEqFilterType.LowShelf
            "HS", "HSC", "HCB", "HIGH_SHELF", "HIGHSHELF" -> EchoEqFilterType.HighShelf
            "LP", "LPQ", "LOWPASS", "LOW_PASS" -> EchoEqFilterType.LowPass
            "HP", "HPQ", "HIGHPASS", "HIGH_PASS" -> EchoEqFilterType.HighPass
            "NO", "NOTCH", "BN", "BANDSTOP", "BAND_STOP" -> EchoEqFilterType.BandStop
            "BP", "BPQ", "BANDPASS", "BAND_PASS" -> EchoEqFilterType.BandPass
            else -> {
                val normalized = EchoEqFilterType.normalize(raw)
                normalized.takeIf {
                    it == EchoEqFilterType.PeakDip ||
                        it == EchoEqFilterType.LowShelf ||
                        it == EchoEqFilterType.HighShelf ||
                        it == EchoEqFilterType.LowPass ||
                        it == EchoEqFilterType.HighPass ||
                        it == EchoEqFilterType.BandStop ||
                        it == EchoEqFilterType.BandPass
                }
            }
        }

    private fun formatApoNumber(value: Float): String {
        if (!value.isFinite()) return "0"
        val scaled = (kotlin.math.round(value * 1000f) / 1000f)
        return if (scaled == scaled.toInt().toFloat()) scaled.toInt().toString() else scaled.toString()
    }
}
