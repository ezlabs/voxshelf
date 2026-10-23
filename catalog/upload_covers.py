"""Upload the small covers to the Voxshelf server, skipping ones already uploaded.

The list of uploaded recording ids is kept in a text file so later runs (and the nightly job) only send new covers.
Arguments: covers_dir uploaded.txt. The upload key comes from the COVER_UPLOAD_KEY environment variable.
"""
import os
import sys
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

SERVER = "https://voxshelf-ratings.beneichholz9.workers.dev/covers"
covers = Path(sys.argv[1] if len(sys.argv) > 1 else "covers")
done_file = Path(sys.argv[2] if len(sys.argv) > 2 else "uploaded.txt")
key = os.environ["COVER_UPLOAD_KEY"]
done = set(done_file.read_text().split()) if done_file.exists() else set()


def upload(path):
    req = urllib.request.Request(
        f"{SERVER}/{path.name}", data=path.read_bytes(), method="PUT",
        headers={"x-upload-key": key, "content-type": "image/webp", "User-Agent": "Voxshelf/0.1"},
    )
    try:
        urllib.request.urlopen(req, timeout=60).read()
        return path.stem
    except Exception as e:
        print(f"  failed: {path.name}: {e}", flush=True)
        return None


todo = [p for p in covers.glob("*.webp") if p.stem not in done]
print(f"{len(todo)} covers to upload ({len(done)} already uploaded)", flush=True)
with ThreadPoolExecutor(16) as pool:
    for i, rid in enumerate(pool.map(upload, todo), 1):
        if rid:
            done.add(rid)
        if i % 1000 == 0:
            done_file.write_text("\n".join(sorted(done, key=int)))
            print(f"{i}/{len(todo)} uploaded", flush=True)
done_file.write_text("\n".join(sorted(done, key=int)))
print(f"Done: {len(done)} covers on the server")
