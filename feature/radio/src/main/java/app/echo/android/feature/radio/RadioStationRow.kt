package app.echo.android.feature.radio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.model.radio.EchoRadioStation

@Composable
internal fun RadioStationRow(
    station: EchoRadioStation,
    active: Boolean,
    pending: Boolean,
    actions: RadioActions,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    saved: Boolean = true,
    onSave: (() -> Unit)? = null,
    onFavorite: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    Row(modifier.heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RadioArtwork(station, Modifier.size(44.dp), thumbnail = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        station.name,
                        Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (station.favorite) {
                        EchoIcon(Icons.Rounded.Favorite, stringResource(R.string.radio_favorites), Modifier.size(12.dp))
                    }
                }
                Text(
                    listOfNotNull(station.countryCode.takeIf { it.isNotBlank() }, station.codec,
                        station.bitrateKbps?.let { "$it kbps" }).joinToString(" · ")
                        .ifBlank { station.genre.ifBlank { stringResource(R.string.radio_network_stream) } },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(
            enabled = !pending,
            onClick = { if (active) actions.stop() else actions.play(station, null) },
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (active) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
            ),
        ) {
            if (pending) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                EchoIcon(
                    if (active) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    stringResource(if (active) R.string.radio_stop_named else R.string.radio_play_named, station.name),
                )
            }
        }
        if (onSave != null) {
            IconButton(enabled = !saved, onClick = onSave) {
                EchoIcon(
                    if (saved) Icons.Rounded.Check else Icons.Rounded.Add,
                    stringResource(if (saved) R.string.radio_already_saved else R.string.radio_save),
                    Modifier.size(20.dp),
                )
            }
        }
        if (onFavorite != null) {
            var menu by remember(station.id) { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) {
                    EchoIcon(Icons.Rounded.MoreHoriz, stringResource(R.string.radio_more))
                }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(if (station.favorite) R.string.radio_unfavorite else R.string.radio_favorite)) },
                        onClick = { menu = false; onFavorite() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.radio_edit)) },
                        onClick = { menu = false; onEdit?.invoke() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.radio_delete)) },
                        onClick = { menu = false; onDelete?.invoke() })
                }
            }
        }
    }
}

@Composable
internal fun RadioStationDivider() {
    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
}
