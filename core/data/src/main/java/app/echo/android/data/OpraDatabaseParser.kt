package app.echo.android.data

import app.echo.android.model.playback.EchoEqFilterType
import app.echo.android.model.playback.OpraDatabaseStatus
import app.echo.android.model.playback.OpraEqBand
import app.echo.android.model.playback.OpraHeadphoneCorrectionPreset
import app.echo.android.model.playback.OpraHeadphoneCorrectionProduct
import java.text.Normalizer
import org.json.JSONObject

internal data class OpraVendor(
    val id: String,
    val name: String,
)

internal data class OpraProduct(
    val id: String,
    val vendorId: String,
    val name: String,
    val subtype: String?,
)

internal data class OpraEq(
    val id: String,
    val productId: String,
    val author: String,
    val details: String?,
    val link: String?,
    val preampDb: Float,
    val bands: List<OpraEqBand>,
)

internal data class OpraDatabase(
    val vendors: Map<String, OpraVendor>,
    val products: Map<String, OpraProduct>,
    val eqsByProductId: Map<String, List<OpraEq>>,
    val status: OpraDatabaseStatus,
)

internal object OpraDatabaseParser {
    fun parse(rawText: String, source: String): OpraDatabase = parseLines(rawText.lineSequence(), source)

    fun parseLines(lines: Sequence<String>, source: String): OpraDatabase {
        val vendors = mutableMapOf<String, OpraVendor>()
        val products = mutableMapOf<String, OpraProduct>()
        val eqsByProductId = mutableMapOf<String, MutableList<OpraEq>>()
        var eqCount = 0

        lines.forEach { line ->
            if (line.isBlank()) return@forEach
            val record = JSONObject(line) // Reject truncated/HTML cache files instead of reporting an empty database.
            runCatching {
                val id = record.optTrimmedString("id") ?: return@runCatching
                val data = record.optJSONObject("data") ?: return@runCatching
                when (record.optTrimmedString("type")) {
                    "vendor" -> {
                        vendors[id] = OpraVendor(
                            id = id,
                            name = data.optTrimmedString("name") ?: id,
                        )
                    }
                    "product" -> {
                        val vendorId = data.optTrimmedString("vendor_id") ?: return@runCatching
                        val name = data.optTrimmedString("name") ?: return@runCatching
                        products[id] = OpraProduct(
                            id = id,
                            vendorId = vendorId,
                            name = name,
                            subtype = data.optTrimmedString("subtype"),
                        )
                    }
                    "eq" -> {
                        if (data.optTrimmedString("type") != "parametric_eq") return@runCatching
                        val productId = data.optTrimmedString("product_id") ?: return@runCatching
                        val parameters = data.optJSONObject("parameters") ?: return@runCatching
                        val bands = parseBands(parameters) ?: return@runCatching
                        val preamp = parameters.optFloat("gain_db") ?: return@runCatching
                        if (preamp !in -24f..12f) return@runCatching
                        val eq = OpraEq(
                            id = id,
                            productId = productId,
                            author = data.optTrimmedString("author") ?: "OPRA",
                            details = data.optTrimmedString("details"),
                            link = data.optTrimmedString("link"),
                            preampDb = preamp,
                            bands = bands,
                        )
                        eqsByProductId.getOrPut(productId) { mutableListOf() }.add(eq)
                        eqCount += 1
                    }
                }
            }
        }

        require(vendors.isNotEmpty() && products.isNotEmpty() && eqCount > 0) { "opra_invalid_database" }
        return OpraDatabase(
            vendors = vendors,
            products = products,
            eqsByProductId = eqsByProductId,
            status = OpraDatabaseStatus(
                source = source,
                vendorCount = vendors.size,
                productCount = products.size,
                eqCount = eqCount,
            ),
        )
    }

    fun search(
        database: OpraDatabase,
        query: String,
        limit: Int = 16,
    ): List<OpraHeadphoneCorrectionProduct> {
        val tokens = normalizeSearchText(query).split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        return database.products.values
            .mapNotNull { product ->
                val vendor = database.vendors[product.vendorId] ?: OpraVendor(product.vendorId, product.vendorId)
                val score = scoreProduct(tokens, product, vendor)
                if (score < 0) {
                    null
                } else {
                    val result = createProductResult(database, product, vendor)
                    if (result.presets.isEmpty()) {
                        null
                    } else {
                        score to result
                    }
                }
            }
            .sortedWith(
                compareByDescending<Pair<Int, OpraHeadphoneCorrectionProduct>> { it.first }
                    .thenBy { it.second.productName },
            )
            .map { it.second }
            .take(limit)
    }

    private fun parseBands(parameters: JSONObject): List<OpraEqBand>? {
        val array = parameters.optJSONArray("bands") ?: return null
        if (array.length() !in 1..32) return null
        val output = ArrayList<OpraEqBand>(array.length())
        for (index in 0 until array.length()) {
            val band = array.optJSONObject(index) ?: return null
            val type = EchoEqFilterType.normalize(band.optTrimmedString("type") ?: return null)
            val frequency = band.optFloat("frequency") ?: return null
            val gain = band.optFloat("gain_db") ?: 0f
            val q = band.optFloat("q")
            val slope = band.optFloat("slope")
            if (!frequency.isFinite() || frequency <= 0f || !gain.isFinite() || gain !in -60f..36f) return null
            when (type) {
                EchoEqFilterType.LowPass, EchoEqFilterType.HighPass ->
                    if ((slope ?: 12f) !in listOf(6f, 12f, 18f, 24f, 30f, 36f)) return null
                EchoEqFilterType.PeakDip, EchoEqFilterType.LowShelf, EchoEqFilterType.HighShelf,
                EchoEqFilterType.BandPass, EchoEqFilterType.BandStop ->
                    if (q == null || !q.isFinite() || q !in 0.1f..100f) return null
                else -> return null // Never apply only the recognized part of a correction.
            }
            output.add(OpraEqBand(type, frequency, gain, q, slope))
        }
        return output
    }

    private fun createProductResult(
        database: OpraDatabase,
        product: OpraProduct,
        vendor: OpraVendor,
    ): OpraHeadphoneCorrectionProduct {
        val presets = database.eqsByProductId[product.id]
            .orEmpty()
            .map { eq ->
                OpraHeadphoneCorrectionPreset(
                    eqId = eq.id,
                    productId = product.id,
                    productName = product.name,
                    vendorName = vendor.name,
                    author = eq.author,
                    details = eq.details,
                    sourceUrl = eq.link,
                    preampDb = eq.preampDb,
                    bands = eq.bands,
                )
            }
        return OpraHeadphoneCorrectionProduct(
            productId = product.id,
            productName = product.name,
            vendorName = vendor.name,
            subtype = product.subtype,
            presets = presets,
        )
    }

    private fun scoreProduct(tokens: List<String>, product: OpraProduct, vendor: OpraVendor): Int {
        val haystack = normalizeSearchText(
            "${vendor.name} ${product.name} ${product.id.replace('_', ' ').replace("::", " ")}",
        )
        val compact = haystack.replace(" ", "")
        if (!tokens.all { haystack.contains(it) || compact.contains(it) }) return -1
        val productName = normalizeSearchText(product.name)
        val vendorName = normalizeSearchText(vendor.name)
        return tokens.sumOf { token ->
            when {
                productName == token || vendorName == token -> 120
                productName.startsWith(token) || vendorName.startsWith(token) -> 70
                else -> 20
            }
        }
    }

    internal fun normalizeSearchText(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKD)
            .replace(Regex("[\\u0300-\\u036f]"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9\\u4e00-\\u9fff]+"), " ")
            .trim()
}

internal fun JSONObject.optTrimmedString(name: String): String? =
    optString(name).trim().takeIf { it.isNotBlank() }

internal fun JSONObject.optFloat(name: String): Float? {
    if (!has(name) || isNull(name)) return null
    val value = optDouble(name, Double.NaN)
    return value.takeIf { it.isFinite() }?.toFloat()?.takeIf { it.isFinite() }
}
