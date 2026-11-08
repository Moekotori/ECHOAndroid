package app.echo.android.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon

/**
 * The "Tracks" heading with a search icon on its right. Tapping the icon turns the row into a search field,
 * and the close button clears the query and brings the heading back.
 */
@Composable
internal fun ArtistTracksSearchHeading(count: Int, query: String, onQueryChange: (String) -> Unit) {
    var searching by rememberSaveable { mutableStateOf(query.isNotEmpty()) }
    val scheme = MaterialTheme.colorScheme
    if (!searching) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(R.string.artist_tracks), color = scheme.onSurface,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            )
            Row(Modifier.padding(end = 0.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(artistTrackCountLabel(count), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = { searching = true }) {
                    EchoIcon(Icons.Rounded.Search, stringResource(R.string.artist_search_tracks), Modifier.size(21.dp), tint = scheme.onSurfaceVariant)
                }
            }
        }
        return
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
        cursorBrush = SolidColor(scheme.primary),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        decorationBox = { innerTextField ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .background(scheme.surfaceContainerLow, RoundedCornerShape(14.dp))
                    .padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                EchoIcon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = scheme.onSurfaceVariant)
                Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                    if (query.isEmpty()) Text(
                        stringResource(R.string.artist_search_tracks),
                        style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                    )
                    innerTextField()
                }
                IconButton(onClick = { onQueryChange(""); searching = false }) {
                    EchoIcon(Icons.Rounded.Close, stringResource(R.string.artist_clear_search), Modifier.size(18.dp))
                }
            }
        },
    )
}
