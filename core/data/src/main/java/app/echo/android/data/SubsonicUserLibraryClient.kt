package app.echo.android.data

import app.echo.android.model.library.*
import java.net.URI
import org.json.JSONObject

private const val UserPageSize = 50
private const val MaxUserSongs = 25_000

internal fun JSONObject.requiredUserObject(key: String): JSONObject = optJSONObject(key)
    ?: error("Incomplete Navidrome response: $key")

internal fun JSONObject.validUserRows(key: String, limit: Int): List<JSONObject> {
    require(!has(key) || isNull(key) || optJSONArray(key) != null || optJSONObject(key) != null) {
        "Invalid Navidrome collection"
    }
    val rows = jsonObjects(key)
    require(optJSONArray(key)?.length()?.let { it == rows.size } != false) { "Malformed Navidrome collection" }
    require(rows.size <= limit && rows.all { it.optString("id").isNotBlank() && !it.isNull("id") }) {
        "Incomplete or oversized Navidrome collection"
    }
    return rows
}

private fun JSONObject.userText(key: String): String? = if (isNull(key)) null else optString(key).trim().takeIf(String::isNotBlank)

internal fun JSONObject.userEntry(endpoint: SubsonicEndpoint, kind: NavidromeKind): NavidromeEntry {
    val id = requireNotNull(userText("id"))
    val song = if (kind == NavidromeKind.Track) toSubsonicSong() else null
    return NavidromeEntry(id, userText("name") ?: userText("title") ?: id, kind,
        track = song?.toLibraryTrackEntity(endpoint, 0)?.toEchoTrack(),
        artworkUri = userText("coverArt")?.let { unsignedSubsonicResourceUrl(endpoint.normalizedBaseUrl, "getCoverArt.view", it,
            listOf("size" to SubsonicClient.CoverArtSizePx.toString())) } ?: userText("artistImageUrl")?.takeIf(::isSubsonicWebUrl),
        favorite = userText("starred") != null, rating = optInt("userRating").coerceIn(0, 5),
        url = userText("streamUrl").orEmpty(), homepage = userText("homePageUrl").orEmpty())
}

private fun <T> userPage(entries: List<T>, page: Int, map: (T) -> NavidromeEntry): NavidromePage {
    require(page in 0..10_000)
    val start = (page * UserPageSize).coerceAtMost(entries.size)
    return NavidromePage(entries.subList(start, (start + UserPageSize).coerceAtMost(entries.size)).map(map), page,
        hasMore = start + UserPageSize < entries.size)
}

internal fun SubsonicClient.favoriteEntries(endpoint: SubsonicEndpoint): List<NavidromeEntry> {
    val root = request("getStarred2.view").requiredUserObject("starred2")
    return listOf("song" to NavidromeKind.Track, "album" to NavidromeKind.Album, "artist" to NavidromeKind.Artist)
        .flatMap { (key, kind) -> root.validUserRows(key, 50_000).map { it.userEntry(endpoint, kind).copy(favorite = true) } }
}

internal fun SubsonicClient.readUserLibrary(endpoint: SubsonicEndpoint, query: NavidromeQuery): NavidromePage = when (query) {
    is NavidromeQuery.Collection -> when (query.collection) {
        NavidromeCollection.Favorites -> {
            val root = request("getStarred2.view").requiredUserObject("starred2")
            val rows = listOf("song" to NavidromeKind.Track, "album" to NavidromeKind.Album, "artist" to NavidromeKind.Artist)
                .flatMap { (key, kind) -> root.validUserRows(key, 50_000).map { it to kind } }
            require(rows.size <= 50_000)
            userPage(rows, query.page) { (row, kind) -> row.userEntry(endpoint, kind).copy(favorite = true) }
        }
        NavidromeCollection.Artists -> {
            val root = request("getArtists.view").requiredUserObject("artists")
            val artists = root.jsonObjects("index").flatMap { it.validUserRows("artist", 100_000) }
            require(artists.size <= 100_000)
            userPage(artists, query.page) { it.userEntry(endpoint, NavidromeKind.Artist) }
        }
        NavidromeCollection.Bookmarks -> {
            val bookmarks = request("getBookmarks.view").requiredUserObject("bookmarks").jsonObjects("bookmark")
            require(bookmarks.size <= 2_000)
            userPage(bookmarks, query.page) { bookmark -> bookmark.requiredUserObject("entry").userEntry(endpoint, NavidromeKind.Track)
                .copy(positionMs = bookmark.optLong("position").coerceAtLeast(0), comment = bookmark.userText("comment").orEmpty()) }
        }
        NavidromeCollection.Radios -> userPage(request("getInternetRadioStations.view").requiredUserObject("internetRadioStations")
            .validUserRows("internetRadioStation", 200), query.page) { it.userEntry(endpoint, NavidromeKind.Radio) }
        NavidromeCollection.Queue -> readQueue(endpoint, query.page)
    }
    is NavidromeQuery.Search -> {
        require(query.text.isNotBlank() && query.text.length <= 500 && query.page in 0..10_000)
        val offset = (query.page * 16).toString()
        val root = request("search3.view", listOf("query" to query.text) +
            listOf("song", "album", "artist").flatMap { listOf("${it}Count" to "17", "${it}Offset" to offset) })
            .requiredUserObject("searchResult3")
        val groups = listOf("artist" to NavidromeKind.Artist, "album" to NavidromeKind.Album, "song" to NavidromeKind.Track)
            .map { (key, kind) -> root.validUserRows(key, 17).map { it.userEntry(endpoint, kind) } }
        NavidromePage(groups.flatMap { it.take(16) }, query.page, groups.any { it.size > 16 })
    }
    is NavidromeQuery.Browse -> {
        require(query.id.isNotBlank() && query.kind in listOf(NavidromeKind.Album, NavidromeKind.Artist))
        val artist = query.kind == NavidromeKind.Artist
        val key = if (artist) "artist" else "album"
        val root = request(if (artist) "getArtist.view" else "getAlbum.view", listOf("id" to query.id)).requiredUserObject(key)
        require(root.userText("id") == query.id)
        val rows = root.validUserRows(if (artist) "album" else "song", if (artist) 10_000 else MaxUserSongs)
        if (!artist && root.has("songCount")) require(root.optInt("songCount") == rows.size)
        userPage(rows, query.page) { row ->
            if (!artist) {
                if (row.userText("album") == null) row.put("album", root.userText("name"))
                if (row.userText("albumArtist") == null && row.userText("albumArtists") == null) {
                    row.put("albumArtists", root.opt("artists"))
                }
                if (row.userText("coverArt") == null) row.put("coverArt", root.userText("coverArt"))
            }
            row.userEntry(endpoint, if (artist) NavidromeKind.Album else NavidromeKind.Track)
        }
    }
}

private fun SubsonicClient.supportsIndexQueue(): Boolean {
    val extensions = request("getOpenSubsonicExtensions.view").jsonObjects("openSubsonicExtensions")
    return extensions.any { it.userText("name") == "indexBasedQueue" }
}

internal fun SubsonicClient.readQueue(endpoint: SubsonicEndpoint, page: Int? = null): NavidromePage {
    val indexed = try { supportsIndexQueue() } catch (cancelled: java.util.concurrent.CancellationException) { throw cancelled }
        catch (_: Exception) { false }
    val root = request(if (indexed) "getPlayQueueByIndex.view" else "getPlayQueue.view")
        .requiredUserObject(if (indexed) "playQueueByIndex" else "playQueue")
    val entries = root.validUserRows("entry", MaxUserSongs)
    val index = if (indexed) root.optInt("currentIndex", -1) else entries.indexOfFirst { it.userText("id") == root.userText("current") }
    require(entries.isEmpty() || index in entries.indices) { "Invalid Navidrome queue position" }
    val result = if (page != null) userPage(entries, page) { it.userEntry(endpoint, NavidromeKind.Track) }
        else NavidromePage(entries.map { it.userEntry(endpoint, NavidromeKind.Track) })
    return result.copy(currentIndex = index.coerceAtLeast(0), positionMs = root.optLong("position").coerceAtLeast(0))
}

internal fun SubsonicClient.saveQueue(ids: List<String>, currentIndex: Int, positionMs: Long) {
    require(ids.isNotEmpty() && ids.size <= MaxUserSongs && ids.all(String::isNotBlank) && currentIndex in ids.indices && positionMs >= 0)
    val indexed = try { supportsIndexQueue() } catch (cancelled: java.util.concurrent.CancellationException) { throw cancelled }
        catch (_: Exception) { false }
    request(if (indexed) "savePlayQueueByIndex.view" else "savePlayQueue.view",
        ids.map { "id" to it } + listOf((if (indexed) "currentIndex" else "current") to
            (if (indexed) currentIndex.toString() else ids[currentIndex]), "position" to positionMs.toString()), mutation = true)
}

internal fun SubsonicClient.playlistSettings(endpoint: SubsonicEndpoint, id: String): NavidromePlaylistSettings {
    val root = request("getPlaylist.view", listOf("id" to id)).requiredUserObject("playlist")
    require(root.userText("id") == id)
    val songs = root.validUserRows("entry", MaxUserSongs)
    require(!root.has("songCount") || root.optInt("songCount") == songs.size)
    return NavidromePlaylistSettings(id, requireNotNull(root.userText("name")), root.userText("comment").orEmpty(),
        root.optBoolean("public"), !root.optBoolean("readonly") &&
            (root.userText("owner") == null || root.userText("owner") == endpoint.username.trim()),
        songs.map { it.userEntry(endpoint, NavidromeKind.Track) })
}

internal fun SubsonicClient.executeUserCommand(command: NavidromeCommand) {
    fun mutate(path: String, vararg params: Pair<String, String>) { request("$path.view", params.toList(), mutation = true) }
    when (command) {
        is NavidromeCommand.Favorite -> {
            require(command.id.isNotBlank() && command.kind != NavidromeKind.Radio)
            val key = when (command.kind) { NavidromeKind.Track -> "id"; NavidromeKind.Album -> "albumId"; else -> "artistId" }
            mutate(if (command.value) "star" else "unstar", key to command.id)
        }
        is NavidromeCommand.Rating -> { require(command.id.isNotBlank() && command.value in 0..5)
            mutate("setRating", "id" to command.id, "rating" to command.value.toString()) }
        is NavidromeCommand.Bookmark -> { require(command.id.isNotBlank() && command.positionMs >= 0 && command.comment.length <= 1000)
            mutate("createBookmark", "id" to command.id, "position" to command.positionMs.toString(), "comment" to command.comment) }
        is NavidromeCommand.DeleteBookmark -> { require(command.id.isNotBlank()); mutate("deleteBookmark", "id" to command.id) }
        is NavidromeCommand.Radio -> {
            require(command.name.isNotBlank() && command.name.length <= 200 && isSubsonicWebUrl(command.url) &&
                (command.homepage.isBlank() || isSubsonicWebUrl(command.homepage)))
            request(if (command.id == null) "createInternetRadioStation.view" else "updateInternetRadioStation.view",
                listOfNotNull(command.id?.let { "id" to it }, "name" to command.name.trim(), "streamUrl" to command.url.trim(),
                    "homepageUrl" to command.homepage.trim()), mutation = true)
        }
        is NavidromeCommand.DeleteRadio -> { require(command.id.isNotBlank()); mutate("deleteInternetRadioStation", "id" to command.id) }
        is NavidromeCommand.Playlist -> {
            require(command.id.isNotBlank() && command.name.isNotBlank() && command.description.length <= 4000)
            command.songIds?.let { songs -> require(songs.size <= MaxUserSongs && songs.all(String::isNotBlank))
                request("createPlaylist.view", listOf("playlistId" to command.id) + songs.map { "songId" to it }, mutation = true) }
            mutate("updatePlaylist", "playlistId" to command.id, "name" to command.name.trim(),
                "comment" to command.description, "public" to command.public.toString())
        }
    }
}

internal fun isSubsonicWebUrl(value: String): Boolean = runCatching {
    val uri = URI(value.trim())
    uri.scheme?.lowercase() in listOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null
}.getOrDefault(false)
