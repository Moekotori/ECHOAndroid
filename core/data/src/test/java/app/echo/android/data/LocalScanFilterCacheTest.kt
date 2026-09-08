package app.echo.android.data

import app.echo.android.model.library.LibraryScanOptions
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalScanFilterCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    private val options = LibraryScanOptions(minSizeBytes = 0)

    @Test fun unchangedRejectionIsReusedButFileChangesInvalidateIt() {
        val cache = LocalScanFilterCache()
        cache.remember("file", 500_000, 100, 1000)
        assertTrue(cache.shouldSkip("file", 500_000, 100, options))
        assertFalse(cache.shouldSkip("file", 500_000, 101, options))
        cache.remember("file", 500_000, 100, 1000)
        assertFalse(cache.shouldSkip("file", 500_001, 100, options))
        assertFalse(cache.shouldSkip("other-uri", 500_000, 100, options))
    }

    @Test fun relaxedFiltersReconsiderPreviouslyRejectedFiles() {
        val cache = LocalScanFilterCache()
        cache.remember("file", 500_000, 100, 1000)
        assertFalse(cache.shouldSkip("file", 500_000, 100, options.copy(minDurationMs = 0)))
    }

    @Test fun cacheSurvivesRestartAndRemainsBounded() {
        val file = temporary.newFile("rejections.json")
        val cache = LocalScanFilterCache(file, capacity = 2)
        cache.remember("first", 500_000, 100, 1000)
        cache.remember("second", 500_000, 100, 1000)
        cache.remember("third", 500_000, 100, 1000)
        cache.flush()
        val restored = LocalScanFilterCache(file, capacity = 2)
        assertFalse(restored.shouldSkip("first", 500_000, 100, options))
        assertTrue(restored.shouldSkip("second", 500_000, 100, options))
        assertTrue(restored.shouldSkip("third", 500_000, 100, options))
    }

    @Test fun unreliableMetadataAndCorruptCacheNeverHideFiles() {
        val file = temporary.newFile("invalid.json").apply { writeText("broken") }
        val cache = LocalScanFilterCache(file)
        cache.remember("file", 500_000, 0, 1000)
        assertFalse(cache.shouldSkip("file", 500_000, 0, options))
    }

    @Test fun reconciliationOnlySelectsPotentialSameFileSnapshots() {
        fun fp(id: String, path: String, size: Long = 42) = TrackFingerprint(id, "content://$id", null, "fp", size, 100, path)
        val document = fp("saf:one", "Music/")
        val match = fp("mediastore:one", "Music/")
        assertEquals(listOf(match), documentDuplicateCandidates(sequenceOf(match,
            fp("mediastore:other-dir", "Other/"), fp("mediastore:other-size", "Music/", 43)), listOf(document)))
        assertTrue(documentDuplicateCandidates(sequence { error("Must not enumerate the library without SAF rows") }, emptyList()).isEmpty())
    }
}
