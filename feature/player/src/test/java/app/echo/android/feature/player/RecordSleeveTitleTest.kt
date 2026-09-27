package app.echo.android.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordSleeveTitleTest {
    @Test fun separatesTrailingEditionWithoutLosingMetadata() {
        assertEquals(
            "21st Century Schizoid Man" to "(Including 'Mirrors')",
            recordSleeveTitleParts("21st Century Schizoid Man (Including 'Mirrors')"),
        )
    }

    @Test fun preservesParenthesesThatArePartOfTheMainTitle() {
        listOf("(Don't Fear) The Reaper", "Song (Part I) continued", "(Untitled)", "普通歌曲").forEach {
            assertEquals(it to null, recordSleeveTitleParts(it))
        }
    }
}
