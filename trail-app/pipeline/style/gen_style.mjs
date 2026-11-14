// Generates the app's MapLibre styles (light + dark) from the Protomaps basemap style,
// muted so trails stand out, plus the ATV trail layers on top.
// Usage: node gen_style.mjs <outdir>
import { layers, namedFlavor } from "@protomaps/basemaps";
import fs from "node:fs";
import path from "node:path";

const out = process.argv[2] || ".";

const TRAIL = {
  light: { free: "#0A9A5E", maybe: "#F2A900", road: "#1F45D6", no: "#7A1A5C", casing: "rgba(26,26,26,0.7)", center: "#FFFFFF", label: "#1F2A24", halo: "#FFFFFF" },
  dark:  { free: "#6EF0B4", maybe: "#FFCF33", road: "#6F8FFF", no: "#D6336C", casing: "#000000", center: "#1C1F22", label: "#E6E9EC", halo: "#111316" },
};

function flavor(mode) {
  const f = structuredClone(namedFlavor(mode === "dark" ? "dark" : "light"));
  if (mode === "light") {
    Object.assign(f, {
      background: "#ECE8E0", earth: "#F1EEE8", water: "#A9C8DC",
      park_a: "#E3E8DA", park_b: "#D6E2CC", wood_a: "#E6EADF", wood_b: "#DCE5D3", scrub_a: "#E8EBE0", scrub_b: "#DDE5D5",
      minor_a: "#FFFFFF", minor_b: "#FFFFFF", minor_service: "#FAF8F4", other: "#F6F3EE",
      minor_casing: "#D2CCC2", minor_service_casing: "#DDD7CD", major_casing_early: "#D2CCC2", major_casing_late: "#D2CCC2",
      highway_casing_early: "#C9C2B6", highway_casing_late: "#C9C2B6", link_casing: "#D2CCC2",
      buildings: "#DDD8CF", boundaries: "#B9B2A6",
      city_label: "#3B3B3B", city_label_halo: "#F1EEE8", subplace_label: "#5A5A5A", subplace_label_halo: "#F1EEE8",
      roads_label_minor: "#5B5652", roads_label_major: "#4A4542", address_label: "#7A746F",
    });
    f.landcover = { ...f.landcover, forest: "rgba(222,230,214,1)", scrub: "rgba(232,235,222,1)", grassland: "rgba(232,236,222,1)", farmland: "rgba(236,234,220,1)", urban_area: "rgba(232,229,223,1)" };
  } else {
    Object.assign(f, {
      background: "#16191B", earth: "#1C1F22", water: "#2A3A4A",
      park_a: "#1F2622", park_b: "#202A24", wood_a: "#1E2421", wood_b: "#1F2722",
      minor_a: "#3A3F44", minor_b: "#3A3F44", major: "#474D53", highway: "#50575E",
      city_label: "#C9CDD2", subplace_label: "#A9AEB4", roads_label_minor: "#A4A9AF", roads_label_major: "#B6BBC1",
    });
  }
  return f;
}

function width(scale) {
  return ["interpolate", ["exponential", 1.5], ["zoom"], 8, 1.2 * scale, 10, 2 * scale, 14, 5 * scale, 17, 8 * scale];
}

// Colours for connected trail systems (network view). Distinct from the cyan route and from each other.
const SYSTEMS = {
  light: ["#1B9E77", "#D95F02", "#7570B3", "#E7298A", "#66A61E", "#C99700", "#A6761D", "#1F78B4"],
  dark:  ["#4FD1A5", "#FF8C42", "#A9A4FF", "#FF6FB5", "#9BE15D", "#FFD23F", "#E0A458", "#5DADE2"],
};

function networkLayers(mode) {
  const c = TRAIL[mode];
  const pal = SYSTEMS[mode];
  const match = ["match", ["get", "s"]];
  pal.forEach((col, i) => match.push(i + 1, col));
  match.push(mode === "dark" ? "#8A939C" : "#8C8C8C");
  const src = { source: "trails", "source-layer": "trails" };
  const hidden = { visibility: "none" };
  return [
    // dims the basemap so the network stands out
    { id: "net_dim", type: "fill", source: "region", layout: hidden,
      paint: { "fill-color": mode === "dark" ? "#0E1012" : "#FFFFFF", "fill-opacity": mode === "dark" ? 0.55 : 0.6 } },
    { id: "net_road", type: "line", ...src, minzoom: 9, filter: ["==", ["get", "c"], 2],
      layout: { ...hidden, "line-cap": "round", "line-join": "round" },
      paint: { "line-color": c.road, "line-width": width(0.7), "line-opacity": 0.75 } },
    { id: "net_casing", type: "line", ...src, filter: ["all", ["has", "s"]],
      layout: { ...hidden, "line-cap": "round", "line-join": "round" },
      paint: { "line-color": c.casing, "line-width": ["interpolate", ["exponential", 1.5], ["zoom"], 8, 3, 10, 5, 14, 10, 17, 14] } },
    { id: "net_trail", type: "line", ...src, filter: ["all", ["has", "s"]],
      layout: { ...hidden, "line-cap": "round", "line-join": "round" },
      paint: { "line-color": match, "line-width": width(1.3) } },
    { id: "net_maybe_mark", type: "line", ...src, filter: ["all", ["has", "s"], ["==", ["get", "c"], 1]],
      layout: { ...hidden, "line-cap": "butt" },
      paint: { "line-color": c.center, "line-width": ["interpolate", ["linear"], ["zoom"], 10, 0.8, 16, 2], "line-dasharray": [2, 4] } },
    { id: "net_junction", type: "circle", source: "trails", "source-layer": "junctions", minzoom: 11, layout: hidden,
      paint: {
        "circle-radius": ["interpolate", ["linear"], ["zoom"], 11, ["match", ["get", "j"], "a", 3.5, 2.5], 16, ["match", ["get", "j"], "a", 8, 6]],
        "circle-color": ["match", ["get", "j"], "a", c.road, mode === "dark" ? "#FFFFFF" : "#222222"],
        "circle-stroke-color": mode === "dark" ? "#111316" : "#FFFFFF", "circle-stroke-width": 1.5 } },
    { id: "net_label", type: "symbol", ...src, minzoom: 11, filter: ["all", ["has", "n"], ["has", "s"]],
      layout: { ...hidden, "symbol-placement": "line", "text-field": ["get", "n"], "text-font": ["Noto Sans Medium"], "text-size": 13,
        "symbol-spacing": 250 },
      paint: { "text-color": c.label, "text-halo-color": c.halo, "text-halo-width": 2 } },
  ];
}

function trailLayers(mode) {
  const c = TRAIL[mode];
  const src = { source: "trails", "source-layer": "trails" };
  const isRoad = ["==", ["get", "c"], 2];
  const layersOut = [];
  // casing under everything that isn't a road class
  // dotted "gap" links: where open data is missing the short bit joining a trail to the network
  layersOut.push({ id: "trail_gap", type: "line", ...src, minzoom: 12, filter: ["==", ["get", "k"], 8],
    layout: { "line-cap": "round" },
    paint: { "line-color": mode === "dark" ? "#C9CDD2" : "#5A5A5A", "line-width": ["interpolate", ["linear"], ["zoom"], 12, 1.5, 17, 3.5],
      "line-dasharray": [0.5, 2] } });
  layersOut.push({ id: "trail_casing", type: "line", ...src, filter: ["all", ["!=", ["get", "c"], 2], ["!=", ["get", "k"], 8]],
    layout: { "line-cap": "round", "line-join": "round" },
    paint: { "line-color": c.casing, "line-width": ["interpolate", ["exponential", 1.5], ["zoom"], 8, 2.2, 10, 3.5, 14, 8, 17, 11.5],
      "line-opacity": ["interpolate", ["linear"], ["zoom"], 8, 0.35, 12, 0.8] } });
  layersOut.push({ id: "trail_no", type: "line", ...src, filter: ["==", ["get", "c"], 3],
    layout: { "line-cap": "round", "line-join": "round" },
    paint: { "line-color": c.no, "line-width": width(0.6), "line-dasharray": [0.1, 2] } });
  layersOut.push({ id: "trail_road_casing", type: "line", ...src, minzoom: 10, filter: isRoad,
    layout: { "line-cap": "round", "line-join": "round" },
    paint: { "line-color": c.road, "line-opacity": ["interpolate", ["linear"], ["zoom"], 10, 0.3, 12, 0.5, 15, 0.85], "line-width": width(0.55) } });
  layersOut.push({ id: "trail_road_center", type: "line", ...src, minzoom: 13, filter: isRoad,
    layout: { "line-cap": "round", "line-join": "round" },
    paint: { "line-color": c.center, "line-width": ["interpolate", ["linear"], ["zoom"], 13, 0.8, 17, 2.2] } });
  layersOut.push({ id: "trail_maybe", type: "line", ...src, filter: ["all", ["==", ["get", "c"], 1], ["!=", ["get", "k"], 8]],
    layout: { "line-cap": "butt", "line-join": "round" },
    paint: { "line-color": c.maybe, "line-width": width(1), "line-dasharray": [3, 1.5] } });
  layersOut.push({ id: "trail_free", type: "line", ...src, filter: ["==", ["get", "c"], 0],
    layout: { "line-cap": "round", "line-join": "round" },
    paint: { "line-color": c.free, "line-width": width(1) } });
  layersOut.push({ id: "trail_label", type: "symbol", ...src, minzoom: 13, filter: ["all", ["has", "n"], ["!=", ["get", "c"], 2]],
    layout: { "symbol-placement": "line", "text-field": ["get", "n"], "text-font": ["Noto Sans Medium"], "text-size": 13,
      "symbol-spacing": 300, "text-max-angle": 30 },
    paint: { "text-color": c.label, "text-halo-color": c.halo, "text-halo-width": 2 } });
  return layersOut;
}

function style(mode) {
  const base = layers("protomaps", flavor(mode), { lang: "en" }).filter((l) => l.id !== "pois");
  const firstLabel = base.findIndex((l) => l.id === "address_label");
  const satellite = { id: "satellite", type: "raster", source: "satellite", layout: { visibility: "none" }, paint: { "raster-fade-duration": 150 } };
  const all = [...base.slice(0, firstLabel), satellite, ...trailLayers(mode), ...networkLayers(mode), ...base.slice(firstLabel)];
  return {
    version: 8,
    name: `Bancroft ATV ${mode}`,
    glyphs: "asset://fonts/{fontstack}/{range}.pbf",
    sprite: `asset://sprites/${mode}`,
    sources: {
      protomaps: { type: "vector", url: "pmtiles://asset://basemap.pmtiles", attribution: "© OpenStreetMap contributors, Protomaps" },
      trails: { type: "vector", url: "pmtiles://asset://trails.pmtiles", attribution: "Ontario LIO (OGL-Ontario), OpenStreetMap" },
      // online only: Esri World Imagery (shown when the rider picks Satellite and has signal / Wi-Fi)
      satellite: { type: "raster", tileSize: 256, maxzoom: 19,
        tiles: ["https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],
        attribution: "Imagery © Esri, Maxar, Earthstar Geographics" },
      region: { type: "geojson", data: { type: "Feature", properties: {}, geometry: { type: "Polygon",
        coordinates: [[[-80.5, 43.5], [-75.5, 43.5], [-75.5, 46.7], [-80.5, 46.7], [-80.5, 43.5]]] } } },
    },
    layers: all,
  };
}

fs.mkdirSync(out, { recursive: true });
for (const mode of ["light", "dark"]) {
  fs.writeFileSync(path.join(out, `${mode}.json`), JSON.stringify(style(mode)));
}
console.log("styles written to", out);
