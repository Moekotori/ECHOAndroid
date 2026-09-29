package app.echo.android.data

import app.echo.android.model.settings.EchoCustomColors
import app.echo.android.model.settings.EchoSavedColorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoSavedColorThemeCodecTest {
    @Test
    fun roundTripsNamesAndOpaqueColors() {
        val themes = listOf(
            EchoSavedColorTheme(
                id = "night",
                name = "夜 \"蓝\"",
                colors = EchoCustomColors(0xFF112233.toInt(), 0xFF445566.toInt(), 0xFF778899.toInt()),
            ),
        )
        val restored = EchoSavedColorThemeCodec.decode(EchoSavedColorThemeCodec.encode(themes))
        assertEquals(themes, restored)
    }

    @Test
    fun corruptInputIsEmpty() {
        assertTrue(EchoSavedColorThemeCodec.decode(null).isEmpty())
        assertTrue(EchoSavedColorThemeCodec.decode("").isEmpty())
        assertTrue(EchoSavedColorThemeCodec.decode("{").isEmpty())
        assertTrue(EchoSavedColorThemeCodec.decode("[{\"id\":\"\",\"name\":\"X\"}]").isEmpty())
    }
}
