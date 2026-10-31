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
        assertTrue(R.string.settings_gapless in titles)
        assertTrue(R.string.audio_focus_title in titles)
        assertTrue(R.string.audio_output_reset in titles)
        val audioOutput = availableSettingsSearchItems(availability()).single { it.titleRes == R.string.settings_section_volume }
        assertEquals(R.string.audio_output_title, settingsSearchAnchorResource(audioOutput, availability()))
        assertFalse(R.string.settings_lyrics_sync_tools in titles)
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
    fun accessibilityControlsHaveUniqueDestinationsAndVisibleAnchors() {
        val state = availability()
        val items = availableSettingsSearchItems(state)
        listOf(R.string.settings_ui_font_size, R.string.settings_lyrics_font_size,
            R.string.accessibility_spacious_layout, R.string.accessibility_lyrics_motion,
            R.string.accessibility_word_highlight,
            R.string.accessibility_lyrics_spacing, R.string.accessibility_translation,
            R.string.accessibility_romanization, R.string.accessibility_mono,
            R.string.accessibility_balance, R.string.accessibility_balance_reset,
            R.string.accessibility_rest_timer,
            R.string.settings_playback_haptics, R.string.accessibility_screen_reader,
            R.string.accessibility_system_display).forEach { title ->
            val item = items.single { it.titleRes == title }
            assertEquals(SettingsCategory.Accessibility, item.category)
            assertEquals(title, settingsSearchAnchorResource(item, state))
        }
        val category = items.single { it.titleRes == R.string.accessibility_title }
        assertEquals(R.string.settings_section_readability, settingsSearchAnchorResource(category, state))
        val reader = items.single { it.titleRes == R.string.accessibility_screen_reader }
        assertTrue(R.string.accessibility_screen_reader_unknown in reader.keywords)
    }

    @Test
    fun everySearchTargetInASplitCategoryOpensItsSubPageOrTheEntryList() {
        val enabled = SettingsSearchAvailability(
            scheduledDarkEnabled = true, customBackgroundMode = "image", backgroundAdvancedAvailable = true,
            trackFadeEnabled = true, replayGainEnabled = true, lastFmEnabled = true, lastFmConnected = true,
            listenBrainzEnabled = true, listenBrainzConnected = true, setlistFmLocked = false,
            importedFont = true, notificationPermissionNeeded = true,
        )
        val split = SettingsSubPage.entries.map { it.category }.toSet()
        // These live on a category's entry list, where no sub-page is opened.
        val onEntryList = setOf(
            R.string.settings_category_appearance, R.string.settings_category_playback,
            R.string.settings_category_services, R.string.settings_section_connect,
            R.string.settings_section_music_services, R.string.settings_pc_handoff,
            R.string.settings_section_library, R.string.settings_audio_tags,
            R.string.settings_watched_folder_rescan, R.string.settings_local_music,
        )
        val unmapped = listOf(availability(), enabled).flatMap { state ->
            availableSettingsSearchItems(state)
                .filter { it.category in split }
                .map { it.category to settingsSearchAnchorResource(it, state) }
                .filter { (category, anchor) ->
                    settingsSubPageForAnchor(category, anchor) == null && anchor !in onEntryList
                }
        }.distinct()
        assertEquals(emptyList<Pair<SettingsCategory?, Int>>(), unmapped)
    }

    @Test
    fun usbControlsSearchOpensTheirIndependentPageWithoutAConnectedDac() {
        val state = availability()
        val items = availableSettingsSearchItems(state)
        listOf(R.string.settings_usb_exclusive, R.string.settings_usb_bitperfect,
            R.string.settings_usb_auto_request, R.string.settings_test_usb).forEach { title ->
            val item = items.single { it.titleRes == title }
            assertEquals(SettingsCategory.Usb, item.category)
            assertEquals(title, settingsSearchAnchorResource(item, state))
        }
    }
}
