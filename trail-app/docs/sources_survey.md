# ATV/ORV map-source survey: Bancroft, Ontario (bbox lat 44.16–45.96, lon -79.13 to -76.58)

Compiled 2026-11-14. Scope: map sources and named routes only (municipal road ORV by-laws are covered by a separate agent).

## Method and limits (read first)

- **Direct page fetching was blocked in this session.** The network proxy refused northernontario.travel, discoverbancroft.com, thetrail.ca, overpass-api.de and download.geofabrik.de. Every finding below comes from **web-search result snippets and summaries**, not from reading full pages, maps or datasets.
- Because of that, nothing here was checked against geometry. No OSM/Overpass query was run, LIO layers were not downloaded, and EOTA, QuadON and OFSC maps were not opened.
- Treat "confidence" as the level of agreement between sources, not as ground truth. Permit prices and seasonal rules change, so verify them before use.
- To finish the job, allow these hosts in the session network settings: overpass-api.de (or download.geofabrik.de), geohub.lio.gov.on.ca / ws.lioservices.lrc.gov.on.ca, www.thetrail.ca, ofatv.org, chatvc.ca.

## Key takeaways

1. **There is almost no ATV trail that is both named and free near Bancroft.** The main network (EOTA Hastings Heritage Trail, Coe Hill Riders/OFATV, HATVA, RCATV, KATVA club trails) all needs a pass. In the app, these should be flagged **"maybe"**.
2. **Likely free riding:**
   - Crown-land forest access roads, e.g. the Weslemkoon Lake area east of Bancroft. Crown land outside parks and conservation reserves needs no trail permit, but registration, plate, insurance and helmet are still required.
   - Unpaved municipal roads where a by-law allows ATVs, for example Old Hastings Road and Limerick Township roads. The other agent's by-law research applies here.
   - KATVA notes that its "Crown land only" trails need no KATVA permit.
3. **"Eco loop" / "Echo loop":** No ATV route by either name was found anywhere. The best match is the **OFSC "Bon Echo Loop"**, about 232 km. It is a **snowmobile-only winter tour** staged from Bancroft and Gilmour (Hwy 62, near Old Hastings Road), maintained by the Old Hastings Snow Riders and Mazinaw Powerline SC. Under the OFSC code of conduct, ATVs are **prohibited on OFSC trails at any time of year**. Other possibilities:
   - a mishearing of "EOTA loop"
   - the EOTA "Hastings loop" mentioned in a Northern Ontario Travel article
   
   Ask the rider which they mean. Do not map the Bon Echo Loop as ATV-OK.
4. **Licensing:** Only OSM (ODbL), the Ontario LIO datasets (OGL-Ontario) and NRCan CanVec/NRN (OGL-Canada) can legally feed app data. Every club, tourism and commercial source (EOTA, QuadON, OFSC ITG, Trailforks, onX, Gaia, AllTrails, Backroad Mapbooks, Trak Maps, RidePal) is **for verification only**.

---

## JSON

```json
{
  "generated": "2026-11-14",
  "method_caveat": "WebSearch snippets only; WebFetch/curl blocked by session network policy for all target sites. No geometry verified.",
  "sources": [
    {
      "id": "osm",
      "name": "OpenStreetMap",
      "url": "https://www.openstreetmap.org/relation/7319029",
      "coverage": "Hastings Heritage Trail mapped as multiple ways (e.g. way 75369488, 498425014, 1125492301) grouped in relation 7319029; tagged highway=path, surface=gravel, bicycle=designated, railway=abandoned; some geometry from CanVec 6.0. Old Hastings Road present as a road; forest roads likely partly from CanVec/ORN imports. ATV tagging (atv=*, motor_vehicle=*) on these ways NOT verified.",
      "currency": "Continuously edited; Bancroft railway features edited recently per way history snippet",
      "atv_permit_status_shown": "Only if tagged (atv=yes/permit/no); usually absent. Relation membership/tags not checked.",
      "licence": "ODbL 1.0 - usable in app with attribution; share-alike applies to derived databases",
      "app_use": "USE (primary geometry base)",
      "access": "Overpass API / Geofabrik ontario-latest.osm.pbf extract / planet; offline-friendly",
      "confidence_in_assessment": "medium"
    },
    {
      "id": "lio_otn",
      "name": "Ontario Trail Network (OTN) Segment + Access Point (LIO)",
      "url": "https://data2.ontario.ca/dataset/ontario-trail-network",
      "rest": "https://ws.lioservices.lrc.gov.on.ca/arcgis2/rest/services/LIO_OPEN_DATA/LIO_Open04/MapServer/19",
      "coverage": "Province-wide trail segments with TRAIL_NAME, TRAIL_ASSOCIATION, PERMITTED_USES (free text, 250 chars), length. Likely includes Hastings Heritage Trail/EOTA; values for ATV not verified.",
      "currency": "Segments last updated 2021-07-12; dataset validated 2023-07-10 - STALE for permissions",
      "atv_permit_status_shown": "PERMITTED_USES may list ATV; no permit/pass field",
      "licence": "Open Government Licence - Ontario (attribution)",
      "app_use": "USE (cross-check/attribute enrichment), but permissions may be outdated",
      "access": "Download (GeoHub/LIO Warehouse shapefile/FGDB), ArcGIS REST",
      "confidence_in_assessment": "medium-high"
    },
    {
      "id": "lio_trail_segments",
      "name": "MNRF Trail Segment (separate LIO dataset)",
      "url": "https://data2.ontario.ca/dataset/trail-segments",
      "coverage": "MNRF-held trail lines incl. Crown land trails; data description PDF at publicdocs.mnr.gov.on.ca",
      "currency": "Unknown (not checked)",
      "atv_permit_status_shown": "Unknown",
      "licence": "OGL-Ontario",
      "app_use": "USE",
      "access": "Download / REST",
      "confidence_in_assessment": "low-medium"
    },
    {
      "id": "lio_mnrf_roads",
      "name": "MNRF Road Segments (resource/forest access roads)",
      "url": "https://data2.ontario.ca/dataset/ministry-of-natural-resources-and-forestry-road-segments",
      "coverage": "Resource access roads under MNRF jurisdiction plus some ORN municipal/provincial roads; dense in Bancroft-Minden Forest, Weslemkoon area",
      "currency": "Last validated 2025-10-20 (one CKAN mirror says 2015 - check GeoHub)",
      "atv_permit_status_shown": "No. Road use-management (closures) not in this layer; set in Forest Management Plan operational planning. 2026 MNR action plan says Bancroft Minden FMP access planning was not done as required.",
      "licence": "OGL-Ontario",
      "app_use": "USE (best source for 'free' Crown-land riding candidates)",
      "access": "Download (GeoHub), ArcGIS REST (LIO_Open09 layer 18 per snippet)",
      "confidence_in_assessment": "medium-high"
    },
    {
      "id": "lio_clupa",
      "name": "Crown Land Use Policy Atlas (CLUPA) + Provincial Park / Conservation Reserve regulated boundaries",
      "url": "https://www.ontario.ca/page/crown-land-use-policy-atlas",
      "data": "https://data2.ontario.ca/dataset/crown-land-use-policy-atlas",
      "coverage": "Land-use designations (G, E, P, C, R, W); policy reports per area. Parks (e.g. Egan Chutes, Silent Lake, Bon Echo, Lake St. Peter) and conservation reserves = ORV generally prohibited.",
      "currency": "Maintained by MNR; not checked",
      "atv_permit_status_shown": "Policy text per area (not a line layer); use as exclusion mask",
      "licence": "OGL-Ontario",
      "app_use": "USE as no-go/caution polygons",
      "access": "Download polygons; web atlas",
      "confidence_in_assessment": "high"
    },
    {
      "id": "orn_canvec",
      "name": "Ontario Road Network (ORN) / NRCan CanVec & National Road Network",
      "url": "https://data2.ontario.ca/dataset/ontario-road-network-orn-composite",
      "coverage": "All public roads incl. unpaved/seasonal; road class, surface",
      "currency": "ORN updated regularly",
      "atv_permit_status_shown": "No (combine with municipal by-laws from other agent)",
      "licence": "OGL-Ontario / OGL-Canada",
      "app_use": "USE",
      "access": "Download",
      "confidence_in_assessment": "high"
    },
    {
      "id": "eota",
      "name": "Eastern Ontario Trails Alliance (thetrail.ca) interactive map + printed map + 4 self-guided tours",
      "url": "https://www.thetrail.ca/passes/",
      "coverage": "Hastings Heritage Trail (Glen Ross -> Lake St. Peter, ~156 km) and ~700-2,700 km network (figures vary by article); loops such as Tweed-Gunter, Centre Hastings Heritage Loop; 'Hastings loop'",
      "currency": "Live site; one article warns the EOTA map shows some trails NOT covered by its pass",
      "atv_permit_status_shown": "Yes - pass required (OnePass/Gold). Prices seen: $150/yr, $75/wk, $20/day (Town of Bancroft page, undated); another snippet $25/day",
      "licence": "Proprietary/copyright EOTA; no open licence found",
      "app_use": "VERIFY ONLY (ask EOTA for data-sharing permission)",
      "access": "Web map / print map (free by mail); no GPX/KML download found",
      "confidence_in_assessment": "medium"
    },
    {
      "id": "ofatv_quadon",
      "name": "OFATV QuadON app (Mapgears)",
      "url": "https://apps.apple.com/ca/app/quadon/id1549704696",
      "coverage": "Only OFATV member-club trails (~10,000 km, 24 clubs). Includes Coe Hill Riders (600+ km North Hastings/North Kawartha) and RCATV; Hastings County (EOTA) trails accessible to OFATV permit holders under 2021 EOTA-OFATV MOU.",
      "currency": "Live; club-maintained",
      "atv_permit_status_shown": "Yes (all trails require QuadON permit, ~$195/yr, $175 early renewal)",
      "licence": "Proprietary",
      "app_use": "VERIFY ONLY",
      "access": "App only (offline in-app); OFATV Garmin maps sold separately",
      "confidence_in_assessment": "medium-high"
    },
    {
      "id": "coe_hill_riders",
      "name": "Coe Hill Riders ATV Club (chatvc.ca, Facebook)",
      "url": "https://chatvc.ca/",
      "coverage": "600+ km in North Hastings / North Kawartha; maintains Wollaston Twp rail trail (Wollaston Lake Rd <-> Old Hastings Rd) under 2019 OFATV land-use agreement (renewal under review Mar 2024); Nephton Mines Loop charity ride",
      "currency": "Active 2024-2026",
      "atv_permit_status_shown": "Yes - OFATV/QuadON permit; OPP recommended 'permit required' signage on Wollaston rail trail",
      "licence": "Proprietary",
      "app_use": "VERIFY ONLY",
      "access": "Via QuadON app; site maps not inspected",
      "confidence_in_assessment": "medium"
    },
    {
      "id": "hatva",
      "name": "Haliburton ATV Association (HATVA) map books / app",
      "url": "https://www.destinationontario.com/en-ca/things-to-do/outdoor-adventures/atving/atv-trails-ontario",
      "coverage": "1,700-3,000 km (sources differ) Haliburton County; connects to Kawartha, Muskoka, Madawaska; tour operators say routes reach east toward Bancroft",
      "currency": "Unknown",
      "atv_permit_status_shown": "Yes - HATVA pass (Destination Ontario also lists ATVOntario Powerpass/EOTA reciprocity - unverified)",
      "licence": "Proprietary",
      "app_use": "VERIFY ONLY",
      "access": "Paid map book; app",
      "confidence_in_assessment": "low-medium (Bancroft connection unconfirmed)"
    },
    {
      "id": "rcatv_madawaska",
      "name": "Renfrew County ATV Club (Madawaska Valley / Barry's Bay / Opeongo / Settler's Route)",
      "url": "https://www.countyofrenfrew.on.ca/news/posts/trail-permits-required-for-atv-and-off-road-motorcycle-riders-on-renfrew-county-recreational-trails/",
      "coverage": "400-600+ km; Barry's Bay hub; Opeongo system (Wilno-Madawaska); Settler's Route 106 km (endpoints differ by source); Combermere-Craigmount ~20 km",
      "currency": "Active",
      "atv_permit_status_shown": "Yes - OFATV permit via getapermit.ofatv.org choosing RCATV (day ~$25); county rail trails ATV season May 1-Nov 30",
      "licence": "Proprietary",
      "app_use": "VERIFY ONLY",
      "access": "QuadON; club site",
      "confidence_in_assessment": "medium"
    },
    {
      "id": "katva",
      "name": "Kawartha ATV Association (KATVA)",
      "url": "https://lindsaydistrictchamberofcommerce.wildapricot.org/resources/Documents/KATVA-MAP-2021.pdf",
      "coverage": "SW corner of bbox (Kinmount/Victoria Rail Trail; second system); PDF map 2021",
      "currency": "2021 map",
      "atv_permit_status_shown": "Yes; older article says permits required on all KATVA trails EXCEPT Crown-land-only trails",
      "licence": "Proprietary",
      "app_use": "VERIFY ONLY",
      "access": "PDF image map",
      "confidence_in_assessment": "low-medium"
    },
    {
      "id": "tourism_bancroft_hastings",
      "name": "Town of Bancroft trails pages / Discover Bancroft (discoverbancroft.com/atv) / Hastings County tourism (hastings.ca) / Hastings Highlands",
      "url": "https://bancroft.hosted.civiclive.com/visiting/trails/hastings_heritage_trail",
      "coverage": "Hastings Heritage Trail descriptions, pass fees ($150/$75/$20 ATV), Coe Hill ATVing & snowmobiling; Discover Bancroft ATV page not readable (fetch blocked)",
      "currency": "Undated",
      "atv_permit_status_shown": "Yes for HHT",
      "licence": "Copyright (municipal/tourism); no open licence",
      "app_use": "VERIFY ONLY",
      "access": "Web pages / possibly PDF maps",
      "confidence_in_assessment": "medium"
    },
    {
      "id": "hastings_county_gis",
      "name": "Hastings County ArcGIS item 'Hastings Heritage Trail - Overview'",
      "url": "https://www.arcgis.com/home/item.html?id=9a2e8d24111f4c5b85d9b6dab8322e70&sublayer=0",
      "coverage": "Hastings Heritage Trail geometry (county-owned trail)",
      "currency": "Unknown; county signing extended access agreement w/ Imperial Oil & EOTA (Oct 2025 agenda)",
      "atv_permit_status_shown": "Unknown",
      "licence": "Unknown - check item terms; may be open",
      "app_use": "POSSIBLY USE if item licence permits; else verify",
      "access": "ArcGIS Online item (maybe FeatureServer export)",
      "confidence_in_assessment": "low"
    },
    {
      "id": "northern_ontario_travel",
      "name": "Northern Ontario Travel / Destination Ontario / Come Wander route guides",
      "url": "https://northernontario.travel/atving/the-old-hastings-road-atv-trail",
      "coverage": "Old Hastings Road ride (Bancroft->Marmora, ~45 km of old road from Hwy 62 south of Bancroft to Hwy 62 near Millbridge; Ormsby, Glanmire); Bancroft 'world-class riding'; 'Which permit do I need'; Bon Echo Loop (snowmobile); Limerick Lake Lodge (HHT km 104)",
      "currency": "Articles mostly 2015-2023, often undated",
      "atv_permit_status_shown": "Narrative only; Destination Ontario disclaims 'not all trails listed are approved ATV trails'",
      "licence": "Copyright editorial",
      "app_use": "VERIFY ONLY (route ideas)",
      "access": "Articles, no GPX",
      "confidence_in_assessment": "medium"
    },
    {
      "id": "ofsc_itg",
      "name": "OFSC Interactive Trail Guide / Go Snowmobiling app (Old Hastings Snow Riders, Mazinaw Powerline SC; District 2)",
      "url": "https://www.ofsc.on.ca/trail-maps/",
      "coverage": "Old Hastings Snow Riders ~350 km (Bancroft, McArthur Mills, Coe Hill, Gilmour); Bon Echo Loop ~232 km (TOP E101, 1009/1009a, TOP E, B106E, E106, 961, 962)",
      "currency": "Seasonal; offline in summer",
      "atv_permit_status_shown": "Snowmobile only. OFSC code: 'Do not ride an ATV on any OFSC trail at any time of year'; off-season use = trespass",
      "licence": "Proprietary",
      "app_use": "USE ONLY AS NEGATIVE/CAUTION reference (do not redistribute geometry)",
      "access": "Web/app only",
      "confidence_in_assessment": "high (ATV prohibition)"
    },
    {
      "id": "backroad_mapbooks",
      "name": "Backroad Mapbooks (Cottage Country Ontario 7th ed., Eastern Ontario, Bancroft District Crown Land Recreation Map E/W, BRMB GPS maps, BRMB ATV overlay in Gaia)",
      "url": "https://backroadmapbooks.com/blogs/default-blog/cottage-country-off-road-thrills-atv-trails-and-overland-routes-for-adventure-seekers",
      "coverage": "Motorized trails, Crown land, forest roads; Weslemkoon Lake loop (~120 km) + Merrill Lake trails; topo pages CCON55 Bancroft, CCON56 Weslemkoon; 60 ATV listings in Cottage Country",
      "currency": "Bancroft District map 2013; mapbook 7th ed. (recent); GPS maps updated periodically",
      "atv_permit_status_shown": "Partially (classifies motorized trails; not permit status reliably)",
      "licence": "Proprietary (paid)",
      "app_use": "VERIFY ONLY",
      "access": "Paid print/PDF/Avenza/Garmin/Gaia overlay",
      "confidence_in_assessment": "medium"
    },
    {
      "id": "trailforks",
      "name": "Trailforks",
      "url": "https://www.trailforks.com/region/bancroft/",
      "coverage": "Bancroft region: 'No trails have been added' (2 moto entries); Eastern region lists 482 ATV/ORV trails overall",
      "currency": "Crowd-sourced",
      "atv_permit_status_shown": "Sometimes (per-trail notes)",
      "licence": "Proprietary ToS (GPX downloads for personal use with login)",
      "app_use": "VERIFY ONLY (sparse here)",
      "access": "Web/app; GPX/KML with login",
      "confidence_in_assessment": "medium"
    },
    {
      "id": "onx",
      "name": "onX Offroad",
      "url": "https://www.onxmaps.com/offroad/trails/ca/ontario/lingham-hydro-line-ndzdxv9rd6kv",
      "coverage": "Patchy in Ontario. Found: Lingham Hydro Line (~19.3 mi, Hastings County hydro corridor between Hwy 62 and 41), Scotch Line (Minden). No Bancroft/Coe Hill listings found.",
      "currency": "Unknown",
      "atv_permit_status_shown": "Not reliably; hydro corridor legality not stated",
      "licence": "Proprietary",
      "app_use": "VERIFY ONLY",
      "access": "Subscription app",
      "confidence_in_assessment": "low-medium"
    },
    {
      "id": "gaia",
      "name": "Gaia GPS",
      "url": "https://backroadmapbooks.com/blogs/app/trails-overlay-for-gaia-gps",
      "coverage": "BRMB ATV trails overlay (premium) - Canada-wide 69,000 mi; no OFATV/QuadON layer found",
      "currency": "Per BRMB updates",
      "atv_permit_status_shown": "No",
      "licence": "Proprietary",
      "app_use": "VERIFY ONLY",
      "access": "Subscription",
      "confidence_in_assessment": "medium"
    },
    {
      "id": "alltrails",
      "name": "AllTrails",
      "url": "https://www.alltrails.com/trail/canada/ontario/hastings-heritage-trail",
      "coverage": "Hastings Heritage Trail (incl. Lake St. Peter-Maynooth segment) listed for hiking/biking; no OHV listing for Old Hastings Road found",
      "currency": "Crowd",
      "atv_permit_status_shown": "No",
      "licence": "Proprietary",
      "app_use": "VERIFY ONLY (low value for ATV)",
      "access": "App/web",
      "confidence_in_assessment": "medium"
    },
    {
      "id": "ridepal",
      "name": "RidePal",
      "url": "https://www.ridepal.app/trails/canada/ontario/tudor-and-cashel/old-hastings-road",
      "coverage": "MTB-oriented, probably OSM-derived: Old Hastings Road 'unmaintained section' 0.2 mi; Hastings Heritage Trail entries with inconsistent lengths",
      "currency": "Unknown",
      "atv_permit_status_shown": "No",
      "licence": "Proprietary (underlying likely ODbL - unconfirmed)",
      "app_use": "IGNORE",
      "access": "Web",
      "confidence_in_assessment": "low"
    },
    {
      "id": "trakmaps",
      "name": "Trak Maps (ATV Ontario Maps) - OTC partner",
      "url": "https://apps.apple.com/us/app/trakmaps-ohv/id1482393205",
      "coverage": "Claims 300,000 mi OHV trails; EOTA/Hastings coverage unconfirmed",
      "currency": "Unknown",
      "atv_permit_status_shown": "Unknown",
      "licence": "Proprietary",
      "app_use": "VERIFY ONLY",
      "access": "App with offline zones",
      "confidence_in_assessment": "low"
    },
    {
      "id": "ontario_trails_council",
      "name": "Ontario Trails Council (ontariotrails.on.ca) listings",
      "url": "https://www.ontariotrails.on.ca/trail/hastings-heritage-trail-bancroft",
      "coverage": "Listings: Hastings Heritage Trail (156 km, novice, OnePass EOTA/OFATV), Coe Hill ATV Club Trails, North Hastings Scenic Routes (#8 Weslemkoon = car/motorcycle/cycle scenic drive, NOT an ATV trail), IB&O Rail Trail (listed as ATV, 14 km)",
      "currency": "Mixed",
      "atv_permit_status_shown": "Sometimes",
      "licence": "Copyright; OTC says it doesn't own/manage trails",
      "app_use": "VERIFY ONLY",
      "access": "Web",
      "confidence_in_assessment": "medium"
    },
    {
      "id": "ride_the_highlands",
      "name": "Ride the Highlands (Ontario's Highlands Tourism) ADV loops - Timber Trail Loop (446 km), Gravel Crusher (~700 km)",
      "url": "https://northernontario.travel/motorcycle-touring/ride-highlands-timber-trail-loop-advs",
      "coverage": "Dual-sport/ADV motorcycle routes on public gravel roads through Haliburton/Hastings; not ATV trails",
      "currency": "2023-2025",
      "atv_permit_status_shown": "No (road-legal motorcycles); ATV legality depends on by-laws",
      "licence": "Copyright; GPX availability not confirmed",
      "app_use": "VERIFY ONLY",
      "access": "Web/GPX (unconfirmed)",
      "confidence_in_assessment": "low-medium"
    },
    {
      "id": "ontario_trails_project_gps",
      "name": "Ontario Trails Project (GPSFileDepot volunteer Garmin map)",
      "url": "https://gpsfiledepot.com/maps/view/211",
      "coverage": "Volunteer trail overlay (Garmin/GPX/Shapefile); provenance unknown",
      "currency": "Unknown",
      "atv_permit_status_shown": "Unknown",
      "licence": "Unknown",
      "app_use": "VERIFY ONLY until licence known",
      "access": "Download",
      "confidence_in_assessment": "low"
    }
  ],
  "routes": [
    {
      "name": "Hastings Heritage Trail (former Central Ontario Railway)",
      "location": "Glen Ross (Trent River) north through Marmora, Ormsby, Bancroft, Maynooth to Lake St. Peter; ~156 km (one old listing says 64 km/COLT section)",
      "permit": "pass_required",
      "permit_detail": "EOTA OnePass/Gold ($150/yr, $75/wk, $20-25/day - undated) OR OFATV/QuadON permit under 2021 EOTA-OFATV MOU (Hastings County trails only). Winter: unclear - Limerick Lake page says 'for 2025/26 wheeled vehicle trail use prohibited until end of snowmobile season'; trail also carries OFSC TOP E106/B106E in winter.",
      "app_flag": "maybe",
      "sources": ["osm", "lio_otn(probable)", "eota", "ofatv_quadon", "tourism_bancroft_hastings", "hastings_county_gis", "northern_ontario_travel", "ontario_trails_council", "alltrails", "ridepal", "ofsc_itg(winter)"],
      "confidence": "high (existence & ATV use with pass); medium (winter rules)"
    },
    {
      "name": "Old Hastings Road (Hastings Colonization Road)",
      "location": "Leaves Hwy 62 just south of Bancroft, south ~45 km via Ormsby (meets Hwy 620 + HHT), Glanmire ghost town, to Hwy 62 near Millbridge; continues toward Marmora via HHT",
      "permit": "free_or_bylaw_dependent",
      "permit_detail": "It is a (partly seasonal/unmaintained) municipal road in Tudor & Cashel / Wollaston / Limerick etc. - no trail pass found as required; legality governed by municipal ORV by-laws (other agent). Tudor & Cashel by-law 2023-34 refuses opening unopened road allowances; side lanes are unassumed/private. Conflicting reports on Glanmire-Murphy Corners condition (older: seasonal/impassable; newer: well maintained).",
      "app_flag": "free? (pending by-law check)",
      "sources": ["northern_ontario_travel", "osm", "orn_canvec", "ridepal", "ofsc_itg(adjacent Gilmour staging)"],
      "confidence": "high (route exists, ridden by ATVs); medium (no-permit status)"
    },
    {
      "name": "Bon Echo Loop (OFSC snowmobile tour) - probable meaning of 'Echo/eco loop'",
      "location": "~232 km loop SE of Bancroft: Bancroft - Gilmour (Hwy 62) - Cloyne (Hwy 41) - Mazinaw/Bon Echo - McArthur Mills - Bancroft; TOP E101, 1009/1009a, TOP E, B106E, E106, 961, 962",
      "permit": "not_atv",
      "permit_detail": "Snowmobile only, OFSC permit, winter. ATVs prohibited on OFSC trails year-round (private-land permission is snowmobile/winter only). Parts overlap HHT (E106/B106E) where EOTA ATV rules apply separately in non-winter.",
      "app_flag": "exclude (or show as snowmobile-only caution)",
      "sources": ["ofsc_itg", "northern_ontario_travel", "destinationontario"],
      "confidence": "high (snowmobile loop); low (that this is what rider means)"
    },
    {
      "name": "Weslemkoon Lake Loop + Merrill Lake trails",
      "location": "East of Bancroft (Addington Highlands/Hastings), ~120 km of trails looping Weslemkoon Lake on forest access roads; staging off Mississippi Rd or near end of Weslemkoon Lake Rd; spur to Merrill Lake",
      "permit": "probably_free",
      "permit_detail": "Crown land / forest access roads - no club trail pass reported; ORV registration/insurance/helmet required; seasonal/FMP road restrictions possible; HATVA/club involvement not confirmed",
      "app_flag": "free (verify Crown status per segment with CLUPA + MNRF roads)",
      "sources": ["backroad_mapbooks", "lio_mnrf_roads(implied)", "anglersatlas", "onX snippet echo"],
      "confidence": "medium"
    },
    {
      "name": "Wollaston Township rail trail (Coe Hill branch, COR)",
      "location": "Between Wollaston Lake Road and Old Hastings Road (Coe Hill area)",
      "permit": "pass_required",
      "permit_detail": "OFATV land-use agreement (2019), maintained by Coe Hill Riders; OPP recommended 'permit required' signs; renewal status after mid-2024 unknown",
      "app_flag": "maybe",
      "sources": ["coe_hill_riders", "bancroftthisweek"],
      "confidence": "medium"
    },
    {
      "name": "Coe Hill Riders club network (incl. Nephton Mines Loop)",
      "location": "North Hastings / North Kawartha (Coe Hill, Wollaston, Nephton/Apsley area); 600+ km",
      "permit": "pass_required",
      "permit_detail": "OFATV QuadON permit ($195/$175)",
      "app_flag": "maybe",
      "sources": ["coe_hill_riders", "ofatv_quadon", "ontario_trails_council", "mybancroftnow"],
      "confidence": "medium (network exists; individual trail names/geometry unverified)"
    },
    {
      "name": "Tweed to Gunter Loop / Centre Hastings Heritage Loop (EOTA)",
      "location": "South edge of bbox: Tweed - Stoco Lake - Flinton/Gunter - Marmora Mine; Madoc/Marmora/Stirling",
      "permit": "pass_required",
      "permit_detail": "EOTA pass",
      "app_flag": "maybe",
      "sources": ["eota (via articles)", "comewander", "centrehastings.com"],
      "confidence": "medium"
    },
    {
      "name": "Lingham Hydro Line",
      "location": "Hydro corridor in Hastings County between Hwy 62 and Hwy 41 (Lingham Lake area, NE of Tweed); ~19 mi",
      "permit": "unknown",
      "permit_detail": "Only onX lists it; hydro corridor land is typically Hydro One/private - ATV legality unverified (possible trespass)",
      "app_flag": "unknown - do not mark free",
      "sources": ["onx"],
      "confidence": "low"
    },
    {
      "name": "Settler's Route / Opeongo / Pakkotinna systems (RCATV)",
      "location": "Madawaska Valley / Renfrew County (Golden Lake-Wilno-Barry's Bay-Killaloe-Foymount); ~106 km",
      "permit": "pass_required",
      "permit_detail": "OFATV permit via RCATV; rail trails May 1-Nov 30",
      "app_flag": "maybe",
      "sources": ["comewander (two conflicting endpoint descriptions)", "rcatv_madawaska"],
      "confidence": "medium (endpoints conflict)"
    },
    {
      "name": "HATVA network / IB&O Rail Trail",
      "location": "West of Bancroft (Haliburton County, Wilberforce/Highlands East); claimed links east toward Bancroft",
      "permit": "pass_required",
      "permit_detail": "HATVA pass (reciprocity with EOTA unverified). IB&O permit status unconfirmed.",
      "app_flag": "maybe",
      "sources": ["hatva", "ontario_trails_council", "destinationontario"],
      "confidence": "low-medium (Bancroft connector unconfirmed)"
    },
    {
      "name": "Dungannon Mud Bog / ~75 km ride",
      "location": "Dungannon area near Bancroft (event)",
      "permit": "unknown",
      "permit_detail": "Event ride; not a standing public trail",
      "app_flag": "exclude",
      "sources": ["destinationontario", "comewander"],
      "confidence": "low"
    }
  ],
  "contradictions": [
    "Hastings Heritage Trail winter: EOTA/tourism say 'four-season, open year-round' vs Limerick Lake page '2025/26 wheeled vehicle trail use prohibited until end of snowmobile season' and OFSC uses it as TOP E106/B106E.",
    "Hastings Heritage Trail permits: EOTA pass vs OFATV permit (MOU 2021 covers Hastings County trails only, not wider EOTA network). EOTA map shows some trails its pass does not cover. Day pass $20 vs $25.",
    "Hastings Heritage Trail length/endpoints: 156 km Glen Ross-Lake St. Peter vs 'Trent River' south end vs 64 km (COLT section) old listing; RidePal lengths inconsistent (10.9 mi / 21.8 mi singletrack).",
    "'Bon Echo Loop' is widely promoted around Bancroft/Gilmour but is snowmobile-only; ATV use on OFSC trails is trespass.",
    "Old Hastings Road Glanmire-Murphy Corners: 'seasonal/impassable' (older) vs 'well-maintained' (newer).",
    "Weslemkoon: Backroad Mapbooks = 120 km ATV loop; Ontario Trails 'Weslemkoon Lake Trail - Scenic Route #8' is a car/motorcycle/cycle scenic drive, not ATV.",
    "Settler's Route endpoints differ between two Come Wander articles.",
    "EOTA network size: 700+ km vs 1,400 km vs 2,700-3,000 km depending on article/year.",
    "MNRF Road Segments currency: 2025-10-20 validation vs 2015 on mirror."
  ]
}
```

---

## Notes per source (prose)

### Open, app-usable data
- **OpenStreetMap (ODbL).** This should be the base geometry. The Hastings Heritage Trail is mapped as relation 7319029, built from ways tagged `highway=path`, `surface=gravel`, `bicycle=designated` and `railway=abandoned`. Some segments come from a CanVec import. ATV tags were **not** verified. Query Overpass in the bbox for `atv=*`, `motor_vehicle=*`, `highway=track` and `snowmobile=*`. When OSM shows no ATV permission, do not infer that riding is free.
- **LIO / Ontario GeoHub (OGL-Ontario).** OTN Segment has a free-text PERMITTED_USES field, but it was last updated in 2021, so treat it as stale. Other usable layers:
  - **MNRF Road Segments**, validated 2025, the best source of candidate free Crown-land forest roads
  - **CLUPA** and the provincial park / conservation reserve boundaries, which serve as an ORV exclusion mask
  - **ORN**, the full public road network
- **Hastings County ArcGIS item** "Hastings Heritage Trail - Overview". Its licence is unknown. It might be the authoritative centreline.

### Club and official sources (verify only, no redistribution)
- **EOTA (thetrail.ca).** Authoritative for the Hastings Heritage Trail. Hastings County owns the trail and EOTA maintains it under agreement, paid for by pass sales. An October 2025 county agenda extends access agreements with Imperial Oil and EOTA. No GPX/KML download was found; a free printed map is available by mail.
- **OFATV QuadON.** Shows member-club trails only, and every one requires a permit.
- **Coe Hill Riders** (OFATV, formed 2018). Riders get 600+ km of trails. The club maintains the Wollaston rail trail.
- **HATVA, RCATV and KATVA** sit on the bbox edges. All need passes. KATVA's "Crown land only" trails are permit-free according to an older article.
- **OFSC ITG.** Snowmobile only. Its value to the app is **negative**: it shows trails ATVs must avoid. The Old Hastings Snow Riders run about 350 km.

### Commercial and crowd sources (verify only)
- **Backroad Mapbooks** is the richest source for Crown-land motorized routes, including the Weslemkoon loop. The Bancroft District Crown Land map is dated 2013.
- **Trailforks** is nearly empty for Bancroft.
- **onX** is patchy here; its only local entry found was the Lingham Hydro Line.
- **Gaia** uses the BRMB overlay.
- **AllTrails** and **RidePal** are hiking/MTB oriented and add nothing on ATV status.
- **Trak Maps** coverage is unconfirmed.

### What to tell the rider
- "Free" options are realistically:
  - Crown-land forest roads, especially east and north-east of Bancroft (Weslemkoon area)
  - unpaved municipal roads where by-laws allow ATVs, such as Old Hastings Road and Limerick Township roads (pending the other agent's by-law results)
- All named club and rail trails need a pass, so flag them "maybe".
- Ask what "eco loop" means. If it is the Bon Echo Loop, it is a winter snowmobile tour and closed to ATVs.

## Source URLs
- https://northernontario.travel/atving/the-old-hastings-road-atv-trail
- https://northernontario.travel/atving/great-riding-in-bancroft
- https://northernontario.travel/snowmobiling/ofsc-bon-echo-loop
- https://northernontario.travel/atving/which-atv-trail-pass-required-ride-each-part-ontario
- https://northernontario.travel/atving/limerick-lake-lodge-eastern-ontario-trails-alliance-atv-trails
- https://limericklake.com/atvtrails.html
- https://www.thetrail.ca/passes/
- https://bancroft.hosted.civiclive.com/visiting/trails/hastings_heritage_trail
- https://www.destinationontario.com/en-ca/things-to-do/outdoor-adventures/atving/atv-trails-ontario
- https://comewander.ca/story/atv-trails ; https://comewander.ca/story/get-muddy-these-atv-routes-and-runs
- https://hastingscounty.civicweb.net/document/197023/ (Oct 2025 P&D agenda)
- https://www.quintenews.com/2021/03/17/atvs-riding-hastings-county-trails/ (EOTA-OFATV MOU)
- https://www.arcgis.com/home/item.html?id=9a2e8d24111f4c5b85d9b6dab8322e70&sublayer=0
- https://chatvc.ca/ ; https://www.bancroftthisweek.com/coe-hill-riders-looks-to-extend-land-use-agreement/
- https://apps.apple.com/ca/app/quadon/id1549704696
- https://ofsc.on.ca/?p=20323 (OFSC code of conduct: no ATVs on OFSC trails)
- https://www.district2ofsc.ca/member-clubs/old-hastings-snow-riders/?id=161
- https://data2.ontario.ca/dataset/ontario-trail-network
- https://ws.lioservices.lrc.gov.on.ca/arcgis2/rest/services/LIO_OPEN_DATA/LIO_Open04/MapServer/19
- https://data2.ontario.ca/dataset/ministry-of-natural-resources-and-forestry-road-segments
- https://data2.ontario.ca/dataset/trail-segments
- https://www.ontario.ca/page/crown-land-use-policy-atlas
- https://www.ontario.ca/page/recreational-activities-on-crown-land
- https://ontario.ca/files/2026-09/mnr-bancroft-minden-forest-management-action-plan-en-2026-09-11.pdf
- https://www.openstreetmap.org/way/75369488 ; https://www.openstreetmap.org/way/498425014
- https://backroadmapbooks.com/blogs/default-blog/cottage-country-off-road-thrills-atv-trails-and-overland-routes-for-adventure-seekers
- https://store.avenza.com/products/bancroft-district-crown-land-recreation-map-on-rec-map-bundle-backroad-mapbooks-map
- https://backroadmapbooks.com/blogs/app/trails-overlay-for-gaia-gps
- https://www.trailforks.com/region/bancroft/
- https://www.onxmaps.com/offroad/trails/ca/ontario/lingham-hydro-line-ndzdxv9rd6kv
- https://www.alltrails.com/trail/canada/ontario/hastings-heritage-trail
- https://www.ridepal.app/trails/canada/ontario/tudor-and-cashel/old-hastings-road
- https://www.ontariotrails.on.ca/trail/hastings-heritage-trail-bancroft
- https://www.ontariotrails.on.ca/trail/weslemkoon-lake-trail-north-hastings-scenic-route-8
- https://www.countyofrenfrew.on.ca/news/posts/trail-permits-required-for-atv-and-off-road-motorcycle-riders-on-renfrew-county-recreational-trails/
- https://lindsaydistrictchamberofcommerce.wildapricot.org/resources/Documents/KATVA-MAP-2021.pdf
- https://tudorandcashel.com/wp-content/uploads/2023/08/2023-34-Policy-for-Use-of-Unopened-Road-Allowance.pdf
- https://www.anglersatlas.com/content/395265/weslemkoon-lake/lake-weslemkoon-located
- https://gpsfiledepot.com/maps/view/211
