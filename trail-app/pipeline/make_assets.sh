#!/usr/bin/env bash
# Rebuild everything the app bundles. Usage: pipeline/make_assets.sh <workdir>
# Needs: python3 (shapely, mapbox-vector-tile, pmtiles), go-pmtiles, tippecanoe, node.
set -euo pipefail
WORK=${1:?workdir}
HERE=$(cd "$(dirname "$0")" && pwd)
ASSETS="$HERE/../android/app/src/main/assets"
PM=${PMTILES:-$HOME/go/bin/go-pmtiles}
BUILD=${PROTOMAPS_BUILD:-$(date -u -d yesterday +%Y%m%d)}
mkdir -p "$WORK/tiles" "$WORK/out" "$ASSETS"/{style,sprites}

echo "== LIO open data"; python3 "$HERE/fetch_lio.py" "$WORK" && date -u +%F > "$WORK/lio/FETCHED_AT"
echo "== basemap ($BUILD)"
"$PM" extract "https://build.protomaps.com/$BUILD.pmtiles" "$ASSETS/basemap.pmtiles" --region="$HERE/region.geojson" --maxzoom=14
"$PM" extract "https://build.protomaps.com/$BUILD.pmtiles" "$WORK/tiles/z15.pmtiles" --region="$HERE/region.geojson" --minzoom=15 --maxzoom=15
echo "== classify + graph"; python3 "$HERE/build_data.py" "$WORK" "$WORK/out"
tippecanoe -q -o "$ASSETS/trails.pmtiles" -Z7 -z14 --force --no-feature-limit --no-tile-size-limit --simplification=4 \
  -j "$(cat "$HERE/tile_filter.json")" -L trails:"$WORK/out/trails.geojson" -L junctions:"$WORK/out/junctions.geojson"
cp "$WORK/out/graph.bin" "$WORK/out/pois.json" "$WORK/out/meta.json" "$ASSETS/"
echo "== styles, fonts, sprites"
(cd "$HERE/style" && npm install --silent && node gen_style.mjs "$ASSETS/style")
RAW=https://raw.githubusercontent.com/protomaps/basemaps-assets/main
for font in "Noto Sans Regular" "Noto Sans Medium" "Noto Sans Italic"; do
  mkdir -p "$ASSETS/fonts/$font"
  for r in 0-255 256-511 8192-8447; do
    curl -sSf "$RAW/fonts/${font// /%20}/$r.pbf" -o "$ASSETS/fonts/$font/$r.pbf"
  done
done
for m in light dark; do for s in "" "@2x"; do for e in json png; do
  curl -sSf "$RAW/sprites/v4/$m$s.$e" -o "$ASSETS/sprites/$m$s.$e"
done; done; done
echo done
