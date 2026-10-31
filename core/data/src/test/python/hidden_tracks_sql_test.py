"""Run with python core/data/src/test/python/hidden_tracks_sql_test.py.
Uses Room's v24 schema and production migration/query SQL; no Android runtime required.
"""
from pathlib import Path
import json
import re
import sqlite3

module = Path(__file__).parents[3]
source = module / "src/main/java/app/echo/android/data"
schema = json.loads((module / "schemas/app.echo.android.data.EchoLibraryDatabase/24.json").read_text(encoding="utf-8"))["database"]
db = sqlite3.connect(":memory:")
db.row_factory = sqlite3.Row
entities = {e["tableName"]: e for e in schema["entities"]}
for name, entity in entities.items():
    db.execute(entity["createSql"].replace("${TABLE_NAME}", name))
    for index in entity.get("indices", []):
        db.execute(index["createSql"].replace("${TABLE_NAME}", name))

migration = (source / "LibraryHiddenTrackMigration.kt").read_text(encoding="utf-8")
for constant in ("CreateTable", "CreateIndex"):
    db.execute(re.search(r'const val ' + constant + r' = "([^"\n]+)"', migration)[1])
view = re.search(r'const val VisibleLibraryTrackSql = "([^"\n]+)"', (source / "LibraryHiddenTracks.kt").read_text(encoding="utf-8"))[1]
db.execute("CREATE VIEW `visible_library_tracks` AS " + view)
new_schema = json.loads((module / "schemas/app.echo.android.data.EchoLibraryDatabase/25.json").read_text(encoding="utf-8"))["database"]
assert next(v for v in new_schema["views"] if v["viewName"] == "visible_library_tracks")["createSql"].replace("${VIEW_NAME}", "visible_library_tracks") == "CREATE VIEW `visible_library_tracks` AS " + view

def insert(table, **values):
    fields = entities[table]["fields"]
    row = {f["columnName"]: ("" if f["affinity"] == "TEXT" else 0) if f.get("notNull", False) else None for f in fields}
    row.update(values)
    db.execute(f'INSERT INTO {table} ({",".join(row)}) VALUES ({",".join("?" for _ in row)})', list(row.values()))

def queries(file):
    return {name: sql for sql, name in re.findall(r'@Query\(\s*"""((?:(?!""").)*)"""\s*,?\s*\)\s*(?:suspend\s+)?fun (\w+)', (source / file).read_text(encoding="utf-8"), re.S)}

track_queries = queries("LibraryTrackDao.kt")
playlist_queries = queries("LibraryPlaylistDao.kt")
params = dict(albumKey="album", playlistId="playlist", limit=100, title="", artist="", album="", query="")
for identifier in ("a", "b"):
    insert("library_tracks", id=identifier, contentUri=f"file:/{identifier}.flac", title=identifier, artist="Artist", album="Album", albumKey="album", artistKey="artist", genre="Rock", genreKey="rock", source="mediastore", durationMs=180000, normalizedTitle=identifier, relativePath="Music/")
    insert("library_playlist_tracks", playlistId="playlist", trackId=identifier, position=ord(identifier))
    insert("library_favorites", trackId=identifier, favoritedAtEpochMs=10)
    insert("library_playback_stats", trackId=identifier, playCount=3, lastPlayedAtEpochMs=10)
    insert("library_track_artists", trackId=identifier, artistKey="artist", name="Artist")

checks = [(track_queries, "pageTracks"), (track_queries, "pageTracksByAlbum"), (track_queries, "searchVoiceTracks"), (playlist_queries, "pagePlaylistTracks"), (playlist_queries, "getPlaylistTracksForPlayback")]
def assert_visible(expected):
    for collection, name in checks:
        assert [r["id"] for r in db.execute(collection[name], params)] == expected, name
    # Also exercise the independently constructed direct album/artist playback queries.
    text = (source / "EchoLibraryRepository.kt").read_text(encoding="utf-8")
    for method in ("albumPlaybackQuery", "artistPlaybackQuery"):
        sql = re.search(r'private fun ' + method + r'.*?"""(.*?)"""', text, re.S)[1]
        key = "album" if method.startswith("album") else "artist"
        assert [r["id"] for r in db.execute(sql, (key, 100))] == expected, method

def rebuild_and_count(expected):
    sql_file = (source / "EchoLibraryDatabase.kt").read_text(encoding="utf-8")
    for kind, table in (("Album", "album"), ("Folder", "folder"), ("Genre", "genre")):
        suffix = "ForLocalKeysSql" if kind == "Album" else "ForKeysSql"
        sql = re.search(r'const val Rebuild' + kind + r'Summaries' + suffix + r'\s*=\s*"""(.*?)"""', sql_file, re.S)[1]
        db.execute(f"DELETE FROM library_{table}_summaries")
        db.execute(sql.replace(":keys", ":key"), {"key": {"album": "album", "folder": "Music/", "genre": "rock"}[table]})
        assert db.execute(f"SELECT SUM(trackCount) FROM library_{table}_summaries").fetchone()[0] == expected
    artist_sql = re.search(r'const val RebuildForKeys = """(.*?)"""', (source / "LibraryArtistIndexSql.kt").read_text(encoding="utf-8"), re.S)[1]
    db.execute("DELETE FROM library_artist_summaries")
    db.execute(artist_sql.replace(":keys", ":key"), {"key": "artist"})
    assert db.execute("SELECT SUM(trackCount) FROM library_artist_summaries").fetchone()[0] == expected

assert_visible(["a", "b"])
rebuild_and_count(2)
db.execute("INSERT INTO library_hidden_tracks VALUES ('a', 'a', 'Artist', 'Album', 10)")
assert_visible(["b"])
rebuild_and_count(1)
for table in ("library_tracks", "library_favorites", "library_playlist_tracks", "library_playback_stats"):
    assert db.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0] == 2, table
assert db.execute("SELECT playCount FROM library_playback_stats WHERE trackId='a'").fetchone()[0] == 3
# Scanner updates and clear/rebuild preserve the independent hidden preference.
db.execute("UPDATE library_tracks SET lastSeenScanRunId=2 WHERE id='a'")
saved = dict(db.execute("SELECT * FROM library_tracks WHERE id='a'").fetchone())
db.execute("DELETE FROM library_tracks WHERE id='a'")
insert("library_tracks", **saved)
assert_visible(["b"])
db.execute("DELETE FROM library_hidden_tracks WHERE trackId='a'")
assert_visible(["a", "b"])
rebuild_and_count(2)
db.execute("INSERT INTO library_hidden_tracks VALUES ('a', 'a', 'Artist', 'Album', 10)")
db.execute("INSERT INTO library_hidden_tracks VALUES ('b', 'b', 'Artist', 'Album', 10)")
assert_visible([])
rebuild_and_count(None)
db.execute("DELETE FROM library_hidden_tracks")
assert_visible(["a", "b"])
rebuild_and_count(2)
print("Hidden tracks SQLite checks passed: v24 migration, browse/search/direct playback, summaries, retained user data, rescan and restore.")
