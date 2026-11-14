# Research verification (v2)

Every rule the app uses was re-checked against primary sources where they were reachable, plus at least one
independent source. Full machine-readable results: `pipeline/rules/provincial_law_verified.json`,
`pipeline/rules/orv_rules.json` (townships, with `sources` + `verified_primary` per entry), `pipeline/rules/trails_verified.json`.

## Provincial law (read on e-Laws, ontario.ca/laws)
- **O. Reg. 316/03 s.2**: ORVs may not use a provincial highway unless it's listed in **Schedule B** (or crossing directly).
  v1 had this backwards. Corrected near Bancroft:
  - Hwy 62: open Madoc → 300 m S of Bay Lake Rd (B26) and Hybla Rd → Hwy 127 (B27); **closed through Bancroft**.
  - Hwy 28: open Apsley (CR 504) → Hwy 118 jct (B16) and 880 m E of Hastings St → Hwy 41 (B17); **closed in town core**.
  - Hwy 118, 127 open in full (B3); Hwy 41, 60 (outside Algonquin), 132 open in listed stretches; Hwy 7, 37, 121, 17 closed.
  - Conditions (Part III): shoulder, with traffic, lights on at all times, 20 km/h where posted ≤50 else 50 km/h,
    plate/insurance/G2-M2+ licence, helmet, no passenger on a single-rider ATV.
- **s.4.1(2) + O. Reg. 8/03**: in listed rural municipalities ORVs may use municipal roads unless a by-law prohibits it.
  Every township around Bancroft is listed. **The Town of Bancroft is not**, so it relies on its own by-law (49-2015: all town roads).
- **Parks**: O. Reg. 347/07 s.34, no ORVs in provincial parks. **Conservation reserves**: O. Reg. 319/07 s.13, ORVs allowed on
  existing roads/trails unless prohibited (v1 wrongly marked them "no").
- **Crown land roads**: O. Reg. 316/03 s.5, allowed unless the road authority prohibits; closures under Public Lands Act s.28/s.52.

## Township by-laws (full text read for 7, the rest from council minutes / news, all with source links)
High confidence: Bancroft 49-2015, Faraday 25-2015, Wollaston 05-2021, Tudor & Cashel 2015-22, Carlow/Mayo 21-2015 (all roads).
Default-open (8/03, no prohibiting by-law found): Hastings Highlands, Limerick, Madoc Twp, Centre Hastings, and others (marked low/medium).

## Trails (24 named routes, 2–3 sources each: EOTA site, OFSC, Ontario Trail Network data, tourism & news)
- Agree: Hastings Heritage Trail (EOTA/OFATV pass), EOTA E-trails & loops, Park-To-Park, Algonquin Trail (May 1–Nov 30, permit),
  K&P, Coe Hill Siderail, Weslemkoon Lake trail, Old Hastings Road (road-legal, no pass).
- Fixed: Trans Canada Trail across Hastings → *maybe* (EOTA pass trail); Bytown motorcycle trails → *no* (dirt-bike only);
  OFSC snowmobile trails → *no* (OFSC: "Do not ride an ATV on any OFSC trail at any time of year").
- EOTA 2026 ATV pass: $150/season, $75/week, $25/day. OFATV permit honoured on Hastings Heritage Trail + Trans Canada rail trail across Hastings.
- "Eco loop": no ATV route by that name in any source. Closest: EOTA's Tweed–Flinton–Gilmour loop; the OFSC "Bon Echo Loop" is snowmobile-only.

## Still uncertain (shown as lower confidence in the app)
Several township by-law texts couldn't be opened (Highlands East, Algonquin Highlands, South Algonquin, Greater Madawaska);
Peterborough & Renfrew county road schedules conflict between sources; OSM `atv=*`/`snowmobile=*` tags weren't
available (OSM servers refused connections from the build machine).
