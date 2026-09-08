package app.echo.android.feature.library

import app.echo.android.feature.library.R as L10nR
import androidx.compose.ui.res.stringResource

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import app.echo.android.design.echoClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Scanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import app.echo.android.design.ArtworkPalette
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoColors
import app.echo.android.design.EchoMotion
import app.echo.android.design.EchoGlassBorder
import app.echo.android.design.EchoGlassInk
import app.echo.android.design.EchoGlassPanel
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.EchoIconBadge
import app.echo.android.design.EchoPanel
import app.echo.android.design.EchoTextButton
import app.echo.android.design.EmptyState
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.RoonInk
import app.echo.android.design.RoonMuted
import app.echo.android.design.displayMetadataOrUnknown
import app.echo.android.design.formatDuration
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoTrackMetadataUpdate
import app.echo.android.model.library.FolderSummary
import app.echo.android.model.library.LibraryScanPhase
import app.echo.android.model.library.LibraryScanProgress
import kotlinx.coroutines.delay

private data class LibraryGlassColors(
    val surface: Color,
    val elevatedSurface: Color,
    val border: Color,
    val content: Color,
    val muted: Color,
)

@Composable
private fun rememberLibraryGlassColors(): LibraryGlassColors {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    return remember(scheme, dark) {
        LibraryGlassColors(
            surface = if (dark) EchoGlassPanel.copy(alpha = 0.58f) else Color.White.copy(alpha = 0.60f),
            elevatedSurface = if (dark) EchoGlassInk.copy(alpha = 0.48f) else Color.White.copy(alpha = 0.56f),
            border = if (dark) Color.White.copy(alpha = 0.13f) else EchoGlassBorder.copy(alpha = 0.78f),
            content = if (dark) Color.White.copy(alpha = 0.96f) else RoonInk,
            muted = if (dark) Color.White.copy(alpha = 0.74f) else RoonMuted,
        )
    }
}

@Composable
internal fun rememberLibraryControlColor(): Color {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) { scheme.primary }
}

@Composable
internal fun rememberLibraryArtworkAccent(): Color {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) { scheme.primary }
}

@Composable
internal fun LibraryPagerTabs(
    selectedMode: LibraryViewMode,
    onSelectMode: (LibraryViewMode) -> Unit,
) {
    val colors = rememberLibraryGlassColors()
    val accent = rememberLibraryControlColor()
    val visibleModes = remember {
        listOf(
            LibraryViewMode.Songs,
            LibraryViewMode.Folders,
            LibraryViewMode.Albums,
            LibraryViewMode.Artists,
            LibraryViewMode.Playlists,
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 0.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        visibleModes.forEach { mode ->
            val selected = selectedMode == mode
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .echoClickable { onSelectMode(mode) }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = mode.label(),
                    color = if (selected) accent else colors.muted,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                )
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (selected) accent else Color.Transparent),
                )
            }
        }
    }
}

@Composable
internal fun LibraryPlaceholderPage(
    title: String,
    subtitle: String,
) {
    val colors = rememberLibraryGlassColors()
    EchoPanel(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EchoIconBadge(Icons.Rounded.LibraryMusic)
            Text(title, color = colors.content, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, color = colors.muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun LibrarySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    expandedWidth: Dp = 220.dp,
) {
    val colors = rememberLibraryGlassColors()
    val dark = LocalEchoDarkTheme.current
    val accent = rememberLibraryControlColor()
    val shape = RoundedCornerShape(18.dp)
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var expanded by remember { mutableStateOf(query.isNotBlank()) }
    val width by animateDpAsState(
        targetValue = if (expanded) expandedWidth else 46.dp,
        animationSpec = EchoMotion.silkDp(320),
        label = "library-search-width",
    )

    LaunchedEffect(query) {
        if (query.isNotBlank()) expanded = true
    }
    LaunchedEffect(expanded) {
        if (expanded) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    Box(
        modifier = modifier
            .width(width)
            .height(56.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        AnimatedVisibility(
            visible = !expanded,
            enter = fadeIn(animationSpec = tween(durationMillis = 180, easing = EchoMotion.Silk)),
            exit = fadeOut(animationSpec = tween(durationMillis = 120, easing = EchoMotion.SilkExit)),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .echoClickable { expanded = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = stringResource(L10nR.string.feature_library_search_library_80ef90),
                    tint = colors.content,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandHorizontally(
                expandFrom = Alignment.End,
                animationSpec = EchoMotion.silkSize(320),
            ) + fadeIn(animationSpec = tween(durationMillis = 220, easing = EchoMotion.Silk)),
            exit = shrinkHorizontally(
                shrinkTowards = Alignment.End,
                animationSpec = EchoMotion.silkSize(240),
            ) + fadeOut(animationSpec = tween(durationMillis = 140, easing = EchoMotion.SilkExit)),
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .width(expandedWidth)
                    .height(54.dp)
                    .clip(shape)
                    .border(BorderStroke(1.dp, colors.border), shape)
                    .focusRequester(focusRequester),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = colors.muted,
                        modifier = Modifier.size(20.dp),
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            onQueryChange("")
                            expanded = false
                        },
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(L10nR.string.feature_library_close_search_50a720),
                            tint = colors.muted,
                        )
                    }
                },
                placeholder = {
                    Text(
                        stringResource(L10nR.string.feature_library_search_songs_artists_albums_14dc2c),
                        color = colors.muted,
                        maxLines = 1,
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.content),
                shape = shape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = if (dark) EchoGlassPanel.copy(alpha = 0.48f) else Color.White.copy(alpha = 0.58f),
                    unfocusedContainerColor = if (dark) EchoGlassPanel.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.52f),
                    disabledContainerColor = if (dark) EchoGlassPanel.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.42f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = accent,
                ),
            )
        }
    }
}

@Composable
internal fun FolderList(
    folders: LazyPagingItems<FolderSummary>,
    onOpenFolder: (FolderSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (folders.loadState.refresh is LoadState.Loading) {
        EmptyState(stringResource(L10nR.string.feature_library_loading_folders_54c760))
        return
    }
    if (folders.loadState.refresh is LoadState.Error) {
        EmptyState(stringResource(L10nR.string.feature_library_failed_to_load_folders_d4887b))
        return
    }
    if (folders.itemCount == 0) {
        LibraryPlaceholderPage(
            title = stringResource(L10nR.string.feature_library_folder_view_2b3c84),
            subtitle = stringResource(L10nR.string.feature_library_this_library_has_no_browsable_storage_paths_yet_fb0395),
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 2.dp, bottom = LibraryBottomControlsPadding),
    ) {
        items(
            count = folders.itemCount,
            key = { index: Int -> folders.peek(index)?.folderKey ?: "folder-$index" },
        ) { index: Int ->
            folders[index]?.let { folder ->
                FolderRow(
                    folder = folder,
                    onClick = { onOpenFolder(folder) },
                )
            }
        }
    }
}
@Composable
private fun FolderRow(
    folder: FolderSummary,
    onClick: () -> Unit,
) {
    val colors = rememberLibraryGlassColors()
    val dark = LocalEchoDarkTheme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (dark) 0.dp else 6.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = Color.Black.copy(alpha = 0.03f),
                spotColor = Color.Black.copy(alpha = 0.05f),
            )
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (dark) {
                    EchoGlassPanel.copy(alpha = 0.42f)
                } else {
                    Color.White.copy(alpha = 0.72f)
                },
            )
            .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(20.dp))
            .echoClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ArtworkTile(
                artworkUri = folder.artworkUri,
                modifier = Modifier.size(64.dp),
                accent = rememberLibraryArtworkAccent(),
                showSignal = folder.artworkUri.isNullOrBlank(),
                cornerRadius = 13.dp,
                elevation = if (dark) 0.dp else 3.dp,
                placeholderIconSize = 28.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    folderDisplayName(folder),
                    color = colors.content,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    folderPathLabel(folder),
                    color = colors.muted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    FolderMetaChip(
                        icon = Icons.Rounded.MusicNote,
                        text = libraryTrackCountLabel(folder.trackCount),
                    )
                    folder.albumCount.takeIf { it > 0 }?.let {
                        FolderMetaChip(
                            icon = Icons.Rounded.LibraryMusic,
                            text = stringResource(L10nR.string.feature_library_it_albums_3c43c1, (it).toString()),
                        )
                    }
                }
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = stringResource(L10nR.string.feature_library_open_folder_ab1708),
                tint = if (dark) Color.White.copy(alpha = 0.68f) else colors.muted,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
private fun FolderMetaChip(
    icon: ImageVector,
    text: String,
) {
    val colors = rememberLibraryGlassColors()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (LocalEchoDarkTheme.current) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (LocalEchoDarkTheme.current) Color.White.copy(alpha = 0.72f) else colors.muted,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text,
            color = colors.content,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
internal fun folderDisplayName(folder: FolderSummary): String =
    folder.path
        ?.trim('/')
        ?.substringAfterLast('/')
        ?.takeIf { it.isNotBlank() }
        ?: stringResource(L10nR.string.feature_library_unknown_path_e282e8)

@Composable
internal fun folderSubtitle(folder: FolderSummary): String =
    listOf(
        libraryTrackCountLabel(folder.trackCount),
        libraryAlbumCountLabel(folder.albumCount),
        stringResource(L10nR.string.feature_library_folder_artistcount_artists_6d0d7e, (folder.artistCount).toString()),
        formatDuration(folder.durationMs),
        formatByteSize(folder.totalSizeBytes),
    ).joinToString(" · ")

@Composable
private fun folderPathLabel(folder: FolderSummary): String =
    folder.path?.takeIf { it.isNotBlank() }
        ?: stringResource(L10nR.string.feature_library_mediastore_did_not_provide_a_path_53b27e)

private fun formatByteSize(bytes: Long): String =
    when {
        bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / (1024f * 1024f * 1024f))
        bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024f * 1024f))
        bytes >= 1024L -> "%.1f KB".format(bytes / 1024f)
        else -> "$bytes B"
    }

@Composable
internal fun LibraryOverview(
    trackCount: Int,
    albumCount: Int,
    artistCount: Int,
) {
    val colors = rememberLibraryGlassColors()
    val accent = rememberLibraryControlColor()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        colors.surface,
                        colors.elevatedSurface,
                        accent.copy(alpha = if (LocalEchoDarkTheme.current) 0.18f else 0.08f),
                    ),
                ),
            )
            .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(22.dp))
            .padding(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            LibraryMetric(stringResource(L10nR.string.feature_library_songs_107b60), trackCount.toString(), Modifier.weight(1f))
            LibraryMetric(stringResource(L10nR.string.feature_library_albums_e68c2b), albumCount.toString(), Modifier.weight(1f))
            LibraryMetric(stringResource(L10nR.string.feature_library_artists_e168aa), artistCount.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
internal fun LibraryMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val colors = rememberLibraryGlassColors()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, color = colors.content, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, color = colors.muted, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun LibraryViewSwitcher(
    selectedMode: LibraryViewMode,
    onSelectMode: (LibraryViewMode) -> Unit,
) {
    val colors = rememberLibraryGlassColors()
    val accent = rememberLibraryControlColor()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.elevatedSurface)
            .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(18.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LibraryViewMode.entries.forEach { mode ->
            val selected = selectedMode == mode
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) accent.copy(alpha = 0.18f) else Color.Transparent)
                    .echoClickable { onSelectMode(mode) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    mode.icon,
                    contentDescription = mode.label(),
                    tint = if (selected) accent else colors.muted,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    mode.label(),
                    color = if (selected) colors.content else colors.muted,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun LibraryViewModeMenu(
    selectedMode: LibraryViewMode,
    onSelectMode: (LibraryViewMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = rememberLibraryGlassColors()
    val accent = rememberLibraryControlColor()
    Box {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.elevatedSurface)
                .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(14.dp))
                .echoClickable { expanded = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                selectedMode.icon,
                contentDescription = stringResource(L10nR.string.feature_library_switch_library_view_f99a55),
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            LibraryViewMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Text(
                            mode.label(),
                            color = if (mode == selectedMode) accent else colors.content,
                            fontWeight = if (mode == selectedMode) FontWeight.Bold else FontWeight.SemiBold,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            mode.icon,
                            contentDescription = null,
                            tint = if (mode == selectedMode) accent else colors.muted,
                        )
                    },
                    onClick = {
                        onSelectMode(mode)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun AlbumWall(
    albums: LazyPagingItems<AlbumSummary>,
    onOpenAlbum: (AlbumSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (albums.loadState.refresh is LoadState.Loading) {
        EmptyState(stringResource(L10nR.string.feature_library_loading_albums_75c4f3))
        return
    }
    if (albums.loadState.refresh is LoadState.Error) {
        EmptyState(stringResource(L10nR.string.feature_library_failed_to_load_albums_8a8685))
        return
    }
    if (albums.itemCount == 0) {
        EmptyState(
            stringResource(L10nR.string.feature_library_this_library_has_no_albums_to_show_yet_925b12),
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(bottom = LibraryBottomControlsPadding),
    ) {
        items(
            count = albums.itemCount,
            key = { index: Int -> albums.peek(index)?.albumKey ?: "album-$index" },
        ) { index: Int ->
            albums[index]?.let { album ->
                AlbumWallCard(album = album, onClick = { onOpenAlbum(album) })
            }
        }
    }
}

@Composable
internal fun AlbumWallCard(
    album: AlbumSummary,
    onClick: () -> Unit,
) {
    val artistLabel = displayMetadataOrUnknown(album.albumArtist ?: album.artist, unknownArtistLabel())
    val colors = rememberLibraryGlassColors()
    Column(
        modifier = Modifier
            .echoClickable(onClick = onClick)
            .padding(bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ArtworkTile(
            artworkUri = album.artworkUri,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            accent = rememberLibraryArtworkAccent(),
            showSignal = album.artworkUri == null,
            cornerRadius = 14.dp,
            elevation = 8.dp,
        )
        Text(
            displayMetadataOrUnknown(album.title, unknownAlbumLabel()),
            color = colors.content,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "$artistLabel / ${libraryTrackCountLabel(album.trackCount)}",
            color = colors.muted,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun ArtistWall(
    artists: LazyPagingItems<ArtistSummary>,
    onOpenArtist: (ArtistSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (artists.loadState.refresh is LoadState.Loading) {
        EmptyState(stringResource(L10nR.string.feature_library_loading_artists_e9b16d))
        return
    }
    if (artists.loadState.refresh is LoadState.Error) {
        EmptyState(stringResource(L10nR.string.feature_library_failed_to_load_artists_da82a6))
        return
    }
    if (artists.itemCount == 0) {
        EmptyState(
            stringResource(L10nR.string.feature_library_this_library_has_no_artists_to_show_yet_d95101),
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(top = 6.dp, bottom = LibraryBottomControlsPadding),
    ) {
        items(
            count = artists.itemCount,
            key = { index: Int -> artists.peek(index)?.artistKey ?: "artist-$index" },
        ) { index: Int ->
            artists[index]?.let { artist ->
                ArtistWallCard(artist = artist, onClick = { onOpenArtist(artist) })
            }
        }
    }
}

@Composable
internal fun ArtistWallCard(
    artist: ArtistSummary,
    onClick: () -> Unit,
) {
    // 每位艺人按标识生成稳定柔和的取色，呼应详情页（同步、无额外位图解码）
    val palette = remember(artist.artistKey) { ArtworkPalette.fromSeed(artist.artistKey) }
    val colors = rememberLibraryGlassColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .echoClickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        ArtistWallAvatar(
            artworkUri = artist.artworkUri,
            palette = palette,
        )
        Text(
            displayMetadataOrUnknown(artist.name, unknownArtistLabel()),
            color = colors.content,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            "${libraryAlbumCountLabel(artist.albumCount)} · ${libraryTrackCountLabel(artist.trackCount)}",
            color = colors.muted,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ArtistWallAvatar(
    artworkUri: String?,
    palette: ArtworkPalette,
) {
    val shape = CircleShape
    Box(
        modifier = Modifier
            .size(76.dp)
            .shadow(
                elevation = 6.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.12f),
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        palette.vibrant.copy(alpha = if (LocalEchoDarkTheme.current) 0.48f else 0.55f),
                        Color.White.copy(alpha = if (LocalEchoDarkTheme.current) 0.16f else 0.70f),
                        palette.soft.copy(alpha = if (LocalEchoDarkTheme.current) 0.24f else 0.42f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (artworkUri.isNullOrBlank()) {
            Icon(
                Icons.Rounded.Person,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.88f),
                modifier = Modifier.size(38.dp),
            )
        } else {
            EchoArtworkImage(
                artworkUri = artworkUri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.34f),
                shape = CircleShape,
            )
        }
    }
}

@Composable
internal fun LibraryScanStatus(
    scanState: LibraryScanProgress,
    onCancelScan: () -> Unit,
) {
    val colors = rememberLibraryGlassColors()
    EchoPanel(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EchoIconBadge(Icons.Rounded.Scanner)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = scanPhaseLabel(scanState.phase),
                        color = colors.content,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = scanState.currentTitle
                            ?.takeIf { it.isNotBlank() }
                            ?: stringResource(L10nR.string.feature_library_incrementally_indexing_local_music_3fc64d),
                        color = colors.muted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                EchoTextButton(
                    text = stringResource(L10nR.string.feature_library_cancel_scan_dcb52d),
                    onClick = onCancelScan,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LibraryMetric(stringResource(L10nR.string.feature_library_scanned_862bfd), scanProgressValue(scanState), Modifier.weight(1f))
                LibraryMetric(stringResource(L10nR.string.feature_library_added_155093), scanState.insertedCount.toString(), Modifier.weight(1f))
                LibraryMetric(stringResource(L10nR.string.feature_library_updated_c440c1), scanState.updatedCount.toString(), Modifier.weight(1f))
                LibraryMetric(stringResource(L10nR.string.feature_library_removed_7be2fd), scanState.deletedCount.toString(), Modifier.weight(1f))
            }
            scanState.error?.takeIf { it.isNotBlank() }?.let { error ->
                Text(error, color = EchoColors.Coral, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun scanProgressValue(scanState: LibraryScanProgress): String =
    scanState.totalCount?.let { total -> "${scanState.scannedCount}/$total" }
        ?: scanState.scannedCount.toString()

@Composable
private fun scanPhaseLabel(phase: LibraryScanPhase): String =
    when (phase) {
        LibraryScanPhase.Idle -> stringResource(L10nR.string.feature_library_waiting_to_scan_a5c343)
        LibraryScanPhase.Preparing -> stringResource(L10nR.string.feature_library_preparing_scan_7e7bcf)
        LibraryScanPhase.QueryingMediaStore -> stringResource(L10nR.string.feature_library_reading_mediastore_87cd18)
        LibraryScanPhase.Diffing -> stringResource(L10nR.string.feature_library_comparing_library_90f3c1)
        LibraryScanPhase.WritingDatabase -> stringResource(L10nR.string.feature_library_writing_library_f9ac03)
        LibraryScanPhase.CleaningRemoved -> stringResource(L10nR.string.feature_library_cleaning_removed_music_40b12d)
        LibraryScanPhase.Completed -> stringResource(L10nR.string.feature_library_scan_complete_fbdf16)
        LibraryScanPhase.Cancelled -> stringResource(L10nR.string.feature_library_scan_cancelled_1eefcb)
        LibraryScanPhase.Error -> stringResource(L10nR.string.feature_library_scan_failed_f4c0ae)
    }

@Composable
internal fun LibraryScanResultBanner(scanState: LibraryScanProgress) {
    val colors = rememberLibraryGlassColors()
    val message = when (scanState.phase) {
        LibraryScanPhase.Completed -> stringResource(L10nR.string.feature_library_scan_complete_scanstate_scannedcount_tracks_added_scanstate_inse_bf2d9e, (scanState.scannedCount).toString(), (scanState.insertedCount).toString(), (scanState.updatedCount).toString(), (scanState.deletedCount).toString())
        LibraryScanPhase.Cancelled -> stringResource(L10nR.string.feature_library_scan_cancelled_the_existing_library_was_kept_266734)
        LibraryScanPhase.Error -> scanState.error ?: stringResource(L10nR.string.feature_library_library_scan_failed_94b709)
        else -> null
    } ?: return
    var visible by remember(message) { mutableStateOf(true) }

    LaunchedEffect(message, scanState.phase) {
        if (scanState.phase != LibraryScanPhase.Error) {
            delay(3_200L)
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(durationMillis = 160)) + expandVertically(tween(durationMillis = 180)),
        exit = fadeOut(tween(durationMillis = 180)) + shrinkVertically(tween(durationMillis = 220)),
    ) {
        EchoPanel(Modifier.fillMaxWidth()) {
            Text(
                text = message,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = if (scanState.phase == LibraryScanPhase.Error) EchoColors.Coral else colors.muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun LibraryBootstrapState() {
    val colors = rememberLibraryGlassColors()
    EchoPanel(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EchoIconBadge(Icons.Rounded.LibraryMusic)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(L10nR.string.feature_library_no_local_songs_yet_fa3b6a),
                    color = colors.content,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(L10nR.string.feature_library_scan_local_music_from_the_top_right_corner_584da2),
                    color = colors.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
internal fun LibraryDetailPage(
    title: String,
    subtitle: String?,
    tracks: LazyPagingItems<EchoTrack>,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onPlayTrack: (EchoTrack) -> Unit,
    onUpdateTrackMetadata: ((EchoTrackMetadataUpdate) -> Unit)? = null,
    onImportLyrics: ((EchoTrack) -> Unit)? = null,
    onPickArtwork: ((EchoTrack) -> Unit)? = null,
    onMatchNeteaseMetadata: ((EchoTrack) -> Unit)? = null,
    onAddToPlaylist: ((EchoTrack) -> Unit)? = null,
    onPlayNext: ((EchoTrack) -> Unit)? = null,
    onEnqueue: ((EchoTrack) -> Unit)? = null,
    onRemoveFromPlaylist: ((EchoTrack) -> Unit)? = null,
    onMoveTrack: ((fromIndex: Int, toIndex: Int) -> Unit)? = null,
    headerActions: @Composable RowScope.() -> Unit = {},
    showAudioInfoTags: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = rememberLibraryGlassColors()
    val trackListState = rememberLazyListState()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EchoPanel(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            title,
                            color = colors.content,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        subtitle?.takeIf { it.isNotBlank() }?.let { value ->
                            Text(
                                value,
                                color = colors.muted,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    EchoTextButton(
                        text = stringResource(L10nR.string.feature_library_back_49093c),
                        onClick = onBack,
                    )
                    EchoTextButton(
                        text = stringResource(L10nR.string.feature_library_play_all_55c80e),
                        onClick = onPlayAll,
                    )
                    headerActions()
                }
            }
        }

        when {
            tracks.loadState.refresh is LoadState.Loading -> EmptyState(
                stringResource(L10nR.string.feature_library_loading_tracks_8e2147),
            )
            tracks.loadState.refresh is LoadState.Error -> EmptyState(
                stringResource(L10nR.string.feature_library_failed_to_load_tracks_f65c9b),
            )
            tracks.itemCount == 0 -> EmptyState(
                stringResource(L10nR.string.feature_library_no_tracks_yet_c4614a),
            )
            else -> TrackList(
                tracks = tracks,
                onPlayTrack = onPlayTrack,
                onUpdateTrackMetadata = onUpdateTrackMetadata,
                onImportLyrics = onImportLyrics,
                onPickArtwork = onPickArtwork,
                onMatchNeteaseMetadata = onMatchNeteaseMetadata,
                onAddToPlaylist = onAddToPlaylist,
                onPlayNext = onPlayNext,
                onEnqueue = onEnqueue,
                onRemoveFromPlaylist = onRemoveFromPlaylist,
                onMoveTrack = onMoveTrack,
                showAudioInfoTags = showAudioInfoTags,
                listState = trackListState,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
