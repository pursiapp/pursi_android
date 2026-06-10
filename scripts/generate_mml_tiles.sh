#!/usr/bin/env bash
#
# generate_mml_tiles.sh
# Downloads MML taustakartta tiles for coastal regions and packages them.
#
# Prerequisites:
#   MML_API_KEY environment variable must be set
#   Requires: curl, pigz (or gzip), python3
#
# Output: output/*.tar.gz — one file per region
#
set -euo pipefail

[ -z "${MML_API_KEY:-}" ] && { echo "ERROR: MML_API_KEY not set"; exit 1; }

# Rannikkoalueet (7 kpl)
REGIONS=(
    "Suomenlahti-lansi:59.5:60.5:22.0:26.0"
    "Suomenlahti-ita:59.5:60.8:25.0:28.5"
    "Saaristomeri:59.8:60.8:19.0:23.0"
    "Selkameri:60.5:62.5:19.0:22.0"
    "Pohjanlahti-etela:62.5:64.0:20.0:23.5"
    "Pohjanlahti-pohjoinen:63.5:65.5:22.0:26.0"
    "Perameri:65.0:66.0:23.0:26.0"
    # Sisävesialueet (joille Traficomilla on veneilykartat)
    "Saimaa:61.0:62.5:27.0:30.0"
    "Paijanne:61.2:62.3:25.0:26.5"
    "Keitele:62.7:63.2:25.5:26.5"
    "Nasijarvi:61.5:62.0:23.5:24.0"
    "Oulujarvi:64.2:64.5:26.8:27.8"
)

MIN_ZOOM=8
MAX_ZOOM=14
BASE_URL="https://avoin-karttakuva.maanmittauslaitos.fi/avoin/wmts/1.0.0/taustakartta/default/WGS84_Pseudo-Mercator"
PARALLEL=${PARALLEL:-20}

STAGING_DIR=$(mktemp -d)
trap 'rm -rf "$STAGING_DIR"' EXIT

echo "=== MML Taustakartta tile generator ==="
echo "Zoom range: $MIN_ZOOM - $MAX_ZOOM, parallel: $PARALLEL"
echo ""

download_tile() {
    local url="$1" out="$2"
    mkdir -p "$(dirname "$out")"
    curl -sf -o "$out" "$url" 2>/dev/null || true
}
export -f download_tile
export BASE_URL MML_API_KEY

download_region() {
    local name="$1" min_lat="$2" max_lat="$3" min_lng="$4" max_lng="$5"
    local out_dir="$STAGING_DIR/$name"
    local joblist="$STAGING_DIR/${name}_jobs.txt"
    rm -f "$joblist"

    echo "--- Region: $name ---"

    for z in $(seq $MIN_ZOOM $MAX_ZOOM); do
        n=$(( 1 << z ))

        x_min=$(python3 -c "print(int(($min_lng + 180) / 360 * $n))")
        x_max=$(python3 -c "print(int(($max_lng + 180) / 360 * $n))")
        [ "$x_min" -lt 0 ] && x_min=0
        [ "$x_max" -ge "$n" ] && x_max=$(( n - 1 ))

        y_min=$(python3 -c "
import math
r=math.radians($max_lat)
print(int((1-math.log(math.tan(r)+1/math.cos(r))/math.pi)/2*$n))")
        y_max=$(python3 -c "
import math
r=math.radians($min_lat)
print(int((1-math.log(math.tan(r)+1/math.cos(r))/math.pi)/2*$n))")
        [ "$y_min" -lt 0 ] && y_min=0
        [ "$y_max" -ge "$n" ] && y_max=$(( n - 1 ))

        count=$(( (x_max - x_min + 1) * (y_max - y_min + 1) ))
        echo "  Zoom $z: ${count} tiles"

        for ((x = x_min; x <= x_max; x++)); do
            for ((y = y_min; y <= y_max; y++)); do
                echo "$BASE_URL/$z/$y/$x.png?api-key=$MML_API_KEY $out_dir/$z/$x/$y.png" >> "$joblist"
            done
        done
    done

    total=$(wc -l < "$joblist")
    echo "  Downloading ${total} tiles (${PARALLEL} parallel)..."
    xargs -P "$PARALLEL" -I{} bash -c 'download_tile $(echo {})' _ < "$joblist"

    actual=$(find "$out_dir" -name "*.png" | wc -l)
    echo "  Downloaded: $actual / $total tiles"
    echo ""
}

for region_def in "${REGIONS[@]}"; do
    IFS=':' read -r name min_lat max_lat min_lng max_lng <<< "$region_def"
    download_region "$name" "$min_lat" "$max_lat" "$min_lng" "$max_lng"
done

mkdir -p output
echo "=== Packaging ==="
for region_def in "${REGIONS[@]}"; do
    name="${region_def%%:*}"
    src="$STAGING_DIR/$name"
    dst="output/${name}.tar.gz"
    if [ -d "$src" ]; then
        echo "  $name → $dst"
        tar -C "$STAGING_DIR" -cf - "$name" | pigz -9 > "$dst"
        echo "  Size: $(du -h "$dst" | cut -f1)"
    fi
done

echo ""
echo "=== Done ==="
ls -lh output/
