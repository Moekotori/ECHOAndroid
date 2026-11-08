"""Exercise the real 29 -> 30 SQL, including private-playlist isolation and rating ownership."""
from pathlib import Path
import json
import re
import sqlite3

module = Path(__file__).parents[3]
schemas = module / "schemas/app.echo.android.data.EchoLibraryDatabase"
old = json.loads((schemas / "29.json").read_text())["database"]
new = json.loads((schemas / "30.json").read_text())["database"]
names = {"library_tracks", "library_smart_rules", "file_playlist_entries"}


def create(db, schema):
    for table in schema["entities"]:
        if table["tableName"] in names:
            db.execute(table["createSql"].replace("${TABLE_NAME}", table["tableName"]))
            for index in table.get("indices", []):
                db.execute(index["createSql"].replace("${TABLE_NAME}", table["tableName"]))


db = sqlite3.connect(":memory:")
create(db, old)
entity = next(t for t in old["entities"] if t["tableName"] == "library_tracks")
row = {f["columnName"]: (0 if f["affinity"] == "INTEGER" else "")
       for f in entity["fields"] if f.get("notNull", False)}
row.update(id="song", contentUri="content://audio/1", source="mediastore", title="Keep song")
db.execute(f"INSERT INTO library_tracks ({','.join(row)}) VALUES ({','.join('?' for _ in row)})", list(row.values()))
source = (module / "src/main/java/app/echo/android/data/LibraryUserMetadataMigration.kt").read_text()
for literal in re.findall(r'db.execSQL\(("(?:[^"\\]|\\.)*")\)', source):
    db.execute(json.loads(literal))
assert db.execute("SELECT title, rating, embeddedRating, localRating FROM library_tracks").fetchone() == ("Keep song", 0, None, None)
db.execute("INSERT INTO file_playlist_entries VALUES ('private', 'files:test', 0, 'uri-key', 1000, '{}')")
assert db.execute("SELECT COUNT(*) FROM library_tracks").fetchone()[0] == 1
assert db.execute("SELECT COUNT(*) FROM file_playlist_entries").fetchone()[0] == 1

# Execute the DAO update directly: an explicit local zero also overrides an embedded five.
dao = (module / "src/main/java/app/echo/android/data/LibraryTrackDao.kt").read_text()
rating_sql = re.search(r'@Query\("(UPDATE library_tracks SET localRating = :rating[^"\n]+)"\)', dao)[1]
db.execute("UPDATE library_tracks SET embeddedRating = 5")
db.execute(rating_sql, {"id": "song", "rating": 0})
assert db.execute("SELECT rating, localRating FROM library_tracks").fetchone() == (0, 0)
db.execute(rating_sql, {"id": "song", "rating": None})
assert db.execute("SELECT rating, localRating FROM library_tracks").fetchone() == (5, None)

fresh = sqlite3.connect(":memory:")
create(fresh, new)
for name in names:
    columns = lambda connection: sorted(r[1:] for r in connection.execute(f"PRAGMA table_info({name})"))
    assert columns(db) == columns(fresh), name
    indices = lambda connection: sorted((r[1], r[2]) for r in connection.execute(f"PRAGMA index_list({name})"))
    assert indices(db) == indices(fresh), name
print("User metadata migration 29 -> 30 passed: Room schema, retained tracks, isolated file entries, rating ownership.")
