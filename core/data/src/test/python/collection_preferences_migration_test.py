"""Verify the collection-preferences migration and album-favorites queries against SQLite."""
import json
from pathlib import Path
import re
import sqlite3

module = Path(__file__).parents[3]
source = module / "src/main/java/app/echo/android/data"
schemas = module / "schemas/app.echo.android.data.EchoLibraryDatabase"
old, new = [json.loads((schemas / f"{version}.json").read_text())["database"]["entities"] for version in (30, 31)]


def create(db, tables, names):
    for table in tables:
        name = table["tableName"]
        if name not in names:
            continue
        db.execute(table["createSql"].replace("${TABLE_NAME}", name))
        for index in table.get("indices", []):
            db.execute(index["createSql"].replace("${TABLE_NAME}", name))


db = sqlite3.connect(":memory:")
create(db, old, {"library_album_summaries", "library_favorites"})
db.execute("INSERT INTO library_favorites VALUES ('existing-song', 10)")
migration = (source / "LibraryCollectionPreferencesMigration.kt").read_text()
for sql in re.findall(r'db\.execSQL\("([^"]+)"\)', migration):
    db.execute(sql)
assert db.execute("SELECT trackId FROM library_favorites").fetchall() == [("existing-song",)]

fresh = sqlite3.connect(":memory:")
new_tables = {"library_liked_albums", "library_hidden_builtin_playlists"}
create(fresh, new, new_tables)
for name in new_tables:
    for pragma in ("table_info", "index_list"):
        assert sorted(db.execute(f"PRAGMA {pragma}({name})")) == sorted(fresh.execute(f"PRAGMA {pragma}({name})"))

for key, remote, title in (("a", 0, "First Album"), ("b", 0, "Second Album"), ("c", 1, "Remote Album")):
    db.execute("""INSERT INTO library_album_summaries
        (albumKey, isRemote, title, trackCount, durationMs, addedAtSeconds) VALUES (?, ?, ?, 1, 10, 0)""",
        (key, remote, title))
dao = (source / "LibraryLikedAlbumDao.kt").read_text()
summaries = re.search(r'LikedAlbumSummaries = """(.*?)"""', dao, re.S)[1]
order = re.search(r'LikedAlbumOrder = "([^"]+)"', dao)[1]
filter_sql = re.search(r'@Query\(LikedAlbumSummaries \+ """(.*?)""" \+ LikedAlbumOrder\)', dao, re.S)[1]
page = summaries + filter_sql + order
liked = re.search(r'@Query\("""(.*?)"""\)', dao, re.S)[1]
assert db.execute(liked, {"albumKey": "a"}).fetchone() == (0,)
assert db.execute(liked, {"albumKey": "c"}).fetchone() is None
db.executemany("INSERT INTO library_liked_albums VALUES (?, ?)", (("a", 11), ("b", 12), ("c", 13)))
assert [row[0] for row in db.execute(page, {"query": None})] == ["b", "a"]
assert [row[0] for row in db.execute(page, {"query": "first"})] == ["a"]
assert [row[0] for row in db.execute(summaries + order + " LIMIT 1")] == ["b"]
assert db.execute(liked, {"albumKey": "a"}).fetchone() == (1,)
db.execute("DELETE FROM library_liked_albums WHERE albumKey = 'a'")
assert db.execute(liked, {"albumKey": "a"}).fetchone() == (0,)
assert db.execute("SELECT COUNT(*) FROM library_favorites").fetchone() == (1,)
print("Collection preferences migration 30 -> 31 and album favorites passed.")
