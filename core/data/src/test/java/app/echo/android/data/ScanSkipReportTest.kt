package app.echo.android.data

import app.echo.android.model.library.*
import org.junit.Assert.*
import org.junit.Test

class ScanSkipReportTest {
    @Test fun nameRulesAreLiteralCaseInsensitiveAndDoNotInspectFolderNames() {
        val options = LibraryScanOptions(excludedNameKeywords = setOf("lecture", "[sample]"))
        assertFalse(options.includesFileName("LECTURE 01.mp3"))
        assertFalse(options.includesFileName("Song [sample].flac"))
        assertTrue(options.includesFileName("sample.mp3"))
        assertTrue(options.accepts(0, 0, "Music/lectures"))
        assertEquals(LibraryScanSkipReason.Name, options.rejectionReason(0, 0, "Music", "lecture.mp3"))
    }

    @Test fun detailsCountAllRejectionsButKeepOnlyFortySmallSamples() {
        val report = ScanSkipReport()
        repeat(100) { report.record(LibraryScanSkippedItem(LibraryScanSkipReason.Duration, "a".repeat(300), "p".repeat(2000))) }
        assertEquals(100, report.counts()[LibraryScanSkipReason.Duration])
        assertEquals(40, report.samples().size)
        assertEquals(256, report.samples().first().name.length)
        assertEquals(1024, report.samples().first().path?.length)
    }
}
