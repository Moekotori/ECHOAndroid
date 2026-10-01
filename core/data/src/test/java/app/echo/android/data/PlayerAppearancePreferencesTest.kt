package app.echo.android.data

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.echo.android.model.settings.EchoLyricsPageStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerAppearancePreferencesTest {
    @Test fun explicitAppearanceSurvivesEitherThemeWithoutResettingSizes() {
        val lyricsKey = stringPreferencesKey("lyrics_page_style")
        for (lyrics in EchoLyricsPageStyle.entries) {
            val prefs = mutablePreferencesOf(lyricsKey to lyrics.id)
            PlayerAppearancePreferences.write(prefs, "record_sleeve", 1.1f, 0.8f)

            for (theme in listOf(EchoThemeMode.Light, EchoThemeMode.Dark, EchoThemeMode.System)) {
                assertEquals(lyrics, PlayerAppearancePreferences.lyricsStyle(prefs, theme))
            }
            assertEquals("record_sleeve", PlayerAppearancePreferences.style(prefs))
            assertEquals(1.1f, PlayerAppearancePreferences.textScale(prefs))
            assertEquals(0.8f, PlayerAppearancePreferences.artworkScale(prefs))
        }
    }

    @Test fun missingLyricsFollowThemeWhileSavedCoverKeepsItsBinding() {
        val prefs = mutablePreferencesOf()
        assertEquals(EchoLyricsPageStyle.Paper, PlayerAppearancePreferences.lyricsStyle(prefs, EchoThemeMode.Light))
        assertEquals(EchoLyricsPageStyle.Mist, PlayerAppearancePreferences.lyricsStyle(prefs, EchoThemeMode.Dark))
        prefs[stringPreferencesKey("player_page_style")] = "classic"
        assertEquals(EchoLyricsPageStyle.Mist, PlayerAppearancePreferences.lyricsStyle(prefs, EchoThemeMode.Light))
        PlayerAppearancePreferences.write(prefs, "record_sleeve", 1f, 1f)
        assertEquals(EchoLyricsPageStyle.Paper, PlayerAppearancePreferences.lyricsStyle(prefs, EchoThemeMode.Dark))
    }

    @Test fun sizeOnlyEditKeepsTheLightLyricDefault() {
        val prefs = mutablePreferencesOf()
        PlayerAppearancePreferences.write(prefs, "classic", 1.1f, 0.8f)
        assertEquals(EchoLyricsPageStyle.Paper, PlayerAppearancePreferences.lyricsStyle(prefs, EchoThemeMode.Light))
        assertEquals(1.1f, PlayerAppearancePreferences.textScale(prefs))
        assertEquals(0.8f, PlayerAppearancePreferences.artworkScale(prefs))
    }

    @Test fun afterglowSelectionSurvivesCoverStyleAndScaleChanges() {
        for (lyrics in listOf(EchoLyricsPageStyle.AfterglowMist, EchoLyricsPageStyle.AfterglowNight)) {
            for (player in listOf("classic", "record_sleeve", "pixel_handheld", "type_poster")) {
                assertEquals(lyrics, PlayerAppearancePreferences.boundLyricsStyle(player, lyrics))
                assertEquals(player, PlayerAppearancePreferences.boundPlayerStyle(lyrics, player))
            }
        }
    }
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
        assertEquals("classic", PlayerAppearancePreferences.style(prefs))
        assertEquals(1f, PlayerAppearancePreferences.textScale(prefs))
        assertEquals(1f, PlayerAppearancePreferences.artworkScale(prefs))
    }

    @Test fun lightSongStylesUseLightLyricsAndDarkUsesTheDarkBase() {
        assertEquals(EchoLyricsPageStyle.Mist, PlayerAppearancePreferences.boundLyricsStyle("classic"))
        assertEquals(EchoLyricsPageStyle.Paper, PlayerAppearancePreferences.boundLyricsStyle("record_sleeve"))
        assertEquals(EchoLyricsPageStyle.Paper, PlayerAppearancePreferences.boundLyricsStyle("pixel_handheld"))
        assertEquals(EchoLyricsPageStyle.Paper, PlayerAppearancePreferences.boundLyricsStyle("type_poster"))
        assertEquals("classic", PlayerAppearancePreferences.boundPlayerStyle(EchoLyricsPageStyle.Mist, "record_sleeve"))
        assertEquals("classic", PlayerAppearancePreferences.boundPlayerStyle(EchoLyricsPageStyle.Mist, "pixel_handheld"))
        assertEquals("classic", PlayerAppearancePreferences.boundPlayerStyle(EchoLyricsPageStyle.Mist, "classic"))
        assertEquals("record_sleeve", PlayerAppearancePreferences.boundPlayerStyle(EchoLyricsPageStyle.Paper, "classic"))
        assertEquals("pixel_handheld", PlayerAppearancePreferences.boundPlayerStyle(EchoLyricsPageStyle.Paper, "pixel_handheld"))
        assertEquals("type_poster", PlayerAppearancePreferences.boundPlayerStyle(EchoLyricsPageStyle.Paper, "type_poster"))
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
        assertEquals("classic", PlayerAppearancePreferences.style(prefs))
        assertEquals(1f, PlayerAppearancePreferences.textScale(prefs))
        assertEquals(1f, PlayerAppearancePreferences.artworkScale(prefs))
        assertEquals(EchoLyricsPageStyle.Mist, PlayerAppearancePreferences.boundLyricsStyle("future-style"))
        PlayerAppearancePreferences.write(prefs, "classic", 10f, -1f)
        assertEquals(1.2f, PlayerAppearancePreferences.textScale(prefs))
        assertEquals(0.7f, PlayerAppearancePreferences.artworkScale(prefs))
    }
}
