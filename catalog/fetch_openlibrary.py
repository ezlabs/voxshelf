"""Look up Open Library star ratings for catalog books, most recommended first.

Results are kept in a cache file so each run only looks up books it hasn't seen, plus a slice of
old entries to refresh. Arguments: catalog.db cache.json [max lookups this run].
"""
import json
import re
import sqlite3
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

API = "https://openlibrary.org/search.json"
REFRESH_DAYS = 30
db = sqlite3.connect(sys.argv[1] if len(sys.argv) > 1 else "catalog.db")
cache_file = Path(sys.argv[2] if len(sys.argv) > 2 else "openlibrary.json")
budget = int(sys.argv[3]) if len(sys.argv) > 3 else 3000
cache = json.loads(cache_file.read_text(encoding="utf-8")) if cache_file.exists() else {}


def norm(text):
    return re.sub(r"[^a-z0-9]+", " ", (text or "").lower()).strip()


def bare(title):
    """The title without a leading article or subtitle: "The Count of Monte-Cristo [3/5]" -> "count of monte cristo"."""
    short = re.split(r"[:;(\[]|,? or,? ", title or "", maxsplit=1, flags=re.IGNORECASE)[0]
    return re.sub(r"^(the|a|an) ", "", norm(short))


def key(title, authors):
    return f"{norm(title)}|{norm((authors or '').split(',')[0])}"


def search(params):
    params = {**params, "limit": 5, "fields": "key,title,ratings_average,ratings_count"}
    req = urllib.request.Request(f"{API}?{urllib.parse.urlencode(params)}", headers={"User-Agent": "Voxshelf/0.1 (https://voxshelf.app; hello@voxshelf.app)"})
    return json.load(urllib.request.urlopen(req, timeout=30)).get("docs", [])


def lookup(title, author):
    # Open Library lists many editions; try the full title and the short one, then take the
    # best-rated result whose title really is this book.
    docs = search({"title": title, "author": author})
    if bare(title) != norm(title):
        time.sleep(0.5)
        docs += search({"q": f"{bare(title)} {author.split()[-1] if author else ''}".strip()})
    same = [d for d in docs if bare(d.get("title")) == bare(title)]
    best = max(same or [{}], key=lambda d: d.get("ratings_count") or 0)
    return [best.get("key"), best.get("ratings_count") or 0, round(best.get("ratings_average") or 0, 2)]


works = db.execute(
    "SELECT title, authors FROM book GROUP BY work_id ORDER BY MAX(work_score) DESC"
).fetchall()
now = time.time()
todo = [w for w in works if key(*w) not in cache]
todo += sorted((w for w in works if key(*w) in cache and now - cache[key(*w)][3] > REFRESH_DAYS * 86400),
               key=lambda w: cache[key(*w)][3])
done = failed = 0
for title, authors in todo[:budget]:
    try:
        cache[key(title, authors)] = lookup(title, (authors or "").split(",")[0].strip()) + [now]
        done += 1
    except Exception as e:  # keep going; the book is retried next run
        failed += 1
        print(f"  failed: {title}: {e}", flush=True)
    if done % 250 == 0:
        cache_file.write_text(json.dumps(cache), encoding="utf-8")
        print(f"{done} looked up", flush=True)
    time.sleep(0.5)
cache_file.write_text(json.dumps(cache), encoding="utf-8")
print(f"Looked up {done} ({failed} failed); {len(cache)} books cached, {len(todo) - done} left for later runs")
