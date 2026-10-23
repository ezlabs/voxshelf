"""Build the compact on-phone catalog: books only, trimmed descriptions, recordings grouped into works."""
import gzip
import json
import re
import sqlite3
import sys
from collections import defaultdict
from pathlib import Path

from categories import candidates, flags, merge, resolve

DESC_CHARS = 300
VARIANT = re.compile(r"\s*\(([^)]*\b(?:version \d+|dramatic reading|abridged)\b[^)]*)\)\s*", re.I)

raw_dir = Path(sys.argv[1] if len(sys.argv) > 1 else "raw")
out = Path(sys.argv[2] if len(sys.argv) > 2 else "catalog.db")
version = sys.argv[3] if len(sys.argv) > 3 else "dev"
popularity_file = Path(sys.argv[4] if len(sys.argv) > 4 else "popularity.json")
popularity = json.loads(popularity_file.read_text(encoding="utf-8")) if popularity_file.exists() else {}
books = [b for f in sorted(raw_dir.glob("page_*.json")) for b in json.loads(f.read_text(encoding="utf-8"))]


def clean_text(html):
    text = re.sub(r"<[^>]+>", " ", html or "")
    return re.sub(r"\s+", " ", text).strip()


def trim(text):
    if len(text) <= DESC_CHARS:
        return text
    return text[:DESC_CHARS].rsplit(" ", 1)[0] + "…"


rows = []
works = defaultdict(list)
for b in books:
    title = b.get("title") or ""
    sections = b.get("sections") or []
    if "do not re-use" in title.lower() or not sections:
        continue
    variant = VARIANT.search(title)
    base = VARIANT.sub(" ", title).strip()
    label = (variant.group(1) if variant else "").lower()
    author_ids = ",".join(sorted(a.get("id") or "" for a in (b.get("authors") or [])))
    key = (base.lower(), author_ids, b.get("language"))
    reader_names = {}
    for s in sections:
        for r in s.get("readers") or []:
            reader_names.setdefault(r.get("reader_id"), (r.get("display_name") or "").strip())
    readers = set(reader_names)
    row = {
        "id": int(b["id"]),
        "title": base,
        "authors": ", ".join(f"{a.get('first_name') or ''} {a.get('last_name') or ''}".strip()
                             for a in (b.get("authors") or [])),
        "language": b.get("language"),
        "genres": "|".join(g.get("name") or "" for g in (b.get("genres") or [])),
        "description": trim(clean_text(b.get("description"))),
        "secs": int(b.get("totaltimesecs") or 0) or sum(int(x.get("playtime") or 0) for x in sections),
        "chapters": int(b.get("num_sections") or 0),
        "readers": ", ".join(n for n in reader_names.values() if n) if len(readers) <= 3 else f"{len(readers)} readers",
        "solo": 1 if len(readers) == 1 else 0,
        "dramatic": 1 if "dramatic" in label else 0,
        "abridged": 1 if "abridged" in label else 0,
        "cover": b.get("coverart_thumbnail"),
    }
    # Internet Archive download counts: all-time and last month.
    archive_id = (b.get("url_iarchive") or "").rstrip("/").rsplit("/", 1)[-1]
    row["downloads"], row["month"] = (popularity.get(archive_id) or [0, 0, 0])[:2]
    genres = [g.get("name") or "" for g in (b.get("genres") or [])]
    row.update(flags(genres))
    row["_cands"] = candidates(genres)
    rows.append(row)
    works[key].append(row)

for work_id, members in enumerate(works.values(), 1):
    # Recordings of the same book share a shelf and subjects, so every version shows up in the same places.
    shelf, subjects = resolve(merge(r.pop("_cands") for r in members))
    work_downloads = sum(r["downloads"] for r in members)
    work_month = sum(r["month"] for r in members)
    for r in members:
        r["work_id"] = work_id
        r["work_downloads"] = work_downloads
        r["work_month"] = work_month
        r["shelf"] = shelf
        r["subjects"] = "|".join(subjects)

out.unlink(missing_ok=True)
con = sqlite3.connect(out)
cols = list(rows[0].keys())
con.execute(f"CREATE TABLE book ({', '.join(c + (' INTEGER PRIMARY KEY' if c == 'id' else '') for c in cols)})")
con.executemany(f"INSERT INTO book VALUES ({','.join('?' * len(cols))})", [tuple(r[c] for c in cols) for r in rows])
con.execute("CREATE INDEX book_work ON book(work_id)")
con.execute("CREATE INDEX book_shelf ON book(shelf)")
con.execute("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT)")
con.execute("INSERT INTO meta VALUES ('version', ?)", (version,))
con.execute("CREATE VIRTUAL TABLE book_fts USING fts4(content='book', title, authors)")
con.execute("INSERT INTO book_fts(book_fts) VALUES ('rebuild')")
con.commit()
con.execute("VACUUM")
con.close()

multi = [m for m in works.values() if len(m) > 1]
size = out.stat().st_size
print(f"Books: {len(rows)}; works: {len(works)}; works with 2+ recordings: {len(multi)} "
      f"({sum(len(m) for m in multi)} recordings)")
print(f"Phone catalog: {size / 1e6:.1f} MB, {len(gzip.compress(out.read_bytes(), 9)) / 1e6:.1f} MB compressed")
for m in sorted(multi, key=len, reverse=True)[:8]:
    print(f"  {len(m)}x {m[0]['title']} / {m[0]['authors']} ({m[0]['language']})")
