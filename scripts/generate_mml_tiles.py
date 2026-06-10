#!/usr/bin/env python3
"""
generate_mml_tiles.py
Downloads MML taustakartta tiles for coastal regions and packages them.

MML API -kohteliaisuus:
  - Rinnakkaisia latauksia rajoitetusti (5–10)
  - Satunnainen viive pyyntöjen välillä (jitter)
  - Uudelleenyritys eksponentiaalisella viiveellä (max 3)
  - 429 Too Many Requests → odotetaan ja yritetään uudelleen

Vaatii: MML_API_KEY-ympäristömuuttujan, Python 3.6+
"""

import os
import sys
import math
import time
import random
import tarfile
import json
import urllib.request
import urllib.error
import concurrent.futures
from pathlib import Path

api_key = os.environ.get("MML_API_KEY")
if not api_key:
    print("ERROR: MML_API_KEY not set", file=sys.stderr)
    sys.exit(1)

REGIONS = [
    # Rannikkoalueet (7 kpl)
    ("Suomenlahti-lansi", 59.5, 60.5, 22.0, 26.0),
    ("Suomenlahti-ita",   59.5, 60.8, 25.0, 28.5),
    ("Saaristomeri",      59.8, 60.8, 19.0, 23.0),
    ("Selkameri",         60.5, 62.5, 19.0, 22.0),
    ("Pohjanlahti-etela", 62.5, 64.0, 20.0, 23.5),
    ("Pohjanlahti-pohjoinen", 63.5, 65.5, 22.0, 26.0),
    ("Perameri",          65.0, 66.0, 23.0, 26.0),
    # Sisävesialueet (joille Traficomilla on veneilykartat)
    ("Saimaa",            61.0, 62.5, 27.0, 30.0),
    ("Paijanne",          61.2, 62.3, 25.0, 26.5),
    ("Keitele",           62.7, 63.2, 25.5, 26.5),
    ("Nasijarvi",         61.5, 62.0, 23.5, 24.0),
    ("Oulujarvi",         64.2, 64.5, 26.8, 27.8),
]

# Jos komentorivillä annettu alueen nimi, suodatetaan vain se
if len(sys.argv) > 1 and sys.argv[1] != "all":
    requested = sys.argv[1]
    filtered = [r for r in REGIONS if r[0] == requested]
    if not filtered:
        names = ", ".join(r[0] for r in REGIONS)
        print(f"ERROR: tuntematon alue '{requested}'. Valitse: {names}", file=sys.stderr)
        sys.exit(1)
    REGIONS = filtered

MIN_ZOOM = 8
MAX_ZOOM = 14
BASE_URL = ("https://avoin-karttakuva.maanmittauslaitos.fi/avoin/wmts/"
            "1.0.0/taustakartta/default/WGS84_Pseudo-Mercator")
PARALLEL = int(os.environ.get("PARALLEL", "5"))
MAX_RETRIES = 3

staging = Path("staging")
output = Path("output")
output.mkdir(exist_ok=True)

def lat2y(lat, n):
    r = math.radians(lat)
    return int((1.0 - math.log(math.tan(r) + 1.0 / math.cos(r)) / math.pi) / 2.0 * n)

def download_tile(args):
    url, path = args
    path.parent.mkdir(parents=True, exist_ok=True)

    for attempt in range(1, MAX_RETRIES + 1):
        try:
            req = urllib.request.Request(url)
            with urllib.request.urlopen(req, timeout=30) as resp:
                if resp.status == 200:
                    data = resp.read()
                    if len(data) > 100:  # PNG is at least ~200 bytes
                        path.write_bytes(data)
                        return True
                elif resp.status == 429:
                    retry_after = int(resp.headers.get("Retry-After", "30"))
                    print(f"  429 Too Many Requests, waiting {retry_after}s...")
                    time.sleep(retry_after)
                    continue
            return False
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return False  # tile doesn't exist, not an error
            elif e.code == 429:
                print(f"  429, retry {attempt}/{MAX_RETRIES}")
                time.sleep(2 ** attempt * 5 + random.uniform(0, 5))
                continue
            else:
                if attempt < MAX_RETRIES:
                    time.sleep(2 ** attempt * 2)
                    continue
                return False
        except Exception:
            if attempt < MAX_RETRIES:
                time.sleep(2 ** attempt * 2)
                continue
            return False
    return False

def download_region(name, min_lat, max_lat, min_lng, max_lng):
    print(f"--- Region: {name} ---")
    tiles = []

    for z in range(MIN_ZOOM, MAX_ZOOM + 1):
        n = 1 << z
        x_min = max(0, int((min_lng + 180) / 360 * n))
        x_max = min(n - 1, int((max_lng + 180) / 360 * n))
        y_min = max(0, lat2y(max_lat, n))
        y_max = min(n - 1, lat2y(min_lat, n))

        count = (x_max - x_min + 1) * (y_max - y_min + 1)
        print(f"  Zoom {z}: {count} tiles")

        for x in range(x_min, x_max + 1):
            for y in range(y_min, y_max + 1):
                url = f"{BASE_URL}/{z}/{y}/{x}.png?api-key={api_key}"
                path = staging / name / str(z) / str(x) / f"{y}.png"
                tiles.append((url, path))

    total = len(tiles)
    print(f"  Downloading {total} tiles ({PARALLEL} parallel)...")

    # Download in batches with delay between batches to avoid rate limiting
    ok = 0
    batch_size = PARALLEL * 20  # submit 20 batches of PARALLEL requests

    with concurrent.futures.ThreadPoolExecutor(max_workers=PARALLEL) as pool:
        for batch_start in range(0, len(tiles), batch_size):
            batch = tiles[batch_start:batch_start + batch_size]
            futures = [pool.submit(download_tile, t) for t in batch]
            for i, future in enumerate(concurrent.futures.as_completed(futures), 1):
                if future.result():
                    ok += 1
            # Small delay between batches to be nice to MML
            if batch_start + batch_size < len(tiles):
                time.sleep(random.uniform(0.5, 1.5))
            progress = min(batch_start + batch_size, total)
            print(f"    Progress: {progress}/{total} ({ok} OK)")

    actual = sum(1 for _ in (staging / name).rglob("*.png"))
    print(f"  Downloaded: {actual} / {total} tiles")
    print()

for name, *bbox in REGIONS:
    download_region(name, *bbox)

print("=== Packaging ===")
for name, *_ in REGIONS:
    src = staging / name
    dst = output / f"{name}.tar.gz"
    if src.exists() and any(src.rglob("*.png")):
        print(f"  {name} → {dst}")
        with tarfile.open(dst, "w:gz") as tar:
            tar.add(src, arcname=name)
        size = dst.stat().st_size
        print(f"  Size: {size / 1024 / 1024:.1f} MB")

print()
print("=== Done ===")
for f in sorted(output.iterdir()):
    sz = f.stat().st_size
    if sz > 0:
        print(f"  {f.name}: {sz / 1024 / 1024:.1f} MB")
