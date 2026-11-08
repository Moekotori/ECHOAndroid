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
        val catalog = AutoEqCatalog.read(File("src/main/assets/autoeq/catalog-v1.tsv.gzip").inputStream(), manufacturers())
        assertEquals(8850, catalog.status.eqCount)
        assertEquals(6038, catalog.status.productCount)
        assertEquals(659, catalog.status.vendorCount)
        assertEquals("7ae0f56d53074872b028649617a22bbb4232feb7", catalog.revision)
        val hd650 = catalog.search("HD650", "Sennheiser").single { it.productName == "Sennheiser HD 650" }
        assertEquals("Sennheiser", hd650.vendorName)
        assertEquals(7, hd650.presets.size)
        val profile = hd650.presets.single { it.author == "Auriculares Argentina · over-ear" }
        assertEquals("Sennheiser HD 650", profile.productName)
        assertEquals(-6.4f, profile.preampDb, 0f)
        assertEquals(10, profile.bands.size)
        assertEquals(7.1f, profile.bands.first().gainDb, 0f)
        assertEquals(profile.bands, EchoParametricEq.sanitize(profile.bands))
        assertEquals("autoeq:2b4d56b84eabcb473e5f", profile.eqId)
        assertEquals("AutoEQ / Sennheiser HD 650 / Auriculares Argentina · over-ear", profile.displayName)
        assertTrue(catalog.search("", null).isEmpty())
        assertTrue(catalog.search("unmatched-headphone", null).isEmpty())
        assertTrue(catalog.search("", "Sennheiser").size > 16)
        assertFalse(catalog.brands.any { it.id == "crinacle" || it.id == "Rtings" })
        val variants = catalog.search("64 Audio Nio m20 Apex module", "64 Audio")
            .single { it.productName == "64 Audio Nio (m20 Apex module)" }.presets.filter { it.author.startsWith("crinacle ·") }
        assertEquals(2, variants.size)
        assertEquals(2, variants.map { it.author }.distinct().size) // Distinguish 711/5128 measurement rigs.
        // Every imported profile must survive the exact normalization used by the PEQ editor.
        val products = catalog.brands.flatMap { brand ->
            catalog.search("", brand.id).also { assertEquals(brand.productCount, it.size) }
        }
        assertEquals(catalog.status.productCount, products.size)
        assertEquals(catalog.status.eqCount, products.sumOf { it.presets.size })
        assertEquals(catalog.status.eqCount, products.flatMap { it.presets }.map { it.eqId }.distinct().size)
        assertTrue(catalog.search("HD650 Rtings", null).any { it.productId == hd650.productId })
        products.forEach { product ->
            product.presets.forEach { assertEquals(it.bands, EchoParametricEq.sanitize(it.bands)) }
        }
    }

    @Test fun manufacturerIndexPreservesMultiwordBrandsAndCanonicalAliases() {
        val index = manufacturers()
        assertEquals("64 Audio", index.brandFor("64 Audio Nio"))
        assertEquals("Dan Clark Audio", index.brandFor("Dan Clark Audio Stealth"))
        assertEquals("Dan Clark Audio", index.brandFor("MrSpeakers Aeon"))
        assertEquals("KBEAR", index.brandFor("KBEar KB01"))
        assertEquals("Alpha & Delta", index.brandFor("Alpha & Delta D6"))
        assertEquals("Alpha Design Labs", index.brandFor("Alpha Design Labs H118"))
        assertThrows(IllegalStateException::class.java) { index.brandFor("SennheiserUnknown Model") }
    }

    @Test fun refusesTruncatedOrPartiallyUnsupportedCatalogs() {
        val header = "# revision=7ae0f56d53074872b028649617a22bbb4232feb7\n# count=1\n"
        val row = "12345678901234567890\tover-ear\tc291cmNl\tU2VubmhlaXNlciBtb2RlbA==\t-6.0\tP,1000,NaN,1\tb3Zlci1lYXI=\n"
        for (text in listOf(header, header + row, (header + row).replace("NaN", "Infinity"),
            (header + row).replace("P,1000,NaN,1", "X,1000,1,1"))) {
            val bytes = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(text.toByteArray()) } }.toByteArray()
            assertThrows(IllegalArgumentException::class.java) { AutoEqCatalog.read(ByteArrayInputStream(bytes), manufacturers()) }
        }
    }

    private fun manufacturers() = AutoEqManufacturers(File("src/main/assets/autoeq/manufacturers.tsv").inputStream())
}
