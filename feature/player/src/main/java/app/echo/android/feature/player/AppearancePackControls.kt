package app.echo.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.model.settings.EchoAppearancePack
import app.echo.android.model.settings.EchoColorTheme
import app.echo.android.design.echoThemeTokens
import coil.compose.AsyncImage

enum class AppearancePackNotice { Exported, Applied, Restored, ReadFailed, WriteFailed, ApplyFailed }

@Composable
fun AppearancePackControls(
    preview: EchoAppearancePack?, background: ByteArray?, busy: Boolean, canUndo: Boolean,
    notice: AppearancePackNotice?, onImport: () -> Unit, onExport: (String) -> Unit,
    onApply: () -> Unit, onDismiss: () -> Unit, onUndo: () -> Unit,
) {
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("ECHO") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsGroupLabel(stringResource(R.string.appearance_pack_title))
        Text(stringResource(R.string.appearance_pack_detail), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onImport, enabled = !busy) { Text(stringResource(R.string.appearance_pack_import)) }
            TextButton(onClick = { naming = true }, enabled = !busy) { Text(stringResource(R.string.appearance_pack_export)) }
        }
        if (canUndo) TextButton(onClick = onUndo, enabled = !busy) { Text(stringResource(R.string.appearance_pack_undo)) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        notice?.let {
            val error = it in listOf(AppearancePackNotice.ReadFailed, AppearancePackNotice.WriteFailed, AppearancePackNotice.ApplyFailed)
            Text(stringResource(when (it) {
                AppearancePackNotice.Exported -> R.string.appearance_pack_exported
                AppearancePackNotice.Applied -> R.string.appearance_pack_applied
                AppearancePackNotice.Restored -> R.string.appearance_pack_restored
                AppearancePackNotice.ReadFailed -> R.string.appearance_pack_read_failed
                AppearancePackNotice.WriteFailed -> R.string.appearance_pack_write_failed
                AppearancePackNotice.ApplyFailed -> R.string.appearance_pack_apply_failed
            }), style = MaterialTheme.typography.bodySmall,
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (naming) AlertDialog(onDismissRequest = { naming = false },
        title = { Text(stringResource(R.string.appearance_pack_export)) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it.filterNot(Char::isISOControl).take(48) }, singleLine = true,
            label = { Text(stringResource(R.string.appearance_pack_name)) }) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { naming = false; onExport(name.trim()) }) {
            Text(stringResource(R.string.appearance_pack_export)) } },
        dismissButton = { TextButton(onClick = { naming = false }) { Text(stringResource(R.string.appearance_pack_cancel)) } })
    if (preview != null) AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() }, title = { Text(preview.name) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppearancePackPreview(preview, background)
            Text(stringResource(R.string.appearance_pack_preview_note), style = MaterialTheme.typography.bodySmall)
            if (preview.dynamicColorEnabled) Text(stringResource(R.string.appearance_pack_dynamic_colors), style = MaterialTheme.typography.bodySmall)
            if (notice == AppearancePackNotice.ApplyFailed) Text(stringResource(R.string.appearance_pack_apply_failed), color = MaterialTheme.colorScheme.error)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        } },
        confirmButton = { TextButton(onClick = onApply, enabled = !busy) { Text(stringResource(R.string.appearance_pack_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.appearance_pack_cancel)) } },
    )
}

@Composable
private fun AppearancePackPreview(pack: EchoAppearancePack, image: ByteArray?) {
    val dark = app.echo.android.design.echoTheme().dark
    val palette = remember(pack.colorTheme, pack.colors, dark) { echoThemeTokens(EchoColorTheme.fromId(pack.colorTheme), dark, pack.colors) }
    val font = when (pack.fontFamily) { "serif" -> FontFamily.Serif; "monospace" -> FontFamily.Monospace; else -> FontFamily.SansSerif }
    Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp)).background(palette.bgMid),
        contentAlignment = Alignment.Center) {
        if (image != null) AsyncImage(model = image, contentDescription = null, modifier = Modifier.matchParentSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.35f + pack.backgroundDim.coerceAtMost(0.5f)))
            .padding(20.dp), verticalArrangement = Arrangement.spacedBy((12 * pack.lineSpacing).dp)) {
            Text(stringResource(R.string.appearance_pack_preview_line), modifier = Modifier.fillMaxWidth(),
                color = Color.White, fontFamily = font, fontSize = (21 * pack.fontScale).sp,
                textAlign = when (pack.alignment) { "center" -> TextAlign.Center; "end" -> TextAlign.End; else -> TextAlign.Start }, maxLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(palette.accent, palette.secondary, palette.bgMid).forEach { color ->
                    Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(color))
                }
            }
        }
    }
}
