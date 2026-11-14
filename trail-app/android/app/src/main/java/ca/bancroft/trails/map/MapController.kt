package ca.bancroft.trails.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.location.Location
import ca.bancroft.trails.data.MapStyle
import ca.bancroft.trails.data.Pin
import ca.bancroft.trails.data.PinType
import ca.bancroft.trails.data.Poi
import ca.bancroft.trails.data.RouteMode
import ca.bancroft.trails.routing.Route
import ca.bancroft.trails.ui.MapViewModel
import ca.bancroft.trails.ui.Target
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.symbolSortKey
import org.maplibre.android.style.layers.PropertyFactory.textAnchor
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textMaxWidth
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textOptional
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Owns everything drawn on the MapLibre map besides the bundled style. */
class MapController(private val ctx: Context, val map: MapLibreMap) {
    private var style: Style? = null
    var dark = false; private set
    private val density = ctx.resources.displayMetrics.density

    // last-known content, re-applied after a style switch
    private var pois: List<Poi> = emptyList()
    private var layers = MapViewModel.Layers(true, true, true, true)
    private var pins: List<Pin> = emptyList()
    private var routes: Map<RouteMode, Route?> = emptyMap()
    private var selectedMode: RouteMode? = null
    private var dest: Target? = null
    private var selection: Target? = null
    private var mapStyle = MapStyle.STANDARD
    private var track: List<Pair<Double, Double>> = emptyList()
    private var userTrails: List<ca.bancroft.trails.data.UserTrail> = emptyList()
    private var ridden: List<List<Pair<Double, Double>>> = emptyList()
    private var home: Target.Home? = null

    fun loadStyle(dark: Boolean, onReady: () -> Unit) {
        if (style != null && this.dark == dark) { onReady(); return }
        this.dark = dark
        map.setStyle(Style.Builder().fromUri("asset://style/${if (dark) "dark" else "light"}.json")) { s ->
            style = s
            addImages(s)
            addOwnLayers(s)
            applyAll()
            onReady()
        }
    }

    // ------------------------------------------------------------------ icons
    private fun addImages(s: Style) {
        fun dot(color: Int, glyph: String, sizeDp: Float = 30f, ring: Int = Color.WHITE): Bitmap {
            val px = (sizeDp * density).toInt()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = ring; c.drawCircle(px / 2f, px / 2f, px / 2f, p)
            p.color = color; c.drawCircle(px / 2f, px / 2f, px / 2f - 2.5f * density, p)
            p.color = Color.WHITE; p.textAlign = Paint.Align.CENTER; p.typeface = Typeface.DEFAULT_BOLD
            p.textSize = px * 0.5f
            val y = px / 2f - (p.descent() + p.ascent()) / 2
            c.drawText(glyph, px / 2f, y, p)
            return bmp
        }
        s.addImage("ic-gas", dot(0xFFE8710A.toInt(), "⛽"))
        s.addImage("ic-food", dot(0xFFC2185B.toInt(), "🍴"))
        s.addImage("ic-poi", dot(0xFF6A4FB3.toInt(), "★"))
        s.addImage("ic-parking", dot(0xFF1565C0.toInt(), "P"))
        s.addImage("ic-gate", dot(0xFF37474F.toInt(), "⛔", 24f))
        s.addImage("ic-view", dot(0xFF6A4FB3.toInt(), "👁"))
        s.addImage("ic-camp", dot(0xFF2E7D32.toInt(), "⛺"))
        s.addImage("ic-water", dot(0xFF0277BD.toInt(), "💧"))
        s.addImage("ic-dest", dot(0xFFD32F2F.toInt(), "⚑", 36f))
        s.addImage("ic-home", dot(0xFF0B6E4F.toInt(), "🏠", 38f))
        for (t in PinType.entries) s.addImage("pin-${t.name}", dot(if (dark) 0xFF263238.toInt() else Color.WHITE, t.emoji, 34f,
            ring = if (dark) Color.WHITE else 0xFF263238.toInt()))
    }

    // ------------------------------------------------------------------ sources & layers
    private fun addOwnLayers(s: Style) {
        val below = if (s.getLayer("address_label") != null) "address_label" else null
        fun addBelow(l: org.maplibre.android.style.layers.Layer) { if (below != null) s.addLayerBelow(l, below) else s.addLayer(l) }

        s.addSource(GeoJsonSource("route-alt"))
        s.addSource(GeoJsonSource("route"))
        s.addSource(GeoJsonSource("pois"))
        s.addSource(GeoJsonSource("pins"))
        s.addSource(GeoJsonSource("dest"))
        s.addSource(GeoJsonSource("selection"))
        s.addSource(GeoJsonSource("track"))
        s.addSource(GeoJsonSource("route-off"))
        s.addSource(GeoJsonSource("usertrails"))
        s.addSource(GeoJsonSource("ridden"))
        s.addSource(GeoJsonSource("home"))

        addBelow(LineLayer("selection-line", "selection").withProperties(
            lineColor(if (dark) "#FFFFFF" else "#111111"), lineOpacity(0.35f),
            lineWidth(Expression.interpolate(Expression.exponential(1.5), Expression.zoom(), Expression.stop(10, 8), Expression.stop(16, 18))),
            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)))
        // where you've ridden before (faint), and trails you found that no map has (teal, dashed)
        addBelow(LineLayer("ridden-line", "ridden").withProperties(
            lineColor(if (dark) "#B388FF" else "#6A1B9A"), lineOpacity(0.35f), lineWidth(Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(9, 1.5), Expression.stop(16, 5))),
            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)))
        addBelow(LineLayer("usertrail-casing", "usertrails").withProperties(
            lineColor(if (dark) "#000000" else "#FFFFFF"), lineWidth(Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(9, 4), Expression.stop(16, 10))),
            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)))
        addBelow(LineLayer("usertrail-line", "usertrails").withProperties(
            lineColor(if (dark) "#64FFDA" else "#00897B"), lineWidth(Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(9, 2.5), Expression.stop(16, 6))),
            lineDasharray(arrayOf(2f, 1f)), lineCap(Property.LINE_CAP_BUTT), lineJoin(Property.LINE_JOIN_ROUND)))
        addBelow(LineLayer("route-alt-line", "route-alt").withProperties(
            lineColor(if (dark) "#8A939C" else "#7D868F"), lineOpacity(0.8f),
            lineWidth(Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(8, 3), Expression.stop(16, 7))),
            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)))
        addBelow(LineLayer("route-casing", "route").withProperties(
            lineColor(if (dark) "#0B1A22" else "#FFFFFF"),
            lineWidth(Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(8, 6), Expression.stop(16, 13))),
            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)))
        addBelow(LineLayer("route-line", "route").withProperties(
            lineColor("#00B3FF"),
            lineWidth(Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(8, 3.5), Expression.stop(16, 8))),
            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)))

        addBelow(LineLayer("track-casing", "track").withProperties(
            lineColor(if (dark) "#000000" else "#FFFFFF"), lineWidth(7f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)))
        addBelow(LineLayer("track-line", "track").withProperties(
            lineColor(if (dark) "#F5F5F5" else "#263238"), lineWidth(3.5f), lineDasharray(arrayOf(1.5f, 1.2f)),
            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)))

        addBelow(LineLayer("route-off-line", "route-off").withProperties(
            lineColor("#00B3FF"), lineWidth(5f), lineDasharray(arrayOf(1f, 1.2f)), lineCap(Property.LINE_CAP_ROUND)))

        val labelColor = if (dark) "#E6E9EC" else "#2B2B2B"
        val halo = if (dark) "#111316" else "#FFFFFF"
        s.addLayer(SymbolLayer("poi-icons", "pois").withProperties(
            iconImage(get("icon")), iconSize(Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(9, 0.55), Expression.stop(14, 0.9))),
            iconAllowOverlap(false), symbolSortKey(get("rank")),
            textField(Expression.step(Expression.zoom(), literal(""), Expression.stop(13, get("name")))),
            textFont(arrayOf("Noto Sans Medium")), textSize(13f), textOffset(arrayOf(0f, 1.4f)), textAnchor(Property.TEXT_ANCHOR_TOP),
            textOptional(true), textMaxWidth(8f), textColor(labelColor), textHaloColor(halo), textHaloWidth(2f)))
        s.addLayer(SymbolLayer("pin-icons", "pins").withProperties(
            iconImage(get("icon")), iconAllowOverlap(true), iconIgnorePlacement(true),
            textField(get("name")), textFont(arrayOf("Noto Sans Medium")), textSize(13f), textOffset(arrayOf(0f, 1.6f)),
            textAnchor(Property.TEXT_ANCHOR_TOP), textOptional(true), textColor(labelColor), textHaloColor(halo), textHaloWidth(2f)))
        s.addLayer(SymbolLayer("home-icon", "home").withProperties(iconImage("ic-home"), iconAllowOverlap(true), iconIgnorePlacement(true),
            iconSize(Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(8, 0.7), Expression.stop(14, 1.0)))))
        s.addLayer(SymbolLayer("dest-icon", "dest").withProperties(iconImage("ic-dest"), iconAllowOverlap(true), iconIgnorePlacement(true)))
    }

    private fun applyAll() {
        setPois(pois, layers); setPins(pins); setRoutes(routes, selectedMode); setDest(dest); setSelection(selection)
        setMapStyle(mapStyle); setTrack(track); setUserTrails(userTrails); setRidden(ridden); setHome(home)
    }

    fun setUserTrails(list: List<ca.bancroft.trails.data.UserTrail>) {
        userTrails = list
        style?.getSourceAs<GeoJsonSource>("usertrails")?.setGeoJson(FeatureCollection.fromFeatures(list.filter { it.pts.size > 1 }.map { t ->
            Feature.fromGeometry(LineString.fromLngLats(t.pts.map { Point.fromLngLat(it.second, it.first) })).apply { addNumberProperty("id", t.id) }
        }))
    }

    fun setRidden(list: List<List<Pair<Double, Double>>>) {
        ridden = list
        style?.getSourceAs<GeoJsonSource>("ridden")?.setGeoJson(FeatureCollection.fromFeatures(list.filter { it.size > 1 }.map { pts ->
            Feature.fromGeometry(LineString.fromLngLats(pts.map { Point.fromLngLat(it.second, it.first) }))
        }))
    }

    fun setHome(h: Target.Home?) {
        home = h
        style?.getSourceAs<GeoJsonSource>("home")?.setGeoJson(FeatureCollection.fromFeatures(
            listOfNotNull(h?.let { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)) })))
    }

    /** Standard map, satellite photos (needs signal), or the simplified trail-network view. */
    fun setMapStyle(m: MapStyle) {
        mapStyle = m
        val s = style ?: return
        fun vis(id: String, on: Boolean) { s.getLayer(id)?.setProperties(visibility(if (on) Property.VISIBLE else Property.NONE)) }
        val net = m == MapStyle.NETWORK
        vis("satellite", m == MapStyle.SATELLITE)
        for (id in listOf("trail_casing", "trail_maybe", "trail_free", "trail_label", "trail_road_casing", "trail_road_center")) vis(id, !net)
        for (id in listOf("net_dim", "net_road", "net_casing", "net_trail", "net_maybe_mark", "net_junction", "net_label")) vis(id, net)
    }

    fun setTrack(pts: List<Pair<Double, Double>>) {
        track = pts
        style?.getSourceAs<GeoJsonSource>("track")?.setGeoJson(FeatureCollection.fromFeatures(
            if (pts.size < 2) emptyList() else listOf(Feature.fromGeometry(LineString.fromLngLats(pts.map { Point.fromLngLat(it.second, it.first) })))))
    }

    fun fitPoints(pts: List<Pair<Double, Double>>, topPx: Int, bottomPx: Int) {
        stopFollow()
        if (pts.size < 2) return
        val b = LatLngBounds.Builder()
        pts.forEach { b.include(LatLng(it.first, it.second)) }
        val pad = (40 * density).toInt()
        runCatching { map.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), pad, topPx + pad, pad, bottomPx + pad), 700) }
    }

    fun setPois(list: List<Poi>, l: MapViewModel.Layers) {
        pois = list; layers = l
        val src = style?.getSourceAs<GeoJsonSource>("pois") ?: return
        val feats = list.filter {
            when (it.cat) { "gas" -> l.gas; "food" -> l.food; "poi", "parking" -> l.poi; "gate" -> l.gates; else -> false }
        }.map { p ->
            val icon = when {
                p.cat == "gas" -> "ic-gas"; p.cat == "food" -> "ic-food"; p.cat == "parking" -> "ic-parking"; p.cat == "gate" -> "ic-gate"
                p.kind == "viewpoint" || p.kind == "peak" -> "ic-view"
                p.kind == "camp_site" || p.kind == "caravan_site" -> "ic-camp"
                p.kind == "drinking_water" || p.kind == "waterfall" || p.kind == "beach" -> "ic-water"
                else -> "ic-poi"
            }
            val rank = when (p.cat) { "gas" -> 0; "food" -> 1; "parking" -> 3; "gate" -> 4; else -> 2 }
            Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)).apply {
                addStringProperty("icon", icon); addStringProperty("name", p.name); addNumberProperty("rank", rank)
                addStringProperty("cat", p.cat); addStringProperty("kind", p.kind)
                addNumberProperty("lat", p.lat); addNumberProperty("lon", p.lon)
            }
        }
        src.setGeoJson(FeatureCollection.fromFeatures(feats))
    }

    fun setPins(list: List<Pin>) {
        pins = list
        val src = style?.getSourceAs<GeoJsonSource>("pins") ?: return
        src.setGeoJson(FeatureCollection.fromFeatures(list.map { p ->
            Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)).apply {
                addStringProperty("icon", "pin-${p.type.name}"); addStringProperty("name", p.name); addNumberProperty("id", p.id)
            }
        }))
    }

    fun setRoutes(r: Map<RouteMode, Route?>, selected: RouteMode?) {
        routes = r; selectedMode = selected
        val s = style ?: return
        fun line(route: Route) = Feature.fromGeometry(LineString.fromLngLats(route.lats.indices.map { Point.fromLngLat(route.lons[it], route.lats[it]) }))
        val sel = selected?.let { r[it] }
        s.getSourceAs<GeoJsonSource>("route")?.setGeoJson(FeatureCollection.fromFeatures(listOfNotNull(sel?.let { line(it) })))
        s.getSourceAs<GeoJsonSource>("route-off")?.setGeoJson(FeatureCollection.fromFeatures(buildList {
            if (sel != null && sel.lats.size > 1) {
                if (sel.offStartM > 0) add(Feature.fromGeometry(LineString.fromLngLats(listOf(Point.fromLngLat(sel.lons[0], sel.lats[0]), Point.fromLngLat(sel.lons[1], sel.lats[1])))))
                if (sel.offEndM > 0) { val n = sel.lats.size
                    add(Feature.fromGeometry(LineString.fromLngLats(listOf(Point.fromLngLat(sel.lons[n - 2], sel.lats[n - 2]), Point.fromLngLat(sel.lons[n - 1], sel.lats[n - 1]))))) }
            }
        }))
        s.getSourceAs<GeoJsonSource>("route-alt")?.setGeoJson(FeatureCollection.fromFeatures(
            r.filterKeys { it != selected }.values.filterNotNull().filter { it.distanceM != sel?.distanceM }.map { line(it) }))
    }

    fun setDest(t: Target?) {
        dest = t
        style?.getSourceAs<GeoJsonSource>("dest")?.setGeoJson(FeatureCollection.fromFeatures(
            listOfNotNull(t?.let { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)) })))
    }

    /** Highlight the tapped trail segment (geometry from the rendered tile feature). */
    fun setSelection(t: Target?, feature: Feature? = null) {
        selection = t
        style?.getSourceAs<GeoJsonSource>("selection")?.setGeoJson(
            FeatureCollection.fromFeatures(listOfNotNull(feature?.takeIf { t is Target.Trail })))
    }

    // ------------------------------------------------------------------ hit testing
    sealed interface Hit {
        data class PoiHit(val f: Feature) : Hit
        data class PinHit(val id: Long) : Hit
        data class TrailHit(val f: Feature) : Hit
        data class UserTrailHit(val id: Long) : Hit
        data object HomeHit : Hit
    }

    fun hitTest(pt: PointF): Hit? {
        val r = 22 * density
        val box = RectF(pt.x - r, pt.y - r, pt.x + r, pt.y + r)
        map.queryRenderedFeatures(box, "pin-icons").firstOrNull()?.let { f ->
            return Hit.PinHit(f.getNumberProperty("id").toLong())
        }
        if (map.queryRenderedFeatures(box, "home-icon").isNotEmpty()) return Hit.HomeHit
        map.queryRenderedFeatures(box, "poi-icons").firstOrNull()?.let { return Hit.PoiHit(it) }
        map.queryRenderedFeatures(RectF(pt.x - r * 0.7f, pt.y - r * 0.7f, pt.x + r * 0.7f, pt.y + r * 0.7f), "usertrail-line").firstOrNull()
            ?.let { return Hit.UserTrailHit(it.getNumberProperty("id").toLong()) }
        val tr = RectF(pt.x - r * 0.7f, pt.y - r * 0.7f, pt.x + r * 0.7f, pt.y + r * 0.7f)
        val trails = map.queryRenderedFeatures(tr, "trail_free", "trail_maybe", "trail_no", "trail_road_casing", "net_trail", "net_road")
        // prefer off-road classes over roads when overlapping
        return trails.minByOrNull { (it.getNumberProperty("c")?.toInt() ?: 9).let { c -> if (c == 2) 5 else c } }?.let { Hit.TrailHit(it) }
    }

    // ------------------------------------------------------------------ camera & location
    @SuppressLint("MissingPermission")
    fun enableLocation(onPuckClick: () -> Unit = {}) {
        val s = style ?: return
        val lc = map.locationComponent
        if (!lc.isLocationComponentActivated) {
            val opts = LocationComponentOptions.builder(ctx)
                .accuracyAlpha(0.15f)
                .foregroundTintColor(0xFF1A73E8.toInt())
                .bearingTintColor(0xFF1A73E8.toInt())
                .trackingGesturesManagement(true)
                .build()
            lc.activateLocationComponent(LocationComponentActivationOptions.builder(ctx, s)
                .locationComponentOptions(opts).useDefaultLocationEngine(false).build())
            lc.addOnLocationClickListener { onPuckClick() }
        }
        lc.isLocationComponentEnabled = true
        lc.renderMode = RenderMode.COMPASS
    }

    fun pushLocation(loc: Location) {
        val lc = map.locationComponent
        if (lc.isLocationComponentActivated) lc.forceLocationUpdate(loc)
    }

    fun follow(navigating: Boolean, bottomPadPx: Int, topPadPx: Int) {
        val lc = map.locationComponent
        if (!lc.isLocationComponentActivated || lc.lastKnownLocation == null) return
        if (navigating) {
            lc.renderMode = RenderMode.GPS
            // puck in the lower third: pad the top so the camera centre sits low
            map.moveCamera(CameraUpdateFactory.paddingTo(0.0, topPadPx + map.height * 0.35, 0.0, bottomPadPx.toDouble()))
            lc.setCameraMode(CameraMode.TRACKING_GPS, 900, 16.5, null, 45.0, null)
        } else {
            lc.renderMode = RenderMode.COMPASS
            map.moveCamera(CameraUpdateFactory.paddingTo(0.0, 0.0, 0.0, 0.0))
            lc.setCameraMode(CameraMode.TRACKING, 900, maxOf(map.cameraPosition.zoom, 14.0), null, 0.0, null)
        }
    }

    fun stopFollow() {
        val lc = map.locationComponent
        if (lc.isLocationComponentActivated) lc.cameraMode = CameraMode.NONE
        map.moveCamera(CameraUpdateFactory.paddingTo(0.0, 0.0, 0.0, 0.0))
    }

    fun flyTo(lat: Double, lon: Double, zoom: Double?) {
        stopFollow()
        val pos = CameraPosition.Builder().target(LatLng(lat, lon)).zoom(zoom ?: map.cameraPosition.zoom).tilt(0.0).build()
        map.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 700)
    }

    fun fit(route: Route, topPx: Int, bottomPx: Int) {
        stopFollow()
        if (route.lats.isEmpty()) return
        val b = LatLngBounds.Builder()
        for (i in route.lats.indices step maxOf(1, route.lats.size / 200)) b.include(LatLng(route.lats[i], route.lons[i]))
        b.include(LatLng(route.lats.last(), route.lons.last()))
        val pad = (40 * density).toInt()
        runCatching {
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), pad, topPx + pad, pad, bottomPx + pad), 700)
        }
    }

    fun resetNorth() {
        map.animateCamera(CameraUpdateFactory.bearingTo(0.0), 300)
        map.animateCamera(CameraUpdateFactory.tiltTo(0.0), 300)
    }
}
