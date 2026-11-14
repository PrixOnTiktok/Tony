"""Extract OpenStreetMap roads/paths and POIs from the Protomaps basemap (z14 tiles).

The sandbox can't reach OSM servers directly, but the Protomaps basemap is built from
OSM (planet replication date is in the archive metadata), so its z14 tiles hold every
road, track and path plus POIs. Lines are clipped at tile edges; build_data.py stitches them.

Usage: python3 extract_osm_from_tiles.py basemap.pmtiles outdir
Writes roads.jsonl and pois.jsonl (one GeoJSON feature per line, lon/lat).
"""
import gzip, json, math, sys
import mapbox_vector_tile
from pmtiles.reader import Reader, MmapSource, all_tiles

Z = 14
KEEP_ROAD = {"kind", "kind_detail", "name", "ref", "access", "is_bridge", "is_tunnel", "is_link", "oneway", "network", "shield_text", "layer", "level", "min_zoom"}
KEEP_POI = {"kind", "kind_detail", "name", "elevation", "min_zoom"}


def tile_to_lonlat(z, x, y, px, py, extent):
    n = 2 ** z
    lon = (x + px / extent) / n * 360.0 - 180.0
    yy = y + 1 - py / extent  # mapbox_vector_tile flips y by default (origin bottom-left)
    lat = math.degrees(math.atan(math.sinh(math.pi * (1 - 2 * yy / n))))
    return [round(lon, 6), round(lat, 6)]


def conv(geom, z, x, y, extent):
    t = geom["type"]
    c = geom["coordinates"]
    f = lambda p: tile_to_lonlat(z, x, y, p[0], p[1], extent)
    if t == "Point":
        return {"type": t, "coordinates": f(c)}
    if t == "MultiPoint" or t == "LineString":
        return {"type": t, "coordinates": [f(p) for p in c]}
    if t == "MultiLineString" or t == "Polygon":
        return {"type": t, "coordinates": [[f(p) for p in l] for l in c]}
    if t == "MultiPolygon":
        return {"type": t, "coordinates": [[[f(p) for p in l] for l in poly] for poly in c]}
    return None


def main():
    src, out = sys.argv[1], sys.argv[2]
    roads = open(f"{out}/roads.jsonl", "w")
    pois = open(f"{out}/pois.jsonl", "w")
    nr = npz = 0
    with open(src, "rb") as fh:
        reader = Reader(MmapSource(fh))
        for (z, x, y), data in all_tiles(reader.get_bytes):
            if z != Z:
                continue
            try:
                data = gzip.decompress(data)
            except OSError:
                pass
            t = mapbox_vector_tile.decode(data)
            for lname, fout, keep in (("roads", roads, KEEP_ROAD), ("pois", pois, KEEP_POI)):
                layer = t.get(lname)
                if not layer:
                    continue
                ext = layer.get("extent", 4096)
                for feat in layer["features"]:
                    g = conv(feat["geometry"], z, x, y, ext)
                    if not g:
                        continue
                    props = {k: v for k, v in feat["properties"].items() if k in keep}
                    props["_tile"] = [x, y]
                    if "id" in feat:
                        props["_id"] = feat["id"]
                    fout.write(json.dumps({"type": "Feature", "geometry": g, "properties": props}) + "\n")
                    if lname == "roads":
                        nr += 1
                    else:
                        npz += 1
    print("roads", nr, "pois", npz)


if __name__ == "__main__":
    main()
