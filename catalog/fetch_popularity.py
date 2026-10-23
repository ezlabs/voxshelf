"""Download Internet Archive download counts for every LibriVox recording (all-time, last month, last week)."""
import json
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

API = "https://archive.org/services/search/v1/scrape"
out = Path(sys.argv[1] if len(sys.argv) > 1 else "popularity.json")

counts = {}
cursor = None
while True:
    params = {"fields": "identifier,downloads,month,week", "q": "collection:librivoxaudio", "count": 10000}
    if cursor:
        params["cursor"] = cursor
    req = urllib.request.Request(f"{API}?{urllib.parse.urlencode(params)}",
                                 headers={"User-Agent": "voxshelf-catalog/0.1 (open source project)"})
    with urllib.request.urlopen(req, timeout=120) as resp:
        data = json.load(resp)
    for item in data.get("items", []):
        counts[item["identifier"]] = [item.get("downloads", 0), item.get("month", 0), item.get("week", 0)]
    cursor = data.get("cursor")
    print(f"{len(counts)} of {data.get('total')}", flush=True)
    if not cursor:
        break
    time.sleep(3)

out.write_text(json.dumps(counts), encoding="utf-8")
