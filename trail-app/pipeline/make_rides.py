"""Turn researched recommended routes (facts + place coordinates only, no copied GPX) into assets/rides.json.
The app draws each ride itself with its own router through the listed points.
Usage: python3 make_rides.py <out_assets_dir>"""
import json, os, sys
sys.path.insert(0, os.path.dirname(__file__))
from region import in_region

src = json.load(open(os.path.join(os.path.dirname(__file__), "rides", "recommended_routes.json")))


def pt(p):
    if not p or p.get("lat") is None or p.get("lon") is None or not in_region(p["lat"], p["lon"], 0):
        return None
    return {"name": p.get("name", "").split(" (")[0], "lat": round(p["lat"], 5), "lon": round(p["lon"], 5)}


def kind(t):
    t = (t or "").lower()
    return "loop" if t.startswith("loop") else "out_back" if "out-and-back" in t else "oneway"


rides = []
for r in src["routes"]:
    start = r.get("staging") if isinstance(r.get("staging"), dict) else None
    pts = [p for p in [pt(start)] + [pt(w) for w in r.get("waypoints") or []] if p]
    if not pts:
        continue
    rides.append({
        "id": r.get("id") or r["name"],
        "name": r["name"].split(" (")[0],
        "type": kind(r.get("type")),
        "km": r.get("length_km"),
        "time": r.get("typical_time") or r.get("duration"),
        "difficulty": r.get("difficulty"),
        "season": r.get("season"),
        "pass": r.get("pass_required"),
        "highlights": " · ".join(r["highlights"]) if isinstance(r.get("highlights"), list) else r.get("highlights"),
        "sources": (r.get("sources") or [])[:3],
        "points": pts,
        "confidence": r.get("confidence"),
    })
places = [{"name": d["name"].split(" (")[0], "lat": d["lat"], "lon": d["lon"], "what": d.get("what"), "note": d.get("note"),
           "sources": (d.get("sources") or [])[:2]}
          for d in src["destinations"] if d.get("lat") is not None and in_region(d["lat"], d["lon"], 0)]
out = os.path.join(sys.argv[1], "rides.json")
json.dump({"rides": rides, "places": places}, open(out, "w"), separators=(",", ":"))
print(len(rides), "rides,", len(places), "places ->", out)
