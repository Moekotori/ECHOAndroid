package app.echo.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryHygienePolicyTest {
    @Test
    fun reportsMissingUris() {
        val missing = LibraryHygienePolicy.missingIds(
            listOf("a" to "content://a", "b" to "content://b", "c" to ""),
            exists = { it == "content://a" },
        )
        assertEquals(listOf("b"), missing)
    }

    @Test
    fun keepsMediaStoreIdWhenMergingDuplicates() {
        val groups = LibraryHygienePolicy.duplicateGroups(
            listOf(
                "saf:one" to "fp",
                "mediastore:2" to "fp",
                "saf:two" to "fp",
                "mediastore:9" to "other",
            ),
        )
        assertEquals(1, groups.size)
        assertEquals("mediastore:2", groups.single().keepId)
        assertEquals(listOf("saf:one", "saf:two"), groups.single().removeIds)
    }
}
