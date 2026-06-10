#!/usr/bin/env python3
"""
Generate navmark icons using k-yle/OpenSeaMap-vector navmark-renderer.

Supports both day and night-mode variants. Night variant inverts dark colors
(black → light gray) while preserving navigationally significant colors.

Requirements:
  - Node.js 18+
  - npm
  - rsvg-convert (librsvg)

Usage:
  # Generate both day and night variants:
      python3 scripts/generate-navmark-icon.py

  # Generate only night variants:
      python3 scripts/generate-navmark-icon.py --night-only

  # Generate only day variants:
      python3 scripts/generate-navmark-icon.py --day-only

  # Regenerate from scratch (re-clone repo):
      python3 scripts/generate-navmark-icon.py --night-only --force-clone

Output:
  app/src/main/assets/navmarks/           # Day PNGs (60×120)
  app/src/main/assets/navmarks-night/     # Night PNGs (60×120)

Source:
  https://github.com/k-yle/OpenSeaMap-vector (packages/navmark-renderer)

Color mapping (night mode):
  stroke="#000"    -> stroke="#ccc"      (soft white outlines)
  fill="black"     -> fill="#c0c0c0"     (dark bands become light)
  fill="#000"      -> fill="#c0c0c0"
  fill="white"     -> fill="#1a1a1a"     (light bands become dark)
  fill="#fff"      -> fill="#1a1a1a"
  fill="#999"      -> fill="#ddd"        (topmark mast)
  fill="#555"      -> fill="#aaa"
  red, green, yellow, orange, magenta    -> preserved unchanged

Day variants use navmark-renderer's default colors unchanged.
"""

import argparse, json, os, shutil, subprocess, sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parent.parent
SCRIPTS_DIR = PROJECT / "scripts"
RENDERER_DIR = Path("/tmp/OpenSeaMap-vector/packages/navmark-renderer")
NAVMARKS_DIR = PROJECT / "app" / "src" / "main" / "assets" / "navmarks"
NAVMARKS_NIGHT_DIR = PROJECT / "app" / "src" / "main" / "assets" / "navmarks-night"


def step(msg):
    print(f"\n=== {msg} ===")


def check_prerequisites():
    missing = [cmd for cmd in ["node", "npm", "rsvg-convert"] if not shutil.which(cmd)]
    if missing:
        print(f"ERROR: missing prerequisites: {', '.join(missing)}")
        sys.exit(1)


def clone_or_update(force=False):
    tmp_dir = Path("/tmp/OpenSeaMap-vector")
    if tmp_dir.exists() and force:
        shutil.rmtree(tmp_dir)
    if tmp_dir.exists():
        step("Updating existing clone")
        subprocess.run(["git", "-C", str(tmp_dir), "pull"], check=True, timeout=60)
    else:
        step("Cloning OpenSeaMap-vector")
        subprocess.run(
            ["git", "clone", "https://github.com/k-yle/OpenSeaMap-vector.git", str(tmp_dir)],
            check=True, timeout=120
        )


def install_deps():
    step("Installing npm dependencies")
    subprocess.run(["npm", "install"], cwd=str(RENDERER_DIR), check=True, timeout=180)


def build_lib():
    step("Building navmark-renderer library")
    subprocess.run(["npm", "run", "build"], cwd=str(RENDERER_DIR), check=True, timeout=120)


def copy_scripts():
    step("Copying generation scripts to renderer directory")
    src_gen = SCRIPTS_DIR / "navmark-generator" / "generate.mjs"
    src_conf = SCRIPTS_DIR / "navmark-generator" / "icons.json"
    shutil.copy2(src_gen, RENDERER_DIR / "generate.mjs")
    shutil.copy2(src_conf, RENDERER_DIR / "icons.json")


def generate(mode: str):
    step(f"Generating {mode} variant SVGs")
    subprocess.run(
        ["node", "generate.mjs", mode],
        cwd=str(RENDERER_DIR), check=True, timeout=120
    )


def convert_to_png(src_svg_dir: Path, dst_png_dir: Path, label: str):
    step(f"Converting {label} SVGs to PNGs")
    dst_png_dir.mkdir(parents=True, exist_ok=True)
    svgs = sorted(src_svg_dir.glob("*.svg"))
    if not svgs:
        print(f"WARNING: no SVGs found in {src_svg_dir}")
        return
    count = 0
    for svg_path in svgs:
        png_path = dst_png_dir / f"{svg_path.stem}.png"
        subprocess.run(
            ["rsvg-convert", "-w", "60", "-h", "120", str(svg_path), "-o", str(png_path)],
            check=True, timeout=30, capture_output=True
        )
        count += 1
    print(f"Converted {count} PNGs to {dst_png_dir}")


def main():
    parser = argparse.ArgumentParser(description="Generate navmark icons")
    parser.add_argument("--night-only", action="store_true", help="Generate only night variants")
    parser.add_argument("--day-only", action="store_true", help="Generate only day variants")
    parser.add_argument("--force-clone", action="store_true", help="Re-clone repo from scratch")
    args = parser.parse_args()

    mode = "day" if args.day_only else ("night" if args.night_only else "both")

    check_prerequisites()
    clone_or_update(args.force_clone)
    install_deps()
    build_lib()
    copy_scripts()

    generate(mode)

    if mode in ("day", "both"):
        convert_to_png(Path("/tmp/pursi-navmarks/navmarks"), NAVMARKS_DIR, "day")
    if mode in ("night", "both"):
        convert_to_png(Path("/tmp/pursi-navmarks/navmarks-night"), NAVMARKS_NIGHT_DIR, "night")

    print(f"\nDone!")


if __name__ == "__main__":
    main()
