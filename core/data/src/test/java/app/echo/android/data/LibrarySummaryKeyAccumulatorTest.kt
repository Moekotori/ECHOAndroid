package app.echo.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySummaryKeyAccumulatorTest {
    @Test fun batchedKeysPreserveUnionOrderAndDoNotMutatePreviousSnapshots() {
        val first = LibrarySummaryKeySet(linkedSetOf("a", "b"), setOf("artist"), setOf("folder"))
        val second = LibrarySummaryKeySet(linkedSetOf("b", "c"), setOf("guest"), setOf("folder"), setOf("genre"))
        val accumulator = LibrarySummaryKeyAccumulator()
        accumulator.add(first)
        val snapshot = accumulator.build()
        accumulator.add(second)
        assertEquals(first, snapshot)
        assertEquals(first + second, accumulator.build())
        assertEquals(listOf("a", "b", "c"), accumulator.build().albumKeys.toList())
    }
}
