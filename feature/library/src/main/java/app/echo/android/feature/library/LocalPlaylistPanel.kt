package app.echo.android.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import app.echo.android.design.ArtworkTile
import app.echo.android.design.EchoAccent
import app.echo.android.design.EchoAccentDeep
import app.echo.android.design.EchoGlassBorder
import app.echo.android.design.EchoHomeMist
import app.echo.android.design.EchoIconBadge
import app.echo.android.design.EchoPanel
import app.echo.android.design.EchoTextButton
import app.echo.android.design.EmptyState
import app.echo.android.design.echoString
import app.echo.android.model.library.EchoPlaylist
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.EchoTrackMetadataUpdate

@Composable
internal fun LocalPlaylistPanel(
    playlists: List<EchoPlaylist>,
    onOpenPlaylist: (EchoPlaylist) -> Unit,
    onPlayPlaylist: (EchoPlaylist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onRenamePlaylist: (EchoPlaylist, String) -> Unit,
    onDeletePlaylist: (EchoPlaylist) -> Unit,
    modifier: Modifier = Modifier,
) {
    var createVisible by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<EchoPlaylist?>(null) }
    var deleting by remember { mutableStateOf<EchoPlaylist?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = LibraryBottomControlsPadding),
    ) {
        item {
            LocalPlaylistHeader(
                playlistCount = playlists.size,
                onCreatePlaylist = { createVisible = true },
            )
        }
        if (playlists.isEmpty()) {
            item {
                EmptyState(
                    echoString(
                        en = "No local playlists yet. Create one from the top-right, or add from a song menu.",
                        zh = "还没有本地歌单。点右上角创建，或从歌曲菜单加入。",
                        ja = "ローカルのプレイリストはまだありません。右上から作成するか、曲メニューから追加してください。",
                    ),
                )
            }
        } else {
            items(
                items = playlists,
                key = { it.id },
            ) { playlist ->
                LocalPlaylistRow(
                    playlist = playlist,
                    onOpen = { onOpenPlaylist(playlist) },
                    onPlay = { onPlayPlaylist(playlist) },
                    onRename = { if (playlist.canEdit) renaming = playlist },
                    onDelete = { if (playlist.canEdit) deleting = playlist },
                )
            }
        }
    }

    if (createVisible) {
        PlaylistNameDialog(
            title = echoString(en = "New playlist", zh = "新建歌单", ja = "プレイリストを作成"),
            confirmLabel = echoString(en = "Create", zh = "创建", ja = "作成"),
            initialName = "",
            onDismiss = { createVisible = false },
            onConfirm = { name ->
                onCreatePlaylist(name)
                createVisible = false
            },
        )
    }
    renaming?.let { playlist ->
        PlaylistNameDialog(
            title = echoString(en = "Rename playlist", zh = "重命名歌单", ja = "プレイリスト名を変更"),
            confirmLabel = echoString(en = "Save", zh = "保存", ja = "保存"),
            initialName = playlist.name,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                onRenamePlaylist(playlist, name)
                renaming = null
            },
        )
    }
    deleting?.let { playlist ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(echoString(en = "Delete playlist", zh = "删除歌单", ja = "プレイリストを削除")) },
            text = {
                Text(
                    echoString(
                        en = "Delete “${playlist.name}”? Songs in the library will not be deleted.",
                        zh = "删除「${playlist.name}」？曲库里的歌曲不会被删。",
                        ja = "「${playlist.name}」を削除しますか？ライブラリの曲は削除されません。",
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePlaylist(playlist)
                        deleting = null
                    },
                ) {
                    Text(echoString(en = "Delete", zh = "删除", ja = "削除"))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(echoString(en = "Cancel", zh = "取消", ja = "キャンセル"))
                }
            },
        )
    }
}

@Composable
internal fun PlaylistDetailPage(
    playlist: EchoPlaylist,
    tracks: LazyPagingItems<EchoTrack>,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onPlayTrack: (EchoTrack) -> Unit,
    onRenamePlaylist: (String) -> Unit,
    onDeletePlaylist: () -> Unit,
    onRemoveTrack: (EchoTrack) -> Unit,
    onMoveTrack: (fromIndex: Int, toIndex: Int) -> Unit,
    onUpdateTrackMetadata: ((EchoTrackMetadataUpdate) -> Unit)? = null,
    onImportLyrics: ((EchoTrack) -> Unit)? = null,
    onPickArtwork: ((EchoTrack) -> Unit)? = null,
    onAddToPlaylist: ((EchoTrack) -> Unit)? = null,
    onPlayNext: ((EchoTrack) -> Unit)? = null,
    onEnqueue: ((EchoTrack) -> Unit)? = null,
    showAudioInfoTags: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxSize()) {
        LibraryDetailPage(
            title = playlistDisplayName(playlist),
            subtitle = playlistCaption(playlist),
            tracks = tracks,
            onBack = onBack,
            onPlayAll = onPlayAll,
            onPlayTrack = onPlayTrack,
            onUpdateTrackMetadata = onUpdateTrackMetadata,
            onImportLyrics = onImportLyrics,
            onPickArtwork = onPickArtwork,
            onAddToPlaylist = onAddToPlaylist,
            onPlayNext = onPlayNext,
            onEnqueue = onEnqueue,
            onRemoveFromPlaylist = onRemoveTrack.takeIf { playlist.canRemoveTracks },
            onMoveTrack = onMoveTrack.takeIf { playlist.canEdit },
            showAudioInfoTags = showAudioInfoTags,
            headerActions = {
                if (playlist.canEdit) {
                    EchoTextButton(
                        text = echoString(en = "Rename", zh = "重命名", ja = "名前を変更"),
                        onClick = { renaming = true },
                    )
                    EchoTextButton(
                        text = echoString(en = "Delete", zh = "删除", ja = "削除"),
                        onClick = { deleting = true },
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
    if (renaming) {
        PlaylistNameDialog(
            title = echoString(en = "Rename playlist", zh = "重命名歌单", ja = "プレイリスト名を変更"),
            confirmLabel = echoString(en = "Save", zh = "保存", ja = "保存"),
            initialName = playlist.name,
            onDismiss = { renaming = false },
            onConfirm = { name ->
                onRenamePlaylist(name)
                renaming = false
            },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(echoString(en = "Delete playlist", zh = "删除歌单", ja = "プレイリストを削除")) },
            text = {
                Text(
                    echoString(
                        en = "Delete “${playlist.name}”?",
                        zh = "删除「${playlist.name}」？",
                        ja = "「${playlist.name}」を削除しますか？",
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePlaylist()
                        deleting = false
                    },
                ) {
                    Text(echoString(en = "Delete", zh = "删除", ja = "削除"))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = false }) {
                    Text(echoString(en = "Cancel", zh = "取消", ja = "キャンセル"))
                }
            },
        )
    }
}

@Composable
internal fun AddToPlaylistDialog(
    playlists: List<EchoPlaylist>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (EchoPlaylist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        PlaylistNameDialog(
            title = echoString(en = "New playlist and add", zh = "新建歌单并加入", ja = "プレイリストを作成して追加"),
            confirmLabel = echoString(en = "Create", zh = "创建", ja = "作成"),
            initialName = "",
            onDismiss = { creating = false },
            onConfirm = { name ->
                onCreatePlaylist(name)
                creating = false
            },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(echoString(en = "Add to playlist", zh = "加入歌单", ja = "プレイリストに追加")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { creating = true }) {
                    Text(echoString(en = "New playlist", zh = "新建歌单", ja = "プレイリストを作成"))
                }
                if (playlists.isEmpty()) {
                    Text(
                        echoString(
                            en = "No playlists yet. Create one first.",
                            zh = "还没有歌单。先创建一个。",
                            ja = "プレイリストはまだありません。先に作成してください。",
                        ),
                    )
                } else {
                    playlists.filter { it.canEdit || it.isLikedSongs }.forEach { playlist ->
                        Text(
                            echoString(
                                en = "${playlistDisplayName(playlist)} · ${playlist.trackCount} tracks",
                                zh = "${playlistDisplayName(playlist)} · ${playlist.trackCount} 首",
                                ja = "${playlistDisplayName(playlist)} · ${playlist.trackCount} 曲",
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectPlaylist(playlist) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(echoString(en = "Cancel", zh = "取消", ja = "キャンセル"))
            }
        },
    )
}

@Composable
private fun PlaylistNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    val canConfirm = name.trim().isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(80) },
                singleLine = true,
                label = { Text(echoString(en = "Name", zh = "名称", ja = "名前")) },
            )
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = { onConfirm(name) },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(echoString(en = "Cancel", zh = "取消", ja = "キャンセル"))
            }
        },
    )
}

@Composable
private fun playlistDisplayName(playlist: EchoPlaylist): String =
    if (playlist.isLikedSongs) {
        echoString(en = "Liked songs", zh = "喜欢的歌曲", ja = "お気に入り")
    } else {
        playlist.name
    }

@Composable
private fun playlistCaption(playlist: EchoPlaylist): String {
    val count = playlist.trackCount
    return when {
        playlist.isLikedSongs -> echoString(
            en = "$count tracks · Liked songs",
            zh = "$count 首 · 喜欢的歌曲",
            ja = "$count 曲 · お気に入り",
        )
        playlist.canEdit -> echoString(
            en = "$count tracks · Local playlist",
            zh = "$count 首 · 本地歌单",
            ja = "$count 曲 · ローカルプレイリスト",
        )
        else -> echoString(
            en = "$count tracks · Navidrome",
            zh = "$count 首 · Navidrome",
            ja = "$count 曲 · Navidrome",
        )
    }
}

@Composable
private fun LocalPlaylistHeader(
    playlistCount: Int,
    onCreatePlaylist: () -> Unit,
) {
    EchoPanel(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EchoIconBadge(Icons.Rounded.LibraryMusic)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    echoString(en = "Local playlists", zh = "本地歌单", ja = "ローカルプレイリスト"),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    echoString(
                        en = "$playlistCount playlists",
                        zh = "$playlistCount 个歌单",
                        ja = "プレイリスト $playlistCount 件",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            EchoTextButton(
                text = echoString(en = "New", zh = "新建", ja = "新規"),
                onClick = onCreatePlaylist,
            )
        }
    }
}

@Composable
private fun LocalPlaylistRow(
    playlist: EchoPlaylist,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(EchoHomeMist.copy(alpha = 0.46f))
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkTile(
            artworkUri = playlist.artworkUri,
            modifier = Modifier.size(58.dp),
            accent = EchoAccent,
            cornerRadius = 12.dp,
            elevation = 3.dp,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                playlistDisplayName(playlist),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                playlistCaption(playlist),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (playlist.canEdit) {
            IconButtonLite(icon = Icons.Rounded.Edit, onClick = onRename)
            IconButtonLite(icon = Icons.Rounded.DeleteOutline, onClick = onDelete)
        }
        IconButtonLite(icon = Icons.Rounded.PlayArrow, onClick = onPlay)
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun IconButtonLite(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = EchoAccentDeep.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, EchoGlassBorder),
        shape = RoundedCornerShape(12.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}
