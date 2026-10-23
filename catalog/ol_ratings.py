"""Update star ratings in the Open Library cache from Open Library's monthly ratings file.

fetch_openlibrary.py finds each book's Open Library work key once, through their search. The rating numbers
then come from this small monthly download (about 9 MB), as Open Library asks bulk users to do, instead of
searching for every book again.
Arguments: cache.json
"""
import collections
import gzip
import json
import sys
import tempfile
import urllib.request
from pathlib import Path

URL = "https://openlibrary.org/data/ol_dump_ratings_latest.txt.gz"
cache_file = Path(sys.argv[1] if len(sys.argv) > 1 else "openlibrary.json")
cache = json.loads(cache_file.read_text(encoding="utf-8"))

with tempfile.TemporaryDirectory() as tmp:
    dump = Path(tmp) / "ratings.txt.gz"
    req = urllib.request.Request(URL, headers={"User-Agent": "Voxshelf/0.1 (https://voxshelf.app; hello@voxshelf.app)"})
    with urllib.request.urlopen(req, timeout=300) as response, dump.open("wb") as out:
        out.write(response.read())
    # Each line: work key, edition key (or \N), rating 1-5, date.
    totals = collections.defaultdict(lambda: [0, 0])
    with gzip.open(dump, "rt", encoding="utf-8") as lines:
        for line in lines:
            parts = line.rstrip("\n").split("\t")
            if len(parts) >= 3 and parts[2].isdigit():
                totals[parts[0]][0] += 1
                totals[parts[0]][1] += int(parts[2])

changed = 0
for entry in cache.values():
    key = entry[0]
    if not key:
        continue
    count, total = totals.get(key, (0, 0))
    new = [count, round(total / count, 2) if count else 0]
    if entry[1:3] != new:
        entry[1:3] = new
        changed += 1
cache_file.write_text(json.dumps(cache), encoding="utf-8")
print(f"{len(totals):,} rated works in the monthly file; updated {changed} of {len(cache):,} cached books")
