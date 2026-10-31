package app.echo.android.model.library

enum class NavidromeKind { Track, Album, Artist, Radio }
enum class NavidromeCollection { Favorites, Artists, Bookmarks, Radios, Queue }

/** Server identities stay separate from Android's local library keys. */
data class NavidromeEntry(
    val id: String,
    val name: String,
    val kind: NavidromeKind,
    val track: EchoTrack? = null,
    val artworkUri: String? = null,
    val favorite: Boolean = false,
    val rating: Int = 0,
    val positionMs: Long = 0,
    val comment: String = "",
    val url: String = "",
    val homepage: String = "",
)

data class NavidromePage(
    val entries: List<NavidromeEntry>,
    val page: Int = 0,
    val hasMore: Boolean = false,
    val currentIndex: Int = 0,
    val positionMs: Long = 0,
)

sealed interface NavidromeQuery {
    data class Collection(val collection: NavidromeCollection, val page: Int = 0) : NavidromeQuery
    data class Search(val text: String, val page: Int = 0) : NavidromeQuery
    data class Browse(val kind: NavidromeKind, val id: String, val page: Int = 0) : NavidromeQuery
}

data class NavidromeInformation(
    val entry: NavidromeEntry,
    val description: String? = null,
    val sourceUrl: String? = null,
    val facts: List<Pair<String, String>> = emptyList(),
)

data class NavidromePlaylistSettings(
    val id: String,
    val name: String,
    val description: String,
    val public: Boolean,
    val writable: Boolean,
    /** Preserve server order and repeated songs when replacing a playlist. */
    val songs: List<NavidromeEntry>,
)

sealed interface NavidromeCommand {
    data class Favorite(val kind: NavidromeKind, val id: String, val value: Boolean) : NavidromeCommand
    data class Rating(val id: String, val value: Int) : NavidromeCommand
    data class Bookmark(val id: String, val positionMs: Long, val comment: String) : NavidromeCommand
    data class DeleteBookmark(val id: String) : NavidromeCommand
    data class Radio(val id: String? = null, val name: String, val url: String, val homepage: String) : NavidromeCommand
    data class DeleteRadio(val id: String) : NavidromeCommand
    data class Playlist(val id: String, val name: String, val description: String, val public: Boolean,
        val songIds: List<String>? = null) : NavidromeCommand
}

enum class NavidromeReceiptState { Pending, Sending, Unknown, Rejected }
data class NavidromeReceipt(val key: String, val songId: String, val timestampMs: Long,
    val state: NavidromeReceiptState, val attempts: Int)
enum class NavidromeReceiptResolution { Retry, Confirmed, Ignore }
