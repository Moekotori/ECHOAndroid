package app.echo.android.data

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.echo.android.model.settings.EchoHomeLayout
import app.echo.android.model.settings.EchoHomeSection
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutPreferencesTest {
    @Test fun emptyPreferencesPreserveTheExistingDefaultHome() {
        assertEquals(EchoHomeLayout(), HomeLayoutPreferences.read(mutablePreferencesOf()))
    }

    @Test fun unknownAndDuplicateIdsDoNotBreakSavedLayouts() {
        val preferences = mutablePreferencesOf(
            stringPreferencesKey("home_section_order") to "future-section,artists,artists,recent",
            stringPreferencesKey("home_hidden_sections") to "future-section,recent,recent",
        )
        val restored = HomeLayoutPreferences.read(preferences)
        assertEquals(listOf(EchoHomeSection.Artists, EchoHomeSection.Recent), restored.order.take(2))
        assertEquals(EchoHomeSection.entries.size, restored.order.size)
        assertEquals(setOf(EchoHomeSection.Recent), restored.hidden)
    }

    @Test fun allHiddenAndReorderedSectionsSurviveReloadAndReset() {
        val preferences = mutablePreferencesOf()
        val layout = EchoHomeLayout(EchoHomeSection.entries.reversed(), EchoHomeSection.entries.toSet())
        HomeLayoutPreferences.write(preferences, layout)
        assertEquals(layout, HomeLayoutPreferences.read(preferences))
        HomeLayoutPreferences.write(preferences, EchoHomeLayout())
        assertEquals(EchoHomeLayout(), HomeLayoutPreferences.read(preferences))
    }
}
