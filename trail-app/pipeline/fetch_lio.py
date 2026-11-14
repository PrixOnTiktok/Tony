"""Download Land Information Ontario (LIO) open-data layers for the Bancroft region.

Open Government Licence - Ontario. Output: one GeoJSON per layer in <outdir>/lio/.
Usage: python3 fetch_lio.py <outdir>
"""
import json, os, sys, time, urllib.parse, urllib.request

from region import BBOX  # (west, south, east, north)

BASE = "https://ws.lioservices.lrc.gov.on.ca/arcgis2/rest/services/LIO_OPEN_DATA"
LAYERS = {
    "otn_trails":        ("LIO_Open04", 19),  # Ontario Trail Network segments (permitted uses, association)
    "mnrf_roads":        ("LIO_Open09", 18),  # MNRF forest / resource access roads
    "mnr_barriers":      ("LIO_Open09", 22),  # gates, berms, boulders on MNR roads
    "mnrf_bridges":      ("LIO_Open09", 42),  # road water crossings (bridges)
    "crown_unpatented":  ("LIO_Open08", 34),  # Crown land (unpatented)
    "prov_parks":        ("LIO_Open03", 4),   # provincial parks (no ORVs)
    "cons_reserves":     ("LIO_Open03", 2),   # conservation reserves
    "municipalities":    ("LIO_Open03", 14),  # lower/single-tier municipal boundaries
    "orn_roads":         ("LIO_Open09", 0),   # Ontario Road Network geometry
}


def _get(url, data=None):
    for attempt in range(6):
        try:
            req = urllib.request.Request(url, data=data)
            with urllib.request.urlopen(req, timeout=180) as r:
                d = json.load(r)
            if "error" in d:
                raise RuntimeError(d["error"])
            return d
        except Exception as ex:  # noqa
            print("  retry", attempt, ex, file=sys.stderr)
            time.sleep(3 * (attempt + 1))
    raise RuntimeError("failed " + url[:120])


def fetch(svc, layer, chunk=500):
    """Fetch all features intersecting BBOX: list object ids, then pull them in chunks."""
    w, s, e, n = BBOX
    url = f"{BASE}/{svc}/MapServer/{layer}/query"
    geo = {"geometry": f"{w},{s},{e},{n}", "geometryType": "esriGeometryEnvelope",
           "inSR": 4326, "spatialRel": "esriSpatialRelIntersects"}
    ids = _get(url + "?" + urllib.parse.urlencode({"where": "1=1", "returnIdsOnly": "true", "f": "json", **geo}))
    oids = sorted(ids.get("objectIds") or [])
    feats = []
    for i in range(0, len(oids), chunk):
        body = urllib.parse.urlencode({
            "objectIds": ",".join(map(str, oids[i:i + chunk])), "outFields": "*",
            "outSR": 4326, "f": "geojson"}).encode()
        feats += _get(url, body).get("features", [])
        print(f"  {svc}/{layer}: {len(feats)}/{len(oids)}", file=sys.stderr)
    return {"type": "FeatureCollection", "features": feats}


def main():
    out = os.path.join(sys.argv[1], "lio")
    os.makedirs(out, exist_ok=True)
    only = sys.argv[2:]
    for name, (svc, layer) in LAYERS.items():
        if only and name not in only:
            continue
        print("fetch", name, file=sys.stderr)
        fc = fetch(svc, layer)
        with open(os.path.join(out, name + ".geojson"), "w") as f:
            json.dump(fc, f)
        print(name, len(fc["features"]))


if __name__ == "__main__":
    main()
