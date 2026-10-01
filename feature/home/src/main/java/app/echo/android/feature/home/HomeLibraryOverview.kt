package app.echo.android.feature.home

import app.echo.android.design.EchoIcon

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.*
import app.echo.android.model.library.LibraryScanProgress
import app.echo.android.feature.home.R as L10nR

@Composable
internal fun HomeEmptyLibrary(scanState: LibraryScanProgress, onOpenLibrary: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        shape = RectangleShape, color = Color.Transparent) {
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            EchoIcon(Icons.Rounded.LibraryMusic, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            HomeSectionHeader(stringResource(L10nR.string.feature_home_start_with_local_music_9875f9))
            if (scanState.isScanning) {
                HomeLibraryScanHint(scanState, onOpenLibrary)
            } else {
                Text(stringResource(L10nR.string.home_daily_album_empty),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onOpenLibrary, shape = RoundedCornerShape(6.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(L10nR.string.home_listening_open_library))
            }
        }
    }
}

@Composable
internal fun LibraryOverview(
    trackCount: Int,
    albumCount: Int,
    artistCount: Int,
    scanState: LibraryScanProgress = LibraryScanProgress(),
    onOpenLibrary: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                LibraryMetric(
                    stringResource(L10nR.string.feature_home_songs_107b60),
                    trackCount.toString(),
                    Modifier.weight(1f),
                    onClick = onOpenLibrary,
                )
                LibraryMetric(
                    stringResource(L10nR.string.feature_home_albums_e68c2b),
                    albumCount.toString(),
                    Modifier.weight(1f),
                    onClick = onOpenLibrary,
                )
                LibraryMetric(
                    stringResource(L10nR.string.feature_home_artists_e168aa),
                    artistCount.toString(),
                    Modifier.weight(1f),
                    onClick = onOpenLibrary,
                )
            }
            if (scanState.isScanning) {
                HomeLibraryScanHint(scanState = scanState, onOpenLibrary = onOpenLibrary)
            }
        }
    }
}

@Composable
private fun HomeLibraryScanHint(
    scanState: LibraryScanProgress,
    onOpenLibrary: () -> Unit,
) {
    val progress = scanState.totalCount?.let { total -> "${scanState.scannedCount}/$total" }
        ?: scanState.scannedCount.toString()
    val detail = scanState.currentTitle?.takeIf { it.isNotBlank() } ?: progress
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .echoClickable(onClick = onOpenLibrary)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EchoIcon(
            imageVector = Icons.Rounded.LibraryMusic,
            contentDescription = null,
            tint = echoAccentColor(),
            modifier = Modifier.size(18.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = stringResource(L10nR.string.feature_home_scanning_library_d0b14c),
                color = homeTitleColor(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (detail == progress) progress else "$progress · $detail",
                color = homeBodyColor(),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun LibraryMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .echoClickable(role = Role.Button, onClick = onClick)
                        .padding(vertical = 4.dp)
                } else {
                    Modifier
                },
            ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, color = homeTitleColor(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Text(label, color = homeBodyColor(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Normal)
    }
}
