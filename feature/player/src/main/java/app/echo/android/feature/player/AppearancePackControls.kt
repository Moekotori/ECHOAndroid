package app.echo.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.EchoDropdownMenu
import app.echo.android.design.EchoIcon
import app.echo.android.design.echoIconColor
import app.echo.android.design.echoThemeTokens
import app.echo.android.model.settings.EchoAppearancePack
import app.echo.android.model.settings.EchoColorTheme
import coil.compose.AsyncImage

enum class AppearancePackNotice { Exported, Applied, Restored, ReadFailed, WriteFailed, ApplyFailed }

@Composable
fun AppearancePackControls(
    preview: EchoAppearancePack?, background: ByteArray?, busy: Boolean, canUndo: Boolean,
    notice: AppearancePackNotice?, snackbar: SnackbarHostState, onImport: () -> Unit, onExport: (String) -> Unit,
    onApply: () -> Unit, onDismiss: () -> Unit, onUndo: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("ECHO") }
    val menuLabel = stringResource(R.string.appearance_pack_title)
    Box {
        IconButton(
            onClick = { menuOpen = true },
            modifier = Modifier.semantics { contentDescription = menuLabel },
            enabled = !busy,
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), color = echoIconColor(), strokeWidth = 2.dp)
            } else {
                EchoIcon(Icons.Rounded.Inventory2, contentDescription = null)
            }
        }
        EchoDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.appearance_pack_import)) },
                leadingIcon = { EchoIcon(Icons.Rounded.UploadFile, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onImport()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.appearance_pack_export)) },
                leadingIcon = { EchoIcon(Icons.Rounded.FileDownload, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    naming = true
                },
            )
            if (canUndo) DropdownMenuItem(
                text = { Text(stringResource(R.string.appearance_pack_undo)) },
                leadingIcon = { EchoIcon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onUndo()
                },
            )
        }
    }
    if (notice != null && (notice != AppearancePackNotice.ApplyFailed || preview == null)) {
        val message = stringResource(
            when (notice) {
                AppearancePackNotice.Exported -> R.string.appearance_pack_exported
                AppearancePackNotice.Applied -> R.string.appearance_pack_applied
                AppearancePackNotice.Restored -> R.string.appearance_pack_restored
                AppearancePackNotice.ReadFailed -> R.string.appearance_pack_read_failed
                AppearancePackNotice.WriteFailed -> R.string.appearance_pack_write_failed
                AppearancePackNotice.ApplyFailed -> R.string.appearance_pack_apply_failed
            },
        )
        LaunchedEffect(snackbar, message) {
            snackbar.showSnackbar(message, duration = SnackbarDuration.Long)
        }
    }
    if (naming) AlertDialog(onDismissRequest = { naming = false },
        title = { Text(stringResource(R.string.appearance_pack_export)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.appearance_pack_detail), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = name, onValueChange = { name = it.filterNot(Char::isISOControl).take(48) },
                    singleLine = true, label = { Text(stringResource(R.string.appearance_pack_name)) })
            }
        },
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
