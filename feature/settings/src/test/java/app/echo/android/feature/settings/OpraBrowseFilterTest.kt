package app.echo.android.feature.settings

import app.echo.android.model.playback.OpraHeadphoneBrand
import app.echo.android.model.playback.OpraHeadphoneCorrectionPreset
import app.echo.android.model.playback.OpraHeadphoneCorrectionProduct
import org.junit.Assert.*
import org.junit.Test

class OpraBrowseFilterTest {
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
