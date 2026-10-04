package app.echo.android.feature.player

import app.echo.android.design.EchoIcon

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.echoTheme
import app.echo.android.model.settings.EchoLyricsPageStyle
import kotlin.math.roundToInt
import app.echo.android.feature.player.R as L10nR

@Composable
internal fun LyricsSettingsPanel(
    lyricsFontFamily: FontFamily?,
    lyricsPageStyle: EchoLyricsPageStyle,
    playerPageStyle: String,
    onLyricsPageStyleChange: (String) -> Unit,
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
    var typeExpanded by remember { mutableStateOf(true) }
    var colorExpanded by remember { mutableStateOf(false) }
    var motionExpanded by remember { mutableStateOf(false) }
    var contentExpanded by remember { mutableStateOf(false) }
    val heading = stringResource(L10nR.string.feature_player_lyrics_settings_843bc9)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().height(maxHeight * 0.88f)
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(if (dark) echoTheme().panel else Color(0xFFF4F1F3))
                .navigationBarsPadding(),
        ) {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LyricsSettingsHandle(onDismiss)
                Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EchoIcon(Icons.Rounded.Lyrics, null, tint = app.echo.android.design.echoAccentColor(), modifier = Modifier.size(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text(heading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = titleColor)
                        Text(stringResource(L10nR.string.feature_player_font_color_and_display_b51a7b),
                            style = MaterialTheme.typography.bodySmall, color = mutedColor)
                    }
                    GlyphButton(Icons.Rounded.Close, stringResource(L10nR.string.feature_player_close_lyrics_settings_752454),
                        touchSize = 48.dp, iconSize = 22.dp, tint = titleColor, background = Color.Transparent, onClick = onDismiss)
                }
            }
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
            )
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides titleColor) {
                Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(L10nR.string.player_appearance_title), style = MaterialTheme.typography.titleSmall)
                    PlayerPageStyleSelector(app.echo.android.model.settings.EchoPlayerPageStyle.fromId(playerPageStyle), onLyricsPageStyleChange)
                    PlaybackSettingsSection(Icons.Rounded.TextFields,
                        stringResource(L10nR.string.lyrics_setting_typography),
                        "${lyricsFontDetail(lyricsFontMode, importedFontUri)} · ${(lyricsFontScale * 100).roundToInt()}%",
                        expanded = typeExpanded, onToggleExpanded = { typeExpanded = !typeExpanded }) {
                        androidx.compose.foundation.layout.FlowRow(
                            Modifier.fillMaxWidth().selectableGroup(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            lyricsFontOptions().forEach { (value, label) ->
                                PlaybackChoiceChip(text = label, selected = lyricsFontMode == value, fillWidth = false, onClick = {
                                    if (value == "imported" && importedFontUri.isNullOrBlank()) {
                                        onDismiss(); onImportLyricsFont()
                                    } else onLyricsFontFamilyChange(value)
                                })
                            }
                        }
                        if (!importedFontUri.isNullOrBlank()) TextButton(onClick = { onDismiss(); onImportLyricsFont() }) {
                            Text(stringResource(L10nR.string.lyrics_setting_replace_font))
                        }
                        Text(stringResource(L10nR.string.feature_player_alignment_66dbdb), style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LyricsAlignmentOptions.forEach { option ->
                                PlaybackChoiceChip(text = lyricsAlignmentLabel(option.value), selected = lyricsAlignment == option.value,
                                    onClick = { onLyricsAlignmentChange(option.value) }, fillWidth = false)
                            }
                        }
                        LyricsSettingSlider(stringResource(L10nR.string.feature_player_type_size_8ff7ee), lyricsFontScale, 0.50f..1.28f, 1f, onLyricsFontScaleChange)
                        LyricsSettingSlider(stringResource(L10nR.string.feature_player_line_spacing_ecbb6f), lyricsLineSpacing, 0.50f..1.38f, 1f, onLyricsLineSpacingChange)
                    }
                    PlaybackSettingsSection(Icons.Rounded.ColorLens,
                        stringResource(L10nR.string.lyrics_setting_color_background), lyricsColorLabel(lyricsColorMode),
                        expanded = colorExpanded, onToggleExpanded = { colorExpanded = !colorExpanded }) {
                        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            LyricsColorOptions.forEach { option ->
                                LyricsColorSwatch(option, selected = option.value == lyricsColorMode,
                                    onClick = { onLyricsColorModeChange(option.value) }, modifier = Modifier.weight(1f))
                            }
                        }
                        LyricsSettingSlider(stringResource(L10nR.string.feature_player_dim_be8c03), lyricsBackgroundDim, 0f..0.78f, 0f, onLyricsBackgroundDimChange)
                        LyricsSettingToggle(stringResource(L10nR.string.feature_player_emphasis_6517be), focusGlowEnabled, onLyricsFocusGlowChange)
                    }
                    PlaybackSettingsSection(Icons.Rounded.Lyrics,
                        stringResource(L10nR.string.lyrics_setting_motion), lyricsMotionLabel(lyricsMotionMode),
                        expanded = motionExpanded, onToggleExpanded = { motionExpanded = !motionExpanded }) {
                        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            LyricsMotionOptions.forEach { option ->
                                PlaybackChoiceChip(text = lyricsMotionLabel(option.value), selected = lyricsMotionMode == option.value,
                                    onClick = { onLyricsMotionModeChange(option.value) }, modifier = Modifier.weight(1f))
                            }
                        }
                        LyricsSettingToggle(stringResource(L10nR.string.lyrics_setting_word_highlight), lyricsWordHighlightEnabled, onLyricsWordHighlightEnabledChange)
                        app.echo.android.design.EchoExpand(lyricsWordHighlightEnabled) {
                            LyricsSettingToggle(
                                stringResource(L10nR.string.lyrics_setting_estimated_word_highlight),
                                lyricsEstimatedWordHighlightEnabled,
                                onLyricsEstimatedWordHighlightEnabledChange,
                                hint = stringResource(L10nR.string.lyrics_setting_estimated_word_highlight_hint),
                            )
                            LyricsSettingSlider(stringResource(L10nR.string.lyrics_setting_highlight_strength), lyricsWordHighlightIntensity,
                                0.45f..1.35f, 1f, onLyricsWordHighlightIntensityChange)
                        }
                        LyricsSettingToggle(stringResource(L10nR.string.feature_player_immersive_d86793), lyricsImmersiveModeEnabled, onLyricsImmersiveModeChange)
                    }
                    PlaybackSettingsSection(Icons.Rounded.Translate,
                        stringResource(L10nR.string.lyrics_setting_content),
                        stringResource(L10nR.string.lyrics_setting_content_summary),
                        expanded = contentExpanded, onToggleExpanded = { contentExpanded = !contentExpanded }) {
                        val noContent = stringResource(L10nR.string.lyrics_setting_no_content)
                        LyricsSettingToggle(stringResource(L10nR.string.feature_player_translation_53c7ce), showTranslation,
                            onLyricsShowTranslationChange, hint = if (!hasTranslation) noContent else null)
                        LyricsSettingToggle(stringResource(L10nR.string.feature_player_romaji_6ad0ab), showRomanization,
                            onLyricsShowRomanizationChange, hint = if (!hasRomanization) noContent else null)
                        LyricsSettingToggle(stringResource(L10nR.string.feature_player_sync_tools_ef1217), showLyricsControlDeck, onShowLyricsControlDeckChange)
                        LyricsSettingToggle(stringResource(L10nR.string.feature_player_online_lyrics_21c928), onlineLyricsEnabled, onOnlineLyricsEnabledChange)
                    }
                    PlaybackActionRow(Icons.Rounded.UploadFile, stringResource(L10nR.string.feature_player_import_lyrics_e7494e),
                        onClick = { onDismiss(); onImportLyrics() })
                    PlaybackActionRow(Icons.Rounded.Album, stringResource(L10nR.string.feature_player_back_to_cover_815543),
                        onClick = { onDismiss(); onCloseLyrics() })
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
            .height(78.dp)
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
            maxLines = 1,
            overflow = TextOverflow.Visible,
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
