package app.echo.android.feature.radio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.model.radio.EchoRadioStation

@Composable
internal fun RadioStationCard(station: EchoRadioStation, active: Boolean, pending: Boolean, actions: RadioActions,
    onOpen: () -> Unit, modifier: Modifier = Modifier, saved: Boolean = true,
    onSave: (() -> Unit)? = null, onFavorite: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null, onDelete: (() -> Unit)? = null) {
    var menu by remember(station.id) { mutableStateOf(false) }
    Card(onClick = onOpen, modifier = modifier, shape = RoundedCornerShape(16.dp),
        border = if (active) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                RadioArtwork(station, Modifier.size(76.dp), framed = false)
                if (station.favorite) Surface(shape = RoundedCornerShape(8.dp), modifier = Modifier.align(Alignment.TopEnd).padding(6.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .9f)) {
                    EchoIcon(Icons.Rounded.Favorite, stringResource(R.string.radio_favorites), Modifier.padding(5.dp).size(14.dp))
                }
            }
            }
            Text(station.name, Modifier.heightIn(min = 36.dp), style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(station.countryCode.takeIf { it.isNotBlank() }, station.codec,
                station.bitrateKbps?.let { "$it kbps" }).joinToString(" · ").ifBlank { station.genre.ifBlank { stringResource(R.string.radio_network_stream) } },
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                FilledTonalIconButton(enabled = !pending, onClick = { if (active) actions.stop() else actions.play(station, null) }) {
                    if (pending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else EchoIcon(if (active) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, stringResource(if (active) R.string.radio_stop_named else R.string.radio_play_named, station.name))
                }
                if (onSave != null) IconButton(enabled = !saved, onClick = onSave) {
                    EchoIcon(if (saved) Icons.Rounded.Check else Icons.Rounded.Add, stringResource(if (saved) R.string.radio_already_saved else R.string.radio_save))
                }
                if (onFavorite != null) Box {
                    IconButton(onClick = { menu = true }) { EchoIcon(Icons.Rounded.MoreHoriz, stringResource(R.string.radio_more)) }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(if (station.favorite) R.string.radio_unfavorite else R.string.radio_favorite)) }, onClick = { menu = false; onFavorite() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.radio_edit)) }, onClick = { menu = false; onEdit?.invoke() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.radio_delete)) }, onClick = { menu = false; onDelete?.invoke() })
                    }
                }
            }
        }
    }
}
