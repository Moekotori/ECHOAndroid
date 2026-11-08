package app.echo.android.model.playback

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.math.roundToInt

object EchoEqualizerShareCodec {
    const val Prefix = "ECHOEQ1."
    const val Version = 2
    private const val MaxDecodedBytes = 12 * 1024
    private const val MaxFilters = 32

    fun encode(preset: EchoEqualizerUserPreset): String? {
        val normalized = EchoEqualizerUserPresets.normalize(preset.copy(id = preset.id.ifBlank { "share" })) ?: return null
        val body = if (normalized.parametric) {
            listOf(
                Version.toString(),
                "1",
                encodeToken(normalized.name),
                formatNumber(normalized.preampDb),
                encodeToken(normalized.sourceLabel.orEmpty()),
                normalized.filters.take(MaxFilters).joinToString(";") { encodeFilter(it) },
                "${formatNumber(normalized.options.strengthPercent)},${formatNumber(normalized.options.tiltDb)}",
                encodeToken(normalized.opraEqId.orEmpty()),
            ).joinToString("|")
        } else {
            listOf(
                Version.toString(),
                "0",
                encodeToken(normalized.name),
                normalized.graphicPresetId,
                normalized.gainsDb.joinToString(",") { formatNumber(it) },
                formatNumber(normalized.preampDb),
                encodeToken(normalized.sourceLabel.orEmpty()),
                "${formatNumber(normalized.options.strengthPercent)},${formatNumber(normalized.options.tiltDb)}",
                encodeToken(normalized.opraEqId.orEmpty()),
            ).joinToString("|")
        }
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(body.toByteArray(StandardCharsets.UTF_8))
        return "$Prefix$encoded."
    }

    fun decode(code: String, id: String): EchoEqualizerUserPreset? {
        if (code.trimStart().startsWith("{") || code.trimStart().startsWith("[")) return runCatching { EchoEqPcJsonCodec.decode(code).singleOrNull()?.copy(id = id) }.getOrNull()
        if (extractEncoded(code) == null) {
            return EchoEqualizerApoCodec.parse(code, id)
        }
        val payload = extractPayload(code) ?: return EchoEqualizerApoCodec.parse(code, id)
        if (payload.size > MaxDecodedBytes) return null
        val parts = String(payload, StandardCharsets.UTF_8).split('|')
        if (parts.size < 5 || parts[0].toIntOrNull() !in 1..Version) return null
        val parametric = parts[1] == "1"
        val preset = if (parametric) {
            if (parts.size < 6) return null
            val filters = decodeFilters(parts[5])
            if (filters.isEmpty() || filters.size > MaxFilters) return null
            EchoEqualizerUserPreset(
                id = id,
                name = decodeToken(parts[2]),
                parametric = true,
                preampDb = parts[3].toFloatOrNull() ?: return null,
                filters = filters,
                sourceLabel = decodeToken(parts[4]).takeIf { it.isNotEmpty() },
                options = decodeOptions(parts.getOrNull(6)),
                opraEqId = parts.getOrNull(7)?.let(::decodeToken)?.takeIf { it.isNotBlank() },
            )
        } else {
            if (parts.size < 7) return null
            EchoEqualizerUserPreset(
                id = id,
                name = decodeToken(parts[2]),
                parametric = false,
                graphicPresetId = parts[3],
                gainsDb = parts[4].split(',').mapNotNull { it.toFloatOrNull() },
                preampDb = parts[5].toFloatOrNull() ?: return null,
                sourceLabel = decodeToken(parts[6]).takeIf { it.isNotEmpty() },
                options = decodeOptions(parts.getOrNull(7)),
                opraEqId = parts.getOrNull(8)?.let(::decodeToken)?.takeIf { it.isNotBlank() },
            )
        }
        return EchoEqualizerUserPresets.normalize(preset)
    }

    fun extract(code: String): String? {
        val encoded = extractEncoded(code) ?: return null
        val canonical = "$Prefix$encoded."
        return canonical.takeIf { decode(canonical, "share") != null }
    }

    private fun extractEncoded(code: String): String? {
        val compact = code.filterNot { it.isWhitespace() }
        val start = compact.indexOf(Prefix, ignoreCase = true)
        if (start < 0) return null
        val encoded = compact.substring(start + Prefix.length).takeWhile { char ->
            char.isLetterOrDigit() || char == '-' || char == '_'
        }
        return encoded.takeIf { it.isNotEmpty() }
    }

    private fun extractPayload(code: String): ByteArray? {
        val encoded = extractEncoded(code) ?: return null
        return runCatching { Base64.getUrlDecoder().decode(encoded) }.getOrNull()
    }

    private fun encodeFilter(band: OpraEqBand): String =
        listOf(
            band.type,
            formatNumber(band.frequencyHz),
            formatNumber(band.gainDb),
            band.q?.let(::formatNumber).orEmpty(),
            band.slope?.let(::formatNumber).orEmpty(),
            if (band.enabled) "1" else "0", band.scope.id, band.dynamicMode.id,
            formatNumber(band.dynamicThresholdDb), formatNumber(band.dynamicRatio),
            formatNumber(band.dynamicAttackMs), formatNumber(band.dynamicReleaseMs),
        ).joinToString(",")

    private fun decodeFilters(raw: String): List<OpraEqBand> =
        raw.split(';').mapNotNull { token ->
            val fields = token.split(',')
            if (fields.size < 3) return@mapNotNull null
            val frequency = fields[1].toFloatOrNull() ?: return@mapNotNull null
            if (!frequency.isFinite() || frequency <= 0f) return@mapNotNull null
            OpraEqBand(
                type = EchoEqFilterType.normalize(fields[0]),
                frequencyHz = frequency,
                gainDb = fields[2].toFloatOrNull()?.takeIf { it.isFinite() } ?: 0f,
                q = fields.getOrNull(3)?.toFloatOrNull()?.takeIf { it.isFinite() },
                slope = fields.getOrNull(4)?.toFloatOrNull()?.takeIf { it.isFinite() },
                enabled = fields.getOrNull(5) != "0",
                scope = EchoEqBandScope.fromId(fields.getOrNull(6)),
                dynamicMode = EchoEqDynamicMode.fromId(fields.getOrNull(7)),
                dynamicThresholdDb = fields.getOrNull(8)?.toFloatOrNull() ?: -24f,
                dynamicRatio = fields.getOrNull(9)?.toFloatOrNull() ?: 2f,
                dynamicAttackMs = fields.getOrNull(10)?.toFloatOrNull() ?: 5f,
                dynamicReleaseMs = fields.getOrNull(11)?.toFloatOrNull() ?: 80f,
            )
        }

    private fun decodeOptions(raw: String?): EchoEqOptions = raw?.split(',')?.let {
        EchoEqOptions(it.getOrNull(0)?.toFloatOrNull() ?: 100f, it.getOrNull(1)?.toFloatOrNull() ?: 0f).normalized
    } ?: EchoEqOptions()

    private fun encodeToken(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun decodeToken(value: String): String =
        runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrDefault(value)

    private fun formatNumber(value: Float): String {
        if (!value.isFinite()) return "0"
        val scaled = (value * 1000f).roundToInt() / 1000f
        return if (scaled == scaled.toInt().toFloat()) {
            scaled.toInt().toString()
        } else {
            scaled.toString()
        }
    }
}
