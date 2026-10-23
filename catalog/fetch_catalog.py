"""Download the full LibriVox catalog politely, one page at a time."""
import json
import sys
import time
import urllib.request
from pathlib import Path

API = "https://librivox.org/api/feed/audiobooks"
PAGE = 500
DELAY_SECONDS = 3

out_dir = Path(sys.argv[1] if len(sys.argv) > 1 else "raw")
out_dir.mkdir(parents=True, exist_ok=True)

offset = 0
while True:
    url = f"{API}?format=json&extended=1&coverart=1&limit={PAGE}&offset={offset}"
    req = urllib.request.Request(url, headers={"User-Agent": "librivox-app-catalog/0.1 (personal open source project)"})
    with urllib.request.urlopen(req, timeout=120) as resp:
        data = json.load(resp)
    books = data.get("books") or []
    if not books:
        break
    (out_dir / f"page_{offset:06d}.json").write_text(json.dumps(books), encoding="utf-8")
    print(f"offset {offset}: {len(books)} books", flush=True)
    if len(books) < PAGE:
        break
    offset += PAGE
    time.sleep(DELAY_SECONDS)
