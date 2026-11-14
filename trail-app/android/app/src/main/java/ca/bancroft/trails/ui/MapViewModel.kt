package ca.bancroft.trails.ui

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ca.bancroft.trails.data.AppData
import ca.bancroft.trails.data.Pin
import ca.bancroft.trails.data.PinStore
import ca.bancroft.trails.data.PinType
import ca.bancroft.trails.data.Poi
import ca.bancroft.trails.data.RouteMode
import ca.bancroft.trails.data.Settings
import ca.bancroft.trails.data.ThemeMode
import ca.bancroft.trails.location.LocationRepo
import ca.bancroft.trails.nav.NavState
import ca.bancroft.trails.nav.Navigator
import ca.bancroft.trails.routing.Route
import ca.bancroft.trails.routing.Router
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.hypot

/** Something the rider tapped or picked. */
sealed interface Target {
    val lat: Double
    val lon: Double
    val title: String

    data class Trail(
        override val lat: Double, override val lon: Double, val name: String?, val cls: Int, val kind: Int,
        val reason: Int, val note: String?, val conf: Int, val flags: Int,
    ) : Target {
        override val title get() = name ?: "Unnamed trail"
    }

    data class Place(val poi: Poi) : Target {
        override val lat get() = poi.lat
        override val lon get() = poi.lon
        override val title get() = poi.title
    }

    data class MyPin(val pin: Pin) : Target {
        override val lat get() = pin.lat
        override val lon get() = pin.lon
        override val title get() = pin.name.ifBlank { pin.type.label }
    }

    data class Point(override val lat: Double, override val lon: Double) : Target {
        override val title get() = "Dropped point"
    }
}

sealed interface Mode {
    data object Browse : Mode
    data class Details(val target: Target) : Mode
    data class Nearby(val category: String) : Mode
    data class Search(val query: String) : Mode
    data class Plan(val dest: Target, val routes: Map<RouteMode, Route?>, val selected: RouteMode, val computing: Boolean) : Mode
    data class Navigating(val dest: Target, val route: Route, val state: NavState?) : Mode
}

class MapViewModel(app: Application) : AndroidViewModel(app) {
    val settings = Settings(app)
    private val pinStore = PinStore(app)
    val locationRepo = LocationRepo(app)

    private val _data = MutableStateFlow<AppData?>(null)
    val data: StateFlow<AppData?> = _data.asStateFlow()
    private var router: Router? = null

    private val _mode = MutableStateFlow<Mode>(Mode.Browse)
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    private val _pins = MutableStateFlow(pinStore.load())
    val pins: StateFlow<List<Pin>> = _pins.asStateFlow()

    private val _theme = MutableStateFlow(settings.theme)
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private val _layers = MutableStateFlow(Layers(settings.showGas, settings.showFood, settings.showPoi, settings.showGates))
    val layers: StateFlow<Layers> = _layers.asStateFlow()

    private val _avoidMaybe = MutableStateFlow(settings.avoidMaybe)
    val avoidMaybe: StateFlow<Boolean> = _avoidMaybe.asStateFlow()

    private val _voice = MutableStateFlow(settings.voice)
    val voice: StateFlow<Boolean> = _voice.asStateFlow()

    /** One-shot camera requests for the map (fit route, fly to place). */
    private val _camera = MutableStateFlow<CameraRequest?>(null)
    val camera: StateFlow<CameraRequest?> = _camera.asStateFlow()

    private var navigator: Navigator? = null
    private var planJob: Job? = null
    private var rerouting = false

    init {
        viewModelScope.launch(Dispatchers.Default) {
            val d = AppData.load(app)
            router = Router(d.graph)
            _data.value = d
        }
    }

    data class Layers(val gas: Boolean, val food: Boolean, val poi: Boolean, val gates: Boolean)
    sealed interface CameraRequest {
        data class FlyTo(val lat: Double, val lon: Double, val zoom: Double?) : CameraRequest
        data class Fit(val route: Route) : CameraRequest
        data object FollowMe : CameraRequest
    }

    fun consumeCamera() { _camera.value = null }

    // ---------- browse / select
    fun back(): Boolean {
        when (val m = _mode.value) {
            is Mode.Browse -> return false
            is Mode.Navigating -> return true // stop needs an explicit hold
            is Mode.Plan -> _mode.value = Mode.Details(m.dest)
            else -> _mode.value = Mode.Browse
        }
        return true
    }

    fun select(t: Target) {
        if (_mode.value is Mode.Navigating) return
        _mode.value = Mode.Details(t)
    }

    fun showNearby(category: String) { _mode.value = Mode.Nearby(category) }
    fun openSearch(q: String = "") { _mode.value = Mode.Search(q) }
    fun close() { if (_mode.value !is Mode.Navigating) _mode.value = Mode.Browse }

    fun flyTo(t: Target, zoom: Double? = 15.0) { _camera.value = CameraRequest.FlyTo(t.lat, t.lon, zoom) }
    fun followMe() { _camera.value = CameraRequest.FollowMe }

    fun nearby(category: String, lat: Double, lon: Double): List<Pair<Poi, Double>> {
        val d = _data.value ?: return emptyList()
        return d.pois.asSequence().filter { it.cat == category }
            .map { it to distM(lat, lon, it.lat, it.lon) }
            .sortedBy { it.second }.take(40).toList()
    }

    fun search(q: String): List<Target> {
        val d = _data.value ?: return emptyList()
        if (q.isBlank()) return emptyList()
        val n = q.trim().lowercase()
        val out = ArrayList<Target>()
        _pins.value.filter { it.name.lowercase().contains(n) || it.note.lowercase().contains(n) }.forEach { out.add(Target.MyPin(it)) }
        d.pois.filter { it.name.lowercase().contains(n) || ca.bancroft.trails.data.kindLabel(it.kind).lowercase().contains(n) }
            .take(40).forEach { out.add(Target.Place(it)) }
        // named trails / roads
        val g = d.graph
        val seen = HashSet<String>()
        for (e in 0 until g.edgeCount) {
            val nm = g.name(e) ?: continue
            if (!nm.lowercase().contains(n) || !seen.add(nm)) continue
            val f = g.eFirst[e] + g.eCount[e] / 2
            out.add(Target.Trail(g.ptLat[f] / 1e6, g.ptLon[f] / 1e6, nm, g.eCls[e].toInt(), g.eKind[e].toInt(), g.eReason[e].toInt(),
                g.note(e), g.eConf[e].toInt(), g.eFlags[e]))
            if (seen.size > 25) break
        }
        return out
    }

    // ---------- pins
    fun addPin(lat: Double, lon: Double, type: PinType, name: String, note: String) {
        val p = Pin(System.currentTimeMillis(), lat, lon, name, note, type, System.currentTimeMillis())
        _pins.value = _pins.value + p
        pinStore.save(_pins.value)
        _mode.value = Mode.Details(Target.MyPin(p))
    }

    fun deletePin(p: Pin) {
        _pins.value = _pins.value.filterNot { it.id == p.id }
        pinStore.save(_pins.value)
        _mode.value = Mode.Browse
    }

    // ---------- routing
    fun planTo(dest: Target) {
        val loc = locationRepo.location.value
        val start = loc?.let { it.latitude to it.longitude }
        if (start == null) {
            _mode.value = Mode.Plan(dest, emptyMap(), settings.routeMode, computing = false)
            return
        }
        computePlan(dest, start.first, start.second)
    }

    private fun computePlan(dest: Target, fromLat: Double, fromLon: Double) {
        val r = router ?: return
        val selected = settings.routeMode
        _mode.value = Mode.Plan(dest, emptyMap(), selected, computing = true)
        planJob?.cancel()
        planJob = viewModelScope.launch {
            val blocked = r.blockedEdges(_pins.value.filter { it.type == PinType.BLOCKED }.map { it.lat to it.lon })
            val routes = withContext(Dispatchers.Default) {
                RouteMode.entries.associateWith { m -> r.route(fromLat, fromLon, dest.lat, dest.lon, m, _avoidMaybe.value, blocked) }
            }
            _mode.value = Mode.Plan(dest, routes, selected, computing = false)
            routes[selected]?.let { _camera.value = CameraRequest.Fit(it) }
        }
    }

    fun chooseMode(m: RouteMode) {
        settings.routeMode = m
        val p = _mode.value as? Mode.Plan ?: return
        _mode.value = p.copy(selected = m)
        p.routes[m]?.let { _camera.value = CameraRequest.Fit(it) }
    }

    fun setAvoidMaybe(v: Boolean) {
        settings.avoidMaybe = v
        _avoidMaybe.value = v
        (_mode.value as? Mode.Plan)?.let { planTo(it.dest) }
    }

    fun startNav() {
        val p = _mode.value as? Mode.Plan ?: return
        val route = p.routes[p.selected] ?: return
        navigator = Navigator(route)
        _mode.value = Mode.Navigating(p.dest, route, null)
        locationRepo.start(fastUpdates = true)
        _camera.value = CameraRequest.FollowMe
    }

    fun stopNav() {
        navigator = null
        _mode.value = Mode.Browse
        locationRepo.start(fastUpdates = false)
    }

    /** Called for every GPS fix. */
    fun onLocation(loc: Location) {
        val m = _mode.value as? Mode.Navigating ?: return
        val nav = navigator ?: return
        val st = nav.update(loc.latitude, loc.longitude)
        _mode.value = m.copy(state = st)
        if (st.offRouteM > 0 && !rerouting) reroute(m.dest, loc)
    }

    private fun reroute(dest: Target, loc: Location) {
        val r = router ?: return
        rerouting = true
        viewModelScope.launch {
            val blocked = r.blockedEdges(_pins.value.filter { it.type == PinType.BLOCKED }.map { it.lat to it.lon })
            val route = withContext(Dispatchers.Default) {
                r.route(loc.latitude, loc.longitude, dest.lat, dest.lon, settings.routeMode, _avoidMaybe.value, blocked)
            }
            if (route != null && _mode.value is Mode.Navigating) {
                navigator = Navigator(route)
                _mode.value = Mode.Navigating(dest, route, navigator!!.update(loc.latitude, loc.longitude))
            }
            rerouting = false
        }
    }

    // ---------- settings
    fun setTheme(t: ThemeMode) { settings.theme = t; _theme.value = t }
    fun setVoice(v: Boolean) { settings.voice = v; _voice.value = v }
    fun setLayers(l: Layers) {
        settings.showGas = l.gas; settings.showFood = l.food; settings.showPoi = l.poi; settings.showGates = l.gates
        _layers.value = l
    }

    fun saveCamera(lat: Double, lon: Double, zoom: Double) {
        settings.lastLat = lat; settings.lastLon = lon; settings.lastZoom = zoom
    }

    companion object {
        fun distM(la1: Double, lo1: Double, la2: Double, lo2: Double) =
            hypot((lo2 - lo1) * 111320.0 * cos(Math.toRadians(la1)), (la2 - la1) * 110540.0)
    }
}
