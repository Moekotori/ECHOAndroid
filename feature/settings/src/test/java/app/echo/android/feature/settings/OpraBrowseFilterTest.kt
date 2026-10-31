package app.echo.android.feature.settings

import app.echo.android.model.playback.OpraHeadphoneBrand
import app.echo.android.model.playback.OpraHeadphoneCorrectionPreset
import app.echo.android.model.playback.OpraHeadphoneCorrectionProduct
import app.echo.android.model.playback.EchoEqualizerState
import app.echo.android.model.playback.EchoEqualizerUserPreset
import app.echo.android.model.playback.OpraEqBand
import org.junit.Assert.*
import org.junit.Test

class OpraBrowseFilterTest {
    @Test fun appliedIndicatorRequiresEnabledMatchingFiltersAndPreamp() {
        val preset = hd650.presets.single().copy(bands = listOf(OpraEqBand("peak_dip", 1000f, 3f, 1f, null)), preampDb = -6f)
        val state = EchoEqualizerState(enabled = true, parametric = true, filters = preset.bands,
            preampDb = preset.preampDb, sourceLabel = preset.displayName)
        assertTrue(state.matchesCorrection(preset))
        assertFalse(state.copy(enabled = false).matchesCorrection(preset))
        assertFalse(state.copy(preampDb = -3f).matchesCorrection(preset))
        assertFalse(state.copy(filters = emptyList()).matchesCorrection(preset))
    }

    @Test fun favoritesCanBeFoundByModelAndMeasurementSource() {
        val saved = EchoEqualizerUserPreset("favorite", "HD 650", sourceLabel = "AutoEQ / Sennheiser HD650 / oratory1990")
        assertEquals(listOf(saved), filterCorrectionFavorites(listOf(saved), "HD-650 oratory"))
        assertTrue(filterCorrectionFavorites(listOf(saved), "AirPods").isEmpty())
        assertTrue(filterCorrectionFavorites(listOf(saved), "---").isEmpty())
    }

    private val hd650 = model("hd650", "HD 650", "Sennheiser", "oratory1990")
    private val hd600 = model("hd600", "HD 600", "Sennheiser", "Crinacle")

    @Test fun scopedSearchAcceptsSpacingAndPunctuationAndMultipleTokens() {
        val products = listOf(hd600, hd650)
        assertEquals(listOf(hd650), filterOpraModels(products, "HD-650 oratory", true))
        assertEquals(products, filterOpraModels(products, "sennheiser", true))
        assertTrue(filterOpraModels(products, "650 crinacle", true).isEmpty())
        assertTrue(filterOpraModels(products, "---", true).isEmpty())
    }

    @Test fun globalSearchKeepsRepositoryMatchesAndBlankScopedSearchKeepsOrder() {
        val products = listOf(hd650, hd600)
        assertSame(products, filterOpraModels(products, "measurement source", false))
        assertSame(products, filterOpraModels(products, "  ", true))
    }

    @Test fun brandsSupportCompactNamesWithoutMatchingPunctuationOnly() {
        val brands = listOf(OpraHeadphoneBrand("64audio", "64 Audio", 68))
        assertEquals(brands, filterOpraBrands(brands, "64-audio"))
        assertSame(brands, filterOpraBrands(brands, " "))
        assertTrue(filterOpraBrands(brands, "---").isEmpty())
    }

    private fun model(id: String, name: String, brand: String, author: String) =
        OpraHeadphoneCorrectionProduct(id, name, brand, null, listOf(
            OpraHeadphoneCorrectionPreset(id, id, name, brand, author, null, null, 0f, emptyList()),
        ))
}
