package app.echo.android.feature.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoAccentDeep
import app.echo.android.design.EchoGlassBorder
import app.echo.android.design.EchoGlassCyan
import app.echo.android.design.EchoHomeMist
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.EchoSectionTitle
import app.echo.android.design.PageChrome
import app.echo.android.design.echoDarkGlassBorder
import app.echo.android.design.echoGlassContainerBrush
import app.echo.android.design.echoGlassRowBrush
import app.echo.android.model.playback.EchoPlaybackStatus
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    status: EchoPlaybackStatus,
    trackCount: Int,
    albumCount: Int,
    artistCount: Int,
    dynamicArtworkEnabled: Boolean,
    compactModeEnabled: Boolean,
    pcHandoffEnabled: Boolean,
    discordPresenceViaPcEnabled: Boolean,
    showLyricsControlDeck: Boolean,
    onlineLyricsEnabled: Boolean,
    usbExclusiveEnabled: Boolean,
    usbExclusiveAutoRequestOnStartup: Boolean,
    usbExclusiveTestResult: String,
    customBackgroundMode: String,
    customBackgroundUri: String?,
    customBackgroundBlur: Float,
    customBackgroundBrightness: Float,
    customBackgroundGlass: Float,
    uiFontFamily: String,
    uiFontScale: Float,
    uiDensityScale: Float,
    lyricsFontFamily: String,
    lyricsFontScale: Float,
    importedFontUri: String?,
    themeMode: String,
    scheduledDarkModeEnabled: Boolean,
    scheduledDarkStartMinute: Int,
    scheduledDarkEndMinute: Int,
    lastFmEnabled: Boolean,
    lastFmApiKey: String?,
    lastFmSharedSecret: String?,
    lastFmSessionKey: String?,
    lastFmStatusLabel: String,
    lastFmErrorLabel: String?,
    lastFmWebAuthPending: Boolean,
    lastFmApiKeyLocked: Boolean,
    lastFmSharedSecretLocked: Boolean,
    onDynamicArtworkEnabledChange: (Boolean) -> Unit,
    onCompactModeEnabledChange: (Boolean) -> Unit,
    onPcHandoffEnabledChange: (Boolean) -> Unit,
    onDiscordPresenceViaPcEnabledChange: (Boolean) -> Unit,
    onShowLyricsControlDeckChange: (Boolean) -> Unit,
    onOnlineLyricsEnabledChange: (Boolean) -> Unit,
    onUsbExclusiveEnabledChange: (Boolean) -> Unit,
    onUsbExclusiveAutoRequestOnStartupChange: (Boolean) -> Unit,
    onTestUsbExclusiveDriver: () -> Unit,
    onPickImageBackground: () -> Unit,
    onPickVideoBackground: () -> Unit,
    onClearCustomBackground: () -> Unit,
    onCustomBackgroundBlurChange: (Float) -> Unit,
    onCustomBackgroundBrightnessChange: (Float) -> Unit,
    onCustomBackgroundGlassChange: (Float) -> Unit,
    onUiFontFamilyChange: (String) -> Unit,
    onUiFontScaleChange: (Float) -> Unit,
    onUiDensityScaleChange: (Float) -> Unit,
    onLyricsFontFamilyChange: (String) -> Unit,
    onLyricsFontScaleChange: (Float) -> Unit,
    onImportUiFont: () -> Unit,
    onImportLyricsFont: () -> Unit,
    onClearImportedFont: () -> Unit,
    onThemeModeChange: (String) -> Unit,
    onScheduledDarkModeEnabledChange: (Boolean) -> Unit,
    onScheduledDarkStartMinuteChange: (Int) -> Unit,
    onScheduledDarkEndMinuteChange: (Int) -> Unit,
    onLastFmEnabledChange: (Boolean) -> Unit,
    onStartLastFmWebAuth: () -> Unit,
    onCompleteLastFmWebAuth: () -> Unit,
    onDisconnectLastFm: () -> Unit,
    onOpenLastFmApiAccounts: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenConnect: () -> Unit,
) {
    val sectionGap = if (compactModeEnabled) 8.dp else 14.dp
    var customBackgroundExpanded by rememberSaveable { mutableStateOf(true) }
    var fontSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var lastFmApiKeyInput by rememberSaveable(lastFmApiKey) { mutableStateOf(lastFmApiKey.orEmpty()) }
    var lastFmSecretInput by rememberSaveable(lastFmSharedSecret) { mutableStateOf(lastFmSharedSecret.orEmpty()) }

    PageChrome(
        title = "设置",
        subtitle = "移动端偏好与 ECHO 互联",
        badge = "设置",
        scrollable = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(sectionGap)) {
            SettingsHeroCard(
                status = status,
                trackCount = trackCount,
                albumCount = albumCount,
                artistCount = artistCount,
                dynamicArtwork = dynamicArtworkEnabled,
            )
            SettingsSectionCard(title = "主题") {
                SettingsChoiceGroupRow(
                    icon = Icons.Rounded.Palette,
                    title = "显示模式",
                    detail = themeDetail(themeMode),
                    options = themeOptions(),
                    selectedValue = themeMode,
                    onOptionSelected = onThemeModeChange,
                )
                SettingsSwitchRow(
                    icon = Icons.Rounded.Settings,
                    title = "定时开启深色模式",
                    detail = "${formatMinuteOfDay(scheduledDarkStartMinute)} - ${formatMinuteOfDay(scheduledDarkEndMinute)} 自动使用深色",
                    checked = scheduledDarkModeEnabled,
                    onCheckedChange = onScheduledDarkModeEnabledChange,
                )
                if (scheduledDarkModeEnabled) {
                    SettingsSliderRow(
                        icon = Icons.Rounded.Speed,
                        title = "深色开始时间",
                        detail = formatMinuteOfDay(scheduledDarkStartMinute),
                        value = scheduledDarkStartMinute.toFloat(),
                        valueRange = 0f..1439f,
                        steps = 95,
                        onValueChange = { onScheduledDarkStartMinuteChange(it.roundToQuarterHour()) },
                    )
                    SettingsSliderRow(
                        icon = Icons.Rounded.Speed,
                        title = "深色结束时间",
                        detail = formatMinuteOfDay(scheduledDarkEndMinute),
                        value = scheduledDarkEndMinute.toFloat(),
                        valueRange = 0f..1439f,
                        steps = 95,
                        onValueChange = { onScheduledDarkEndMinuteChange(it.roundToQuarterHour()) },
                    )
                }
            }
            SettingsSectionCard(title = "界面") {
                SettingsSwitchRow(
                    icon = Icons.Rounded.Palette,
                    title = "动态封面氛围",
                    detail = "播放页和迷你播放器跟随封面微调背景",
                    checked = dynamicArtworkEnabled,
                    onCheckedChange = onDynamicArtworkEnabledChange,
                )
                SettingsSwitchRow(
                    icon = Icons.Rounded.Speed,
                    title = "紧凑显示",
                    detail = "为小屏幕减少卡片间距",
                    checked = compactModeEnabled,
                    onCheckedChange = onCompactModeEnabledChange,
                )
            }
            SettingsSectionCard(
                title = "自定义背景",
                collapsible = true,
                expanded = customBackgroundExpanded,
                onExpandedChange = { customBackgroundExpanded = it },
                persistentContent = {
                    SettingsBackgroundSourceRow(
                        mode = customBackgroundMode,
                        uri = customBackgroundUri,
                        onPickImageBackground = onPickImageBackground,
                        onPickVideoBackground = onPickVideoBackground,
                        onClearCustomBackground = onClearCustomBackground,
                    )
                },
            ) {
                SettingsSliderRow(
                    icon = Icons.Rounded.Palette,
                    title = "毛玻璃模糊",
                    detail = "${customBackgroundBlur.roundToInt()} dp",
                    value = customBackgroundBlur,
                    valueRange = 0f..80f,
                    steps = 15,
                    onValueChange = onCustomBackgroundBlurChange,
                )
                SettingsSliderRow(
                    icon = Icons.Rounded.Speed,
                    title = "背景亮度",
                    detail = "${(customBackgroundBrightness * 100f).roundToInt()}%",
                    value = customBackgroundBrightness,
                    valueRange = 0.35f..1.15f,
                    steps = 15,
                    onValueChange = onCustomBackgroundBrightnessChange,
                )
                SettingsSliderRow(
                    icon = Icons.Rounded.Palette,
                    title = "玻璃覆盖",
                    detail = "${(customBackgroundGlass * 100f).roundToInt()}%",
                    value = customBackgroundGlass,
                    valueRange = 0.08f..0.90f,
                    steps = 13,
                    onValueChange = onCustomBackgroundGlassChange,
                )
            }
            SettingsSectionCard(
                title = "字体",
                collapsible = true,
                expanded = fontSectionExpanded,
                onExpandedChange = { fontSectionExpanded = it },
            ) {
                SettingsChoiceGroupRow(
                    icon = Icons.Rounded.Palette,
                    title = "界面字体",
                    detail = fontDetail(uiFontFamily, importedFontUri),
                    options = fontOptions(importedFontUri),
                    selectedValue = uiFontFamily,
                    onOptionSelected = { value ->
                        if (value == "imported" && importedFontUri.isNullOrBlank()) {
                            onImportUiFont()
                        } else {
                            onUiFontFamilyChange(value)
                        }
                    },
                )
                SettingsSliderRow(
                    icon = Icons.Rounded.Speed,
                    title = "界面字号",
                    detail = "${(uiFontScale * 100f).roundToInt()}%",
                    value = uiFontScale,
                    valueRange = 0.88f..1.18f,
                    steps = 14,
                    onValueChange = onUiFontScaleChange,
                )
                SettingsSliderRow(
                    icon = Icons.Rounded.Settings,
                    title = "界面密度",
                    detail = "${(uiDensityScale * 100f).roundToInt()}%",
                    value = uiDensityScale,
                    valueRange = 0.90f..1.12f,
                    steps = 10,
                    onValueChange = onUiDensityScaleChange,
                )
                SettingsChoiceGroupRow(
                    icon = Icons.Rounded.Lyrics,
                    title = "歌词字体",
                    detail = fontDetail(lyricsFontFamily, importedFontUri),
                    options = fontOptions(importedFontUri),
                    selectedValue = lyricsFontFamily,
                    onOptionSelected = { value ->
                        if (value == "imported" && importedFontUri.isNullOrBlank()) {
                            onImportLyricsFont()
                        } else {
                            onLyricsFontFamilyChange(value)
                        }
                    },
                )
                SettingsSliderRow(
                    icon = Icons.Rounded.Lyrics,
                    title = "歌词字号",
                    detail = "${(lyricsFontScale * 100f).roundToInt()}%",
                    value = lyricsFontScale,
                    valueRange = 0.82f..1.28f,
                    steps = 22,
                    onValueChange = onLyricsFontScaleChange,
                )
                SettingsActionRow(
                    icon = Icons.Rounded.Settings,
                    title = "重新选择字体文件",
                    detail = if (importedFontUri.isNullOrBlank()) "导入 .ttf / .otf 文件" else "当前导入 ${importedFontUri.substringAfterLast('/').takeLast(28)}",
                    onClick = onImportUiFont,
                )
                SettingsActionRow(
                    icon = Icons.Rounded.Settings,
                    title = "清除导入字体",
                    detail = if (importedFontUri.isNullOrBlank()) "没有导入字体" else "界面和歌词会回退到系统字体",
                    enabled = !importedFontUri.isNullOrBlank(),
                    onClick = onClearImportedFont,
                )
            }
            SettingsSectionCard(title = "播放") {
                SettingsSwitchRow(
                    icon = Icons.Rounded.Lyrics,
                    title = "歌词同步工具",
                    detail = "在歌词页显示导入、格式和偏移微调面板",
                    checked = showLyricsControlDeck,
                    onCheckedChange = onShowLyricsControlDeckChange,
                )
                SettingsSwitchRow(
                    icon = Icons.Rounded.Lyrics,
                    title = "网络歌词",
                    detail = "无内嵌或本地歌词时自动尝试网易云和 LRCLIB",
                    checked = onlineLyricsEnabled,
                    onCheckedChange = onOnlineLyricsEnabledChange,
                )
                SettingsSwitchRow(
                    icon = Icons.Rounded.Usb,
                    title = "USB 独占输出",
                    detail = usbExclusiveDetail(status),
                    checked = usbExclusiveEnabled,
                    onCheckedChange = onUsbExclusiveEnabledChange,
                )
                SettingsSwitchRow(
                    icon = Icons.Rounded.Speed,
                    title = "启动时自动请求独占",
                    detail = if (usbExclusiveAutoRequestOnStartup) {
                        "上次未关闭独占时，下次启动会自动请求 USB DAC"
                    } else {
                        "重启后默认关闭独占，需要手动打开"
                    },
                    checked = usbExclusiveAutoRequestOnStartup,
                    onCheckedChange = onUsbExclusiveAutoRequestOnStartupChange,
                )
                SettingsActionRow(
                    icon = Icons.Rounded.Speed,
                    title = "测试 USB 独占驱动",
                    detail = usbExclusiveTestDetail(status, usbExclusiveTestResult),
                    enabled = status.diagnostics.usbConnected,
                    actionLabel = "测试",
                    onClick = onTestUsbExclusiveDriver,
                )
            }
            SettingsSectionCard(title = "互联") {
                LastFmSettingsPanel(
                    enabled = lastFmEnabled,
                    connected = !lastFmSessionKey.isNullOrBlank(),
                    statusLabel = lastFmStatusLabel,
                    errorLabel = lastFmErrorLabel,
                    webAuthPending = lastFmWebAuthPending,
                    apiKey = lastFmApiKeyInput,
                    sharedSecret = lastFmSecretInput,
                    apiKeyLocked = lastFmApiKeyLocked,
                    sharedSecretLocked = lastFmSharedSecretLocked,
                    onEnabledChange = onLastFmEnabledChange,
                    onApiKeyChange = { lastFmApiKeyInput = it },
                    onSharedSecretChange = { lastFmSecretInput = it },
                    onStartWebAuth = onStartLastFmWebAuth,
                    onCompleteWebAuth = onCompleteLastFmWebAuth,
                    onDisconnect = onDisconnectLastFm,
                    onOpenApiAccounts = onOpenLastFmApiAccounts,
                )
                SettingsSwitchRow(
                    icon = Icons.Rounded.Devices,
                    title = "PC 接力入口",
                    detail = "保留桌面端连接与远程控制入口",
                    checked = pcHandoffEnabled,
                    onCheckedChange = onPcHandoffEnabledChange,
                )
                SettingsSwitchRow(
                    icon = Icons.Rounded.Devices,
                    title = "Discord Rich Presence",
                    detail = if (pcHandoffEnabled) {
                        "通过 PC ECHO 把手机播放状态同步到 Discord"
                    } else {
                        "需要先开启 PC 接力入口"
                    },
                    checked = discordPresenceViaPcEnabled,
                    onCheckedChange = onDiscordPresenceViaPcEnabledChange,
                )
                SettingsActionRow(
                    icon = Icons.Rounded.Devices,
                    title = "连接 PC ECHO",
                    detail = if (pcHandoffEnabled) "管理配对和远程播放状态" else "PC 接力入口已关闭",
                    enabled = pcHandoffEnabled,
                    onClick = onOpenConnect,
                )
            }
            SettingsSectionCard(title = "曲库") {
                SettingsActionRow(
                    icon = Icons.Rounded.LibraryMusic,
                    title = "本地音乐",
                    detail = "查看歌曲、专辑、艺术家和扫描入口",
                    onClick = onOpenLibrary,
                )
            }
            Spacer(Modifier.height(156.dp))
        }
    }
}

@Composable
private fun LastFmSettingsPanel(
    enabled: Boolean,
    connected: Boolean,
    statusLabel: String,
    errorLabel: String?,
    webAuthPending: Boolean,
    apiKey: String,
    sharedSecret: String,
    apiKeyLocked: Boolean,
    sharedSecretLocked: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSharedSecretChange: (String) -> Unit,
    onStartWebAuth: () -> Unit,
    onCompleteWebAuth: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenApiAccounts: () -> Unit,
) {
    SettingsSwitchRow(
        icon = Icons.Rounded.Devices,
        title = "Last.fm Connect",
        detail = errorLabel ?: statusLabel,
        checked = enabled,
        onCheckedChange = onEnabledChange,
    )
    if (enabled) {
        if (apiKeyLocked) {
            SettingsActionRow(
                icon = Icons.Rounded.Settings,
                title = "API key",
                detail = "已内置，用户无需填写",
                enabled = false,
                onClick = {},
            )
        } else {
            SettingsTextInputRow(
                icon = Icons.Rounded.Settings,
                title = "API key",
                value = apiKey,
                placeholder = "Last.fm API key",
                onValueChange = onApiKeyChange,
            )
        }
        if (sharedSecretLocked) {
            SettingsActionRow(
                icon = Icons.Rounded.Settings,
                title = "Shared secret",
                detail = "已内置，用户无需填写",
                enabled = false,
                onClick = {},
            )
        } else {
            SettingsTextInputRow(
                icon = Icons.Rounded.Settings,
                title = "Shared secret",
                value = sharedSecret,
                placeholder = "Last.fm shared secret",
                secret = true,
                onValueChange = onSharedSecretChange,
            )
        }
        SettingsActionRow(
            icon = Icons.Rounded.Devices,
            title = if (connected) "重新授权 Last.fm" else "打开 Last.fm 授权网页",
            detail = "在 Last.fm 网站登录并允许 ECHOAndroid 访问",
            enabled = (apiKeyLocked || apiKey.isNotBlank()) && (sharedSecretLocked || sharedSecret.isNotBlank()),
            onClick = onStartWebAuth,
        )
        SettingsActionRow(
            icon = Icons.Rounded.Settings,
            title = "完成网页授权",
            detail = if (webAuthPending) "授权网页点 Allow 后回到这里完成连接" else "请先打开授权网页",
            enabled = webAuthPending,
            onClick = onCompleteWebAuth,
        )
        SettingsActionRow(
            icon = Icons.Rounded.Settings,
            title = "查看 Last.fm API accounts",
            detail = "在网页里查看已申请的 API key 和 shared secret",
            onClick = onOpenApiAccounts,
        )
        if (connected) {
            SettingsActionRow(
                icon = Icons.Rounded.Settings,
                title = "断开 Last.fm",
                detail = "清除本机 session key，保留 API key 方便下次连接",
                onClick = onDisconnect,
            )
        }
    }
}

@Composable
private fun SettingsTextInputRow(
    icon: ImageVector,
    title: String,
    value: String,
    placeholder: String,
    secret: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(echoGlassRowBrush())
            .border(
                if (dark) echoDarkGlassBorder() else BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.78f)),
                RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        SettingsIconBubble(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                title,
                color = scheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun backgroundDetail(mode: String, uri: String?): String {
    val fileName = uri?.substringAfterLast('/')?.takeLast(28)
    return when {
        mode == "image" && !fileName.isNullOrBlank() -> "图片背景：$fileName"
        mode == "video" && !fileName.isNullOrBlank() -> "视频壁纸：$fileName"
        else -> "正在使用默认 ECHO 背景"
    }
}

private data class SettingsChoiceOption(
    val value: String,
    val label: String,
)

private fun themeOptions(): List<SettingsChoiceOption> = listOf(
    SettingsChoiceOption("system", "同步手机"),
    SettingsChoiceOption("light", "浅色"),
    SettingsChoiceOption("dark", "深色"),
)

private fun themeDetail(mode: String): String =
    when (mode) {
        "light" -> "始终使用浅色模式"
        "dark" -> "始终使用深色模式"
        else -> "跟随手机系统外观"
    }

private fun fontOptions(importedFontUri: String?): List<SettingsChoiceOption> = buildList {
    add(SettingsChoiceOption("system", "系统"))
    add(SettingsChoiceOption("outfit", "Outfit"))
    add(SettingsChoiceOption("serif", "衬线"))
    add(SettingsChoiceOption("monospace", "等宽"))
    add(SettingsChoiceOption("imported", if (importedFontUri.isNullOrBlank()) "导入" else "导入字体"))
}

private fun fontDetail(mode: String, importedFontUri: String?): String =
    when (mode) {
        "outfit" -> "使用内置 Outfit"
        "serif" -> "使用系统衬线字体"
        "monospace" -> "使用系统等宽字体"
        "imported" -> importedFontUri?.substringAfterLast('/')?.takeLast(28)?.let { "使用导入字体：$it" } ?: "选择 .ttf / .otf 文件"
        else -> "使用 Android 系统字体"
    }

private fun formatMinuteOfDay(value: Int): String {
    val minuteOfDay = value.coerceIn(0, 23 * 60 + 59)
    val hour = minuteOfDay / 60
    val minute = minuteOfDay % 60
    return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}

private fun Float.roundToQuarterHour(): Int =
    ((this / 15f).roundToInt() * 15).coerceIn(0, 23 * 60 + 59)

@Composable
private fun SettingsHeroCard(
    status: EchoPlaybackStatus,
    trackCount: Int,
    albumCount: Int,
    artistCount: Int,
    dynamicArtwork: Boolean,
) {
    val dark = LocalEchoDarkTheme.current
    val scheme = MaterialTheme.colorScheme
    val heroColors = if (dynamicArtwork) {
        if (dark) {
            listOf(
                EchoGlassCyan.copy(alpha = 0.24f),
                scheme.primary.copy(alpha = 0.18f),
                Color.Transparent,
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.88f),
                EchoHomeMist.copy(alpha = 0.82f),
                EchoAccentDeep.copy(alpha = 0.16f),
            )
        }
    } else {
        if (dark) {
            listOf(
                scheme.primary.copy(alpha = 0.16f),
                EchoGlassCyan.copy(alpha = 0.14f),
                Color.Transparent,
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.92f),
                EchoHomeMist.copy(alpha = 0.76f),
                Color.White.copy(alpha = 0.88f),
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                if (dark) echoGlassContainerBrush(1.12f, accent = EchoGlassCyan) else Brush.linearGradient(heroColors),
            )
            .border(
                if (dark) echoDarkGlassBorder(dynamicArtwork) else BorderStroke(1.dp, EchoGlassBorder),
                RoundedCornerShape(26.dp),
            )
            .padding(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "ECHO Mobile",
                        color = scheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        status.track?.title ?: "本机播放就绪",
                        color = scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                SettingsIconBubble(Icons.Rounded.Settings)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SettingsStatTile("歌曲", trackCount.toString(), Modifier.weight(1f))
                SettingsStatTile("专辑", albumCount.toString(), Modifier.weight(1f))
                SettingsStatTile("艺术家", artistCount.toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    collapsible: Boolean = false,
    expanded: Boolean = true,
    onExpandedChange: (Boolean) -> Unit = {},
    persistentContent: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(if (dark) echoGlassContainerBrush(0.92f) else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.78f), EchoHomeMist.copy(alpha = 0.56f))))
            .border(
                if (dark) echoDarkGlassBorder() else BorderStroke(1.dp, EchoGlassBorder),
                RoundedCornerShape(22.dp),
            )
            .animateContentSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = if (collapsible) {
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onExpandedChange(!expanded) }
                    .padding(vertical = 2.dp)
            } else {
                Modifier.fillMaxWidth()
            },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                color = scheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (collapsible) {
                Icon(
                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        persistentContent()
        if (expanded) {
            content()
        }
    }
}

@Composable
private fun SettingsBackgroundSourceRow(
    mode: String,
    uri: String?,
    onPickImageBackground: () -> Unit,
    onPickVideoBackground: () -> Unit,
    onClearCustomBackground: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(echoGlassRowBrush())
            .border(
                if (dark) echoDarkGlassBorder() else BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.78f)),
                RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIconBubble(Icons.Rounded.Image)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "背景来源",
                color = scheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                backgroundDetail(mode, uri),
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                BackgroundSourceAction(
                    icon = Icons.Rounded.Image,
                    label = "图片",
                    selected = mode == "image" && !uri.isNullOrBlank(),
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    onClick = onPickImageBackground,
                )
                BackgroundSourceAction(
                    icon = Icons.Rounded.Movie,
                    label = "视频",
                    selected = mode == "video" && !uri.isNullOrBlank(),
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    onClick = onPickVideoBackground,
                )
                BackgroundSourceAction(
                    icon = Icons.Rounded.Settings,
                    label = "默认",
                    selected = uri.isNullOrBlank(),
                    enabled = !uri.isNullOrBlank(),
                    modifier = Modifier.weight(1f),
                    onClick = onClearCustomBackground,
                )
            }
        }
    }
}

@Composable
private fun BackgroundSourceAction(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    val accent = if (selected) scheme.primary else scheme.onSurfaceVariant
    Row(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                echoGlassRowBrush(selected = selected, accent = scheme.primary),
            )
            .border(
                if (dark) echoDarkGlassBorder(selected) else BorderStroke(1.dp, if (selected) scheme.primary.copy(alpha = 0.26f) else EchoGlassBorder.copy(alpha = 0.56f)),
                RoundedCornerShape(12.dp),
            )
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .alpha(if (enabled || selected) 1f else 0.48f)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        Text(
            label,
            color = accent,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsRowShell(icon = icon, title = title, detail = detail) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    detail: String,
    enabled: Boolean = true,
    actionLabel: String = "进入",
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    SettingsRowShell(
        icon = icon,
        title = title,
        detail = detail,
        modifier = if (enabled) Modifier.clickable(onClick = onClick) else Modifier,
    ) {
        Text(
            if (enabled) actionLabel else "关闭",
            color = if (enabled) scheme.primary else scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SettingsChoiceGroupRow(
    icon: ImageVector,
    title: String,
    detail: String,
    options: List<SettingsChoiceOption>,
    selectedValue: String,
    onOptionSelected: (String) -> Unit,
) {
    SettingsRowShell(icon = icon, title = title, detail = detail, trailing = {})
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            SettingsOptionChip(
                label = option.label,
                selected = selectedValue == option.value,
                onClick = { onOptionSelected(option.value) },
            )
        }
    }
}

@Composable
private fun SettingsOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(echoGlassRowBrush(selected = selected, accent = scheme.primary))
            .border(
                if (dark) echoDarkGlassBorder(selected) else BorderStroke(1.dp, if (selected) scheme.primary.copy(alpha = 0.26f) else EchoGlassBorder.copy(alpha = 0.56f)),
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun SettingsSliderRow(
    icon: ImageVector,
    title: String,
    detail: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(echoGlassRowBrush())
            .border(
                if (dark) echoDarkGlassBorder() else BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.78f)),
                RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIconBubble(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    detail,
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = 0,
                colors = SliderDefaults.colors(
                    thumbColor = scheme.primary,
                    activeTrackColor = scheme.primary.copy(alpha = 0.82f),
                    inactiveTrackColor = if (dark) Color.White.copy(alpha = 0.16f) else scheme.outlineVariant.copy(alpha = 0.72f),
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
            )
        }
    }
}

@Composable
private fun SettingsRowShell(
    icon: ImageVector,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(echoGlassRowBrush())
            .border(
                if (dark) echoDarkGlassBorder() else BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.78f)),
                RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIconBubble(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title,
                color = scheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detail,
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}

@Composable
private fun SettingsIconBubble(icon: ImageVector) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(echoGlassRowBrush(selected = true, accent = scheme.primary))
            .border(if (dark) echoDarkGlassBorder(true) else BorderStroke(1.dp, scheme.primary.copy(alpha = 0.20f)), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun SettingsStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(echoGlassRowBrush())
            .border(
                if (dark) echoDarkGlassBorder() else BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.78f)),
                RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        Text(value, color = scheme.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

private fun usbExclusiveDetail(status: EchoPlaybackStatus): String {
    val diagnostics = status.diagnostics
    return when {
        diagnostics.usbBitPerfectActive -> "USB DAC 已使用 bit-perfect 链路"
        diagnostics.usbAudioHasIsochronousOut -> "已识别 UAC 独占端点：${diagnostics.usbAudioEndpointSummary ?: "iso OUT"}"
        diagnostics.usbHostPermissionGranted -> "已获得 USB DAC 访问授权；系统输出仍按设备能力决定"
        diagnostics.usbHostPermissionPending -> "请在系统弹窗中允许 ECHO 访问 USB DAC"
        diagnostics.usbBitPerfectSupported -> "插入的 USB DAC 支持 bit-perfect，可按曲目采样率请求"
        diagnostics.usbConnected -> "已连接 USB DAC，但当前只走 Android mixer"
        else -> "高级输出模式；无 USB DAC 或不支持时自动回退"
    }
}

private fun usbExclusiveTestDetail(status: EchoPlaybackStatus, result: String): String {
    val diagnostics = status.diagnostics
    return when {
        !diagnostics.usbConnected -> "未检测到 USB DAC"
        !diagnostics.usbHostPermissionGranted -> "先打开 USB 独占输出并允许系统 USB 授权"
        else -> result
    }
}
