package app.echo.android.design

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class EchoMetadataDisplayTest {
    @Test fun missingAndStoredSentinelsUseTheCurrentDisplayLabel() {
        for (value in listOf(null, " ", "未知艺术家", "Unknown Artist", "不明なアーティスト", "<unknown>")) {
            assertEquals("Unknown artist", displayMetadataOrUnknown(value, "Unknown artist"))
            assertEquals("未知藝術家", displayMetadataOrUnknown(value, "未知藝術家"))
        }
        assertEquals("Unknown album", displayMetadataOrUnknown("未知专辑", "Unknown album"))
        assertEquals("Unknown track", displayMetadataOrUnknown("未知曲目", "Unknown track"))
    }

    @Test fun realTagsKeepTheirLanguageAndSpelling() {
        for (value in listOf("宇多田ヒカル", "Björk", "群星", "Unknown Pleasures")) {
            assertEquals(value, displayMetadataOrUnknown(value, "Unknown artist"))
        }
    }

    @Test fun sentinelMatchingDoesNotDependOnSystemLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("Unknown artist", displayMetadataOrUnknown("UNKNOWN ARTIST", "Unknown artist"))
        } finally {
            Locale.setDefault(original)
        }
    }
}
