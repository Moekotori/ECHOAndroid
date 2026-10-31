package app.echo.android.feature.library

import app.echo.android.model.library.*

/** App-owned callbacks keep server I/O and playback outside Compose and feature dependencies. */
class NavidromeActions(
    val sourceId: String,
    val load: suspend (NavidromeQuery) -> NavidromePage,
    val information: suspend (NavidromeEntry) -> NavidromeInformation,
    val execute: suspend (NavidromeCommand) -> Unit,
    val playlist: suspend (String) -> NavidromePlaylistSettings,
    val refreshPlaylists: suspend () -> Unit,
    val refreshFavorites: suspend () -> Unit,
    val saveQueue: suspend () -> Unit,
    val restoreQueue: suspend () -> Unit,
    val bookmarkCurrent: suspend (String) -> Unit,
    val play: (NavidromeEntry) -> Unit,
    val playQueue: (List<NavidromeEntry>, Int) -> Unit,
    val receipts: suspend () -> List<NavidromeReceipt>,
    val resolveReceipt: suspend (String, NavidromeReceiptResolution) -> Unit,
)
