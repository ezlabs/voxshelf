"""Download the full LibriVox catalog politely, one page at a time."""
import http.client
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

RETRIES = 5


def fetch_page(offset):
    url = f"{API}?format=json&extended=1&coverart=1&limit={PAGE}&offset={offset}"
    req = urllib.request.Request(url, headers={"User-Agent": "voxshelf-catalog/0.1 (open source project)"})
    for attempt in range(1, RETRIES + 1):
        try:
            with urllib.request.urlopen(req, timeout=120) as resp:
                return json.load(resp)
        except (OSError, http.client.HTTPException, json.JSONDecodeError) as exc:
            if attempt == RETRIES:
                raise
            wait = DELAY_SECONDS * 10 * attempt
            print(f"offset {offset}: attempt {attempt} failed ({exc!r}), retrying in {wait}s", flush=True)
            time.sleep(wait)


offset = 0
while True:
    books = fetch_page(offset).get("books") or []
    if not books:
        break
    (out_dir / f"page_{offset:06d}.json").write_text(json.dumps(books), encoding="utf-8")
    print(f"offset {offset}: {len(books)} books", flush=True)
    if len(books) < PAGE:
        break
    offset += PAGE
    time.sleep(DELAY_SECONDS)
