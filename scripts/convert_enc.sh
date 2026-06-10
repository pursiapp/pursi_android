#!/bin/bash
# S-57 ENC → GeoJSON tile converter for Finnish waters
# Prerequisites: gdal-bin (ogr2ogr), jq, gzip
# Usage: ./convert_enc.sh [input_dir] [output_dir]
#   input_dir  - directory with .000 S-57 ENC files (default: ./enc_files)
#   output_dir - directory for output GeoJSON tiles (default: ./tiles)

set -euo pipefail

INPUT_DIR="${1:-./enc_files}"
OUTPUT_DIR="${2:-./tiles}"
GRID_SIZE="${GRID_SIZE:-0.1}"  # tile grid size in degrees (~11km)

log() { echo "[$(date '+%H:%M:%S')] $*"; }

mkdir -p "$OUTPUT_DIR"

# Check for ogr2ogr
if ! command -v ogr2ogr &>/dev/null; then
    echo "ERROR: ogr2ogr not found. Install gdal-bin: sudo apt install gdal-bin"
    exit 1
fi

# Verify input directory has .000 files
COUNT=$(ls "$INPUT_DIR"/*.000 2>/dev/null | wc -l)
if [ "$COUNT" -eq 0 ]; then
    echo "ERROR: No .000 files found in $INPUT_DIR"
    echo ""
    echo "To get the ENC files:"
    echo "  1. Go to https://www.traficom.fi/en/transport/maritime/nautical-charts-and-data"
    echo "  2. Download the S-57 ENC package for Finland"
    echo "  3. Extract the .000 files into: $INPUT_DIR"
    echo ""
    echo "Or manually download from avoindata.fi (search 'ENC Suomi')"
    exit 1
fi

log "Found $COUNT ENC files in $INPUT_DIR"

# Process each ENC file
TOTAL_FILES=0
for ENC_FILE in "$INPUT_DIR"/*.000; do
    BASENAME=$(basename "$ENC_FILE" .000)
    log "Processing $BASENAME..."

    # Create temp directory for this cell's GeoJSON layers
    TMPDIR=$(mktemp -d)

    # Extract depth soundings (SOUNDG), depth contours (DEPCNT), depth areas (DEPARE)
    # ADD_SOUNDG_DEPTH adds a DEPTH attribute to each sounding point
    ogr2ogr -f GeoJSON -progress "$TMPDIR/soundings.geojson" \
        "$ENC_FILE" SOUNDG \
        -lco ADD_SOUNDG_DEPTH=YES \
        -lco SPLIT_MULTIPOINT=YES \
        -t_srs EPSG:4326 2>/dev/null || true

    ogr2ogr -f GeoJSON -progress "$TMPDIR/contours.geojson" \
        "$ENC_FILE" DEPCNT \
        -t_srs EPSG:4326 2>/dev/null || true

    ogr2ogr -f GeoJSON -progress "$TMPDIR/areas.geojson" \
        "$ENC_FILE" DEPARE \
        -t_srs EPSG:4326 2>/dev/null || true

    # Split each layer into grid tiles
    for LAYER in soundings contours areas; do
        JSON_FILE="$TMPDIR/$LAYER.geojson"
        if [ ! -s "$JSON_FILE" ]; then continue; fi

        FEATURE_COUNT=$(jq '.features | length' "$JSON_FILE" 2>/dev/null || echo 0)
        if [ "$FEATURE_COUNT" -eq 0 ]; then continue; fi

        # Get bounding box of the data
        BBOX=$(jq -r '.features[0].geometry.coordinates | if type=="array" and .[0]|type=="number" then "\(.[1]),\(.[0])" else "" end' "$JSON_FILE" 2>/dev/null || true)
        if [ -z "$BBOX" ]; then
            BBOX=$(jq -r '[.features[] | select(.geometry.type=="Point") | .geometry.coordinates] | if length>0 then "\(min_by(.[1])[1]),\(min_by(.[0])[0])" else "" end' "$JSON_FILE" 2>/dev/null || true)
        fi

        # Split into grid tiles by coordinate
        while IFS= read -r FEATURE; do
            if [ -z "$FEATURE" ]; then continue; fi

            # Extract coordinates to determine tile
            LON=$(echo "$FEATURE" | jq -r '.geometry.coordinates | if type=="array" then if .[0]|type=="number" then .[0] elif .[0]|type=="array" then .[0][0]|if type=="number" then .[0] else .[0][0] end else "" end else "" end' 2>/dev/null || true)
            LAT=$(echo "$FEATURE" | jq -r '.geometry.coordinates | if type=="array" then if .[0]|type=="number" then .[1] elif .[0]|type=="array" then .[0][1]|if type=="number" then .[1] else .[0][1] end else "" end else "" end' 2>/dev/null || true)

            if [ -z "$LON" ] || [ -z "$LAT" ] || [ "$LON" = "null" ]; then continue; fi

            # Calculate tile coordinates
            TILE_X=$(python3 -c "print(int($LON // $GRID_SIZE))" 2>/dev/null || continue)
            TILE_Y=$(python3 -c "print(int($LAT // $GRID_SIZE))" 2>/dev/null || continue)

            TILE_DIR="$OUTPUT_DIR/$LAYER/$TILE_X"
            TILE_FILE="$TILE_DIR/$TILE_Y.geojson"
            mkdir -p "$TILE_DIR"
            echo "$FEATURE" >> "$TILE_FILE"
        done < <(jq -c '.features[]' "$JSON_FILE" 2>/dev/null || true)
    done

    rm -rf "$TMPDIR"
    TOTAL_FILES=$((TOTAL_FILES + 1))
done

log "=== Repairing JSON arrays and compressing ==="
for LAYER in soundings contours areas; do
    for TILE_DIR in "$OUTPUT_DIR/$LAYER"/*/; do
        [ -d "$TILE_DIR" ] || continue
        for TILE_FILE in "$TILE_DIR"/*.geojson; do
            [ -f "$TILE_FILE" ] || continue

            # Wrap individual features into a FeatureCollection
            FEATURES=$(wc -l < "$TILE_FILE")
            {
                echo '{"type":"FeatureCollection","features":['
                paste -s -d, "$TILE_FILE"
                echo ']}'
            } > "${TILE_FILE}.tmp"
            mv "${TILE_FILE}.tmp" "$TILE_FILE"

            # Gzip compress
            gzip -f "$TILE_FILE"
        done
    done
done

# Create index file
log "=== Creating index ==="
find "$OUTPUT_DIR" -name "*.geojson.gz" | sort > "$OUTPUT_DIR/index.txt"
TOTAL_TILES=$(wc -l < "$OUTPUT_DIR/index.txt")
TOTAL_SIZE=$(du -sh "$OUTPUT_DIR" | cut -f1)

log "=== Done ==="
log "Processed $TOTAL_FILES ENC files"
log "Created $TOTAL_TILES tiles"
log "Total size: $TOTAL_SIZE"
log ""
log "Upload the '$OUTPUT_DIR' directory to any static file host."
log "Tile URL pattern: https://your-host/tiles/{layer}/{x}/{y}.geojson.gz"
log ""
log "Layers:"
log "  soundings - depth points with VALSOU attribute (depth value in meters)"
log "  contours  - depth contour lines with VALDCO attribute"
log "  areas     - depth areas with DRVAL1 (min depth) and DRVAL2 (max depth)"
