package app.echo.android.model.playback

object EchoEqualizerApoCodec {
    private const val MaxFilters = EchoParametricEq.MaxBands
    private const val Number = """[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?"""
    private val FilterCommand = Regex("""Filter\s*\d*""", RegexOption.IGNORE_CASE)
    private val FilterParams = Regex(
        """(ON|OFF)\s+([A-Za-z_]+)\s+Fc\s+($Number)\s*Hz(?:\s+Gain\s+($Number)\s*dB)?(?:\s+Q\s+($Number))?""",
        RegexOption.IGNORE_CASE,
    )
    private val PreampValue = Regex("""($Number)\s*dB""", RegexOption.IGNORE_CASE)
    private val GraphicPair = Regex("""($Number)\s+($Number)""")
    private val UnsupportedCommands = setOf(
        "include", "copy", "convolution", "vstplugin", "delay", "device", "stage",
        "if", "elseif", "else", "endif", "eval",
    )

    fun parse(text: String, id: String): EchoEqualizerUserPreset? {
        var preamp = 0f
        var hasPreamp = false
        var name: String? = null
        val filters = ArrayList<OpraEqBand>(8)
        var graphic = emptyList<OpraEqBand>()
        var scope = EchoEqBandScope.Stereo
        for (rawLine in text.trimStart().removePrefix("\uFEFF").lineSequence()) {
            val line = rawLine.trim()
            if (line.startsWith('#')) {
                val comment = line.drop(1).trim()
                if (name == null && comment.isNotEmpty() && !comment.startsWith("http", ignoreCase = true)) {
                    name = comment
                }
                continue
            }
            if (':' !in line) continue
            val command = line.substringBefore(':').trim().lowercase()
            val params = line.substringAfter(':').substringBefore('#').trim()
            when {
                command == "preamp" -> {
                    if (scope != EchoEqBandScope.Stereo) return null
                    val match = PreampValue.matchEntire(params) ?: return null
                    val gain = match.groupValues[1].toFloatOrNull()?.takeIf { it.isFinite() } ?: return null
                    preamp += gain
                    hasPreamp = true
                }
                command == "graphiceq" -> {
                    if (graphic.isNotEmpty() || filters.isNotEmpty()) return null
                    graphic = parseGraphic(params, scope) ?: return null
                }
                command == "channel" -> {
                    scope = when (params.uppercase()) {
                        "L", "LEFT" -> EchoEqBandScope.Left
                        "R", "RIGHT" -> EchoEqBandScope.Right
                        "ALL", "L R", "R L" -> EchoEqBandScope.Stereo
                        else -> return null
                    }
                }
                command in UnsupportedCommands -> return null
                FilterCommand.matches(command) -> {
                    if (params.equals("OFF", ignoreCase = true)) continue
                    if (graphic.isNotEmpty() || filters.size >= MaxFilters) return null
                    val band = parseFilter(params) ?: return null
                    filters.add(band.copy(scope = scope))
                }
            }
        }
        val bands = filters.ifEmpty { graphic }
        if (preamp !in -60f..24f || (bands.isEmpty() && !hasPreamp)) return null
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
        if (normalized.filters.any { it.dynamicMode != EchoEqDynamicMode.Off || it.scope in listOf(EchoEqBandScope.Mid, EchoEqBandScope.Side) }) return null
        return buildString {
            append("# ").append(normalized.name).append('\n')
            append("Preamp: ").append(formatApoNumber(normalized.preampDb)).append(" dB\n")
            if (normalized.parametric) {
                normalized.filters.take(MaxFilters).forEachIndexed { index, band ->
                    append("Channel: ").append(when (band.scope) { EchoEqBandScope.Left -> "L"; EchoEqBandScope.Right -> "R"; else -> "ALL" }).append('\n')
                    val effective = band.copy(gainDb = normalized.options.effectiveGain(band))
                    append("Filter ").append(index + 1).append(": ").append(encodeFilter(effective)).append('\n')
                }
            } else {
                append("GraphicEQ: ")
                append(
                    EchoEqualizerPresets.defaultFrequenciesHz.mapIndexed { index, frequency ->
                        "${formatApoNumber(EchoEqualizerPresets.preciseFrequenciesHz[index])} ${formatApoNumber(normalized.options.effectiveGain(OpraEqBand(EchoEqFilterType.PeakDip, EchoEqualizerPresets.preciseFrequenciesHz[index], normalized.gainsDb.getOrElse(index) { 0f }, 1f, null)))}"
                    }.joinToString("; "),
                )
                append('\n')
            }
        }.trimEnd()
    }

    private fun parseFilter(params: String): OpraEqBand? {
        val match = FilterParams.matchEntire(params) ?: return null
        val (toggle, rawType, frequencyText, gainText, qText) = match.destructured
        val type = apoType(rawType) ?: return null
        val frequency = frequencyText.toFloatOrNull()?.takeIf { it in 1f..EchoParametricEq.MaxFrequencyHz } ?: return null
        val gain = if (gainText.isEmpty()) 0f else gainText.toFloatOrNull()?.takeIf { it in -60f..30f } ?: return null
        val q = if (qText.isEmpty()) null else qText.toFloatOrNull()?.takeIf { it in 0.001f..EchoParametricEq.MaxQ } ?: return null
        if (type in EchoEqOptions.GainTypes && gainText.isEmpty()) return null
        return OpraEqBand(
            type = type,
            enabled = toggle.equals("ON", true),
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

    private fun parseGraphic(raw: String, scope: EchoEqBandScope): List<OpraEqBand>? {
        val pairs = raw.trim().removeSuffix(";").split(';')
        if (pairs.size > MaxFilters) return null
        return pairs.map { pair ->
            val match = GraphicPair.matchEntire(pair.trim()) ?: return null
            val (frequencyText, gainText) = match.destructured
            val frequency = frequencyText.toFloatOrNull()?.takeIf { it in 1f..EchoParametricEq.MaxFrequencyHz } ?: return null
            val gain = gainText.toFloatOrNull()?.takeIf { it in -60f..30f } ?: return null
            OpraEqBand(EchoEqFilterType.PeakDip, frequency, gain, 1.2f, null, scope = scope)
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
            append(if (band.enabled) "ON " else "OFF ").append(type).append(" Fc ").append(formatApoNumber(band.frequencyHz)).append(" Hz")
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
