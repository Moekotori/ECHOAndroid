package app.echo.android.model.settings

import org.junit.Assert.*
import org.junit.Test

class EchoWidgetOptionsTest {
    @Test fun invalidOpacityAndNonPlaylistIdentifiersFallBackSafely() {
        val options = EchoWidgetOptions(backgroundOpacity = Float.NaN, playlistMediaId = "https://example.org/private").normalized()
        assertEquals(1f, options.backgroundOpacity, 0f)
        assertNull(options.playlistMediaId)
        assertEquals(0f, EchoWidgetOptions(backgroundOpacity = -1f).normalized().backgroundOpacity, 0f)
    }

}
