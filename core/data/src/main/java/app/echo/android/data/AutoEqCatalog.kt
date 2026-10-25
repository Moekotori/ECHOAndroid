package app.echo.android.data

import app.echo.android.model.playback.*
import java.io.InputStream
import java.net.URLEncoder
import java.util.Base64
import java.util.Locale
import java.util.zip.GZIPInputStream

internal data class AutoEqEntry(val product: OpraHeadphoneCorrectionProduct, val source: String, val searchKey: String, val compactSearchKey: String = searchKey.replace(" ", ""))

internal class AutoEqCatalog(val revision: String, rawEntries: List<AutoEqEntry>) {
    private val entries = rawEntries.groupBy { Triple(it.source, it.product.subtype, it.product.productName) }
        .values.map { variants ->
            variants.first().let { first -> first.copy(product = first.product.copy(
                presets = variants.flatMap { it.product.presets },
            )) }
        }
    val brands = entries.groupingBy { it.source }.eachCount().map { (source, count) ->
        OpraHeadphoneBrand(source, source, count)
    }.sortedBy { it.name.lowercase(Locale.ROOT) }
    val status = OpraDatabaseStatus("bundled-autoeq", brands.size, entries.size, rawEntries.size, revision)

    fun search(query: String, source: String?, limit: Int = 16): List<OpraHeadphoneCorrectionProduct> {
        val tokens = OpraDatabaseParser.normalizeSearchText(query).split(' ').filter(String::isNotBlank)
        if (tokens.isEmpty() && source == null) return emptyList()
        return entries.asSequence().filter { source == null || it.source == source }
            .filter { entry -> tokens.all { token -> token in entry.searchKey || token in entry.compactSearchKey } }
            .sortedWith(compareBy<AutoEqEntry> { it.product.productName.lowercase(Locale.ROOT) }.thenBy { it.product.productId })
            .let { if (source == null) it.take(limit.coerceIn(1, 100)) else it }
            .map { it.product }.toList()
    }

    companion object {
        private val RevisionPattern = Regex("[0-9a-f]{40}")
        private val IdPattern = Regex("[0-9a-f]{20}")
        private val Categories = setOf("in-ear", "over-ear", "earbud")
        fun read(input: InputStream): AutoEqCatalog {
            val entries = ArrayList<AutoEqEntry>()
            val ids = HashSet<String>()
            var revision: String? = null
            var expected = -1
            var chars = 0L
            GZIPInputStream(input).bufferedReader(Charsets.UTF_8).useLines { lines -> lines.forEach { line ->
                chars += line.length
                require(chars <= 4 * 1024 * 1024 && line.length <= 4096) { "autoeq_catalog_too_large" }
                when {
                    line.startsWith("# revision=") -> revision = line.substringAfter('=').also {
                        require(it.matches(RevisionPattern)) { "autoeq_invalid_revision" }
                    }
                    line.startsWith("# count=") -> expected = line.substringAfter('=').toInt()
                    line.startsWith('#') || line.isBlank() -> Unit
                    else -> {
                        require(entries.size < 20_000)
                        val fields = line.split('\t')
                        require(fields.size == 7 && fields[0].matches(IdPattern) && ids.add(fields[0]))
                        require(fields[1] in Categories)
                        fun decode(value: String) = String(Base64.getDecoder().decode(value), Charsets.UTF_8)
                            .also { require(it.isNotBlank() && it.length <= 300 && it.none(Char::isISOControl)) }
                        val source = decode(fields[2])
                        val name = decode(fields[3])
                        val measurementRig = decode(fields[6])
                        require(measurementRig.endsWith(fields[1]))
                        require(listOf(source, name, measurementRig).none { it == ".." || '/' in it || '\\' in it })
                        val preamp = fields[4].toFloat().also { require(it.isFinite() && it in -24f..0f) }
                        val bands = fields[5].split(';').map { filter ->
                            val parts = filter.split(','); require(parts.size == 4)
                            val type = when (parts[0]) {
                                "P" -> EchoEqFilterType.PeakDip
                                "L" -> EchoEqFilterType.LowShelf
                                "H" -> EchoEqFilterType.HighShelf
                                else -> throw IllegalArgumentException("autoeq_unknown_filter")
                            }
                            OpraEqBand(type, parts[1].toFloat(), parts[2].toFloat(), parts[3].toFloat(), null).also {
                                require(it.frequencyHz.isFinite() && it.frequencyHz in EchoParametricEq.MinFrequencyHz..EchoParametricEq.MaxFrequencyHz)
                                require(it.gainDb.isFinite() && it.gainDb in EchoParametricEq.MinGainDb..EchoParametricEq.MaxGainDb)
                                val q = requireNotNull(it.q)
                                require(q.isFinite() && q in EchoParametricEq.MinQ..EchoParametricEq.MaxQ)
                            }
                        }
                        require(bands.size in 1..EchoParametricEq.MaxBands)
                        val id = "autoeq:${fields[0]}"
                        val preset = OpraHeadphoneCorrectionPreset(id, id, name, "AutoEQ", "$source · $measurementRig",
                            "AutoEQ · ${fields[1]}",
                            "https://github.com/jaakkopasanen/AutoEq/blob/${revision ?: error("autoeq_missing_revision")}/results/" +
                                listOf(source, measurementRig, name, "$name ParametricEQ.txt").joinToString("/") {
                                    URLEncoder.encode(it, "UTF-8").replace("+", "%20")
                                }, preamp, bands)
                        entries += AutoEqEntry(OpraHeadphoneCorrectionProduct(id, name, "AutoEQ", fields[1], listOf(preset)),
                            source, OpraDatabaseParser.normalizeSearchText("$name $source"))
                    }
                }
            } }
            require(expected == entries.size && expected > 0 && revision != null) { "autoeq_incomplete_catalog" }
            return AutoEqCatalog(revision, entries)
        }
    }
}
