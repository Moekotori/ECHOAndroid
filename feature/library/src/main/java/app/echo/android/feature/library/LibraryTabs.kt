package app.echo.android.feature.library

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Scanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import app.echo.android.design.EchoAccent
import app.echo.android.design.EchoAccentDeep
import app.echo.android.design.EchoAccentText
import app.echo.android.design.EchoContentMaxWidth
import app.echo.android.design.EchoColors
import app.echo.android.design.EchoDarkGlassBorder
import app.echo.android.design.EchoGlassBorder
import app.echo.android.design.EchoGlassInk
import app.echo.android.design.EchoGlassPanel
import app.echo.android.design.EchoHomeBlue
import app.echo.android.design.EchoHomeMist
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.EchoIconBadge
import app.echo.android.design.EchoPanel
import app.echo.android.design.EchoTextButton
import app.echo.android.design.EmptyState
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.PageChrome
import app.echo.android.design.RoonInk
import app.echo.android.design.RoonMuted
import app.echo.android.design.formatDuration
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.FolderSummary
import app.echo.android.model.library.LibraryScanPhase
import app.echo.android.model.library.LibraryScanProgress
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
internal fun LibraryPagerTabs(
    selectedMode: LibraryViewMode,
    onSelectMode: (LibraryViewMode) -> Unit,
) {
    val colors = rememberLibraryGlassColors()
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
                    .clickable { onSelectMode(mode) }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = mode.label,
                    color = if (selected) EchoAccentText else colors.muted,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                )
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(
                            if (selected) {
                                Brush.horizontalGradient(listOf(EchoAccent, EchoAccentDeep))
                            } else {
                                Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                            },
                        ),
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
    val shape = RoundedCornerShape(18.dp)
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var expanded by remember { mutableStateOf(query.isNotBlank()) }
    val width by animateDpAsState(
        targetValue = if (expanded) expandedWidth else 46.dp,
        animationSpec = tween(durationMillis = 240),
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
            enter = fadeIn(animationSpec = tween(durationMillis = 150)),
            exit = fadeOut(animationSpec = tween(durationMillis = 120)),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { expanded = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = "搜索曲库",
                    tint = if (dark) colors.content else EchoAccentText,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandHorizontally(
                expandFrom = Alignment.End,
                animationSpec = tween(durationMillis = 260),
            ) + fadeIn(animationSpec = tween(durationMillis = 180)),
            exit = shrinkHorizontally(
                shrinkTowards = Alignment.End,
                animationSpec = tween(durationMillis = 200),
            ) + fadeOut(animationSpec = tween(durationMillis = 130)),
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
                            contentDescription = "关闭搜索",
                            tint = colors.muted,
                        )
                    }
                },
                placeholder = {
                    Text(
                        "搜索歌曲、艺术家、专辑",
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
                    cursorColor = EchoAccentText,
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
        EmptyState("正在加载文件夹...")
        return
    }
    if (folders.loadState.refresh is LoadState.Error) {
        EmptyState("文件夹加载失败。")
        return
    }
    if (folders.itemCount == 0) {
        LibraryPlaceholderPage(
            title = "文件夹视图",
            subtitle = "当前曲库还没有可浏览的存储路径。",
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
            .clickable(onClick = onClick)
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
                accent = if (dark) Color.White.copy(alpha = 0.28f) else EchoAccent,
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
                        text = "${folder.trackCount} 首",
                    )
                    folder.albumCount.takeIf { it > 0 }?.let {
                        FolderMetaChip(
                            icon = Icons.Rounded.LibraryMusic,
                            text = "$it 张",
                        )
                    }
                }
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "打开文件夹",
                tint = if (dark) Color.White.copy(alpha = 0.68f) else EchoAccentText,
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
            .background(if (LocalEchoDarkTheme.current) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (LocalEchoDarkTheme.current) Color.White.copy(alpha = 0.72f) else EchoAccentText,
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

internal fun folderDisplayName(folder: FolderSummary): String =
    folder.path
        ?.trim('/')
        ?.substringAfterLast('/')
        ?.takeIf { it.isNotBlank() }
        ?: "未知路径"

internal fun folderSubtitle(folder: FolderSummary): String =
    listOf(
        "${folder.trackCount} 首",
        "${folder.albumCount} 张专辑",
        "${folder.artistCount} 位艺术家",
        formatDuration(folder.durationMs),
        formatByteSize(folder.totalSizeBytes),
    ).joinToString(" · ")

private fun folderPathLabel(folder: FolderSummary): String =
    folder.path?.takeIf { it.isNotBlank() } ?: "MediaStore 未提供路径"

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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        colors.surface,
                        colors.elevatedSurface,
                        EchoAccentDeep.copy(alpha = if (LocalEchoDarkTheme.current) 0.18f else 0.08f),
                    ),
                ),
            )
            .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(22.dp))
            .padding(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            LibraryMetric("歌曲", trackCount.toString(), Modifier.weight(1f))
            LibraryMetric("专辑", albumCount.toString(), Modifier.weight(1f))
            LibraryMetric("艺人", artistCount.toString(), Modifier.weight(1f))
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
                    .background(if (selected) EchoHomeBlue.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { onSelectMode(mode) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    mode.icon,
                    contentDescription = mode.label,
                    tint = if (selected) EchoAccentText else colors.muted,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    mode.label,
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
    Box {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.elevatedSurface)
                .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(14.dp))
                .clickable { expanded = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                selectedMode.icon,
                contentDescription = "切换曲库显示方式",
                tint = EchoHomeBlue,
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
                            mode.label,
                            color = if (mode == selectedMode) EchoHomeBlue else colors.content,
                            fontWeight = if (mode == selectedMode) FontWeight.Bold else FontWeight.SemiBold,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            mode.icon,
                            contentDescription = null,
                            tint = if (mode == selectedMode) EchoHomeBlue else colors.muted,
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
        EmptyState("正在加载专辑...")
        return
    }
    if (albums.loadState.refresh is LoadState.Error) {
        EmptyState("专辑加载失败。")
        return
    }
    if (albums.itemCount == 0) {
        EmptyState("当前曲库还没有可展示的专辑。")
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
    val artistLabel = album.albumArtist ?: album.artist ?: "未知艺术家"
    val colors = rememberLibraryGlassColors()
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ArtworkTile(
            artworkUri = album.artworkUri,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            accent = EchoAccent,
            showSignal = album.artworkUri == null,
            cornerRadius = 14.dp,
            elevation = 8.dp,
        )
        Text(album.title, color = colors.content, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("$artistLabel / ${album.trackCount} 首", color = colors.muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun ArtistWall(
    artists: LazyPagingItems<ArtistSummary>,
    onOpenArtist: (ArtistSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (artists.loadState.refresh is LoadState.Loading) {
        EmptyState("正在加载艺人...")
        return
    }
    if (artists.loadState.refresh is LoadState.Error) {
        EmptyState("艺人加载失败。")
        return
    }
    if (artists.itemCount == 0) {
        EmptyState("当前曲库还没有可展示的艺人。")
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
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        ArtistWallAvatar(
            artworkUri = artist.artworkUri,
            palette = palette,
        )
        Text(
            artist.name,
            color = colors.content,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            "${artist.albumCount} 张专辑 · ${artist.trackCount} 首",
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
                            ?: "正在增量索引本机音乐",
                        color = colors.muted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                EchoTextButton(
                    text = "取消扫描",
                    onClick = onCancelScan,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LibraryMetric("已扫描", scanProgressValue(scanState), Modifier.weight(1f))
                LibraryMetric("新增", scanState.insertedCount.toString(), Modifier.weight(1f))
                LibraryMetric("更新", scanState.updatedCount.toString(), Modifier.weight(1f))
                LibraryMetric("删除", scanState.deletedCount.toString(), Modifier.weight(1f))
            }
            scanState.error?.takeIf { it.isNotBlank() }?.let { error ->
                Text(error, color = Color(0xFFE0796E), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun scanProgressValue(scanState: LibraryScanProgress): String =
    scanState.totalCount?.let { total -> "${scanState.scannedCount}/$total" }
        ?: scanState.scannedCount.toString()

private fun scanPhaseLabel(phase: LibraryScanPhase): String =
    when (phase) {
        LibraryScanPhase.Idle -> "等待扫描"
        LibraryScanPhase.Preparing -> "准备扫描"
        LibraryScanPhase.QueryingMediaStore -> "正在读取 MediaStore"
        LibraryScanPhase.Diffing -> "正在对比曲库"
        LibraryScanPhase.WritingDatabase -> "正在写入曲库"
        LibraryScanPhase.CleaningRemoved -> "正在清理已移除音乐"
        LibraryScanPhase.Completed -> "扫描完成"
        LibraryScanPhase.Cancelled -> "扫描已取消"
        LibraryScanPhase.Error -> "扫描失败"
    }

@Composable
internal fun LibraryScanResultBanner(scanState: LibraryScanProgress) {
    val colors = rememberLibraryGlassColors()
    val message = when (scanState.phase) {
        LibraryScanPhase.Completed -> "扫描完成：${scanState.scannedCount} 首，新增 ${scanState.insertedCount}，更新 ${scanState.updatedCount}，删除 ${scanState.deletedCount}"
        LibraryScanPhase.Cancelled -> "扫描已取消，已保留现有曲库。"
        LibraryScanPhase.Error -> scanState.error ?: "曲库扫描失败。"
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
                color = if (scanState.phase == LibraryScanPhase.Error) Color(0xFFE0796E) else colors.muted,
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
                Text("暂无本机歌曲", color = colors.content, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "点右上角扫描本机音乐。",
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
    showAudioInfoTags: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = rememberLibraryGlassColors()
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
                    EchoTextButton(text = "返回", onClick = onBack)
                    EchoTextButton(text = "播放全部", onClick = onPlayAll)
                }
            }
        }

        when {
            tracks.loadState.refresh is LoadState.Loading -> EmptyState("正在加载曲目...")
            tracks.loadState.refresh is LoadState.Error -> EmptyState("曲目加载失败。")
            tracks.itemCount == 0 -> EmptyState("暂无曲目。")
            else -> TrackList(
                tracks = tracks,
                onPlayTrack = onPlayTrack,
                showAudioInfoTags = showAudioInfoTags,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
