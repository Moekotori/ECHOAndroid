package app.echo.android.data

import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.PlaylistExportFormat
import org.junit.Assert.*
import org.junit.Test

class PlaylistTextExportTest {
    @Test fun csvQuotesMetadataAndPreventsFormulaEvaluation() {
        val song = EchoTrack("1", "content://private/1", "=SUM(1,2)", "A\"B", album = "课程\n录音", durationMs = 65_000)
        val result = exportPlaylistText(listOf(song), PlaylistExportFormat.Csv)
        assertTrue(result.startsWith("\uFEFF"))
        assertTrue(result.contains("\"'=SUM(1,2)\",\"A\"\"B\",\"课程\n录音\",\"1:05\""))
        assertFalse(result.contains("content://"))
    }

    @Test fun plainTextKeepsSequenceAndOneRowPerSong() {
        val result = exportPlaylistText(listOf(EchoTrack("1", "uri", "A\nB", "C\tD"), EchoTrack("2", "uri2", "第二首", "歌手")), PlaylistExportFormat.Text)
        assertEquals("1\tA B\tC D\t\t0:00\n2\t第二首\t歌手\t\t0:00\n", result)
    }
}
