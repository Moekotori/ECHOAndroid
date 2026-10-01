package app.echo.android.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    private fun availability(notificationPermissionNeeded: Boolean = false) = SettingsSearchAvailability(
        scheduledDarkEnabled = false,
        customBackgroundMode = "default",
        backgroundAdvancedAvailable = false,
        trackFadeEnabled = false,
        replayGainEnabled = false,
        lastFmEnabled = false,
        lastFmConnected = false,
        listenBrainzEnabled = false,
        listenBrainzConnected = false,
        setlistFmLocked = false,
        importedFont = false,
        notificationPermissionNeeded = notificationPermissionNeeded,
    )

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

    @Test
    fun removedAndRelocatedOperationsAreNotIndexedAsSettings() {
        val titles = availableSettingsSearchItems(availability()).map { it.titleRes }
        assertFalse(R.string.settings_gapless in titles)
        assertFalse(R.string.settings_lyrics_sync_tools in titles)
        assertFalse(R.string.settings_pin_queue_offline in titles)
        assertFalse(R.string.settings_connect_pc in titles)
        assertFalse(R.string.settings_color_theme in titles)
        assertFalse(R.string.settings_dynamic_color in titles)
        // Secondary controls remain searchable without changing their saved values.
        assertTrue(R.string.settings_performance_mode in titles)
        assertTrue(R.string.settings_usb_bitperfect in titles)
        assertTrue(R.string.eq_preamp in titles)
    }

    @Test
    fun notificationPermissionIsOnlyIndexedWhenActionIsNeeded() {
        assertFalse(availableSettingsSearchItems(availability()).any {
            it.titleRes == R.string.settings_notification_permission
        })
        assertTrue(availableSettingsSearchItems(availability(true)).any {
            it.titleRes == R.string.settings_notification_permission
        })
    }

    @Test
    fun densityHasOneSearchDestinationInInterface() {
        val items = availableSettingsSearchItems(availability())
        val density = items.single { it.titleRes == R.string.settings_ui_density }
        assertEquals(SettingsCategory.Interface, density.category)
        assertTrue(R.string.settings_compact_mode in density.keywords)
        assertFalse(items.any { it.titleRes == R.string.settings_compact_mode })
    }

    @Test
    fun usbDetailsSearchLeadsToTheVisibleOptInBeforeConfigurationIsAvailable() {
        val unavailable = availability()
        val available = unavailable.copy(usbConfigurationVisible = true)
        val bitPerfect = availableSettingsSearchItems(unavailable).single {
            it.titleRes == R.string.settings_usb_bitperfect
        }
        assertEquals(R.string.settings_usb_exclusive, settingsSearchAnchorResource(bitPerfect, unavailable))
        assertEquals(R.string.settings_usb_bitperfect, settingsSearchAnchorResource(bitPerfect, available))
    }
}
