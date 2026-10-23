"""Measure catalog size and data quality, and build a trial on-phone database."""
import gzip
import json
import re
import sqlite3
import sys
from collections import Counter, defaultdict
from pathlib import Path

raw_dir = Path(sys.argv[1] if len(sys.argv) > 1 else "raw")
books = []
for f in sorted(raw_dir.glob("page_*.json")):
    books.extend(json.loads(f.read_text(encoding="utf-8")))

n = len(books)
pct = lambda k: f"{k} ({k / n:.0%})"
print(f"Books: {n}")

languages = Counter(b.get("language") or "?" for b in books)
print(f"Languages: {len(languages)}; top: {languages.most_common(8)}")

genre_counts = Counter()
no_genre = 0
for b in books:
    gs = [g.get("name") for g in (b.get("genres") or [])]
    if not gs:
        no_genre += 1
    genre_counts.update(gs)
print(f"Distinct genres: {len(genre_counts)}; books without genre: {pct(no_genre)}")
print(f"Top genres: {genre_counts.most_common(10)}")

no_desc = sum(1 for b in books if len((b.get("description") or "").strip()) < 20)
no_cover = sum(1 for b in books if not b.get("coverart_jpg"))
no_time = sum(1 for b in books if not int(b.get("totaltimesecs") or 0))
print(f"Missing/short description: {pct(no_desc)}; no cover: {pct(no_cover)}; no duration: {pct(no_time)}")

solo = multi = unknown = 0
for b in books:
    readers = {r.get("reader_id") for s in (b.get("sections") or []) for r in (s.get("readers") or [])}
    if not readers:
        unknown += 1
    elif len(readers) == 1:
        solo += 1
    else:
        multi += 1
print(f"Solo reader: {pct(solo)}; multiple readers: {pct(multi)}; unknown: {pct(unknown)}")

def norm_title(t):
    t = re.sub(r"\(.*?\)|version \d+|vol(ume)?\.? ?\d+", "", (t or "").lower())
    return re.sub(r"[^a-z0-9]+", " ", t).strip()

groups = defaultdict(list)
for b in books:
    authors = ",".join(sorted(a.get("id", "") for a in (b.get("authors") or [])))
    groups[(norm_title(b.get("title")), authors, b.get("language"))].append(b)
multi_version = {k: v for k, v in groups.items() if len(v) > 1}
print(f"Works with 2+ recordings (same title+author+language): {len(multi_version)}, "
      f"covering {sum(len(v) for v in multi_version.values())} books")
top = sorted(multi_version.values(), key=len, reverse=True)[:5]
print("Most recorded:", [(v[0]["title"], len(v)) for v in top])

hours = sum(int(b.get("totaltimesecs") or 0) for b in books) / 3600
chapters = sum(len(b.get("sections") or []) for b in books)
print(f"Total audio: {hours:,.0f} hours across {chapters:,} chapters")

db_path = raw_dir.parent / "catalog_trial.db"
db_path.unlink(missing_ok=True)
con = sqlite3.connect(db_path)
con.execute("CREATE TABLE book (id INTEGER PRIMARY KEY, title TEXT, authors TEXT, language TEXT, "
            "genres TEXT, description TEXT, secs INTEGER, cover TEXT, iarchive TEXT)")
con.execute("CREATE TABLE chapter (book_id INTEGER, num INTEGER, title TEXT, secs INTEGER, url TEXT, readers TEXT)")
con.execute("CREATE VIRTUAL TABLE book_fts USING fts5(title, authors, description, content='book', content_rowid='id')")
for b in books:
    authors = ", ".join(f"{a.get('first_name', '')} {a.get('last_name', '')}".strip() for a in (b.get("authors") or []))
    con.execute("INSERT INTO book VALUES (?,?,?,?,?,?,?,?,?)", (
        int(b["id"]), b.get("title"), authors, b.get("language"),
        "|".join((g.get("name") or "") for g in (b.get("genres") or [])),
        re.sub(r"<[^>]+>", "", b.get("description") or ""), int(b.get("totaltimesecs") or 0),
        b.get("coverart_thumbnail"), b.get("url_iarchive")))
    for s in b.get("sections") or []:
        con.execute("INSERT INTO chapter VALUES (?,?,?,?,?,?)", (
            int(b["id"]), int(s.get("section_number") or 0), s.get("title"), int(s.get("playtime") or 0),
            s.get("listen_url"), "|".join((r.get("display_name") or "") for r in (s.get("readers") or []))))
con.execute("INSERT INTO book_fts(book_fts) VALUES ('rebuild')")
con.commit()
con.execute("VACUUM")
con.close()
size = db_path.stat().st_size
gz = len(gzip.compress(db_path.read_bytes(), 9))
print(f"Trial phone database: {size / 1e6:.1f} MB raw, {gz / 1e6:.1f} MB compressed download")
