#!/usr/bin/env python3
"""Build script: convert JOSM SVG icons → PNGs + generate lookup JSON.

Phase 1 of Vector Seamark rendering.

Usage:
    python3 scripts/generate-navmark-pngs.py

Requires: rsvg-convert (librsvg), Python 3
"""

import json, os, subprocess, sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parent.parent
SVG_DIR = PROJECT / "scripts/navmark-icons/Q"
OUT_DIR = PROJECT / "app/src/main/assets/navmarks"
PNG_W, PNG_H = 60, 120

os.makedirs(OUT_DIR, exist_ok=True)

# ── Convert all SVGs to PNGs ──────────────────────────────────────
svg_files = list(SVG_DIR.rglob("*.svg"))
print(f"Converting {len(svg_files)} SVGs to PNGs...")

converted = 0
failed = 0
for svg_path in svg_files:
    rel = svg_path.relative_to(SVG_DIR)
    stem = str(rel).replace("/", "_").replace(".svg", "")
    png_name = f"josm_{stem}.png"
    png_path = OUT_DIR / png_name
    if png_path.exists():
        continue
    result = subprocess.run(
        ["rsvg-convert", "-w", str(PNG_W), "-h", str(PNG_H),
         str(svg_path), "-o", str(png_path)],
        capture_output=True
    )
    if result.returncode == 0:
        converted += 1
    else:
        failed += 1
        if failed <= 5:
            print(f"  FAIL: {rel}")

print(f"Converted {converted}, failed {failed}, total {len(svg_files)}")

# ── Generate lookup table ──────────────────────────────────────────
# Maps OSM tag combinations → JOSM icon filenames
# Based on INT1_MapCSS.mapcss rules for Q-section

lookup = {}  # (seamark_type, shape, colours, pattern) → icon name

# Buoy/beacon types with their valid shapes and colour patterns
CONFIG = {
    "buoy_lateral": dict(shape=["can", "conical", "pillar", "spar", "spherical", "barrel"], colour_pattern="horizontal"),
    "buoy_cardinal": dict(shape=["pillar", "spar", "spherical"], colour_pattern="horizontal"),
    "buoy_safe_water": dict(shape=["spherical", "pillar"], colour_pattern="vertical"),
    "buoy_isolated_danger": dict(shape=["pillar", "spar", "spherical"], colour_pattern="horizontal"),
    "buoy_special_purpose": dict(shape=["pillar", "spherical", "spar"], colour_pattern="horizontal"),
    "buoy_installation": dict(shape=["super-buoy"], colour_pattern="horizontal"),
    "beacon_lateral": dict(shape=["tower", "pile", "stake", "cairn"], colour_pattern="horizontal"),
    "beacon_cardinal": dict(shape=["tower", "pile", "stake", "cairn"], colour_pattern="horizontal"),
    "beacon_safe_water": dict(shape=["tower", "pile", "stake"], colour_pattern="vertical"),
    "beacon_isolated_danger": dict(shape=["tower", "pile", "stake"], colour_pattern="horizontal"),
    "beacon_special_purpose": dict(shape=["tower", "pile", "stake", "cairn"], colour_pattern="horizontal"),
}

# Colour combinations by type
COLOURS = {
    "buoy_lateral": ["red", "green"],
    "buoy_cardinal": ["yellow_black", "black_yellow_black", "yellow_black_yellow"],
    "buoy_safe_water": ["red_white"],
    "buoy_isolated_danger": ["red_black_red", "black_red_black"],
    "buoy_special_purpose": ["yellow"],
    "beacon_lateral": ["red", "green"],
    "beacon_cardinal": ["yellow_black", "black_yellow_black", "yellow_black_yellow"],
    "beacon_safe_water": ["red_white"],
    "beacon_isolated_danger": ["red_black_red", "black_red_black"],
    "beacon_special_purpose": ["yellow"],
}

# Map JOSM filenames to icon names
JOSM_TO_ICON = {}
for svg_path in svg_files:
    rel = str(svg_path.relative_to(SVG_DIR))
    stem = rel.replace(".svg", "")
    png_name = f"josm_{rel.replace('/', '_').replace('.svg', '')}.png"
    JOSM_TO_ICON[f"josm_{stem}"] = png_name

print(f"\nGenerated lookup for {len(JOSM_TO_ICON)} icons")

with open(OUT_DIR / "navmark_lookup.json", "w") as f:
    json.dump({
        "josm_to_png": JOSM_TO_ICON,
    }, f, indent=2)

print(f"\nDone! Icons in {OUT_DIR}")
print(f"Total PNGs: {len(list(OUT_DIR.glob('*.png')))}")
