"""Download each recording's LibriVox cover once and save a small copy (400px WebP) for fast loading in the app.

Covers already saved, or listed in the optional uploaded.txt, are skipped, so the nightly job only fetches new recordings.
Arguments: catalog.db out_dir [uploaded.txt]
"""
import io
import sqlite3
import sys
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from PIL import Image

SIZE = 400
db = sqlite3.connect(sys.argv[1] if len(sys.argv) > 1 else "catalog.db")
out = Path(sys.argv[2] if len(sys.argv) > 2 else "covers")
out.mkdir(exist_ok=True)
uploaded = set(Path(sys.argv[3]).read_text().split()) if len(sys.argv) > 3 and Path(sys.argv[3]).exists() else set()


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Voxshelf/0.1 (open source LibriVox app)"})
    return urllib.request.urlopen(req, timeout=60).read()


def save(row):
    rid, thumb = row
    path = out / f"{rid}.webp"
    if path.exists() or str(rid) in uploaded:
        return "skipped"
    # The full-size art looks sharp on big screens; some recordings only have the small one.
    for url in (thumb.replace("_thumb", ""), thumb):
        try:
            img = Image.open(io.BytesIO(fetch(url))).convert("RGB")
            img.thumbnail((SIZE, SIZE), Image.LANCZOS)
            img.save(path, "WEBP", quality=72, method=6)
            return "saved"
        except Exception:
            continue
    return "failed"


rows = db.execute("SELECT id, cover FROM book WHERE cover != '' ORDER BY work_score DESC").fetchall()
counts = {"saved": 0, "skipped": 0, "failed": 0}
with ThreadPoolExecutor(8) as pool:
    for i, result in enumerate(pool.map(save, rows), 1):
        counts[result] += 1
        if i % 500 == 0:
            print(f"{i}/{len(rows)} {counts}", flush=True)
print(f"Done: {counts}")
