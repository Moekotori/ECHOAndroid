"""Run the production migration against SQLite and compare it with Room's new schema."""
from pathlib import Path
import json
import re
import sqlite3

module = Path(__file__).parents[3]
schemas = module / "schemas/app.echo.android.data.EchoLibraryDatabase"
old = json.loads((schemas / "28.json").read_text())["database"]
new = json.loads((schemas / "29.json").read_text())["database"]
names = {"library_tracks", "library_playlists", "library_playlist_tracks"}

def create(db, schema):
    for table in schema["entities"]:
        if table["tableName"] in names:
            db.execute(table["createSql"].replace("${TABLE_NAME}", table["tableName"]))
            for index in table["indices"]:
                db.execute(index["createSql"].replace("${TABLE_NAME}", table["tableName"]))

db = sqlite3.connect(":memory:")
create(db, old)
# Populate all required fields exactly as declared by the old exported schema.
entity = next(t for t in old["entities"] if t["tableName"] == "library_tracks")
row = {f["columnName"]: (0 if f["affinity"] == "INTEGER" else "") for f in entity["fields"] if f.get("notNull", False)}
row.update(id="subsonic:a:song:one", contentUri="https://server/rest/stream.view?id=one", source="subsonic:a",
           title="Keep me", artworkUri="https://server/rest/getCoverArt.view?id=cover")
db.execute(f"INSERT INTO library_tracks ({','.join(row)}) VALUES ({','.join('?' for _ in row)})", list(row.values()))
db.execute("INSERT INTO library_playlists VALUES ('p', 'Keep playlist', 'subsonic:a', NULL, 1, 123)")
db.execute("INSERT INTO library_playlist_tracks VALUES ('p', ?, 0)", (row["id"],))
source = (module / "src/main/java/app/echo/android/data/LibrarySubsonicCompatibilityMigration.kt").read_text()
for literal in re.findall(r'db.execSQL\(("(?:[^"\\]|\\.)*")\)', source):
    db.execute(json.loads(literal))
assert db.execute("SELECT title, remoteAlbumId FROM library_tracks").fetchone() == ("Keep me", None)
assert db.execute("SELECT contentUri FROM library_tracks").fetchone()[0].endswith("&echoSource=subsonic:a")
assert db.execute("SELECT trackId, position FROM library_playlist_tracks").fetchall() == [(row["id"], 0)]
db.execute("INSERT INTO library_playlist_tracks VALUES ('p', ?, 2)", (row["id"],))
assert db.execute("SELECT trackId FROM library_playlist_tracks ORDER BY position").fetchall() == [(row["id"],), (row["id"],)]
fresh = sqlite3.connect(":memory:")
create(fresh, new)
for name in names:
    assert db.execute(f"PRAGMA table_info({name})").fetchall() == fresh.execute(f"PRAGMA table_info({name})").fetchall(), name
    indices = lambda connection: sorted((r[1], r[2]) for r in connection.execute(f"PRAGMA index_list({name})"))
    assert indices(db) == indices(fresh), name
print("Subsonic migration 28 -> 29 passed: existing rows, duplicate occurrences, account resource keys, Room schema.")
