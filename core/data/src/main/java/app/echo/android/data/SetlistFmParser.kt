package app.echo.android.data

import app.echo.android.model.library.ArtistSetlist
import app.echo.android.model.library.ArtistSetlistSong
import org.json.JSONArray
import org.json.JSONObject

object SetlistFmParser {
    fun toApiDate(isoDate: String): String? {
        val parts = isoDate.trim().split('-')
        if (parts.size != 3) return null
        val year = parts[0]
        val month = parts[1]
        val day = parts[2]
        if (year.length != 4 || month.length != 2 || day.length != 2) return null
        return "$day-$month-$year"
    }

    fun fromApiDate(apiDate: String): String {
        val parts = apiDate.trim().split('-')
        if (parts.size != 3) return apiDate.trim()
        val day = parts[0].padStart(2, '0')
        val month = parts[1].padStart(2, '0')
        val year = parts[2]
        return if (year.length == 4) "$year-$month-$day" else apiDate.trim()
    }

    fun parseSetlists(json: JSONObject, artistName: String, wantedIsoDate: String?): List<ArtistSetlist> {
        val items = json.optJSONArray("setlist") ?: return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                add(parseSetlist(item, artistName))
            }
        }.let { setlists ->
            if (wantedIsoDate.isNullOrBlank()) setlists
            else setlists.filter { it.eventDate == wantedIsoDate }.ifEmpty { setlists }
        }
    }

    fun parseSetlist(json: JSONObject, fallbackArtist: String): ArtistSetlist {
        val artistName = json.optJSONObject("artist")?.optString("name").orEmpty()
            .ifBlank { fallbackArtist }
        val venue = json.optJSONObject("venue")
        val city = venue?.optJSONObject("city")
        val eventDate = fromApiDate(json.optString("eventDate"))
        return ArtistSetlist(
            id = json.optString("id").ifBlank { eventDate + artistName },
            artistName = artistName,
            eventDate = eventDate,
            venue = venue?.optString("name")?.takeIf { it.isNotBlank() },
            city = city?.optString("name")?.takeIf { it.isNotBlank() },
            url = json.optString("url").takeIf { it.isNotBlank() },
            songs = parseSongs(json.optJSONObject("sets")?.optJSONArray("set")),
            exactDate = true,
        )
    }

    fun encode(setlist: ArtistSetlist): JSONObject = JSONObject()
        .put("id", setlist.id)
        .put("artistName", setlist.artistName)
        .put("eventDate", setlist.eventDate)
        .put("venue", setlist.venue)
        .put("city", setlist.city)
        .put("url", setlist.url)
        .put("exactDate", setlist.exactDate)
        .put("songs", JSONArray().apply {
            setlist.songs.forEach { song ->
                put(
                    JSONObject()
                        .put("title", song.title)
                        .put("artist", song.artist)
                        .put("tape", song.tape),
                )
            }
        })

    fun decode(json: JSONObject): ArtistSetlist {
        val songs = json.optJSONArray("songs") ?: JSONArray()
        return ArtistSetlist(
            id = json.optString("id"),
            artistName = json.optString("artistName"),
            eventDate = json.optString("eventDate"),
            venue = json.optString("venue").takeIf { it.isNotBlank() },
            city = json.optString("city").takeIf { it.isNotBlank() },
            url = json.optString("url").takeIf { it.isNotBlank() },
            exactDate = json.optBoolean("exactDate", true),
            songs = buildList {
                for (index in 0 until songs.length()) {
                    val song = songs.optJSONObject(index) ?: continue
                    add(
                        ArtistSetlistSong(
                            title = song.optString("title"),
                            artist = song.optString("artist").takeIf { it.isNotBlank() },
                            tape = song.optBoolean("tape"),
                        ),
                    )
                }
            },
        )
    }

    private fun parseSongs(sets: JSONArray?): List<ArtistSetlistSong> {
        if (sets == null) return emptyList()
        return buildList {
            for (setIndex in 0 until sets.length()) {
                val songs = sets.optJSONObject(setIndex)?.optJSONArray("song") ?: continue
                for (songIndex in 0 until songs.length()) {
                    val song = songs.optJSONObject(songIndex) ?: continue
                    val title = song.optString("name").trim()
                    if (title.isEmpty()) continue
                    add(
                        ArtistSetlistSong(
                            title = title,
                            artist = song.optJSONObject("cover")?.optString("name")?.takeIf { it.isNotBlank() },
                            tape = song.optBoolean("tape"),
                        ),
                    )
                }
            }
        }
    }
}
