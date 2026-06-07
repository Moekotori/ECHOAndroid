package app.echo.android.data

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull

class MediaStoreTrackScanner(
    private val contentResolver: ContentResolver,
) {
    fun scanAudio(): List<LibraryTrackEntity> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        return contentResolver.query(collection, projection, selection, null, sortOrder)
            ?.use { cursor -> cursor.toTrackEntities(collection) }
            .orEmpty()
    }

    private fun Cursor.toTrackEntities(collection: Uri): List<LibraryTrackEntity> {
        val idIndex = getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleIndex = getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistIndex = getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumIndex = getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val albumArtistIndex = getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ARTIST)
        val albumIdIndex = getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val durationIndex = getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val trackIndex = getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
        val yearIndex = getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
        val mimeIndex = getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
        val sizeIndex = getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
        val modifiedIndex = getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
        val tracks = ArrayList<LibraryTrackEntity>(count.coerceAtLeast(0))

        while (moveToNext()) {
            val mediaId = getLong(idIndex)
            val contentUri = Uri.withAppendedPath(collection, mediaId.toString()).toString()
            val title = getStringOrNull(titleIndex)?.takeIf { it.isNotBlank() } ?: "未知曲目"
            val artist = getStringOrNull(artistIndex)?.takeIf { it.isNotBlank() } ?: "未知艺术家"
            val rawTrack = getLongOrNull(trackIndex)?.toInt()
            val albumId = getLongOrNull(albumIdIndex)?.takeIf { it > 0L }
            tracks += LibraryTrackEntity(
                id = "mediastore:$mediaId",
                contentUri = contentUri,
                title = title,
                artist = artist,
                album = getStringOrNull(albumIndex)?.takeIf { it.isNotBlank() },
                albumArtist = getStringOrNull(albumArtistIndex)?.takeIf { it.isNotBlank() },
                artworkUri = albumId?.let { "content://media/external/audio/albumart/$it" },
                durationMs = getLongOrNull(durationIndex) ?: 0L,
                trackNumber = rawTrack?.rem(1000)?.takeIf { it > 0 },
                discNumber = rawTrack?.div(1000)?.takeIf { it > 0 },
                year = getLongOrNull(yearIndex)?.toInt()?.takeIf { it > 0 },
                mimeType = getStringOrNull(mimeIndex),
                sizeBytes = getLongOrNull(sizeIndex) ?: 0L,
                dateModifiedSeconds = getLongOrNull(modifiedIndex) ?: 0L,
            )
        }
        return tracks
    }
}
