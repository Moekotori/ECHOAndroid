package app.echo.android.feature.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource

/** A page one level below a category. Categories without entries here stay a single flat page. */
internal enum class SettingsSubPage(val category: SettingsCategory, @StringRes val title: Int) {
    AppearanceTheme(SettingsCategory.Appearance, R.string.settings_section_theme),
    AppearanceText(SettingsCategory.Appearance, R.string.settings_section_readability),
    AppearanceBackground(SettingsCategory.Appearance, R.string.settings_section_background),

    PlaybackOutput(SettingsCategory.Playback, R.string.audio_output_title),
    PlaybackListening(SettingsCategory.Playback, R.string.settings_section_listening),
    PlaybackLyrics(SettingsCategory.Playback, R.string.settings_section_lyrics),

    AccessibilityReading(SettingsCategory.Accessibility, R.string.settings_section_readability),
    AccessibilityMotion(SettingsCategory.Accessibility, R.string.accessibility_motion_feedback),
    AccessibilityCare(SettingsCategory.Accessibility, R.string.accessibility_listening),
    AccessibilitySystem(SettingsCategory.Accessibility, R.string.accessibility_system),

    ServicesLastFm(SettingsCategory.Services, R.string.settings_search_lastfm),
    ServicesListenBrainz(SettingsCategory.Services, R.string.settings_listenbrainz),
    ServicesSetlistFm(SettingsCategory.Services, R.string.settings_setlistfm),

    LibraryHealth(SettingsCategory.Library, R.string.settings_library_health_title),
    LibraryMaintenance(SettingsCategory.Library, R.string.settings_section_maintenance),
}

/**
 * What a category page needs to know about its sub-pages. [open] is null when the layout shows
 * everything inline (two panes), in which case [page] is always null too.
 */
internal class SettingsSubNav(
    val page: SettingsSubPage?,
    val open: ((SettingsSubPage) -> Unit)?,
) {
    /** The category's entry list: single pane, no sub-page chosen yet. */
    val isHub: Boolean get() = open != null && page == null

    /** Whether [target]'s content belongs on screen: always when inline, otherwise only on its own page. */
    fun shows(target: SettingsSubPage): Boolean = page == null || page == target

    companion object {
        val Inline = SettingsSubNav(null, null)
    }
}

/** One entry in a category's list of sub-pages; [summary] is the current value, not help text. */
@Composable
internal fun SettingsSubPageRow(
    page: SettingsSubPage,
    icon: ImageVector,
    nav: SettingsSubNav,
    summary: String = "",
) {
    val open = nav.open ?: return
    SettingsMenuRow(
        icon = icon,
        title = stringResource(page.title),
        summary = summary,
        compactMode = LocalSettingsCompactMode.current,
        onClick = { open(page) },
    )
}

private fun anchors(page: SettingsSubPage, vararg titles: Int): List<Pair<Pair<SettingsCategory, Int>, SettingsSubPage>> =
    titles.map { (page.category to it) to page }

// Keyed by category too: the haptics row, for one, is a search target of Accessibility only.
private val anchorPages: Map<Pair<SettingsCategory, Int>, SettingsSubPage> = listOf(
    anchors(SettingsSubPage.AppearanceTheme,
        R.string.settings_section_theme, R.string.settings_display_mode, R.string.settings_color_theme,
        R.string.settings_dynamic_color, R.string.settings_scheduled_dark,
        R.string.settings_dark_start, R.string.settings_dark_end, R.string.settings_section_schedule),
    anchors(SettingsSubPage.AppearanceText,
        R.string.settings_section_readability, R.string.settings_font_advanced, R.string.settings_ui_font,
        R.string.settings_lyrics_font, R.string.settings_import_font, R.string.settings_reselect_font),
    anchors(SettingsSubPage.AppearanceBackground,
        R.string.settings_section_background, R.string.settings_bg_source, R.string.settings_bg_style,
        R.string.settings_blur, R.string.settings_brightness, R.string.settings_glass, R.string.settings_scale,
        R.string.settings_startup_background, R.string.settings_section_background_adjustments),

    anchors(SettingsSubPage.PlaybackOutput,
        R.string.audio_output_title, R.string.audio_focus_title, R.string.audio_output_reset,
        R.string.listening_interruption, R.string.manual_fade_title, R.string.effects_panel_title,
        R.string.settings_replay_gain, R.string.settings_replay_gain_mode, R.string.eq_preamp,
        R.string.settings_section_transition, R.string.settings_gapless, R.string.settings_track_fade,
        R.string.settings_track_fade_duration, R.string.settings_smart_transition),
    anchors(SettingsSubPage.PlaybackListening,
        R.string.settings_section_listening, R.string.settings_shuffle_mode, R.string.settings_shuffle_rules,
        R.string.settings_pause_on_disconnect, R.string.settings_resume_on_reconnect),
    anchors(SettingsSubPage.PlaybackLyrics,
        R.string.settings_section_lyrics, R.string.settings_lyrics_interface, R.string.settings_lyrics_source_order,
        R.string.settings_notification_lyrics, R.string.settings_status_overlay_lyrics,
        R.string.settings_system_status_lyrics, R.string.settings_status_lyrics_hide_translation,
        R.string.settings_online_lyrics, R.string.settings_lock_lyrics, R.string.settings_floating_lyrics,
        R.string.settings_notification_permission),

    anchors(SettingsSubPage.AccessibilityReading,
        R.string.settings_section_readability, R.string.settings_ui_font_size,
        R.string.settings_lyrics_font_size, R.string.accessibility_spacious_layout),
    anchors(SettingsSubPage.AccessibilityMotion,
        R.string.accessibility_motion_feedback, R.string.accessibility_word_highlight,
        R.string.settings_playback_haptics),
    anchors(SettingsSubPage.AccessibilityCare,
        R.string.accessibility_lyrics_reading, R.string.accessibility_lyrics_spacing,
        R.string.accessibility_translation, R.string.accessibility_romanization,
        R.string.accessibility_listening, R.string.accessibility_mono, R.string.accessibility_balance,
        R.string.accessibility_balance_reset, R.string.accessibility_rest, R.string.accessibility_rest_timer),
    anchors(SettingsSubPage.AccessibilitySystem,
        R.string.accessibility_system, R.string.accessibility_screen_reader, R.string.accessibility_system_display),

    anchors(SettingsSubPage.ServicesLastFm,
        R.string.settings_search_lastfm, R.string.settings_search_api_key, R.string.settings_search_shared_secret,
        R.string.settings_lastfm_open_auth, R.string.settings_lastfm_reauth, R.string.settings_lastfm_complete_auth,
        R.string.settings_lastfm_api_accounts, R.string.settings_lastfm_disconnect),
    anchors(SettingsSubPage.ServicesListenBrainz,
        R.string.settings_listenbrainz, R.string.settings_listenbrainz_token,
        R.string.settings_listenbrainz_save, R.string.settings_listenbrainz_disconnect),
    anchors(SettingsSubPage.ServicesSetlistFm,
        R.string.settings_setlistfm, R.string.settings_setlistfm_save),

    anchors(SettingsSubPage.LibraryHealth, R.string.settings_library_health_title),
    anchors(SettingsSubPage.LibraryMaintenance,
        R.string.settings_section_maintenance, R.string.settings_library_cleanup, R.string.settings_clear_index_title,
        R.string.settings_library_repair),
).flatten().toMap()

/**
 * The sub-page that holds a search anchor, so a search result lands on the right page. Null means
 * the anchor lives on the category's entry list (or the category has no sub-pages).
 */
internal fun settingsSubPageForAnchor(category: SettingsCategory?, @StringRes anchor: Int): SettingsSubPage? =
    category?.let { anchorPages[it to anchor] }
