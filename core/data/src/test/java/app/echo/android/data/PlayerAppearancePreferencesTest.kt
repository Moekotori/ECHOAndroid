package app.echo.android.data

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerAppearancePreferencesTest {
    @Test fun preservesEverySelectableStyleAcrossPreferenceReloads() {
        for (style in listOf("classic", "record_sleeve", "pixel_handheld", "type_poster")) {
            val prefs = mutablePreferencesOf()
            PlayerAppearancePreferences.write(prefs, style, 1.1f, 0.8f)
            assertEquals(style, PlayerAppearancePreferences.style(prefs.toPreferences()))
            assertEquals(1.1f, PlayerAppearancePreferences.textScale(prefs.toPreferences()))
            assertEquals(0.8f, PlayerAppearancePreferences.artworkScale(prefs.toPreferences()))
        }
    }
    @Test fun missingPreferencesKeepTheApprovedDesign() {
        val prefs = mutablePreferencesOf()
        assertEquals("record_sleeve", PlayerAppearancePreferences.style(prefs))
        assertEquals(1f, PlayerAppearancePreferences.textScale(prefs))
        assertEquals(1f, PlayerAppearancePreferences.artworkScale(prefs))
    }

    @Test fun savesAndReadsAllAppearanceFieldsWithoutTouchingOtherSettings() {
        val unrelated = stringPreferencesKey("unrelated")
        val prefs = mutablePreferencesOf(unrelated to "kept")
        PlayerAppearancePreferences.write(prefs, "classic", 1.1f, 0.8f)
        assertEquals("classic", PlayerAppearancePreferences.style(prefs))
        assertEquals(1.1f, PlayerAppearancePreferences.textScale(prefs))
        assertEquals(0.8f, PlayerAppearancePreferences.artworkScale(prefs))
        assertEquals("kept", prefs[unrelated])
    }

    @Test fun invalidAndOutOfRangeValuesCannotBreakLayout() {
        val prefs = mutablePreferencesOf()
        PlayerAppearancePreferences.write(prefs, "future-style", Float.NaN, Float.POSITIVE_INFINITY)
        assertEquals("record_sleeve", PlayerAppearancePreferences.style(prefs))
        assertEquals(1f, PlayerAppearancePreferences.textScale(prefs))
        assertEquals(1f, PlayerAppearancePreferences.artworkScale(prefs))
        PlayerAppearancePreferences.write(prefs, "classic", 10f, -1f)
        assertEquals(1.2f, PlayerAppearancePreferences.textScale(prefs))
        assertEquals(0.7f, PlayerAppearancePreferences.artworkScale(prefs))
    }
}
