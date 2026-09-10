package app.echo.android.feature.settings

import app.echo.android.design.backgroundMaxBlur
import app.echo.android.design.echoAnimateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import app.echo.android.design.echoClickable
import app.echo.android.design.echoCombinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoGlassPanel
import app.echo.android.design.EchoHapticKind
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.LocalEchoHapticsEnabled
import app.echo.android.design.PageChrome
import app.echo.android.design.performEchoHaptic
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.settings.EchoBackgroundStyle
import app.echo.android.model.settings.EchoAppLanguage
import app.echo.android.model.settings.EchoEffectivePerformanceMode
import app.echo.android.model.settings.EchoPerformanceMode
import kotlin.math.roundToInt

@Composable
internal fun SettingsInterfaceContent(
    dynamicArtworkEnabled: Boolean,
    compactModeEnabled: Boolean,
    performanceMode: String,
    effectivePerformanceMode: String,
    appLanguage: String,
    onDynamicArtworkEnabledChange: (Boolean) -> Unit,
    onCompactModeEnabledChange: (Boolean) -> Unit,
    onPerformanceModeChange: (String) -> Unit,
    onAppLanguageChange: (String) -> Unit,
) {
            SettingsSectionCard(
                title = stringResource(R.string.settings_section_interface),
            ) {
                SettingsChoiceGroupRow(
                    title = stringResource(R.string.settings_language),
                    detail = languageDetail(appLanguage),
                    options = languageOptions(),
                    selectedValue = appLanguage,
                    onOptionSelected = onAppLanguageChange,
                )
                SettingsChoiceGroupRow(
                    title = stringResource(R.string.settings_performance_mode),
                    detail = performanceModeDetail(performanceMode, effectivePerformanceMode),
                    options = performanceModeOptions(),
                    selectedValue = performanceMode,
                    onOptionSelected = onPerformanceModeChange,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_dynamic_artwork),
                    detail = stringResource(R.string.settings_dynamic_artwork_detail),
                    checked = dynamicArtworkEnabled,
                    onCheckedChange = onDynamicArtworkEnabledChange,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_compact_mode),
                    detail = stringResource(R.string.settings_compact_mode_detail),
                    checked = compactModeEnabled,
                    onCheckedChange = onCompactModeEnabledChange,
                )
            }
}
