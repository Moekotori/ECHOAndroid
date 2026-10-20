package app.echo.android.feature.player

import app.echo.android.design.EchoIcon
import androidx.activity.compose.BackHandler

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.EchoExpand
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.echoAccentColor
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.echoTheme
import app.echo.android.model.settings.EchoLyricsBackdrop
import app.echo.android.model.settings.EchoLyricsPageStyle
import kotlin.math.roundToInt
import app.echo.android.feature.player.R as L10nR

@Composable
internal fun LyricsSettingsPanel(
    lyricsFontFamily: FontFamily?,
    lyricsPageStyle: EchoLyricsPageStyle,
    playerPageStyle: String,
    onLyricsPageStyleChange: (String) -> Unit,
    lyricsBackdrop: String,
    onLyricsBackdropChange: (String) -> Unit,
    lyricsFontMode: String,
    importedFontUri: String?,
    lyricsFontScale: Float,
    lyricsColorMode: String,
    lyricsAlignment: String,
    lyricsLineSpacing: Float,
    lyricsBackgroundDim: Float,
    lyricsWordHighlightEnabled: Boolean,
    lyricsEstimatedWordHighlightEnabled: Boolean,
    lyricsWordHighlightIntensity: Float,
    lyricsImmersiveModeEnabled: Boolean,
    lyricsMotionMode: String,
    lyricAccent: Color,
    showTranslation: Boolean,
    showRomanization: Boolean,
    focusGlowEnabled: Boolean,
    hasTranslation: Boolean,
    hasRomanization: Boolean,
    showLyricsControlDeck: Boolean,
    onlineLyricsEnabled: Boolean,
    onDismiss: () -> Unit,
    onCloseLyrics: () -> Unit,
    onImportLyrics: () -> Unit,
    onImportLyricsFont: () -> Unit,
    onLyricsFontFamilyChange: (String) -> Unit,
    onLyricsFontScaleChange: (Float) -> Unit,
    onLyricsColorModeChange: (String) -> Unit,
    onLyricsAlignmentChange: (String) -> Unit,
    onLyricsLineSpacingChange: (Float) -> Unit,
    onLyricsBackgroundDimChange: (Float) -> Unit,
    onLyricsWordHighlightEnabledChange: (Boolean) -> Unit,
    onLyricsEstimatedWordHighlightEnabledChange: (Boolean) -> Unit,
    onLyricsWordHighlightIntensityChange: (Float) -> Unit,
    onLyricsImmersiveModeChange: (Boolean) -> Unit,
    onLyricsMotionModeChange: (String) -> Unit,
    onLyricsShowTranslationChange: (Boolean) -> Unit,
    onLyricsShowRomanizationChange: (Boolean) -> Unit,
    onLyricsFocusGlowChange: (Boolean) -> Unit,
    onShowLyricsControlDeckChange: (Boolean) -> Unit,
    onOnlineLyricsEnabledChange: (Boolean) -> Unit,
) {
    val dark = LocalEchoDarkTheme.current
    val titleColor = if (dark) Color.White else echoTheme().heading
    val mutedColor = if (dark) Color.White.copy(alpha = 0.65f) else echoTheme().muted
    val accent = echoAccentColor()
    var category by remember { mutableStateOf<LyricsSettingsCategory?>(null) }
    BackHandler(enabled = category != null) { category = null }
    val heading = stringResource(L10nR.string.feature_player_lyrics_settings_843bc9)
    val headerTitle = category?.let { stringResource(it.titleRes) } ?: heading
    val headerSubtitle = if (category == null) stringResource(L10nR.string.feature_player_font_color_and_display_b51a7b) else ""
    val showPreview = category != null && category != LyricsSettingsCategory.Content && category != LyricsSettingsCategory.Backdrop
    val scrollState = remember(category) { ScrollState(0) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = minOf(maxHeight * 0.72f, 600.dp))
                .settingsSheetSurface()
                .navigationBarsPadding(),
        ) {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                LyricsSettingsHandle(onDismiss)
                SettingsSheetHeader(
                    deeper = category != null,
                    title = headerTitle,
                    subtitle = headerSubtitle,
                    backDescription = heading,
                    closeDescription = stringResource(L10nR.string.feature_player_close_lyrics_settings_752454),
                    onBack = { category = null },
                    onClose = onDismiss,
                ) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(accent.copy(alpha = if (dark) 0.26f else 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) { EchoIcon(Icons.Rounded.Lyrics, null, tint = titleColor, modifier = Modifier.size(24.dp)) }
                }
            }
            EchoExpand(showPreview) {
                LyricsSettingsPreview(
                    pageStyle = lyricsPageStyle,
                    fontFamily = lyricsFontFamily,
                    fontScale = lyricsFontScale,
                    colorMode = lyricsColorMode,
                    alignment = lyricsAlignment,
                    lineSpacing = lyricsLineSpacing,
                    backgroundDim = lyricsBackgroundDim,
                    highlightEnabled = lyricsWordHighlightEnabled,
                    highlightIntensity = lyricsWordHighlightIntensity,
                    motionMode = lyricsMotionMode,
                    animateMotion = category == LyricsSettingsCategory.Motion,
                )
            }
            CompositionLocalProvider(LocalContentColor provides titleColor) {
                Column(
                    Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(scrollState)
                        .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 18.dp),
                ) {
                    SettingsPageSwitcher(state = category, isDeeper = { it != null }) { page ->
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (page == null) LyricsSettingsMenu(
                                appearanceSummary = stringResource(playerPageStyleLabel(app.echo.android.model.settings.EchoPlayerPageStyle.fromId(playerPageStyle))),
                                backdropSummary = stringResource(lyricsBackdropLabel(EchoLyricsBackdrop.fromId(lyricsBackdrop))),
                                typographySummary = "${lyricsFontDetail(lyricsFontMode, importedFontUri)} · ${(lyricsFontScale * 100).roundToInt()}%",
                                colorSummary = lyricsColorLabel(lyricsColorMode),
                                motionSummary = lyricsMotionLabel(lyricsMotionMode),
                                onSelect = { category = it },
                            )
                            if (page == LyricsSettingsCategory.Appearance) {
                                PlayerPageStyleSelector(app.echo.android.model.settings.EchoPlayerPageStyle.fromId(playerPageStyle), onLyricsPageStyleChange)
                            }
                            if (page == LyricsSettingsCategory.Backdrop) {
                                PlayerBackdropChoices(
                                    selected = EchoLyricsBackdrop.fromId(lyricsBackdrop),
                                    flatSurface = playerPageStyle != app.echo.android.model.settings.EchoPlayerPageStyle.Classic.id,
                                    onSelect = onLyricsBackdropChange,
                                    onUseClassic = { onLyricsPageStyleChange(app.echo.android.model.settings.EchoPlayerPageStyle.Classic.id) },
                                )
                            }
                            if (page == LyricsSettingsCategory.Typography) {
                                LyricsSettingChoices(options = lyricsFontOptions(), selected = lyricsFontMode, onSelect = { value ->
                                    if (value == "imported" && importedFontUri.isNullOrBlank()) {
                                        onDismiss(); onImportLyricsFont()
                                    } else onLyricsFontFamilyChange(value)
                                })
                                if (!importedFontUri.isNullOrBlank()) TextButton(onClick = { onDismiss(); onImportLyricsFont() }) {
                                    Text(stringResource(L10nR.string.lyrics_setting_replace_font))
                                }
                                SettingsGroupLabel(stringResource(L10nR.string.feature_player_alignment_66dbdb))
                                LyricsSettingChoices(options = LyricsAlignmentOptions.map { it.value to lyricsAlignmentLabel(it.value) },
                                    selected = lyricsAlignment, onSelect = onLyricsAlignmentChange)
                                LyricsSettingSlider(stringResource(L10nR.string.feature_player_type_size_8ff7ee), lyricsFontScale, 0.50f..1.28f, 1f, onLyricsFontScaleChange)
                                LyricsSettingSlider(stringResource(L10nR.string.feature_player_line_spacing_ecbb6f), lyricsLineSpacing, 0.50f..1.38f, 1f, onLyricsLineSpacingChange)
                            }
                            if (page == LyricsSettingsCategory.Color) {
                                Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    LyricsColorOptions.forEach { option ->
                                        LyricsColorSwatch(option, selected = option.value == lyricsColorMode,
                                            onClick = { onLyricsColorModeChange(option.value) }, modifier = Modifier.weight(1f))
                                    }
                                }
                                LyricsSettingSlider(stringResource(L10nR.string.feature_player_dim_be8c03), lyricsBackgroundDim, 0f..0.78f, 0f, onLyricsBackgroundDimChange)
                                LyricsSettingToggle(stringResource(L10nR.string.feature_player_emphasis_6517be), focusGlowEnabled, onLyricsFocusGlowChange)
                            }
                            if (page == LyricsSettingsCategory.Motion) {
                                LyricsSettingChoices(options = LyricsMotionOptions.map { it.value to lyricsMotionLabel(it.value) },
                                    selected = lyricsMotionMode, onSelect = onLyricsMotionModeChange)
                                LyricsSettingToggle(stringResource(L10nR.string.lyrics_setting_word_highlight), lyricsWordHighlightEnabled, onLyricsWordHighlightEnabledChange)
                                app.echo.android.design.EchoExpand(lyricsWordHighlightEnabled) {
                                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        HorizontalDivider(color = mutedColor.copy(alpha = 0.16f))
                                        LyricsSettingToggle(
                                            stringResource(L10nR.string.lyrics_setting_estimated_word_highlight),
                                            lyricsEstimatedWordHighlightEnabled,
                                            onLyricsEstimatedWordHighlightEnabledChange,
                                            hint = stringResource(L10nR.string.lyrics_setting_estimated_word_highlight_hint),
                                        )
                                        LyricsSettingSlider(stringResource(L10nR.string.lyrics_setting_highlight_strength), lyricsWordHighlightIntensity,
                                            0.45f..1.35f, 1f, onLyricsWordHighlightIntensityChange)
                                    }
                                }
                                LyricsSettingToggle(stringResource(L10nR.string.feature_player_immersive_d86793), lyricsImmersiveModeEnabled, onLyricsImmersiveModeChange)
                            }
                            if (page == LyricsSettingsCategory.Content) {
                                val noContent = stringResource(L10nR.string.lyrics_setting_no_content)
                                LyricsSettingToggle(stringResource(L10nR.string.feature_player_translation_53c7ce), showTranslation,
                                    onLyricsShowTranslationChange, hint = if (!hasTranslation) noContent else null)
                                LyricsSettingToggle(stringResource(L10nR.string.feature_player_romaji_6ad0ab), showRomanization,
                                    onLyricsShowRomanizationChange, hint = if (!hasRomanization) noContent else null)
                                LyricsSettingToggle(stringResource(L10nR.string.feature_player_sync_tools_ef1217), showLyricsControlDeck, onShowLyricsControlDeckChange)
                                LyricsSettingToggle(stringResource(L10nR.string.feature_player_online_lyrics_21c928), onlineLyricsEnabled, onOnlineLyricsEnabledChange)
                                PlaybackActionRow(Icons.Rounded.UploadFile, stringResource(L10nR.string.feature_player_import_lyrics_e7494e),
                                    framed = false, onClick = { onDismiss(); onImportLyrics() })
                                PlaybackActionRow(Icons.Rounded.Album, stringResource(L10nR.string.feature_player_back_to_cover_815543),
                                    framed = false, onClick = { onDismiss(); onCloseLyrics() })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsColorSwatch(
    option: LyricsColorOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = LocalEchoDarkTheme.current
    val ringColor by animateColorAsState(
        targetValue = if (selected) app.echo.android.design.echoAccentColor() else if (dark) Color.White.copy(alpha = 0.18f) else echoTheme().heading.copy(alpha = 0.12f),
        animationSpec = tween(durationMillis = if (LocalEchoEffectivePerformanceMode.current.isLightweight) 0 else 180, easing = LyricsSettingsMotionEasing),
        label = "lyrics-palette-ring",
    )
    Column(
        modifier = modifier
            .heightIn(min = 78.dp)
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(top = 5.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .border(BorderStroke(if (selected) 2.5.dp else 1.dp, ringColor), CircleShape)
                .padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(option.color)
                    .border(BorderStroke(1.dp, if (dark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.08f)), CircleShape),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            lyricsColorLabel(option.value),
            color = if (dark) Color.White.copy(alpha = 0.9f) else echoTheme().heading,
            style = MaterialTheme.typography.labelSmall.copy(lineHeight = 14.sp),
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun lyricsColorLabel(mode: String): String = when (mode) {
    "warm" -> stringResource(L10nR.string.feature_player_warm_060f63)
    "blue" -> stringResource(L10nR.string.feature_player_blue_4a9e32)
    "violet" -> stringResource(L10nR.string.feature_player_violet_a03e22)
    "mint" -> stringResource(L10nR.string.feature_player_green_d7b519)
    else -> stringResource(L10nR.string.feature_player_white_cc6c04)
}

@Composable
private fun lyricsAlignmentLabel(mode: String): String = when (mode) {
    "start" -> stringResource(L10nR.string.feature_player_left_f9d864)
    "dynamic" -> stringResource(L10nR.string.feature_player_stage_fffe7d)
    "vertical" -> stringResource(L10nR.string.lyrics_alignment_vertical)
    else -> stringResource(L10nR.string.feature_player_center_3e4c92)
}

@Composable
private fun lyricsMotionLabel(mode: String): String = when (mode) {
    "calm" -> stringResource(L10nR.string.feature_player_calm_207bcb)
    "stage" -> stringResource(L10nR.string.feature_player_stage_fffe7d)
    else -> stringResource(L10nR.string.feature_player_smooth_4989bb)
}

@Composable
private fun lyricsLayoutDetail(alignment: String, spacing: Float): String =
    "${lyricsAlignmentLabel(alignment)} / ${(spacing.coerceIn(0.50f, 1.38f) * 100f).roundToInt()}%"

@Composable
private fun lyricsFontOptions(): List<Pair<String, String>> = buildList {
    add("system" to stringResource(L10nR.string.feature_player_system_90f402))
    add("serif" to stringResource(L10nR.string.feature_player_serif_fb7b05))
    add("monospace" to stringResource(L10nR.string.feature_player_mono_ee96ee))
    add("imported" to stringResource(L10nR.string.feature_player_import_688061))
}

@Composable
private fun lyricsFontDetail(mode: String, importedFontUri: String?): String =
    when (mode) {
        "serif" -> stringResource(L10nR.string.feature_player_system_serif_ee6148)
        "monospace" -> stringResource(L10nR.string.feature_player_system_mono_6ca8fa)
        "imported" -> importedFontUri?.substringAfterLast('/')?.takeLast(18)?.let { name ->
            stringResource(L10nR.string.feature_player_import_name_a3094e, (name).toString())
        } ?: stringResource(L10nR.string.feature_player_choose_a_font_file_a60802)
        else -> stringResource(L10nR.string.feature_player_system_font_8ddbe6)
    }

private data class LyricsColorOption(
    val value: String,
    val color: Color,
)

private data class LyricsTextOption(
    val value: String,
)

private val LyricsColorOptions = listOf(
    LyricsColorOption("white", Color.White),
    LyricsColorOption("warm", Color(0xFFFFD6A0)),
    LyricsColorOption("blue", Color(0xFF9ED8FF)),
    LyricsColorOption("violet", Color(0xFFD9C2FF)),
    LyricsColorOption("mint", Color(0xFFA9F3D0)),
)

private val LyricsAlignmentOptions = listOf(
    LyricsTextOption("center"),
    LyricsTextOption("start"),
    LyricsTextOption("dynamic"),
    LyricsTextOption("vertical"),
)

private val LyricsMotionOptions = listOf(
    LyricsTextOption("calm"),
    LyricsTextOption("smooth"),
    LyricsTextOption("stage"),
)



@Composable
internal fun lyricsColorForMode(mode: String): Color {
    val color = LyricsColorOptions.firstOrNull { it.value == mode }?.color ?: Color.White
    return if (LocalEchoDarkTheme.current) color else if (mode == "white") MaterialTheme.colorScheme.onSurface
    else androidx.compose.ui.graphics.lerp(color, Color(0xFF29252A), 0.62f)
}
