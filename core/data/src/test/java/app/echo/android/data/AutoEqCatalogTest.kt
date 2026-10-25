package app.echo.android.data

import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import org.junit.Assert.*
import org.junit.Test
import app.echo.android.model.playback.EchoParametricEq

class AutoEqCatalogTest {
    @Test fun bundledCatalogLoadsOfflineAndPreservesTheUpstreamFilters() {
        val catalog = AutoEqCatalog.read(File("src/main/assets/autoeq/catalog-v1.tsv.gz").inputStream())
        assertEquals(8850, catalog.status.eqCount)
        assertEquals("7ae0f56d53074872b028649617a22bbb4232feb7", catalog.revision)
        val profile = catalog.search("HD650", "Auriculares Argentina").single().presets.single()
        assertEquals("Sennheiser HD 650", profile.productName)
        assertEquals(-6.4f, profile.preampDb, 0f)
        assertEquals(10, profile.bands.size)
        assertEquals(7.1f, profile.bands.first().gainDb, 0f)
        assertEquals(profile.bands, EchoParametricEq.sanitize(profile.bands))
        assertTrue(profile.eqId.startsWith("autoeq:"))
        assertTrue(catalog.search("", null).isEmpty())
        assertTrue(catalog.search("unmatched-headphone", null).isEmpty())
        assertTrue(catalog.search("", "crinacle").size > 16)
        val variants = catalog.search("64 Audio Nio m20 Apex module", "crinacle")
            .single { it.productName == "64 Audio Nio (m20 Apex module)" }.presets
        assertEquals(2, variants.size)
        assertEquals(2, variants.map { it.author }.distinct().size) // Distinguish 711/5128 measurement rigs.
        // Every imported profile must survive the exact normalization used by the PEQ editor.
        catalog.brands.forEach { source -> catalog.search("", source.id).forEach { product ->
            product.presets.forEach { assertEquals(it.bands, EchoParametricEq.sanitize(it.bands)) }
        } }
    }

    @Test fun refusesTruncatedOrPartiallyUnsupportedCatalogs() {
        val header = "# revision=7ae0f56d53074872b028649617a22bbb4232feb7\n# count=1\n"
        val row = "12345678901234567890\tover-ear\tc291cmNl\tbW9kZWw=\t-6.0\tP,1000,NaN,1\tb3Zlci1lYXI=\n"
        for (text in listOf(header, header + row, (header + row).replace("NaN", "Infinity"),
            (header + row).replace("P,1000,NaN,1", "X,1000,1,1"))) {
            val bytes = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(text.toByteArray()) } }.toByteArray()
            assertThrows(IllegalArgumentException::class.java) { AutoEqCatalog.read(ByteArrayInputStream(bytes)) }
        }
    }
}
