package app.echo.android.feature.library

import app.echo.android.model.library.LibraryTrackFilter
import app.echo.android.model.library.LibraryPresenceFilter
import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.*
import org.junit.Test

class LibraryTrackFilterDraftTest {
    @Test fun saveAndRestoreRetainsLyricsFiltersPreciseRangesAndIncompleteEdits() {
        val filter = LibraryTrackFilter(lyrics = LibraryPresenceFilter.Missing, minSizeBytes = 1, minDurationMs = 1_501)
        val initial = LibraryTrackFilterDraft(filter)
        val scope = object : SaverScope { override fun canBeSaved(value: Any): Boolean = true }
        val saved = requireNotNull(with(LibraryTrackFilterDraftSaver) { scope.save(initial) })
        val restored = requireNotNull(LibraryTrackFilterDraftSaver.restore(saved))
        assertEquals(filter, restored.applied())
        assertEquals(LibraryPresenceFilter.Any, LibraryTrackFilterDraftSaver.restore(saved.take(18))?.filter?.lyrics)
        assertNull(LibraryTrackFilterDraftSaver.restore(listOf("truncated")))
        val invalid = initial.copy(ranges = initial.ranges.toMutableList().also { it[0] = "." })
        val badSaved = requireNotNull(with(LibraryTrackFilterDraftSaver) { scope.save(invalid) })
        assertNull(LibraryTrackFilterDraftSaver.restore(badSaved)?.applied())
    }

    @Test fun openingAndApplyingKeepsEveryByteAndMillisecondIncludingLargeValues() {
        val filter = LibraryTrackFilter(minDurationMs = 1_501, maxDurationMs = Long.MAX_VALUE,
            minSizeBytes = 1, maxSizeBytes = Long.MAX_VALUE)
        val draft = LibraryTrackFilterDraft(filter)
        assertEquals(filter, draft.applied())
        assertEquals(filter.copy(artist = "Artist"), draft.copy(filter = filter.copy(artist = "Artist")).applied())
    }

    @Test fun decimalBoundsAndCommaKeyboardsUseTheCorrectIntegerComparison() {
        val draft = LibraryTrackFilterDraft(LibraryTrackFilter(),
            listOf("", "", "1,501", "2.5", ".1", "0.2", "", ""))
        val result = requireNotNull(draft.applied())
        assertEquals(1_501L, result.minDurationMs)
        assertEquals(2_500L, result.maxDurationMs)
        assertEquals(104_858L, result.minSizeBytes)
        assertEquals(209_715L, result.maxSizeBytes)
        assertNull(draft.copy(ranges = draft.ranges.toMutableList().also { it[0] = "2026.5" }).applied())
        assertNull(draft.copy(ranges = draft.ranges.toMutableList().also { it[2] = "1e99999999" }).applied())
    }

    @Test fun shortcutsOnlyReplaceTheirOwnRangeAndKeepOtherConditions() {
        val original = LibraryTrackFilterDraft(LibraryTrackFilter(artist = "Artist", favoritesOnly = true,
            minYear = 2000, minSampleRateHz = 44_100, maxSampleRateHz = 44_100))
        val next = original.withRange(1, 180, 360)
        val applied = requireNotNull(next.applied())
        assertEquals(180_000L, applied.minDurationMs)
        assertEquals(360_000L, applied.maxDurationMs)
        assertEquals(44_100, applied.minSampleRateHz)
        assertEquals(2000, applied.minYear)
        assertEquals("Artist", applied.artist)
        assertTrue(applied.favoritesOnly)
        assertEquals(1, next.tagCount)
        assertEquals(1, next.conditionCount)
        assertEquals(3, next.rangeCount)
        assertNull(next.withRange(1, null, null).applied()?.minDurationMs)
    }

    @Test fun incompleteReversedAndOverflowingRangesCannotReachTheQuery() {
        val draft = LibraryTrackFilterDraft(LibraryTrackFilter())
        for ((index, value) in listOf(0 to "-1", 0 to "year", 0 to "2147483648", 2 to Long.MAX_VALUE.toString(),
            4 to Long.MAX_VALUE.toString(), 6 to "2147483648")) {
            assertNull(draft.copy(ranges = draft.ranges.toMutableList().also { it[index] = value }).applied())
        }
        assertNull(draft.copy(ranges = listOf("2026", "2000", "", "", "", "", "", "")).applied())
    }

    @Test fun blankBoundsStayUnlimitedAndUnitsConvertOnlyWhenApplied() {
        val draft = LibraryTrackFilterDraft(LibraryTrackFilter(artist = " artist "),
            ranges = listOf("2000", "", "60", "300", "1", "50", "44100", "96000"))
        val result = requireNotNull(draft.applied())
        assertEquals(60_000L, result.minDurationMs)
        assertEquals(300_000L, result.maxDurationMs)
        assertEquals(1_048_576L, result.minSizeBytes)
        assertNull(result.maxYear)
        assertEquals("artist", result.artist)
        assertEquals(" artist ", draft.filter.artist)
        assertFalse(LibraryTrackFilter().isActive)
    }
}
