package app.echo.android.data

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.echo.android.model.settings.EchoLyricsPageStyle
import app.echo.android.model.settings.EchoPlayerPageStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerAppearancePreferencesTest {
    private val playerKey = stringPreferencesKey("player_page_style")
    private val lyricsKey = stringPreferencesKey("lyrics_page_style")

    @Test fun everySharedChoiceSurvivesReloadWithItsLyricPresetAndSizes() {
        for (style in EchoPlayerPageStyle.entries) {
            val prefs = mutablePreferencesOf()
            PlayerAppearancePreferences.write(prefs, style.id, 1.1f, 0.8f)
            val reloaded = prefs.toPreferences()
            assertEquals(style.id, PlayerAppearancePreferences.style(reloaded))
            assertEquals(style.lyricsPreset, PlayerAppearancePreferences.lyricsStyle(reloaded))
            assertEquals(1.1f, PlayerAppearancePreferences.textScale(reloaded))
            assertEquals(0.8f, PlayerAppearancePreferences.artworkScale(reloaded))
        }
    }

    @Test fun oldConflictingOrdinaryLyricsFollowTheSongStyle() {
        for (style in EchoPlayerPageStyle.entries.filterNot { it.lyricsPreset.isAfterglow }) {
            for (lyrics in listOf(EchoLyricsPageStyle.Mist, EchoLyricsPageStyle.Paper)) {
                val prefs = mutablePreferencesOf(playerKey to style.id, lyricsKey to lyrics.id)
                assertEquals(style.id, PlayerAppearancePreferences.style(prefs))
                assertEquals(style.lyricsPreset, PlayerAppearancePreferences.lyricsStyle(prefs))
            }
        }
        val missing = mutablePreferencesOf()
        assertEquals("classic", PlayerAppearancePreferences.style(missing))
        assertEquals(EchoLyricsPageStyle.Mist, PlayerAppearancePreferences.lyricsStyle(missing))
        assertEquals("record_sleeve", PlayerAppearancePreferences.style(mutablePreferencesOf(lyricsKey to "paper")))
    }

    @Test fun oldAfterglowBecomesSharedAndCanBeReplacedFromEitherPage() {
        for (lyrics in listOf(EchoLyricsPageStyle.AfterglowMist, EchoLyricsPageStyle.AfterglowNight)) {
            val prefs = mutablePreferencesOf(playerKey to "pixel_handheld", lyricsKey to lyrics.id)
            assertEquals(lyrics.id, PlayerAppearancePreferences.style(prefs))
            PlayerAppearancePreferences.write(prefs, PlayerAppearancePreferences.style(prefs), 1.1f, 0.8f)
            assertEquals(lyrics, PlayerAppearancePreferences.lyricsStyle(prefs))
            PlayerAppearancePreferences.write(prefs, "classic", 1.1f, 0.8f)
            assertEquals("classic", PlayerAppearancePreferences.style(prefs))
            assertEquals(EchoLyricsPageStyle.Mist, PlayerAppearancePreferences.lyricsStyle(prefs))
        }
        for (legacy in listOf("mist", "paper")) {
            val prefs = mutablePreferencesOf()
            PlayerAppearancePreferences.write(prefs, legacy, 1f, 1f)
            assertEquals(EchoPlayerPageStyle.fromId(legacy).id, PlayerAppearancePreferences.style(prefs))
        }
    }

    @Test fun backupRestoreOverridesCurrentStyleAndKeepsBothFieldsCoherent() {
        val prefs = mutablePreferencesOf()
        PlayerAppearancePreferences.write(prefs, "afterglow_night", 1.1f, 0.8f)
        PlayerAppearancePreferences.restore(prefs, "type_poster", "mist", null, null)
        assertEquals("type_poster", PlayerAppearancePreferences.style(prefs))
        assertEquals("paper", prefs[lyricsKey])
        PlayerAppearancePreferences.restore(prefs, "classic", "afterglow_mist", null, null)
        assertEquals("afterglow_mist", prefs[playerKey])
        PlayerAppearancePreferences.restore(prefs, null, null, 1.2f, null)
        assertEquals("afterglow_mist", PlayerAppearancePreferences.style(prefs))
        assertEquals(1.2f, PlayerAppearancePreferences.textScale(prefs))
        assertEquals(0.8f, PlayerAppearancePreferences.artworkScale(prefs))
    }

    @Test fun invalidValuesClampWithoutTouchingOtherSettings() {
        val unrelated = stringPreferencesKey("unrelated")
        val prefs = mutablePreferencesOf(unrelated to "kept")
        PlayerAppearancePreferences.write(prefs, "future-style", Float.NaN, Float.POSITIVE_INFINITY)
        assertEquals("classic", PlayerAppearancePreferences.style(prefs))
        assertEquals(EchoLyricsPageStyle.Mist, PlayerAppearancePreferences.lyricsStyle(prefs))
        assertEquals(1f, PlayerAppearancePreferences.textScale(prefs))
        assertEquals(1f, PlayerAppearancePreferences.artworkScale(prefs))
        PlayerAppearancePreferences.write(prefs, "classic", 10f, -1f)
        assertEquals(1.2f, PlayerAppearancePreferences.textScale(prefs))
        assertEquals(0.7f, PlayerAppearancePreferences.artworkScale(prefs))
        assertEquals("kept", prefs[unrelated])
    }

    @Test fun scaleOnlyEditsKeepCustomLyricsWhileStyleSwitchAppliesTheSharedPreset() {
        val fontKey = stringPreferencesKey("lyrics_font_family")
        val alignmentKey = stringPreferencesKey("lyrics_alignment")
        val colorKey = stringPreferencesKey("lyrics_color_mode")
        val prefs = mutablePreferencesOf()
        PlayerAppearancePreferences.write(prefs, "pixel_handheld", 1f, 1f)
        assertEquals("system", prefs[fontKey])
        prefs[fontKey] = "imported"
        prefs[alignmentKey] = "end"
        prefs[colorKey] = "pink"
        PlayerAppearancePreferences.write(prefs, "pixel_handheld", 1.1f, 0.8f)
        assertEquals("imported", prefs[fontKey])
        assertEquals("end", prefs[alignmentKey])
        assertEquals("pink", prefs[colorKey])
        PlayerAppearancePreferences.write(prefs, "afterglow_mist", 1.1f, 0.8f)
        assertEquals("serif", prefs[fontKey])
        assertEquals("center", prefs[alignmentKey])
        assertEquals(EchoLyricsColorMode.White, prefs[colorKey])
    }
}
