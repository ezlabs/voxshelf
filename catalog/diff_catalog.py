"""Compare yesterday's catalog with today's and write a small changes file plus an updated manifest.

Usage: diff_catalog.py OLD_DB NEW_DB OUT_DIR VERSION [OLD_MANIFEST]

The app reads manifest.json first. If its version is listed as a "from" in the chain of changes,
it downloads only those small files; otherwise it downloads the full catalog again.
"""
import gzip
import json
import sqlite3
import sys
from pathlib import Path

KEEP_CHANGES = 90

old_db, new_db, out_dir, version = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3]), sys.argv[4]
old_manifest = Path(sys.argv[5]) if len(sys.argv) > 5 else None
out_dir.mkdir(parents=True, exist_ok=True)


def load(path):
    con = sqlite3.connect(path)
    con.row_factory = sqlite3.Row
    rows = {r["id"]: dict(r) for r in con.execute("SELECT * FROM book")}
    con.close()
    return rows


manifest = {"version": version, "full": "catalog.db.gz", "changes": []}
if old_manifest and old_manifest.exists():
    manifest["changes"] = json.loads(old_manifest.read_text(encoding="utf-8")).get("changes", [])
    previous = json.loads(old_manifest.read_text(encoding="utf-8")).get("version")
else:
    previous = None

if previous and previous != version and old_db.exists():
    old, new = load(old_db), load(new_db)
    upserts = [row for book_id, row in new.items() if old.get(book_id) != row]
    deletes = [book_id for book_id in old if book_id not in new]
    name = f"changes-{previous}-{version}.json.gz"
    payload = json.dumps({"from": previous, "to": version, "upserts": upserts, "deletes": deletes},
                         ensure_ascii=False).encode("utf-8")
    (out_dir / name).write_bytes(gzip.compress(payload, 9))
    manifest["changes"].append({"from": previous, "to": version, "file": name,
                                "size": (out_dir / name).stat().st_size})
    print(f"{previous} -> {version}: {len(upserts)} added or changed, {len(deletes)} removed, "
          f"{(out_dir / name).stat().st_size:,} bytes")
else:
    print("No previous catalog to compare with; publishing full catalog only")

manifest["changes"] = manifest["changes"][-KEEP_CHANGES:]
(out_dir / "manifest.json").write_text(json.dumps(manifest, indent=1), encoding="utf-8")
