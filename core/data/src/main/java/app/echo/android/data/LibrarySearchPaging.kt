package app.echo.android.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.map
import kotlinx.coroutines.flow.map

// Match the preview predicates/ranking and let Room page the complete result set.
fun EchoLibraryRepository.pagedSearchAlbums(query: String) = Pager(
    PagingConfig(pageSize = 30, maxSize = 180, enablePlaceholders = false),
    pagingSourceFactory = { database.trackDao().pageSearchAlbums(LibrarySummarySearchQuery.searchAllAlbums(query.trim())) },
).flow

fun EchoLibraryRepository.pagedSearchArtists(query: String) = Pager(
    PagingConfig(pageSize = 30, maxSize = 180, enablePlaceholders = false),
    pagingSourceFactory = { database.trackDao().pageSearchArtists(LibrarySummarySearchQuery.searchArtists(query.trim())) },
).flow

fun EchoLibraryRepository.pagedSearchPlaylists(query: String) = Pager(
    PagingConfig(pageSize = 30, maxSize = 180, enablePlaceholders = false),
    pagingSourceFactory = { database.playlistDao().pageSearchPlaylists(query.trim()) },
).flow.map { page -> page.map { it.toEchoPlaylist() } }
