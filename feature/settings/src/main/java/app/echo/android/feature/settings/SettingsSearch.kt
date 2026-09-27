package app.echo.android.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameNanos
import java.util.Locale

internal data class SettingsSearchFocus(val title: String, val requestId: Int)

internal val LocalSettingsSearchFocus = compositionLocalOf<SettingsSearchFocus?> { null }

@Composable
internal fun Modifier.settingsSearchAnchor(title: String): Modifier {
    val focus = LocalSettingsSearchFocus.current
    val requester = remember { BringIntoViewRequester() }
    val focused = focus?.title == title
    LaunchedEffect(focus, title) {
        if (focused) {
            withFrameNanos { }
            requester.bringIntoView()
        }
    }
    return this.bringIntoViewRequester(requester).then(
        if (focused) Modifier.background(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            RoundedCornerShape(12.dp),
        ) else Modifier,
    )
}

internal enum class SettingsSearchRequirement {
    Always, ScheduledDark, DarkTime, CustomBackground, BackgroundAdvanced, ImageBackground,
    TrackFade, ReplayGain, LastFm, LastFmConnected, ListenBrainz, ListenBrainzConnected,
}

internal data class SettingsSearchAvailability(
    val scheduledDarkEnabled: Boolean,
    val customBackgroundMode: String,
    val customBackgroundReady: Boolean,
    val backgroundAdvancedAvailable: Boolean,
    val trackFadeEnabled: Boolean,
    val replayGainEnabled: Boolean,
    val lastFmEnabled: Boolean,
    val lastFmConnected: Boolean,
    val listenBrainzEnabled: Boolean,
    val listenBrainzConnected: Boolean,
    val importedFont: Boolean,
)

internal data class SettingsSearchItem(
    val category: SettingsCategory?,
    @StringRes val titleRes: Int,
    @StringRes val anchorRes: Int = titleRes,
    val requirement: SettingsSearchRequirement = SettingsSearchRequirement.Always,
    @StringRes val fallbackRes: Int = anchorRes,
    val keywords: List<Int> = emptyList(),
    val opensPlugins: Boolean = false,
)

internal data class SettingsSearchResult(
    val item: SettingsSearchItem,
    val title: String,
    val categoryTitle: String,
    val anchorTitle: String,
    val keywords: String,
)

private fun item(
    category: SettingsCategory?,
    @StringRes title: Int,
    @StringRes anchor: Int = title,
    requirement: SettingsSearchRequirement = SettingsSearchRequirement.Always,
    @StringRes fallback: Int = anchor,
    vararg keywords: Int,
) = SettingsSearchItem(category, title, anchor, requirement, fallback, keywords.toList())

private val indexedSettings = listOf(
    item(SettingsCategory.Appearance, R.string.settings_category_appearance,
        keywords = *intArrayOf(R.string.settings_category_appearance_detail)),
    item(SettingsCategory.Appearance, R.string.settings_section_theme),
    item(SettingsCategory.Appearance, R.string.settings_display_mode,
        keywords = *intArrayOf(R.string.settings_theme_light, R.string.settings_theme_dark, R.string.settings_theme_system)),
    item(SettingsCategory.Appearance, R.string.settings_color_theme),
    item(SettingsCategory.Appearance, R.string.settings_dynamic_color),
    item(SettingsCategory.Appearance, R.string.settings_scheduled_dark,
        requirement = SettingsSearchRequirement.ScheduledDark),
    item(SettingsCategory.Appearance, R.string.settings_dark_start,
        requirement = SettingsSearchRequirement.DarkTime, fallback = R.string.settings_scheduled_dark),
    item(SettingsCategory.Appearance, R.string.settings_dark_end,
        requirement = SettingsSearchRequirement.DarkTime, fallback = R.string.settings_scheduled_dark),
    item(SettingsCategory.Appearance, R.string.settings_section_background),
    item(SettingsCategory.Appearance, R.string.settings_bg_source,
        keywords = *intArrayOf(R.string.settings_bg_image_label, R.string.settings_bg_video_label,
            R.string.settings_bg_default_label)),
    item(SettingsCategory.Appearance, R.string.settings_bg_style,
        requirement = SettingsSearchRequirement.CustomBackground, fallback = R.string.settings_section_background),
    item(SettingsCategory.Appearance, R.string.settings_blur,
        requirement = SettingsSearchRequirement.ImageBackground, fallback = R.string.settings_section_background),
    item(SettingsCategory.Appearance, R.string.settings_brightness,
        requirement = SettingsSearchRequirement.BackgroundAdvanced, fallback = R.string.settings_section_background),
    item(SettingsCategory.Appearance, R.string.settings_glass,
        requirement = SettingsSearchRequirement.BackgroundAdvanced, fallback = R.string.settings_section_background),
    item(SettingsCategory.Appearance, R.string.settings_scale,
        requirement = SettingsSearchRequirement.BackgroundAdvanced, fallback = R.string.settings_section_background),
    item(SettingsCategory.Appearance, R.string.settings_startup_background,
        keywords = *intArrayOf(R.string.settings_startup_background_detail,
            R.string.settings_startup_background_choose)),
    item(SettingsCategory.Appearance, R.string.settings_section_fonts),
    item(SettingsCategory.Appearance, R.string.settings_ui_font,
        keywords = *intArrayOf(R.string.settings_font_outfit, R.string.settings_font_system,
            R.string.settings_font_serif, R.string.settings_font_mono)),
    item(SettingsCategory.Appearance, R.string.settings_ui_font_size),
    item(SettingsCategory.Appearance, R.string.settings_ui_density),
    item(SettingsCategory.Appearance, R.string.settings_lyrics_font),
    item(SettingsCategory.Appearance, R.string.settings_lyrics_font_size),
    item(SettingsCategory.Appearance, R.string.settings_import_font,
        keywords = *intArrayOf(R.string.settings_reselect_font)),
    item(SettingsCategory.Appearance, R.string.settings_clear_font),

    item(SettingsCategory.Interface, R.string.settings_category_interface,
        keywords = *intArrayOf(R.string.settings_category_interface_detail)),
    item(SettingsCategory.Interface, R.string.settings_language),
    item(SettingsCategory.Interface, R.string.settings_performance_mode,
        keywords = *intArrayOf(R.string.settings_perf_auto, R.string.settings_perf_balanced,
            R.string.settings_perf_lightweight, R.string.settings_perf_high)),
    item(SettingsCategory.Interface, R.string.settings_dynamic_artwork),
    item(SettingsCategory.Interface, R.string.settings_compact_mode),

    item(SettingsCategory.Playback, R.string.settings_category_playback,
        keywords = *intArrayOf(R.string.settings_category_playback_detail)),
    item(SettingsCategory.Playback, R.string.settings_gapless),
    item(SettingsCategory.Playback, R.string.settings_track_fade),
    item(SettingsCategory.Playback, R.string.settings_track_fade_duration,
        requirement = SettingsSearchRequirement.TrackFade, fallback = R.string.settings_track_fade),
    item(SettingsCategory.Playback, R.string.settings_smart_transition),
    item(SettingsCategory.Playback, R.string.settings_pause_on_disconnect),
    item(SettingsCategory.Playback, R.string.settings_resume_on_reconnect),
    item(SettingsCategory.Playback, R.string.settings_replay_gain),
    item(SettingsCategory.Playback, R.string.settings_replay_gain_mode,
        requirement = SettingsSearchRequirement.ReplayGain, fallback = R.string.settings_replay_gain,
        keywords = *intArrayOf(R.string.dsp_auto, R.string.dsp_track, R.string.dsp_album)),
    item(SettingsCategory.Playback, R.string.eq_preamp,
        requirement = SettingsSearchRequirement.ReplayGain, fallback = R.string.settings_replay_gain),
    item(SettingsCategory.Playback, R.string.settings_lyrics_sync_tools),
    item(SettingsCategory.Playback, R.string.settings_playback_haptics),
    item(SettingsCategory.Playback, R.string.settings_online_lyrics),
    item(SettingsCategory.Playback, R.string.settings_lock_lyrics),
    item(SettingsCategory.Playback, R.string.settings_usb_exclusive),
    item(SettingsCategory.Playback, R.string.settings_usb_bitperfect),
    item(SettingsCategory.Playback, R.string.settings_usb_auto_request),
    item(SettingsCategory.Playback, R.string.settings_notification_permission),
    item(SettingsCategory.Playback, R.string.settings_test_usb),
    item(SettingsCategory.Playback, R.string.settings_pin_queue_offline),

    item(SettingsCategory.Services, R.string.settings_category_services,
        keywords = *intArrayOf(R.string.settings_category_services_detail)),
    item(SettingsCategory.Services, R.string.settings_section_connect),
    item(SettingsCategory.Services, R.string.settings_search_lastfm),
    item(SettingsCategory.Services, R.string.settings_search_api_key,
        requirement = SettingsSearchRequirement.LastFm, fallback = R.string.settings_search_lastfm),
    item(SettingsCategory.Services, R.string.settings_search_shared_secret,
        requirement = SettingsSearchRequirement.LastFm, fallback = R.string.settings_search_lastfm),
    item(SettingsCategory.Services, R.string.settings_lastfm_open_auth,
        requirement = SettingsSearchRequirement.LastFm, fallback = R.string.settings_search_lastfm,
        keywords = *intArrayOf(R.string.settings_lastfm_reauth)),
    item(SettingsCategory.Services, R.string.settings_lastfm_complete_auth,
        requirement = SettingsSearchRequirement.LastFm, fallback = R.string.settings_search_lastfm),
    item(SettingsCategory.Services, R.string.settings_lastfm_api_accounts,
        requirement = SettingsSearchRequirement.LastFm, fallback = R.string.settings_search_lastfm),
    item(SettingsCategory.Services, R.string.settings_lastfm_disconnect,
        requirement = SettingsSearchRequirement.LastFmConnected, fallback = R.string.settings_search_lastfm),
    item(SettingsCategory.Services, R.string.settings_listenbrainz),
    item(SettingsCategory.Services, R.string.settings_listenbrainz_token,
        requirement = SettingsSearchRequirement.ListenBrainz, fallback = R.string.settings_listenbrainz),
    item(SettingsCategory.Services, R.string.settings_listenbrainz_save,
        requirement = SettingsSearchRequirement.ListenBrainz, fallback = R.string.settings_listenbrainz),
    item(SettingsCategory.Services, R.string.settings_listenbrainz_disconnect,
        requirement = SettingsSearchRequirement.ListenBrainzConnected, fallback = R.string.settings_listenbrainz),
    item(SettingsCategory.Services, R.string.settings_setlistfm),
    item(SettingsCategory.Services, R.string.settings_setlistfm_save),
    item(SettingsCategory.Services, R.string.settings_pc_handoff),
    item(SettingsCategory.Services, R.string.settings_connect_pc),

    item(SettingsCategory.Library, R.string.settings_section_library,
        keywords = *intArrayOf(R.string.settings_category_library_detail)),
    item(SettingsCategory.Library, R.string.settings_audio_tags),
    item(SettingsCategory.Library, R.string.settings_watched_folder_rescan),
    item(SettingsCategory.Library, R.string.settings_offline_wifi_only),
    item(SettingsCategory.Library, R.string.settings_offline_storage),
    item(SettingsCategory.Library, R.string.settings_local_music),
    item(SettingsCategory.Library, R.string.settings_library_cleanup),
    item(SettingsCategory.Library, R.string.settings_clear_index_title),

    item(SettingsCategory.About, R.string.settings_section_about,
        keywords = *intArrayOf(R.string.settings_category_about_detail)),
    item(SettingsCategory.About, R.string.settings_about_qq_group),
    item(SettingsCategory.About, R.string.settings_about_website),
    item(SettingsCategory.About, R.string.settings_about_bug_report),
    item(SettingsCategory.About, R.string.update_check),
    item(SettingsCategory.About, R.string.settings_backup_export),
    item(SettingsCategory.About, R.string.settings_backup_restore),
    item(SettingsCategory.About, R.string.settings_error_log),
    SettingsSearchItem(null, R.string.settings_plugins, keywords = listOf(R.string.settings_plugins_summary),
        opensPlugins = true),
)

@Composable
internal fun rememberSettingsSearchResults(availability: SettingsSearchAvailability): List<SettingsSearchResult> {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration, availability) {
        indexedSettings.map { item ->
            val titleRes = if (item.titleRes == R.string.settings_import_font && availability.importedFont) {
                R.string.settings_reselect_font
            } else item.titleRes
            val anchorRes = if (item.anchorRes == R.string.settings_import_font && availability.importedFont) {
                R.string.settings_reselect_font
            } else item.anchorRes
            val fallback = when (item.requirement) {
                SettingsSearchRequirement.Always, SettingsSearchRequirement.ScheduledDark -> false
                SettingsSearchRequirement.DarkTime -> !availability.scheduledDarkEnabled
                SettingsSearchRequirement.CustomBackground -> !availability.customBackgroundReady
                SettingsSearchRequirement.BackgroundAdvanced -> !availability.backgroundAdvancedAvailable
                SettingsSearchRequirement.ImageBackground -> availability.customBackgroundMode != "image" ||
                    !availability.backgroundAdvancedAvailable
                SettingsSearchRequirement.TrackFade -> !availability.trackFadeEnabled
                SettingsSearchRequirement.ReplayGain -> !availability.replayGainEnabled
                SettingsSearchRequirement.LastFm -> !availability.lastFmEnabled
                SettingsSearchRequirement.LastFmConnected -> !availability.lastFmConnected
                SettingsSearchRequirement.ListenBrainz -> !availability.listenBrainzEnabled
                SettingsSearchRequirement.ListenBrainzConnected -> !availability.listenBrainzConnected
            }
            SettingsSearchResult(
                item = item,
                title = context.getString(titleRes),
                categoryTitle = item.category?.let { context.getString(it.title) }
                    ?: context.getString(R.string.settings_group_app),
                anchorTitle = context.getString(if (fallback) item.fallbackRes else anchorRes),
                keywords = item.keywords.joinToString(" ") { context.getString(it) },
            )
        }
    }
}

internal fun searchSettings(items: List<SettingsSearchResult>, query: String): List<SettingsSearchResult> {
    val words = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter(String::isNotEmpty)
    if (words.isEmpty()) return emptyList()
    return items.mapNotNull { result ->
        val title = result.title.lowercase(Locale.ROOT)
        val category = result.categoryTitle.lowercase(Locale.ROOT)
        val terms = result.keywords.lowercase(Locale.ROOT)
        if (words.any { it !in title && it !in category && it !in terms }) return@mapNotNull null
        val score = when {
            title == words.joinToString(" ") -> 0
            title.startsWith(words.first()) -> 1
            words.all { it in title } -> 2
            words.all { it in terms } -> 3
            else -> 4
        }
        score to result
    }.sortedWith(compareBy<Pair<Int, SettingsSearchResult>> { it.first }
        .thenBy { it.second.item.category?.ordinal ?: Int.MAX_VALUE })
        .map { it.second }
}
