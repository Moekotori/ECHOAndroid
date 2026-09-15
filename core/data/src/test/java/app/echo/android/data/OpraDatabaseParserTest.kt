package app.echo.android.data

import app.echo.android.model.playback.EchoEqFilterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpraDatabaseParserTest {
    @Test
    fun brandBrowseIncludesOnlyCorrectableModelsAndDoesNotTruncate() {
        val original = OpraDatabaseParser.parse(SampleDatabase, "fixture")
        val product = original.products.getValue("sennheiser::hd650")
        val eq = original.eqsByProductId.getValue(product.id)
        val products = (1..25).associate { index -> "model-$index" to product.copy(id = "model-$index", name = "Model $index") }
        val database = original.copy(products = original.products + products,
            eqsByProductId = original.eqsByProductId + products.mapValues { eq })
        val brands = OpraDatabaseParser.brands(database)
        assertEquals(26, brands.single().productCount)
        assertEquals(26, OpraDatabaseParser.browse(database, brands.single().id).size)
        assertEquals("HD 650", OpraDatabaseParser.browse(database, "sennheiser", "hd650").single().productName)
        assertTrue(OpraDatabaseParser.browse(database, "missing").isEmpty())
        assertTrue(OpraDatabaseParser.browse(database, "sennheiser", "HD 800").isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesEmptyDatabase() { OpraDatabaseParser.parse("", "fixture") }

    @Test(expected = org.json.JSONException::class)
    fun refusesTruncatedCacheInsteadOfReturningPartialResults() {
        OpraDatabaseParser.parse(SampleDatabase + "\n{", "fixture")
    }

    @Test
    fun parsesParametricEqAndSkipsProductsWithoutCurves() {
        val database = OpraDatabaseParser.parse(SampleDatabase, "fixture")
        assertEquals(1, database.status.vendorCount)
        assertEquals(2, database.status.productCount)
        assertEquals(1, database.status.eqCount)

        val matches = OpraDatabaseParser.search(database, "HD 650")
        assertEquals(1, matches.size)
        val preset = matches.single().presets.single()
        assertEquals("Sennheiser", preset.vendorName)
        assertEquals("HD 650", preset.productName)
        assertEquals(-6.4f, preset.preampDb, 0.01f)
        assertEquals(EchoEqFilterType.PeakDip, preset.bands[1].type)
        assertEquals(21f, preset.bands[1].frequencyHz, 0.01f)
        assertTrue(OpraDatabaseParser.search(database, "missing model").isEmpty())
        assertTrue(OpraDatabaseParser.search(database, "HD 800").isEmpty())
    }

    @Test
    fun searchMatchesCollapsedModelTokens() {
        val database = OpraDatabaseParser.parse(SampleDatabase, "fixture")
        val matches = OpraDatabaseParser.search(database, "hd650")
        assertEquals("HD 650", matches.single().productName)
    }

    @Test
    fun vendorLogoUrlUsesContentAddressedOpraAssetsAndIgnoresUnsafePaths() {
        val database = OpraDatabaseParser.parse(SampleDatabaseWithLogo, "fixture")
        assertEquals(
            "https://opra.roonlabs.net/assets/8c/c5/8cc5dcc3248e4e9578656cca184e7cbbbad84a97a83132bfc22ed59e8f4d86b6.png",
            OpraDatabaseParser.brands(database).single().logoUrl,
        )
        assertEquals(null, OpraAssetUrls.url("https://evil.example/logo.png"))
        assertEquals(null, OpraAssetUrls.url("assets/../secret.png"))
        assertEquals(null, OpraAssetUrls.url("assets/8c/c5/not-a-hash.png"))
        assertEquals(null, OpraDatabaseParser.parse(SampleDatabase, "fixture").let { OpraDatabaseParser.brands(it).single().logoUrl })
    }
}

private const val SampleDatabase = """
{"type":"vendor","id":"sennheiser","data":{"name":"Sennheiser"}}
{"type":"product","id":"sennheiser::hd650","data":{"name":"HD 650","vendor_id":"sennheiser","subtype":"over_the_ear"}}
{"type":"product","id":"sennheiser::hd800","data":{"name":"HD 800","vendor_id":"sennheiser","subtype":"over_the_ear"}}
{"type":"eq","id":"sennheiser:hd650::autoeq_oratory","data":{"author":"AutoEQ","details":"oratory1990","type":"parametric_eq","product_id":"sennheiser::hd650","parameters":{"gain_db":-6.4,"bands":[{"type":"low_shelf","frequency":105,"gain_db":6.2,"q":0.7},{"type":"peak_dip","frequency":21,"gain_db":-8.8,"q":0.44},{"type":"high_shelf","frequency":10000,"gain_db":-1.2,"q":0.7}]}}}
"""

private const val SampleDatabaseWithLogo = """
{"type":"vendor","id":"sennheiser","data":{"name":"Sennheiser","logo":"assets/8c/c5/8cc5dcc3248e4e9578656cca184e7cbbbad84a97a83132bfc22ed59e8f4d86b6.png"}}
{"type":"product","id":"sennheiser::hd650","data":{"name":"HD 650","vendor_id":"sennheiser","subtype":"over_the_ear"}}
{"type":"eq","id":"sennheiser:hd650::autoeq_oratory","data":{"author":"AutoEQ","details":"oratory1990","type":"parametric_eq","product_id":"sennheiser::hd650","parameters":{"gain_db":-6.4,"bands":[{"type":"low_shelf","frequency":105,"gain_db":6.2,"q":0.7},{"type":"peak_dip","frequency":21,"gain_db":-8.8,"q":0.44},{"type":"high_shelf","frequency":10000,"gain_db":-1.2,"q":0.7}]}}}
"""
