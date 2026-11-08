"""Short SQLite regression/benchmark using SQL emitted by LibraryTrackFilterQueryTest.

Set ECHO_SORT_SQL_DUMP to core/data/build/sort-query-fixtures when running that
Gradle test, then run this file. No Android device or extra Python packages needed.
These desktop synthetic timings are not Android device performance guarantees.
"""
from pathlib import Path
import argparse
import json
import sqlite3
import statistics
import time

parser = argparse.ArgumentParser()
parser.add_argument("--rows", type=int, default=20_000)
options = parser.parse_args()
module = Path(__file__).parents[3]
fixtures = module / "build/sort-query-fixtures"
schema = json.loads((module / "schemas/app.echo.android.data.EchoLibraryDatabase/28.json").read_text(encoding="utf-8"))["database"]
db = sqlite3.connect(":memory:")
entities = {entity["tableName"]: entity for entity in schema["entities"]}
for name, entity in entities.items():
    db.execute(entity["createSql"].replace("${TABLE_NAME}", name))
    for index in entity.get("indices", []):
        db.execute(index["createSql"].replace("${TABLE_NAME}", name))

fields = entities["library_tracks"]["fields"]
columns = [field["columnName"] for field in fields]
defaults = {field["columnName"]: ("" if field["affinity"] == "TEXT" else 0) if field.get("notNull") else None for field in fields}

def row(index):
    value = defaults.copy()
    value.update(id=f"track:{index:07d}", contentUri=f"content://music/{index}",
                 source="saf" if index % 2 else "mediastore",
                 title=f"Song {index * 7919 % options.rows:07d}", artist=f"Artist {index % 271:03d}",
                 album=None if index % 11 == 0 else f"Album {index % 397:03d}",
                 albumArtist=" " if index % 13 == 0 else f"Artist {index % 193:03d}",
                 genre=None if index % 7 == 0 else f"Genre {index % 17:02d}",
                 composer=None if index % 5 == 0 else f"Composer {index % 67:02d}",
                 durationMs=60_000 + index * 97 % 300_000, discNumber=1 + index % 3,
                 trackNumber=None if index % 29 == 0 else 1 + index % 19,
                 year=None if index % 17 == 0 else 1980 + index % 47,
                 sampleRateHz=None if index % 23 == 0 else [44_100, 48_000, 96_000][index % 3],
                 sizeBytes=index * 1237, dateModifiedSeconds=index % 271,
                 mimeType="audio/flac", fileName=f"song-{index % 713:04d}.flac",
                 relativePath=f"Music/{index % 307:03d}/",
                 artworkUri="content://covers/" + "cover" * 80,
                 musicRelationsJson='{"note":"' + "metadata " * 80 + '"}')
    return [value[column] for column in columns]

db.executemany(f"INSERT INTO library_tracks ({','.join(columns)}) VALUES ({','.join('?' for _ in columns)})", map(row, range(options.rows)))
db.executemany("INSERT INTO library_playback_stats VALUES (?,?,?)",
               ((f"track:{i:07d}", i % 31, i * 1234) for i in range(0, options.rows, 3)))
db.commit()
id_column = columns.index("id")
files = sorted(fixtures.glob("*.full.sql"))
assert len(files) == 34, "Generate all 34 production SQL fixtures with the Gradle test first"
results = []

def measure(action):
    started = time.perf_counter()
    result = action()
    return result, (time.perf_counter() - started) * 1000

for file in files:
    mode = file.name.removesuffix(".full.sql")
    full = file.read_text(encoding="utf-8")
    ids = (fixtures / f"{mode}.ids.sql").read_text(encoding="utf-8")
    count_sql = (fixtures / f"{mode}.count.sql").read_text(encoding="utf-8")
    plan = [entry[3] for entry in db.execute("EXPLAIN QUERY PLAN " + count_sql)]
    assert not any("ORDER BY" in entry for entry in plan), (mode, plan)
    count, count_ms = measure(lambda: db.execute(count_sql).fetchone()[0])
    old_count, old_count_ms = measure(lambda: db.execute("SELECT COUNT(*) FROM (" + full + ")").fetchone()[0])
    assert count == old_count == options.rows
    expected, old_page_ms = measure(lambda: db.execute(full + " LIMIT 80 OFFSET 240").fetchall())

    def narrow_page():
        ordered = [entry[0] for entry in db.execute(ids + " LIMIT 80 OFFSET 240")]
        rows = db.execute(f"SELECT * FROM library_tracks WHERE id IN ({','.join('?' for _ in ordered)})", ordered).fetchall()
        by_id = {entry[id_column]: entry for entry in rows}
        return [by_id[identifier] for identifier in ordered]

    actual, new_page_ms = measure(narrow_page)
    assert actual == expected, mode
    results.append(dict(mode=mode, old_initial_ms=round(old_count_ms + old_page_ms, 3),
                        new_initial_ms=round(count_ms + new_page_ms, 3),
                        old_count_ms=round(old_count_ms, 3), new_count_ms=round(count_ms, 3),
                        old_page_ms=round(old_page_ms, 3), new_page_ms=round(new_page_ms, 3)))

report = dict(rows=options.rows, checked_sorts=len(results), sqlite_version=sqlite3.sqlite_version,
              median_old_initial_ms=round(statistics.median(r["old_initial_ms"] for r in results), 3),
              median_new_initial_ms=round(statistics.median(r["new_initial_ms"] for r in results), 3),
              results=results)
output = module / "build/sort-query-benchmark.json"
output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps({key: value for key, value in report.items() if key != "results"}))
print("All sorted windows match; count plans have no sorting. Details:", output)
