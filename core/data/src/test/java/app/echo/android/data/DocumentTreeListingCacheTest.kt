package app.echo.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DocumentTreeListingCacheTest {
    @get:Rule val temporary = TemporaryFolder()

    private val child = DocumentTreeCachedChild(
        documentId = "primary:Music/Album",
        name = "Album",
        mimeType = "vnd.android.document/directory",
        sizeBytes = 0L,
        lastModifiedMs = 50L,
    )

    @Test
    fun matchingNestedMtimeReusesChildrenUntilTheDirectoryChanges() {
        val cache = DocumentTreeListingCache()
        cache.remember("tree", "primary:Music/Album", 50L, isTreeRoot = false, listOf(child))
        assertEquals(listOf(child), cache.listing("tree", "primary:Music/Album", 50L, isTreeRoot = false))
        assertNull(cache.listing("tree", "primary:Music/Album", 51L, isTreeRoot = false))
        assertNull(cache.listing("tree", "primary:Music/Album", 50L, isTreeRoot = false))
    }

    @Test
    fun rootAndUnknownMtimeNeverHideALiveListing() {
        val cache = DocumentTreeListingCache()
        cache.remember("tree", "primary:Music", 50L, isTreeRoot = true, listOf(child))
        assertNull(cache.listing("tree", "primary:Music", 50L, isTreeRoot = true))
        cache.remember("tree", "primary:Music/Album", 0L, isTreeRoot = false, listOf(child))
        assertNull(cache.listing("tree", "primary:Music/Album", 0L, isTreeRoot = false))
    }

    @Test
    fun cacheSurvivesRestartAndDropsCorruptState() {
        val file = temporary.newFile("listings.json")
        val cache = DocumentTreeListingCache(file, capacity = 2)
        cache.remember("tree", "one", 1L, false, listOf(child.copy(documentId = "one", name = "One")))
        cache.remember("tree", "two", 2L, false, listOf(child.copy(documentId = "two", name = "Two")))
        cache.remember("tree", "three", 3L, false, listOf(child.copy(documentId = "three", name = "Three")))
        cache.flush()
        val restored = DocumentTreeListingCache(file, capacity = 2)
        assertNull(restored.listing("tree", "one", 1L, false))
        assertEquals("Two", restored.listing("tree", "two", 2L, false)?.single()?.name)
        assertEquals("Three", restored.listing("tree", "three", 3L, false)?.single()?.name)

        file.writeText("broken")
        val corrupt = DocumentTreeListingCache(file)
        assertNull(corrupt.listing("tree", "two", 2L, false))
    }

    @Test
    fun treesDoNotShareListings() {
        val cache = DocumentTreeListingCache()
        cache.remember("tree-a", "primary:Music/Album", 50L, false, listOf(child))
        assertNull(cache.listing("tree-b", "primary:Music/Album", 50L, false))
        assertTrue(cache.listing("tree-a", "primary:Music/Album", 50L, false)?.isNotEmpty() == true)
    }
}
