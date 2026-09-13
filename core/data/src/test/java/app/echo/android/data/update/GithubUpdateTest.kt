package app.echo.android.data.update

import org.junit.Assert.*
import org.junit.Test

class GithubUpdateTest {
    private fun metadata(code: Long = 26091301, url: String = "https://github.com/moekotori/echoandroid/releases/download/v26.9.13/ECHO.apk",
        size: Long = 42, hash: String = "a".repeat(64)) =
        """{"schemaVersion":1,"versionCode":$code,"versionName":"26.9.13","apkUrl":"$url","size":$size,"sha256":"$hash"}"""

    @Test fun parsesDateVersionAndBoundsNotes() {
        val first = parseUpdate(metadata(), "x".repeat(20000))
        val second = parseUpdate(metadata(code = 26091302), "fix")
        assertTrue(second.versionCode > first.versionCode)
        assertEquals("26.9.13", first.versionName)
        assertEquals(16000, first.notes.length)
        assertEquals(42, first.size)
    }
    @Test fun rejectsForeignRepositoryAndMalformedArtifacts() {
        listOf(
            metadata(url = "https://github.com/moekotori/echosteam/releases/download/v1/ECHO.apk"),
            metadata(url = "http://github.com/moekotori/echoandroid/releases/download/v1/ECHO.apk"),
            metadata(size = 0), metadata(size = 536870913), metadata(hash = "oops"), metadata(code = -1),
        ).forEach { text ->
            try { parseUpdate(text, ""); fail("Accepted invalid manifest") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
