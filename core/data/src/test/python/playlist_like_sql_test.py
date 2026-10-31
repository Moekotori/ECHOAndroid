"""Execute the production browse query in SQLite, including literal LIKE characters."""
from pathlib import Path
import re
import sqlite3

source = Path(__file__).parents[3] / "src/main/java/app/echo/android/data"
text = (source / "LibraryPlaylistDao.kt").read_text(encoding="utf-8")
queries = {
    name: sql
    for sql, name in re.findall(
        r'@Query\(\s*"""(.*?)"""\s*,?\s*\)\s*suspend fun (\w+)', text, re.S
    )
}
query = queries["searchPlaylistsForBrowse"]
db = sqlite3.connect(":memory:")
db.execute("CREATE TABLE library_playlists (id TEXT, name TEXT, source TEXT, trackCount INTEGER, artworkUri TEXT, updatedAtEpochMs INTEGER)")
names = ["Example", "100% hits", "1000 hits", "under_score", "underXscore", r"back\slash"]
db.executemany(
    "INSERT INTO library_playlists VALUES (?, ?, 'mediastore', 0, NULL, 0)",
    [(str(i), name) for i, name in enumerate(names)],
)
for term, expected in [("Ex", "Example"), ("%", "100% hits"), ("_", "under_score"), ("\\", r"back\slash")]:
    escaped = "".join(("\\" if c in "\\%_" else "") + c for c in term)
    rows = db.execute(query, {"source": "mediastore", "query": escaped, "limit": 30}).fetchall()
    assert [row[1] for row in rows] == [expected], (term, rows)
print("Playlist LIKE SQLite checks passed: normal search and literal percent, underscore, backslash.")
