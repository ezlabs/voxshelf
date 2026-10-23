"""Compare recording order by all-time downloads against a blended score, for books with several recordings."""
import math
import sqlite3
import sys

NOW = 2026.8  # late October 2026
db = sqlite3.connect(sys.argv[1] if len(sys.argv) > 1 else "catalog.db")


def score(downloads, month, recorded, up=0, total=0):
    age = max(NOW - (recorded or 2010) - 0.5, 0.5)  # assume mid-year release
    per_year = downloads / age
    recent = month * 12
    base = 0.7 * math.log1p(per_year) + 0.3 * math.log1p(recent)
    liked = (up + 5) / (total + 10)  # starts at 0.5 (neutral); 5 imaginary votes each way
    return base * (0.5 + liked)


works = db.execute("""SELECT work_id, title, COUNT(*) n FROM book WHERE language='English'
                      GROUP BY work_id HAVING n >= 3 ORDER BY MAX(work_downloads) DESC LIMIT 15""").fetchall()
moved = 0
for work_id, title, n in works:
    recs = db.execute("SELECT readers, recorded, downloads, month, solo FROM book WHERE work_id=?", (work_id,)).fetchall()
    old = sorted(recs, key=lambda r: -r[2])
    new = sorted(recs, key=lambda r: -score(r[2], r[3], r[1]))
    moved += old[0] != new[0]
    print(f"\n{title} ({n} recordings){'  <- top pick changes' if old[0] != new[0] else ''}")
    for i, (o, w) in enumerate(zip(old, new), 1):
        fmt = lambda r: f"{(r[0] or '?')[:22]:22} {r[1]}  {r[2]:>7,} all  {r[3]:>5,}/mo"
        print(f"  {i}. {fmt(o)}   |  {fmt(w)}")
print(f"\nTop pick changes in {moved} of {len(works)} books")
