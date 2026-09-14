package app.echo.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RadioBrowserPolicyTest {
    @Test
    fun ignoresBlankAndSingleAsciiLetters() {
        assertNull(RadioBrowserPolicy.normalizedQuery(" "))
        assertNull(RadioBrowserPolicy.normalizedQuery("a"))
        assertEquals("ab", RadioBrowserPolicy.normalizedQuery(" ab "))
    }

    @Test
    fun acceptsASingleCjkCharacter() {
        assertEquals("日", RadioBrowserPolicy.normalizedQuery(" 日 "))
        assertEquals("广播", RadioBrowserPolicy.normalizedQuery("广播"))
    }

    @Test
    fun collapsesWhitespaceAndClipsLongQueries() {
        assertEquals("bbc radio", RadioBrowserPolicy.normalizedQuery("bbc   radio"))
        val long = "a".repeat(RadioBrowserPolicy.MaxQueryLength + 20)
        assertEquals("a".repeat(RadioBrowserPolicy.MaxQueryLength), RadioBrowserPolicy.normalizedQuery(long))
    }
}
