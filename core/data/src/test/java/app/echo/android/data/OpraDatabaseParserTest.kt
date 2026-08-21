package app.echo.android.data

import app.echo.android.model.playback.EchoEqFilterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpraDatabaseParserTest {
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
}

private const val SampleDatabase = """
{"type":"vendor","id":"sennheiser","data":{"name":"Sennheiser"}}
{"type":"product","id":"sennheiser::hd650","data":{"name":"HD 650","vendor_id":"sennheiser","subtype":"over_the_ear"}}
{"type":"product","id":"sennheiser::hd800","data":{"name":"HD 800","vendor_id":"sennheiser","subtype":"over_the_ear"}}
{"type":"eq","id":"sennheiser:hd650::autoeq_oratory","data":{"author":"AutoEQ","details":"oratory1990","type":"parametric_eq","product_id":"sennheiser::hd650","parameters":{"gain_db":-6.4,"bands":[{"type":"low_shelf","frequency":105,"gain_db":6.2,"q":0.7},{"type":"peak_dip","frequency":21,"gain_db":-8.8,"q":0.44},{"type":"high_shelf","frequency":10000,"gain_db":-1.2,"q":0.7}]}}}
"""
