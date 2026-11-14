"""Build the offline data bundle for the Bancroft ATV trail app.

Inputs (see README):  <data>/tiles/z15.pmtiles (Protomaps/OSM), <data>/lio/*.geojson (Ontario LIO),
                      rules/orv_rules.json (researched road rules).
Outputs (<out>):      graph.bin      routable network with per-segment ATV status
                      trails.geojson display layer (turned into trails.pmtiles by tippecanoe)
                      pois.json      gas / food / points of interest / gates
                      meta.json      reason texts, string tables, municipalities, data dates, stats

Every segment gets one of four classes:
  0 FREE   open to ATVs, no pass needed as far as the sources show (Crown land trails, forest roads)
  1 MAYBE  might need a pass or permission (club trails, private land, restricted forest roads, narrow paths)
  2 ROAD   public road where ATVs may ride (municipal by-law / provincial rules)
  3 NO     ATVs not allowed (parks, conservation reserves, prohibited highways, hiking trails)
and a confidence (0 low, 1 medium, 2 high) from how many independent sources agree.
"""
import gzip, json, math, os, struct, sys, collections

import mapbox_vector_tile
import shapely
from shapely.geometry import shape, Point, LineString
from shapely.strtree import STRtree
from pmtiles.reader import Reader, MmapSource, all_tiles

sys.path.insert(0, os.path.dirname(__file__))
from region import CENTER, in_region  # noqa: E402

LAT0, LON0 = CENTER
KX = 111320.0 * math.cos(math.radians(LAT0))
KY = 110540.0

FREE, MAYBE, ROAD, NO = 0, 1, 2, 3
# kinds
K_TRACK, K_PATH, K_FOREST, K_MINOR, K_MAJOR, K_HWY, K_CLUB, K_SERVICE = range(8)
KIND_NAMES = ["Trail / track", "Narrow trail", "Forest access road", "Local road", "Main road", "Highway", "Club trail", "Service road"]
SPEED = {K_TRACK: 20, K_PATH: 10, K_FOREST: 30, K_MINOR: 35, K_MAJOR: 45, K_HWY: 45, K_CLUB: 20, K_SERVICE: 15}
# flags
F_CROWN, F_PARK, F_OSM, F_MNRF, F_OTN, F_GATE, F_BRIDGE, F_LOWCONF, F_PRIVATE, F_CLUB, F_HIKING, F_RESTRICTED, F_UNPAVED, F_SNOW = [1 << i for i in range(14)]

REASONS = [
    "Trail on Crown land: open to ATVs unless signs say otherwise.",                       # 0
    "Forest access road on Crown land (MNRF): open unless gated or posted.",              # 1
    "Club / association trail: a trail pass is probably needed ({note}).",                # 2
    "Crosses private land: you need the landowner's OK.",                                 # 3
    "Forest road marked restricted: {note}.",                                             # 4
    "Narrow trail: may be hiking-only. Check for signs.",                                 # 5
    "{note} allows ATVs on its roads (by-law). Ride the shoulder, 20 km/h where posted 50 or less, 50 km/h max.",  # 6
    "{note}: no ATV by-law found, but rural townships on O. Reg. 8/03 allow ORVs on roads by default. Check signs.",  # 7
    "{note} only allows ATVs on some roads. Look for signs before riding along it.",      # 8
    "ATVs can't ride along this road in {note}. Crossing straight across is OK.",        # 9
    "Provincial highway not open to ORVs here (O. Reg. 316/03). Cross straight across only.",  # 10
    "Provincial highway: ORV rules for this stretch are unconfirmed. Shoulder only, 50 km/h max.",  # 11
    "Provincial highway open to ORVs (O. Reg. 316/03 Sched. B): shoulder, with traffic, lights on, 20 km/h if posted 50 or less, else 50 km/h max.",  # 12
    "Inside {note}: no ORVs in provincial parks (O. Reg. 347/07 s.34).",                # 13
    "Non-motorized trail ({note}): no ATVs.",                                             # 14
    "{note}: ATV road rules not confirmed. Maybe allowed. Check signs or the township.",  # 15
    "Forest access road on private or managed land: permission may be needed.",           # 16
    "Rail trail / multi-use trail: usually needs a club pass ({note}).",                  # 17
    "Trail not on Crown land and not in any club system: probably private. Ask first.",   # 18
    "Inside {note} (conservation reserve): ORVs allowed on existing roads/trails unless posted closed.",  # 19
    "Dirt-bike-only trail ({note}): not open to ATVs.",                                    # 20
    "OFSC snowmobile trail: OFSC says no ATVs at any time of year; most cross private land (trespass).",  # 21
]
R_CROWN, R_FOREST, R_CLUB, R_PRIVATE, R_RESTRICT, R_PATH, R_BYLAW, R_DEFAULT, R_SOME, R_NONE, R_HWY_NO, R_HWY_UNK, R_HWY_OK, R_PARK, R_HIKE, R_UNKNOWN, R_FOREST_PRIV, R_RAIL, R_UNKNOWN_TRAIL, R_RESERVE, R_DIRTBIKE, R_OFSC = range(22)


def to_xy(lon, lat):
    return ((lon - LON0) * KX, (lat - LAT0) * KY)


def to_ll(x, y):
    return (x / KX + LON0, y / KY + LAT0)


# ---------------------------------------------------------------- OSM from tiles
def gpx_to_ll(gx, gy, z=15, ext=4096):
    n = 2 ** z * ext
    lon = gx / n * 360.0 - 180.0
    lat = math.degrees(math.atan(math.sinh(math.pi * (1 - 2 * gy / n))))
    return lon, lat


def clip_line(pts, xmin, ymin, xmax, ymax):
    """Clip a polyline (global pixel coords) to a rectangle; returns list of polylines."""
    out, cur = [], []

    def inside(p):
        return xmin <= p[0] <= xmax and ymin <= p[1] <= ymax

    for i in range(len(pts) - 1):
        a, b = pts[i], pts[i + 1]
        dx, dy = b[0] - a[0], b[1] - a[1]
        t0, t1 = 0.0, 1.0
        ok = True
        for p, q in ((-dx, a[0] - xmin), (dx, xmax - a[0]), (-dy, a[1] - ymin), (dy, ymax - a[1])):
            if p == 0:
                if q < 0:
                    ok = False
                    break
            else:
                r = q / p
                if p < 0:
                    if r > t1:
                        ok = False
                        break
                    t0 = max(t0, r)
                else:
                    if r < t0:
                        ok = False
                        break
                    t1 = min(t1, r)
        if not ok:
            if cur:
                out.append(cur)
                cur = []
            continue
        pa = a if t0 == 0 else (a[0] + t0 * dx, a[1] + t0 * dy)
        pb = b if t1 == 1 else (a[0] + t1 * dx, a[1] + t1 * dy)
        if not cur:
            cur = [pa]
        elif cur[-1] != pa:
            out.append(cur)
            cur = [pa]
        cur.append(pb)
        if t1 < 1:
            out.append(cur)
            cur = []
    if cur:
        out.append(cur)
    return [c for c in out if len(c) >= 2]


ROAD_KIND = {
    ("path", "track"): K_TRACK, ("path", "logging"): K_TRACK, ("path", "path"): K_PATH,
    ("path", "bridleway"): K_PATH,
    ("minor_road", "residential"): K_MINOR, ("minor_road", "unclassified"): K_MINOR, ("minor_road", "road"): K_MINOR,
    ("other", "living_street"): K_MINOR, ("minor_road", "service"): K_SERVICE, ("minor_road", "driveway"): K_SERVICE,
    ("path", "driveway"): K_SERVICE,
    ("major_road", "tertiary"): K_MAJOR, ("major_road", "tertiary_link"): K_MAJOR,
    ("major_road", "secondary"): K_MAJOR, ("major_road", "secondary_link"): K_MAJOR,
    ("major_road", "primary"): K_HWY, ("major_road", "primary_link"): K_HWY,
    ("major_road", "trunk"): K_HWY, ("major_road", "trunk_link"): K_HWY,
}

POI_CATS = {
    "gas": {"fuel"},
    "food": {"restaurant", "fast_food", "cafe", "ice_cream", "pub", "bar", "bakery", "deli", "convenience", "supermarket", "general", "marketplace"},
    "poi": {"viewpoint", "waterfall", "attraction", "museum", "historic", "beach", "picnic_site", "camp_site", "caravan_site",
            "peak", "toilets", "drinking_water", "shelter", "ruins", "monument", "memorial", "mine", "archaeological_site",
            "information", "boat_ramp", "slipway", "lookout"},
    "parking": {"parking"},
}


def read_osm(z15_path):
    lines, pois = [], []
    with open(z15_path, "rb") as fh:
        rd = Reader(MmapSource(fh))
        for (z, x, y), data in all_tiles(rd.get_bytes):
            if z != 15:
                continue
            try:
                data = gzip.decompress(data)
            except OSError:
                pass
            t = mapbox_vector_tile.decode(data, default_options={"y_coord_down": True})
            ox, oy = x * 4096, y * 4096
            lay = t.get("roads")
            if lay:
                for f in lay["features"]:
                    p = f["properties"]
                    k = ROAD_KIND.get((p.get("kind"), p.get("kind_detail")))
                    if k is None:
                        continue
                    g = f["geometry"]
                    parts = [g["coordinates"]] if g["type"] == "LineString" else g["coordinates"] if g["type"] == "MultiLineString" else []
                    for part in parts:
                        pts = [(ox + c[0], oy + c[1]) for c in part]
                        for seg in clip_line(pts, ox, oy, ox + 4096, oy + 4096):
                            lines.append({"g": seg, "kind": k, "name": p.get("name") or "", "ref": str(p.get("ref") or ""),
                                          "bridge": bool(p.get("is_bridge")), "tunnel": bool(p.get("is_tunnel")),
                                          "access": p.get("access"), "src": "osm", "kd": p.get("kind_detail"),
                                          "network": p.get("network") or ""})
            lay = t.get("pois")
            if lay:
                for f in lay["features"]:
                    p = f["properties"]
                    kd = p.get("kind")
                    cat = next((c for c, s in POI_CATS.items() if kd in s), None)
                    if not cat:
                        continue
                    g = f["geometry"]
                    if g["type"] == "Point":
                        c = g["coordinates"]
                    elif g["type"] == "MultiPoint":
                        c = g["coordinates"][0]
                    else:
                        continue
                    if not (0 <= c[0] <= 4096 and 0 <= c[1] <= 4096):
                        continue  # buffer duplicate
                    lon, lat = gpx_to_ll(ox + c[0], oy + c[1])
                    pois.append({"cat": cat, "kind": kd, "name": p.get("name") or "", "lat": round(lat, 6), "lon": round(lon, 6),
                                 "ele": p.get("elevation")})
    return lines, pois


# ---------------------------------------------------------------- graph building
class Grid:
    def __init__(self, cell):
        self.cell = cell
        self.d = collections.defaultdict(list)

    def key(self, x, y):
        return (int(math.floor(x / self.cell)), int(math.floor(y / self.cell)))

    def add_seg(self, item, a, b):
        x0, x1 = sorted((a[0], b[0]))
        y0, y1 = sorted((a[1], b[1]))
        for i in range(int(math.floor(x0 / self.cell)), int(math.floor(x1 / self.cell)) + 1):
            for j in range(int(math.floor(y0 / self.cell)), int(math.floor(y1 / self.cell)) + 1):
                self.d[(i, j)].append(item)

    def near(self, x, y, r):
        out = set()
        for i in range(int(math.floor((x - r) / self.cell)), int(math.floor((x + r) / self.cell)) + 1):
            for j in range(int(math.floor((y - r) / self.cell)), int(math.floor((y + r) / self.cell)) + 1):
                out.update(self.d.get((i, j), ()))
        return out


def seg_dist(p, a, b):
    dx, dy = b[0] - a[0], b[1] - a[1]
    L = dx * dx + dy * dy
    t = 0 if L == 0 else max(0.0, min(1.0, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / L))
    q = (a[0] + t * dx, a[1] + t * dy)
    return math.hypot(p[0] - q[0], p[1] - q[1]), t, q


def lines_to_metric(lines):
    """OSM global-pixel lines -> metric; quantize to 0.5 m so identical vertices coincide."""
    for ln in lines:
        if ln["src"] == "osm":
            out = []
            for gx, gy in ln["g"]:
                lon, lat = gpx_to_ll(gx, gy)
                x, y = to_xy(lon, lat)
                q = (round(x * 2) / 2, round(y * 2) / 2)
                if not out or out[-1] != q:
                    out.append(q)
            ln["xy"] = out
    return [ln for ln in lines if len(ln.get("xy", ())) >= 2]


def snap_endpoints(lines, tol_tile=2.5, tol_t=4.0, tol_mnrf=25.0):
    """Join dangling ends: tile-boundary seams, T-junctions dropped by simplification, MNRF/OTN additions."""
    deg = collections.Counter()
    for ln in lines:
        for v in ln["xy"]:
            deg[v] += 1
    grid = Grid(30.0)
    for li, ln in enumerate(lines):
        xy = ln["xy"]
        for si in range(len(xy) - 1):
            grid.add_seg((li, si), xy[si], xy[si + 1])
    inserts = collections.defaultdict(list)  # li -> [(si, t, point)]
    moved = 0
    for li, ln in enumerate(lines):
        for end in (0, -1):
            p = ln["xy"][end]
            if deg[p] > 1:
                continue
            tol = tol_mnrf if ln["src"] != "osm" else tol_t
            best = None
            for (lj, sj) in grid.near(p[0], p[1], tol):
                if lj == li and (sj == 0 or sj == len(ln["xy"]) - 2):
                    continue
                if lines[lj]["bridge"] != ln["bridge"] and ln["src"] == "osm" and lines[lj]["src"] == "osm":
                    continue
                a, b = lines[lj]["xy"][sj], lines[lj]["xy"][sj + 1]
                d, t, q = seg_dist(p, a, b)
                if d <= tol and (best is None or d < best[0]):
                    best = (d, lj, sj, t, q)
            if best is None:
                continue
            d, lj, sj, t, q = best
            xy_j = lines[lj]["xy"]
            # snap to an existing vertex if close, else insert a new vertex
            if math.hypot(q[0] - xy_j[sj][0], q[1] - xy_j[sj][1]) < 1.0:
                q = xy_j[sj]
            elif math.hypot(q[0] - xy_j[sj + 1][0], q[1] - xy_j[sj + 1][1]) < 1.0:
                q = xy_j[sj + 1]
            else:
                q = (round(q[0] * 2) / 2, round(q[1] * 2) / 2)
                inserts[lj].append((sj, t, q))
            if q != p:
                if end == 0:
                    ln["xy"].insert(0, q)
                else:
                    ln["xy"].append(q)
            deg[q] += 2
            moved += 1
    for lj, ins in inserts.items():
        xy = lines[lj]["xy"]
        ins.sort(key=lambda r: (r[0], r[1]))
        new = []
        k = 0
        for si in range(len(xy)):
            new.append(xy[si])
            while k < len(ins) and ins[k][0] == si:
                if ins[k][2] != new[-1]:
                    new.append(ins[k][2])
                k += 1
        lines[lj]["xy"] = new
    return moved


def build_edges(lines):
    cnt = collections.Counter()
    for ln in lines:
        xy = ln["xy"]
        for i, v in enumerate(xy):
            cnt[v] += 2 if i in (0, len(xy) - 1) else 1
    node_id = {}
    nodes = []

    def nid(v):
        if v not in node_id:
            node_id[v] = len(nodes)
            nodes.append(v)
        return node_id[v]

    edges = []
    for li, ln in enumerate(lines):
        xy = ln["xy"]
        start = 0
        for i in range(1, len(xy)):
            if i == len(xy) - 1 or cnt[xy[i]] > 1:
                pts = xy[start:i + 1]
                if len(pts) >= 2 and not (len(pts) == 2 and pts[0] == pts[1]):
                    L = sum(math.hypot(pts[k + 1][0] - pts[k][0], pts[k + 1][1] - pts[k][1]) for k in range(len(pts) - 1))
                    if L > 0.2:
                        edges.append({"a": nid(pts[0]), "b": nid(pts[-1]), "pts": pts, "len": L, "line": li})
                start = i
    return nodes, edges


def merge_parallel_dupes(edges):
    """Tile buffers can duplicate tiny overlapping bits; drop exact duplicate edges."""
    seen = set()
    out = []
    for e in edges:
        k = (min(e["a"], e["b"]), max(e["a"], e["b"]), round(e["len"]))
        if k in seen:
            continue
        seen.add(k)
        out.append(e)
    return out


def simplify(pts, tol=1.5):
    if len(pts) <= 2:
        return pts
    ls = LineString(pts).simplify(tol, preserve_topology=False)
    c = list(ls.coords)
    c[0], c[-1] = pts[0], pts[-1]
    return [tuple(p) for p in c]


# ---------------------------------------------------------------- polygons / attributes
def load_fc(path):
    with open(path) as f:
        return json.load(f)["features"]


def metric_geom(geom):
    return shapely.transform(geom, lambda a: (a - [LON0, LAT0]) * [KX, KY])


class PolyIndex:
    def __init__(self, feats, label=None):
        self.geoms, self.labels = [], []
        for f in feats:
            if not f.get("geometry"):
                continue
            g = shape(f["geometry"])
            if g.is_empty:
                continue
            g = metric_geom(g.buffer(0) if not g.is_valid else g)
            self.geoms.append(g)
            self.labels.append(label(f["properties"]) if label else None)
        self.tree = STRtree(self.geoms)
        for g in self.geoms:
            shapely.prepare(g)

    def which(self, xs, ys):
        """Return array index of polygon containing each point (or -1)."""
        pts = shapely.points(xs, ys)
        res = [-1] * len(xs)
        idx = self.tree.query(pts, predicate="within")
        for pi, gi in zip(idx[0], idx[1]):
            res[pi] = int(gi)
        return res


def sample_points(pts, fracs=(0.1, 0.3, 0.5, 0.7, 0.9)):
    L = [0.0]
    for k in range(len(pts) - 1):
        L.append(L[-1] + math.hypot(pts[k + 1][0] - pts[k][0], pts[k + 1][1] - pts[k][1]))
    out = []
    for f in fracs:
        d = f * L[-1]
        k = 0
        while k < len(L) - 2 and L[k + 1] < d:
            k += 1
        seg = L[k + 1] - L[k]
        t = 0 if seg == 0 else (d - L[k]) / seg
        out.append((pts[k][0] + t * (pts[k + 1][0] - pts[k][0]), pts[k][1] + t * (pts[k + 1][1] - pts[k][1])))
    return out


def line_feats_metric(feats):
    out = []
    for f in feats:
        g = f.get("geometry")
        if not g:
            continue
        parts = [g["coordinates"]] if g["type"] == "LineString" else g["coordinates"] if g["type"] == "MultiLineString" else []
        for part in parts:
            xy = [to_xy(c[0], c[1]) for c in part]
            if len(xy) >= 2:
                out.append((xy, f["properties"]))
    return out


class LineIndex:
    def __init__(self, items, cell=40.0):
        self.items = items
        self.grid = Grid(cell)
        for i, (xy, _) in enumerate(items):
            for s in range(len(xy) - 1):
                self.grid.add_seg((i, s), xy[s], xy[s + 1])

    def nearest(self, p, r):
        best = None
        for (i, s) in self.grid.near(p[0], p[1], r):
            xy = self.items[i][0]
            d, _, _ = seg_dist(p, xy[s], xy[s + 1])
            if d <= r and (best is None or d < best[0]):
                best = (d, i)
        return best


# ---------------------------------------------------------------- rules
def norm(s):
    return "".join(ch for ch in (s or "").lower() if ch.isalnum())


def load_rules(path):
    r = json.load(open(path))
    by = {}
    for m in r["municipalities"]:
        nm = m["name"].replace("(Township)", "").replace("City of ", "").strip()
        by[norm(nm)] = m
    return r, by


def highway_rule(ref, x, y):
    """Provincial highways per O. Reg. 316/03 (consolidated, last amended O. Reg. 176/22), verified on e-Laws.
    s.2: ORVs may NOT use a provincial highway unless it is listed in Schedule B (or crossing directly).
    Endpoints near Bancroft are taken from OSM junctions (Bay Lake Rd, Hybla Rd, Hwy 118, Hastings St)."""
    lon, lat = to_ll(x, y)
    nums = []
    for part in ref.replace(",", ";").split(";"):
        d = "".join(ch for ch in part if ch.isdigit())
        if d:
            nums.append(int(d))
    if not nums:
        return NO, R_HWY_NO
    ok = any(_hwy_allowed(n, lat, lon) for n in nums)  # concurrent stretches: listed under either number
    return (ROAD, R_HWY_OK) if ok else (NO, R_HWY_NO)


def _hwy_allowed(n, lat, lon):
    if 500 <= n <= 899:                       # Sched. B item 1 (only where still provincial)
        return True
    if n in (118, 127):                       # Sched. B item 3: whole highway
        return True
    if n == 62:
        return (44.511 <= lat < 45.0088       # B26: Hwy 7 (Madoc) to 300 m S of Bay Lake Rd
                or 45.144 <= lat <= 45.232)   # B27: Hybla Rd to Hwy 127 (Maynooth)
    if n == 28:
        if lat < 44.75:                       # south of CR 504 (Apsley): not listed
            return False
        if lon <= -77.9725:                   # B16: CR 504 to Hwy 118 junction west of Bancroft
            return True
        return lon >= -77.844                 # B17: 880 m E of Hastings St to Hwy 41; town core not listed
    if n == 35:
        return lat >= 44.96                   # B18: north of Minden to Hwy 60 (B18.1 south stretch omitted: unverified geometry)
    if n == 41:
        return 44.93 <= lat < 45.50 or lat >= 45.55   # B19 Bon Echo N boundary to Foymount Rd; B20 N of Eganville
    if n == 60:
        return not (-78.93 <= lon <= -78.05)  # A29 Algonquin Park corridor prohibited; B21-B24 elsewhere
    if n == 132:
        return True                           # B46
    return False                              # 7, 17, 37, 115, 121, unlisted 7000-series, 400-series: not listed


# ---------------------------------------------------------------- main
def main():
    data, out = sys.argv[1], sys.argv[2]
    here = os.path.dirname(os.path.abspath(__file__))
    os.makedirs(out, exist_ok=True)
    lio = os.path.join(data, "lio")
    stats = collections.Counter()

    print("reading OSM tiles…", file=sys.stderr)
    lines, pois = read_osm(os.path.join(data, "tiles", "z15.pmtiles"))
    lines = lines_to_metric(lines)
    stats["osm_lines"] = len(lines)

    print("loading LIO…", file=sys.stderr)
    mnrf = line_feats_metric(load_fc(os.path.join(lio, "mnrf_roads.geojson")))
    otn = line_feats_metric(load_fc(os.path.join(lio, "otn_trails.geojson")))
    orn = line_feats_metric(load_fc(os.path.join(lio, "orn_roads.geojson")))

    # OSM-segment index for matching other sources against OSM
    osm_items = [(ln["xy"], i) for i, ln in enumerate(lines)]
    osm_idx = LineIndex(osm_items)

    def coverage(xy, idx, r):
        sp = sample_points(xy, fracs=[k / 10 for k in range(1, 10)])
        hit = sum(1 for p in sp if idx.nearest(p, r))
        return hit / len(sp)

    # Add MNRF forest roads that OSM doesn't have
    added_mnrf = 0
    for xy, p in mnrf:
        if p.get("STATUS") == "Closed" or p.get("ROADBED_DESTROYED_IND") == "Yes":
            continue
        if coverage(xy, osm_idx, 20) >= 0.6:
            stats["mnrf_matched_osm"] += 1
            continue
        lines.append({"xy": [(round(a * 2) / 2, round(b * 2) / 2) for a, b in xy], "kind": K_FOREST,
                      "name": p.get("ROAD_NAME") or "", "ref": "", "bridge": False, "tunnel": False,
                      "access": None, "src": "mnrf"})
        added_mnrf += 1
    stats["mnrf_added"] = added_mnrf

    # Add OTN motorized trails not in OSM
    added_otn = 0
    for xy, p in otn:
        uses = (p.get("PERMITTED_USES") or "")
        if "ATV" not in uses and "Off-Road Motorcycle" not in uses:
            continue
        if coverage(xy, osm_idx, 30) >= 0.5:
            stats["otn_matched_osm"] += 1
            continue
        lines.append({"xy": [(round(a * 2) / 2, round(b * 2) / 2) for a, b in xy], "kind": K_CLUB,
                      "name": p.get("TRAIL_NAME") or "", "ref": "", "bridge": False, "tunnel": False,
                      "access": None, "src": "otn"})
        added_otn += 1
    stats["otn_added"] = added_otn

    print("noding…", file=sys.stderr)
    stats["snapped"] = snap_endpoints(lines)
    nodes, edges = build_edges(lines)
    edges = merge_parallel_dupes(edges)
    stats["nodes"], stats["edges"] = len(nodes), len(edges)

    # keep only the largest connected component plus anything >= 300 m (isolated trails still display)
    parent = list(range(len(nodes)))

    def find(a):
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a

    for e in edges:
        ra, rb = find(e["a"]), find(e["b"])
        if ra != rb:
            parent[ra] = rb
    comp = collections.Counter(find(e["a"]) for e in edges)
    main_comp = comp.most_common(1)[0][0]
    stats["main_component_edges"] = comp[main_comp]

    print("classifying…", file=sys.stderr)
    crown = PolyIndex(load_fc(os.path.join(lio, "crown_unpatented.geojson")))
    # O. Reg. 347/07 s.34: no ORVs in provincial parks. O. Reg. 319/07 s.13: conservation reserves allow ORVs
    # on existing roads and trails unless the manager prohibits it.
    parks = PolyIndex(load_fc(os.path.join(lio, "prov_parks.geojson")),
                      label=lambda p: p.get("COMMON_SHORT_NAME") or p.get("PROTECTED_AREA_NAME_ENG") or "a provincial park")
    reserves = PolyIndex(load_fc(os.path.join(lio, "cons_reserves.geojson")),
                         label=lambda p: p.get("COMMON_SHORT_NAME") or p.get("PROTECTED_AREA_NAME_ENG") or "a conservation reserve")
    munis = PolyIndex(load_fc(os.path.join(lio, "municipalities.geojson")),
                      label=lambda p: (p.get("MUNICIPAL_NAME_SHORTFORM") or p.get("MUNICIPAL_NAME") or "").title())
    rules, rules_by = load_rules(os.path.join(here, "rules", "orv_rules.json"))
    mnrf_idx = LineIndex(mnrf)
    otn_idx = LineIndex(otn)
    otn_motor = [(xy, p) for xy, p in otn if "ATV" in (p.get("PERMITTED_USES") or "") or "Off-Road Motorcycle" in (p.get("PERMITTED_USES") or "")]
    otn_motor_idx = LineIndex(otn_motor)
    orn_idx = LineIndex(orn)
    barriers = [to_xy(*f["geometry"]["coordinates"][:2]) for f in load_fc(os.path.join(lio, "mnr_barriers.geojson"))
                if f.get("geometry") and (f["properties"].get("GATE_IND") == "Yes" or f["properties"].get("BERM_IND") == "Yes"
                                          or f["properties"].get("BOULDER_IND") == "Yes")]
    bar_grid = Grid(50)
    for i, b in enumerate(barriers):
        bar_grid.add_seg(i, b, b)

    mids_x, mids_y = [], []
    samp = []
    for e in edges:
        sp = sample_points(e["pts"], fracs=(0.25, 0.5, 0.75))
        samp.append(sp)
        for p in sp:
            mids_x.append(p[0])
            mids_y.append(p[1])
    crown_hit = crown.which(mids_x, mids_y)
    park_hit = parks.which(mids_x, mids_y)
    reserve_hit = reserves.which(mids_x, mids_y)
    muni_hit = munis.which(mids_x, mids_y)

    strings, string_id = [], {}

    def sid(s):
        if not s:
            return -1
        if s not in string_id:
            string_id[s] = len(strings)
            strings.append(s)
        return string_id[s]

    muni_names = munis.labels
    for ei, e in enumerate(edges):
        ln = lines[e["line"]]
        kind = ln["kind"]
        flags = 0
        src_count = 0
        if ln["src"] == "osm":
            flags |= F_OSM
            src_count += 1
        if ln["bridge"]:
            flags |= F_BRIDGE
        c3 = crown_hit[ei * 3: ei * 3 + 3]
        p3 = park_hit[ei * 3: ei * 3 + 3]
        m3 = muni_hit[ei * 3: ei * 3 + 3]
        on_crown = sum(1 for c in c3 if c >= 0) >= 2
        if on_crown:
            flags |= F_CROWN
        park = next((parks.labels[p] for p in p3 if p >= 0), None) if sum(1 for p in p3 if p >= 0) >= 2 else None
        if park:
            flags |= F_PARK
        r3 = reserve_hit[ei * 3: ei * 3 + 3]
        reserve = reserves.labels[r3[1]] if r3[1] >= 0 else None
        mi = m3[1] if m3[1] >= 0 else next((m for m in m3 if m >= 0), -1)
        muni = muni_names[mi] if mi >= 0 else ""
        mid = samp[ei][1]
        # source matching
        mn = mnrf_idx.nearest(mid, 20)
        mprops = None
        if ln["src"] == "mnrf" or mn:
            flags |= F_MNRF
            src_count += 1
            if mn:
                mprops = mnrf[mn[1]][1]
            else:
                mm = mnrf_idx.nearest(mid, 40)
                mprops = mnrf[mm[1]][1] if mm else None
        om = otn_motor_idx.nearest(mid, 30)
        oa = None if om else otn_idx.nearest(mid, 30)
        oprops = otn_motor[om[1]][1] if om else otn[oa[1]][1] if oa else None
        if not oprops and kind in (K_TRACK, K_PATH) and "Heritage Trail" in (ln["name"] or ""):
            oprops = {"PERMITTED_USES": "ATV", "TRAIL_ASSOCIATION": "Eastern Ontario Trails Alliance"}
        if oprops:
            flags |= F_OTN
            src_count += 1
        if kind in (K_MINOR, K_MAJOR, K_HWY, K_SERVICE, K_FOREST) and orn_idx.nearest(mid, 15):
            src_count += 1
        if any(math.hypot(barriers[b][0] - p[0], barriers[b][1] - p[1]) < 25 for p in samp[ei] for b in bar_grid.near(p[0], p[1], 25)):
            flags |= F_GATE
        if kind in (K_TRACK, K_PATH, K_FOREST, K_CLUB):
            flags |= F_UNPAVED

        cls, reason, note = FREE, R_CROWN, ""
        uses = (oprops or {}).get("PERMITTED_USES") or ""
        assoc = (oprops or {}).get("TRAIL_ASSOCIATION") or ""
        atv_ok = "ATV" in uses
        moto_only = "Off-Road Motorcycle" in uses and not atv_ok
        tname = (oprops or {}).get("TRAIL_NAME") or ""
        # EOTA manages the Trans Canada rail trail across Hastings and Central Frontenac Trailway as pass trails,
        # even where the provincial layer lists only non-motorized uses (verified: EOTA site, 2021 EOTA-OFATV deal).
        eota_pass = bool(oprops) and not atv_ok and ("Eastern Ontario Trails Alliance" in assoc or "Trailway" in tname or
                                                    ("Trans Canada" in tname and to_ll(*mid)[1] < 44.75))
        motor_ok = atv_ok or eota_pass
        osm_name = ln["name"] or ""
        is_ofsc = osm_name.upper().startswith("OFSC") or "snowmobile" in osm_name.lower()
        if park:
            cls, reason, note = NO, R_PARK, park
        elif reserve and kind in (K_TRACK, K_PATH, K_FOREST, K_CLUB):
            cls, reason, note = MAYBE, R_RESERVE, reserve
        elif is_ofsc and kind in (K_TRACK, K_PATH, K_CLUB, K_FOREST):
            cls, reason = NO, R_OFSC
        elif kind in (K_TRACK, K_PATH, K_CLUB) or (kind == K_FOREST and not mprops):
            if oprops and moto_only:
                cls, reason, note = NO, R_DIRTBIKE, tname or assoc or "club"
            elif oprops and motor_ok:
                flags |= F_CLUB
                cls, reason, note = MAYBE, R_CLUB, assoc or "trail club"
            elif oprops and not motor_ok and kind == K_PATH:
                flags |= F_HIKING
                cls, reason, note = NO, R_HIKE, (oprops.get("TRAIL_NAME") or assoc or "trail")
            elif mprops and kind != K_PATH:
                cls, reason, note = classify_forest(mprops, on_crown)
            elif ln.get("access") in ("private", "no"):
                flags |= F_PRIVATE
                cls, reason = NO if ln.get("access") == "no" else MAYBE, R_PRIVATE
            elif kind == K_PATH:
                cls, reason = (MAYBE, R_PATH) if on_crown else (MAYBE, R_UNKNOWN_TRAIL)
                if not on_crown:
                    flags |= F_PRIVATE
            elif on_crown:
                cls, reason = FREE, R_CROWN
            else:
                flags |= F_PRIVATE
                cls, reason = MAYBE, R_UNKNOWN_TRAIL
        elif kind == K_FOREST:
            cls, reason, note = classify_forest(mprops, on_crown)
        elif ln.get("network", "").startswith(("CA:ON:primary", "CA:ON:secondary")) or (kind == K_HWY and not ln.get("network")):
            cls, reason = highway_rule(ln["ref"], mid[0], mid[1])
            if reason == R_HWY_UNK:
                flags |= F_LOWCONF
        else:  # municipal / county roads
            cls, reason, note, low = muni_rule(muni, rules_by)
            if low:
                flags |= F_LOWCONF
        if mprops and mprops.get("STATUS") == "Restricted":
            flags |= F_RESTRICTED
        conf = 2 if src_count >= 2 else 1 if src_count == 1 else 0
        if flags & F_LOWCONF:
            conf = min(conf, 1)
        e.update(cls=cls, kind=kind, flags=flags, reason=reason, note=sid(note), name=sid(ln["name"] or ln["ref"]),
                 muni=mi, conf=conf, main=find(e["a"]) == main_comp)
        stats[f"cls{cls}"] += 1

    # drop isolated scraps (< 300 m and not connected to main network)
    keep = [e for e in edges if e["main"] or e["len"] >= 300]
    stats["edges_kept"] = len(keep)
    used = sorted({e["a"] for e in keep} | {e["b"] for e in keep})
    remap = {old: i for i, old in enumerate(used)}

    # POIs: dedupe + MNR gates
    poi_out = dedupe_pois(pois)
    for b in barriers:
        lon, lat = to_ll(*b)
        poi_out.append({"cat": "gate", "kind": "gate", "name": "Gate / barrier (MNRF)", "lat": round(lat, 6), "lon": round(lon, 6)})
    poi_out = [p for p in poi_out if in_region(p["lat"], p["lon"], 5)]

    write_graph(os.path.join(out, "graph.bin"), [nodes[i] for i in used], keep, remap, strings)
    systems, junctions = trail_systems(keep, nodes)
    stats["trail_systems"] = len({v for v in systems if v > 0})
    stats["junctions"] = len(junctions)
    write_display(os.path.join(out, "trails.geojson"), keep, strings, systems)
    with open(os.path.join(out, "junctions.geojson"), "w") as f:
        for (x, y), kind, deg in junctions:
            lon, lat = to_ll(x, y)
            f.write(json.dumps({"type": "Feature", "geometry": {"type": "Point", "coordinates": [round(lon, 6), round(lat, 6)]},
                                "properties": {"j": kind, "d": deg}}, separators=(",", ":")) + "\n")
    with open(os.path.join(out, "pois.json"), "w") as f:
        json.dump(poi_out, f, separators=(",", ":"))
    meta = {
        "version": 1,
        "region": {"center": [LAT0, LON0], "radius_km": 100},
        "reasons": REASONS, "kinds": KIND_NAMES,
        "classes": ["Free", "Maybe (pass / permission)", "Road: ATVs allowed", "No ATVs"],
        "municipalities": muni_names,
        "muni_rules": {m["name"]: {k: m.get(k) for k in ("roads", "likely", "conditions", "bylaw", "source", "confidence")} for m in rules["municipalities"]},
        "permit_trail_systems": rules.get("permit_trail_systems", []),
        "sources": SOURCES,
        "stats": dict(stats),
        "osm_date": read_osm_date(os.path.join(data, "tiles", "z15.pmtiles")),
        "lio_date": open(os.path.join(lio, "FETCHED_AT")).read().strip() if os.path.exists(os.path.join(lio, "FETCHED_AT")) else "",
        "rules_researched": rules.get("researched", ""),
    }
    with open(os.path.join(out, "meta.json"), "w") as f:
        json.dump(meta, f, separators=(",", ":"))
    print(json.dumps(dict(stats), indent=1))
    print("pois", collections.Counter(p["cat"] for p in poi_out))


SOURCES = [
    {"name": "OpenStreetMap (via Protomaps basemap)", "licence": "ODbL", "use": "roads, tracks, trails, gas, food, POIs"},
    {"name": "Ontario MNRF Road Segments", "licence": "OGL-Ontario", "use": "forest access roads: ownership, status, restrictions"},
    {"name": "Ontario MNR Road Barriers", "licence": "OGL-Ontario", "use": "gates, berms, boulders"},
    {"name": "Crown Land: MNR Unpatented Land", "licence": "OGL-Ontario", "use": "Crown vs private land"},
    {"name": "Provincial Parks & Conservation Reserves", "licence": "OGL-Ontario", "use": "no-ORV zones"},
    {"name": "Ontario Trail Network segments", "licence": "OGL-Ontario", "use": "club / multi-use trails and permitted uses"},
    {"name": "Ontario Road Network", "licence": "OGL-Ontario", "use": "cross-check of public roads"},
    {"name": "Municipal ORV by-laws, O. Reg. 316/03 & 8/03", "licence": "research notes", "use": "where ATVs may ride on roads"},
]


def classify_forest(p, on_crown):
    if p is None:
        return (FREE, R_CROWN, "") if on_crown else (MAYBE, R_UNKNOWN_TRAIL, "")
    detail = p.get("USE_RESTRICTION_DETAIL") or ""
    if p.get("STATUS") == "Closed":
        return NO, R_RESTRICT, detail or "closed"
    if "Ontario Parks" in detail:
        return NO, R_PARK, "a provincial park"
    if p.get("STATUS") == "Restricted" or detail:
        return MAYBE, R_RESTRICT, detail or "restricted use"
    if p.get("OWNERSHIP_CLASS") == "Crown":
        return FREE, R_FOREST, ""
    return MAYBE, R_FOREST_PRIV, ""


def _load_8_03():
    try:
        d = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "rules", "provincial_law_verified.json")))
        x = d["o_reg_8_03_municipalities_in_bbox"]
        names = x.get("fully_or_mostly_in_bbox", []) + x.get("partly_in_bbox_edge", [])
        out = set()
        for n in names:
            out.add(norm(n.split("(")[0]))
            if "(now " in n:
                out.add(norm(n.split("(now ")[1].rstrip(")")))
        return out
    except (OSError, KeyError, ValueError):
        return set()


O_REG_8_03 = _load_8_03()


def muni_rule(muni, rules_by):
    m = rules_by.get(norm(muni))
    if not m:
        if norm(muni) in O_REG_8_03:  # O. Reg. 316/03 s.4.1(2): roads open by default unless a by-law prohibits
            return ROAD, R_DEFAULT, muni, True
        return MAYBE, R_UNKNOWN, muni or "This area", True
    roads, likely, conf = m.get("roads"), m.get("likely"), m.get("confidence")
    if roads == "all":
        return ROAD, R_BYLAW, muni, conf == "low"
    if roads == "some":
        return ROAD, R_SOME, muni, True
    if roads in ("none_owned",):
        return MAYBE, R_UNKNOWN, muni, True
    if roads == "none":
        return NO, R_NONE, muni, conf == "low"
    if likely == "all":
        return ROAD, R_DEFAULT, muni, True
    return MAYBE, R_UNKNOWN, muni, True


def dedupe_pois(pois):
    seen = {}
    for p in pois:
        k = (p["kind"], p["name"], round(p["lat"], 3), round(p["lon"], 3))
        seen.setdefault(k, p)
    out = list(seen.values())
    # drop unnamed common things to keep the map clean
    return [p for p in out if p["name"] or p["kind"] in ("fuel", "viewpoint", "waterfall", "toilets", "picnic_site", "parking", "drinking_water", "shelter")]


def read_osm_date(path):
    with open(path, "rb") as fh:
        rd = Reader(MmapSource(fh))
        md = rd.metadata()
    return md.get("planetiler:osm:osmosisreplicationtime", "")


def write_graph(path, nodes_xy, edges, remap, strings):
    """Binary little-endian. See app/src/main/java/.../data/Graph.kt for the reader."""
    pts_all = []
    erecs = []
    for e in edges:
        pts = simplify(e["pts"])
        first = len(pts_all)
        pts_all.extend(pts)
        erecs.append((remap[e["a"]], remap[e["b"]], first, len(pts), e))
    with open(path, "wb") as f:
        f.write(b"ATVG")
        f.write(struct.pack("<HIIII", 1, len(nodes_xy), len(erecs), len(pts_all), len(strings)))
        for x, y in nodes_xy:
            lon, lat = to_ll(x, y)
            f.write(struct.pack("<ii", int(round(lat * 1e6)), int(round(lon * 1e6))))
        for a, b, first, n, e in erecs:
            f.write(struct.pack("<IIfIHBBHBBhii", a, b, float(e["len"]), first, n, e["cls"], e["kind"], e["flags"],
                                e["reason"], e["conf"], e["muni"], e["name"], e["note"]))
        for x, y in pts_all:
            lon, lat = to_ll(x, y)
            f.write(struct.pack("<ii", int(round(lat * 1e6)), int(round(lon * 1e6))))
        for s in strings:
            b = s.encode("utf-8")[:65535]
            f.write(struct.pack("<H", len(b)))
            f.write(b)
    print("graph.bin", os.path.getsize(path), "bytes", file=sys.stderr)


OFFROAD = (K_TRACK, K_PATH, K_FOREST, K_CLUB)
SYSTEM_COLOURS = 8


def trail_systems(edges, nodes):
    """Group connected off-road trails into 'systems' (what you can ride without touching a road) and colour
    them so neighbouring systems differ. Also find junctions (trail meets trail) and access points (trail meets
    a road you're allowed on): the places where you can hop from one system to another."""
    parent = {}

    def find(a):
        while parent.setdefault(a, a) != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a

    ride = [e for e in edges if e["kind"] in OFFROAD and e["cls"] != NO]
    for e in ride:
        ra, rb = find(e["a"]), find(e["b"])
        if ra != rb:
            parent[ra] = rb
    size, bbox = collections.Counter(), {}
    for e in ride:
        r = find(e["a"])
        size[r] += e["len"]
        xs = [p[0] for p in e["pts"]]; ys = [p[1] for p in e["pts"]]
        b = bbox.get(r, [1e18, 1e18, -1e18, -1e18])
        bbox[r] = [min(b[0], min(xs)), min(b[1], min(ys)), max(b[2], max(xs)), max(b[3], max(ys))]
    colour = {}
    big = [r for r, L in size.most_common() if L >= 2000]
    for r in big:  # greedy colouring: avoid colours of systems within 3 km
        b = bbox[r]
        used = {colour[o] for o in colour
                if not (bbox[o][0] > b[2] + 3000 or bbox[o][2] < b[0] - 3000 or bbox[o][1] > b[3] + 3000 or bbox[o][3] < b[1] - 3000)}
        colour[r] = next((c for c in range(1, SYSTEM_COLOURS + 1) if c not in used), 1 + len(colour) % SYSTEM_COLOURS)
    systems = [colour.get(find(e["a"]), 0) if (e["kind"] in OFFROAD and e["cls"] != NO) else 0 for e in edges]
    # junctions & access points
    inc = collections.defaultdict(list)
    for e in edges:
        inc[e["a"]].append(e); inc[e["b"]].append(e)
    junctions = []
    for n, es in inc.items():
        trail = [e for e in es if e["kind"] in OFFROAD and e["cls"] != NO]
        road = [e for e in es if e["kind"] not in OFFROAD and e["cls"] == ROAD]
        if len(trail) >= 3 or (len(trail) >= 2 and len(es) >= 3 and not road):
            junctions.append((nodes[n], "j", len(es)))
        elif trail and road:
            junctions.append((nodes[n], "a", len(es)))
    return systems, junctions


def write_display(path, edges, strings, systems=None):
    """Line-delimited GeoJSON for tippecanoe; per-zoom visibility is set by tile_filter.json."""
    with open(path, "w") as f:
        for i, e in enumerate(edges):
            if e["kind"] == K_SERVICE and e["cls"] != FREE:
                continue  # driveways/parking aisles: routable but not drawn
            coords = []
            for x, y in simplify(e["pts"], 1.0):
                lon, lat = to_ll(x, y)
                coords.append([round(lon, 6), round(lat, 6)])
            props = {"c": e["cls"], "k": e["kind"], "e": i, "r": e["reason"], "q": e["conf"], "f": e["flags"]}
            if systems and systems[i]:
                props["s"] = systems[i]
            if e["name"] >= 0:
                props["n"] = strings[e["name"]]
            if e["note"] >= 0:
                props["t"] = strings[e["note"]]
            f.write(json.dumps({"type": "Feature",
                                "geometry": {"type": "LineString", "coordinates": coords}, "properties": props},
                               separators=(",", ":")) + "\n")


if __name__ == "__main__":
    main()
