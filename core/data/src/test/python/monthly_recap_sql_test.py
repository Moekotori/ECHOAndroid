"""Small SQLite fixture against the exact @Query bodies compiled by Room."""
from datetime import date
from pathlib import Path
import re
import sqlite3

dao = Path(__file__).parents[2] / "main/java/app/echo/android/data/LibraryPlayEventDao.kt"
queries = dict((name, sql) for sql, name in re.findall(
    r'@Query\(\s*"""((?:(?!""").)*)"""\s*,?\s*\)\s*(?:suspend\s+)?fun (\w+)', dao.read_text(encoding="utf-8"), re.S))
database = sqlite3.connect(":memory:")
database.row_factory = sqlite3.Row
database.executescript("""
CREATE TABLE library_play_events(id INTEGER PRIMARY KEY, trackId TEXT, title TEXT, artist TEXT,
 album TEXT, artworkUri TEXT, listenedMs INTEGER, playedAtEpochMs INTEGER, localEpochDay INTEGER, localHour INTEGER);
CREATE INDEX event_track ON library_play_events(trackId);
CREATE INDEX event_day ON library_play_events(localEpochDay);
CREATE TABLE library_tracks(id TEXT PRIMARY KEY, albumKey TEXT, albumArtist TEXT);
INSERT INTO library_tracks VALUES ('a', 'old-album', 'Old'), ('b', 'fresh-album', 'Fresh'), ('c', 'fresh-album', 'Fresh');
""")
epoch = date(1970, 1, 1)
def day(value):
    return (date.fromisoformat(value) - epoch).days

events = [
    (1, "a", "Old", "2026-08-31", 23),
    (2, "a", "Old", "2026-09-01", 1),
    (3, "b", "Fresh", "2026-09-02", 2),
    (4, "b", "Fresh", "2026-09-04", 2),
    (5, "a", "Old", "2026-09-05", 23),
    (6, "b", "Fresh", "2026-10-01", 2),
    (7, "c", "Guest", "2026-09-03", 3),
]
for identifier, track, artist, local_day, hour in events:
    # Recorded local dates are Hong Kong dates; September 1 at 01:00 starts in UTC August 31.
    played = day(local_day) * 86400000 + hour * 3600000 - 8 * 3600000
    database.execute("INSERT INTO library_play_events VALUES (?, ?, ?, ?, ?, NULL, 60000, ?, ?, ?)",
                     (identifier, track, "Song", artist, "Same title", played, day(local_day), hour))
parameters = {"fromDay": day("2026-09-01"), "untilDay": day("2026-10-01"), "limit": 5}
def query(name):
    return database.execute(queries[name], parameters).fetchall()

totals = query("monthlyTotals")[0]
assert (totals["plays"], totals["listenedMs"], totals["activeDays"]) == (5, 300000, 5)
assert {row["hour"]: row["playCount"] for row in query("monthlyHours")} == {1: 1, 2: 2, 3: 1, 23: 1}
assert query("monthlyNewArtistCount")[0][0] == 2
assert {row["title"]: row["playCount"] for row in query("monthlyNewArtists")} == {"Fresh": 2, "Guest": 1}
albums = query("monthlyAlbums")
assert [(row["rankKey"], row["playCount"]) for row in albums] == [("old-album", 2), ("fresh-album", 1)]
database.execute("DELETE FROM library_tracks WHERE id = 'a'")
fallback = query("monthlyAlbums")
assert len(fallback) == 2 and fallback[0]["playCount"] == 2
print("6 monthly SQLite checks passed: calendar/time-zone boundary, totals, hours, first artists, replays and deleted-track fallback")
