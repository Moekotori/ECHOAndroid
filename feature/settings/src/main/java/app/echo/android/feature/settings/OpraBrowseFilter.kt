package app.echo.android.feature.settings

import app.echo.android.model.playback.OpraHeadphoneBrand
import app.echo.android.model.playback.OpraHeadphoneCorrectionProduct
import app.echo.android.model.playback.EchoEqualizerState
import app.echo.android.model.playback.EchoEqualizerUserPreset
import app.echo.android.model.playback.OpraHeadphoneCorrectionPreset
import kotlin.math.abs
import java.util.Locale

private fun compactOpraSearch(value: String): String =
    value.filter(Char::isLetterOrDigit).lowercase(Locale.ROOT)

internal fun EchoEqualizerState.matchesCorrection(preset: OpraHeadphoneCorrectionPreset): Boolean =
    enabled && parametric && sourceLabel == preset.displayName && filters == preset.bands && abs(preampDb - preset.preampDb) < 0.05f

internal fun EchoEqualizerState.matchesCorrection(preset: EchoEqualizerUserPreset): Boolean =
    enabled && parametric && preset.parametric && sourceLabel == preset.sourceLabel &&
        filters == preset.filters && abs(preampDb - preset.preampDb) < 0.05f

internal fun filterCorrectionFavorites(presets: List<EchoEqualizerUserPreset>, query: String): List<EchoEqualizerUserPreset> {
    val tokens = query.split(Regex("\\s+")).map(::compactOpraSearch).filter(String::isNotEmpty)
    if (query.isBlank()) return presets
    if (tokens.isEmpty()) return emptyList()
    return presets.filter { preset ->
        val key = compactOpraSearch(preset.name + " " + preset.sourceLabel.orEmpty())
        tokens.all { it in key }
    }
}

internal fun filterOpraBrands(brands: List<OpraHeadphoneBrand>, query: String): List<OpraHeadphoneBrand> {
    val needle = compactOpraSearch(query)
    if (query.isBlank()) return brands
    if (needle.isEmpty()) return emptyList()
    return brands.filter { needle in compactOpraSearch(it.name) || needle in compactOpraSearch(it.id) }
}

/** Scoped catalogs are already loaded; filter them off the UI thread without another request. */
internal fun filterOpraModels(
    products: List<OpraHeadphoneCorrectionProduct>, query: String, scoped: Boolean,
): List<OpraHeadphoneCorrectionProduct> {
    if (!scoped || query.isBlank()) return products
    val tokens = query.split(Regex("\\s+")).map(::compactOpraSearch).filter(String::isNotEmpty)
    if (tokens.isEmpty()) return emptyList()
    return products.filter { product ->
        val key = compactOpraSearch(product.productName + " " + product.vendorName + " " +
            product.presets.joinToString(" ") { it.author })
        tokens.all { it in key }
    }
}
