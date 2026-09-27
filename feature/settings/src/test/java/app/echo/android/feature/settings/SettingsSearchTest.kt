package app.echo.android.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsSearchTest {
    private fun result(title: String, category: String, keywords: String = "") = SettingsSearchResult(
        item = SettingsSearchItem(SettingsCategory.Appearance, R.string.settings_section_theme),
        title = title,
        categoryTitle = category,
        anchorTitle = title,
        keywords = keywords,
    )

    @Test
    fun titleAndDescriptionAreSearchableWithoutCaseSensitivity() {
        val theme = result("Theme", "Appearance", "Dark mode and colors")
        val font = result("Font size", "Appearance", "Scale text")

        assertEquals(listOf(theme), searchSettings(listOf(theme, font), "DARK"))
        assertEquals(listOf(font), searchSettings(listOf(theme, font), "font SIZE"))
    }

    @Test
    fun exactTitleRanksBeforeCategoryMatches() {
        val categoryMatch = result("Font size", "Appearance")
        val exact = result("Appearance", "Appearance")

        assertEquals(listOf(exact, categoryMatch), searchSettings(listOf(categoryMatch, exact), "appearance"))
    }

    @Test
    fun blankQueryShowsNoResults() {
        assertEquals(emptyList<SettingsSearchResult>(), searchSettings(listOf(result("Theme", "Appearance")), "  "))
    }
}
