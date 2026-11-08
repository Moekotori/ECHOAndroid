package app.echo.android.data

import androidx.datastore.preferences.core.*
import app.echo.android.model.settings.EchoAppearancePack
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class EchoAppearancePackTest {
    @Test fun personalAudioBackupRetainsAllSettingsAndNormalizesInvalidValues() {
        val dsp = app.echo.android.model.playback.EchoDspSettings(hrtfEnabled = true, hrtfProfile = 2,
            hrtfAmount = 0.7f, equalLoudnessEnabled = true, equalLoudnessReferenceDb = -15f,
            equalLoudnessBassDb = 4f, equalLoudnessTrebleDb = 2f)
        assertEquals(dsp, EchoDspCodec.decode(EchoDspCodec.encode(dsp)))
    }
    @Test fun roundTripPortableAppearanceAndAssets() {
        val s = EchoAppearancePack(name = "夜航", playerStyle = "afterglow_night", fontScale = 0.5f, lineSpacing = 1.38f)
        val decoded = EchoAppearancePackCodec.decode(EchoAppearancePackCodec.encode(DecodedAppearancePack(s, byteArrayOf(1,2,3))))
        assertEquals(s, decoded.appearance); assertArrayEquals(byteArrayOf(1,2,3), decoded.background)
    }

    @Test fun rejectsUnknownVersionTraversalAndInvalidRanges() {
        fun zip(name: String, value: String): ByteArray = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { it.putNextEntry(ZipEntry(name)); it.write(value.toByteArray()); it.closeEntry() }
        }.toByteArray()
        val json = EchoAppearancePackCodec.toJson(EchoAppearancePack())
        for (bytes in listOf(zip("../appearance.json", json.toString()),
            zip("appearance.json", json.put("version", 2).toString()),
            zip("appearance.json", json.put("version", 1).put("fontScale", 20).toString()))) {
            assertTrue(runCatching { EchoAppearancePackCodec.decode(bytes) }.isFailure)
        }
    }

    @Test fun atomicUndoRestoresAbsentFieldsAndLeavesPlaybackAndAccountsAlone() {
        val p = mutablePreferencesOf(stringPreferencesKey("subsonic_password") to "private",
            booleanPreferencesKey("usb_bit_perfect_enabled") to true, stringPreferencesKey("player_page_style") to "classic")
        val before = p.toPreferences()
        AppearancePackPreferences.apply(p, EchoAppearancePack(playerStyle = "afterglow_night"), "file:/background", null)
        assertEquals("private", p[stringPreferencesKey("subsonic_password")])
        assertTrue(p[booleanPreferencesKey("usb_bit_perfect_enabled")] == true)
        assertTrue(AppearancePackPreferences.restore(p))
        assertEquals(before, p.toPreferences())
        assertFalse(AppearancePackPreferences.restore(p))
    }
}
