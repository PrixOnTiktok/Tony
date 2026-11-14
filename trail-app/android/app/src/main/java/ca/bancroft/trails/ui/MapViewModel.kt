package ca.bancroft.trails.ui

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ca.bancroft.trails.data.AppData
import ca.bancroft.trails.data.MapStyle
import ca.bancroft.trails.data.Ride
import ca.bancroft.trails.data.RideRecorder
import ca.bancroft.trails.data.RideStore
import ca.bancroft.trails.data.Sun
import ca.bancroft.trails.data.UserTrail
import ca.bancroft.trails.data.UserTrailStore
import ca.bancroft.trails.location.RideService
import ca.bancroft.trails.nav.Units
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

    data class Point(override val lat: Double, override val lon: Double, val label: String = "Dropped point") : Target {
        override val title get() = label
    }

    data class Home(override val lat: Double, override val lon: Double, val label: String, val exact: Boolean) : Target {
        override val title get() = "Home"
    }

    data class YourTrail(val trail: UserTrail, override val lat: Double, override val lon: Double) : Target {
        override val title get() = "Your trail"
    }

    /** The rider's own position: coordinates, share, emergency call. */
    data class Me(override val lat: Double, override val lon: Double, val accuracyM: Float, val altitudeM: Double?, val speedKmh: Double) : Target {
        override val title get() = "My location"
    }
}

sealed interface Mode {
    data object Browse : Mode
    data class Details(val target: Target) : Mode
    data class Nearby(val category: String) : Mode
    data class Search(val query: String) : Mode
    data class Plan(val dest: Target, val routes: Map<RouteMode, Route?>, val selected: RouteMode, val computing: Boolean) : Mode
    data class Navigating(val dest: Target, val route: Route, val state: NavState?) : Mode
    data class Loop(val km: Int, val mode: RouteMode, val route: Route?, val computing: Boolean, val noGps: Boolean = false) : Mode
    data object Rides : Mode
    data object Sos : Mode
    data object Ideas : Mode
    /** A suggested ride being previewed: a popular route or a quick timed ride. */
    data class Idea(val title: String, val subtitle: String, val ride: ca.bancroft.trails.data.RideIdea?, val route: Route?,
                    val computing: Boolean, val note: String? = null) : Mode
    data class RideView(val ride: Ride) : Mode
}

class MapViewModel(app: Application) : AndroidViewModel(app) {
    val settings = Settings(app)
    private val pinStore = PinStore(app)
    val locationRepo = LocationRepo.get(app)
    private val rideStore = RideStore(app)
    private val userTrailStore = UserTrailStore(app)
    private var autoStartedRecording = false
    val recording = RideRecorder.live

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

    private val _mapStyle = MutableStateFlow(settings.mapStyle)
    val mapStyle: StateFlow<MapStyle> = _mapStyle.asStateFlow()

    private val _rides = MutableStateFlow(rideStore.list())
    val rides: StateFlow<List<Ride>> = _rides.asStateFlow()

    /** Track shown on the map: the live recording or a saved ride being viewed. */
    private val _shownTrack = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
    val shownTrack: StateFlow<List<Pair<Double, Double>>> = _shownTrack.asStateFlow()

    private val _imperial = MutableStateFlow(settings.imperial.also { Units.imperial = it })
    val imperial: StateFlow<Boolean> = _imperial.asStateFlow()

    private val _fuelRange = MutableStateFlow(settings.fuelRangeKm)
    val fuelRange: StateFlow<Int> = _fuelRange.asStateFlow()

    private val _home = MutableStateFlow(Target.Home(settings.homeLat, settings.homeLon, settings.homeLabel, settings.homeExact))
    val home: StateFlow<Target.Home> = _home.asStateFlow()

    private val _userTrails = MutableStateFlow(userTrailStore.load())
    val userTrails: StateFlow<List<UserTrail>> = _userTrails.asStateFlow()

    /** All saved rides, thinned, for the "where I've ridden" layer. */
    private val _ridden = MutableStateFlow<List<List<Pair<Double, Double>>>>(emptyList())
    val ridden: StateFlow<List<List<Pair<Double, Double>>>> = _ridden.asStateFlow()
    private val _showRidden = MutableStateFlow(settings.showRidden)
    val showRidden: StateFlow<Boolean> = _showRidden.asStateFlow()
    private val _autoRecord = MutableStateFlow(settings.autoRecord)
    val autoRecord: StateFlow<Boolean> = _autoRecord.asStateFlow()

    /** Latest time to leave to get home before sunset (null = not relevant / unknown). */
    private val _leaveBy = MutableStateFlow<Long?>(null)
    val leaveBy: StateFlow<Long?> = _leaveBy.asStateFlow()
    private var leaveByCheckedAt = 0L

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
            router = Router(d.graph).also { it.setUserTrails(_userTrails.value.map { t -> t.pts }) }
            _data.value = d
            refreshRidden()
        }
    }

    data class Layers(val gas: Boolean, val food: Boolean, val poi: Boolean, val gates: Boolean)
    sealed interface CameraRequest {
        data class FlyTo(val lat: Double, val lon: Double, val zoom: Double?) : CameraRequest
        data class Fit(val route: Route) : CameraRequest
        data class FitPoints(val pts: List<Pair<Double, Double>>) : CameraRequest
        data object FollowMe : CameraRequest
    }

    fun consumeCamera() { _camera.value = null }

    // ---------- browse / select
    fun back(): Boolean {
        when (val m = _mode.value) {
            is Mode.Browse -> return false
            is Mode.Navigating -> return true // stop needs an explicit hold
            is Mode.Plan -> _mode.value = Mode.Details(m.dest)
            is Mode.Idea -> _mode.value = Mode.Ideas
            is Mode.RideView -> { _shownTrack.value = emptyList(); _mode.value = Mode.Rides }
            else -> { _shownTrack.value = emptyList(); _mode.value = Mode.Browse }
        }
        return true
    }

    fun select(t: Target) {
        if (_mode.value is Mode.Navigating) return
        _mode.value = Mode.Details(t)
    }

    fun showNearby(category: String) { _mode.value = Mode.Nearby(category) }
    fun openSearch(q: String = "") { _mode.value = Mode.Search(q) }
    fun close() { if (_mode.value !is Mode.Navigating) { _shownTrack.value = emptyList(); _mode.value = Mode.Browse } }

    fun showMe() {
        val l = locationRepo.location.value ?: return
        select(Target.Me(l.latitude, l.longitude, l.accuracy, if (l.hasAltitude()) l.altitude else null, l.speed * 3.6))
    }

    // ---------- ride ideas
    fun openIdeas() { _mode.value = Mode.Ideas }

    private fun here(): Pair<Double, Double> = locationRepo.location.value?.let { it.latitude to it.longitude } ?: (_home.value.lat to _home.value.lon)

    /** A popular ride, routed from where you are to its start, then through its points. */
    fun openRide(ride: ca.bancroft.trails.data.RideIdea) {
        val r = router ?: return
        _mode.value = Mode.Idea(ride.name, ride.typeLabel, ride, null, computing = true)
        planJob?.cancel()
        planJob = viewModelScope.launch {
            val (lat, lon) = here()
            val mode = settings.routeMode
            val res = withContext(Dispatchers.Default) {
                val pts = ride.points.map { it.lat to it.lon }
                val core = when {
                    !ride.routable -> null
                    ride.type == "loop" -> r.via(pts + pts.first(), mode, _avoidMaybe.value, loop = true)
                    ride.type == "out_back" -> r.via(pts, mode, _avoidMaybe.value)?.let { r.outAndBack(it) }
                    else -> r.via(pts, mode, _avoidMaybe.value)
                }
                val start = ride.points.first()
                val away = distM(lat, lon, start.lat, start.lon)
                // close by: ride there too. Far away: just the ride (most people trailer to the start)
                val toStart = if (away in 500.0..15_000.0 || (core == null && away > 500)) r.route(lat, lon, start.lat, start.lon, mode, _avoidMaybe.value) else null
                when {
                    core != null && toStart != null -> r.concat(listOf(toStart, core), mode) to "Includes ${ca.bancroft.trails.nav.formatDist(toStart.distanceM)} to get to the start at ${start.name}."
                    core != null && away > 15_000 -> core to "Starts at ${start.name}, ${ca.bancroft.trails.nav.formatDist(away)} from you. Trailer there, then tap Ride it."
                    core != null -> core to null
                    toStart != null -> toStart to "This one has no published route points, so this takes you to the start (${start.name})."
                    else -> null to null
                }
            }
            val cur = _mode.value as? Mode.Idea ?: return@launch
            _mode.value = cur.copy(route = res.first, computing = false, note = res.second)
            res.first?.let { _camera.value = CameraRequest.Fit(it) }
        }
    }

    /**
     * A ride of about [minutes] from where you are: the better of a loop or a there-and-back to something worth
     * seeing (lookout, waterfall, ghost town, general store…), preferring trails.
     */
    fun quickRide(minutes: Int) {
        val r = router ?: return
        val d = _data.value ?: return
        _mode.value = Mode.Idea("$minutes-minute ride", "From ${if (locationRepo.location.value != null) "here" else "home"}", null, null, computing = true)
        planJob?.cancel()
        planJob = viewModelScope.launch {
            val (lat, lon) = here()
            val mode = RouteMode.MOSTLY_TRAILS
            val targetS = minutes * 60.0
            val res = withContext(Dispatchers.Default) {
                var km = (minutes * 0.33).coerceAtLeast(3.0) // ~20 km/h on trails
                var loop = r.loop(lat, lon, km, mode, _avoidMaybe.value)
                // second pass: scale the distance by how far off the time was
                loop?.let { l -> if (kotlin.math.abs(l.timeS - targetS) > targetS * 0.2 && l.timeS > 0) {
                    km = (km * targetS / l.timeS).coerceIn(2.0, 150.0)
                    r.loop(lat, lon, km, mode, _avoidMaybe.value)?.let { l2 -> if (kotlin.math.abs(l2.timeS - targetS) < kotlin.math.abs(l.timeS - targetS)) loop = l2 }
                } }
                // there-and-back to a spot roughly half the time away (second pass corrects for road vs trail speed)
                val spotsAll = (d.places.map { Triple(it.name, it.lat, it.lon) } +
                    d.pois.filter { it.cat == "poi" && it.kind in setOf("viewpoint", "waterfall", "attraction", "beach", "picnic_site", "historic", "peak") && it.name.isNotBlank() }
                        .map { Triple(it.name, it.lat, it.lon) } +
                    d.pois.filter { it.cat == "food" || it.cat == "gas" }.filter { it.name.isNotBlank() }.map { Triple(it.name, it.lat, it.lon) })
                    .map { it to distM(lat, lon, it.second, it.third) }
                fun bestOutBack(reach: Double) = spotsAll.filter { it.second in reach * 0.35..reach * 0.9 }
                    .sortedBy { kotlin.math.abs(it.second - reach * 0.65) }.take(6)
                    .mapNotNull { (s, _) -> r.route(lat, lon, s.second, s.third, mode, _avoidMaybe.value)?.let { s.first to r.outAndBack(it) } }
                    .minByOrNull { kotlin.math.abs(it.second.timeS - targetS) + (1 - it.second.trailShare) * 300 }
                val reach0 = minutes / 2.0 / 60.0 * 20_000.0
                var outBack = bestOutBack(reach0)
                outBack?.let { ob -> if (kotlin.math.abs(ob.second.timeS - targetS) > targetS * 0.2 && ob.second.timeS > 0) {
                    val again = bestOutBack(reach0 * targetS / ob.second.timeS)
                    if (again != null && kotlin.math.abs(again.second.timeS - targetS) < kotlin.math.abs(ob.second.timeS - targetS)) outBack = again
                } }
                val loopFinal = loop
                val loopScore = loopFinal?.let { kotlin.math.abs(it.timeS - targetS) + (1 - it.trailShare) * 300 } ?: Double.MAX_VALUE
                val obScore = outBack?.let { kotlin.math.abs(it.second.timeS - targetS) + (1 - it.second.trailShare) * 300 - 120 } ?: Double.MAX_VALUE
                if (obScore < loopScore) outBack!!.second to "There and back to ${outBack.first}" else loopFinal to "A loop from here"
            }
            val cur = _mode.value as? Mode.Idea ?: return@launch
            _mode.value = cur.copy(subtitle = res.second, route = res.first, computing = false,
                note = if (res.first == null) "Couldn't find a ride that short from here. Try a longer one." else null)
            res.first?.let { _camera.value = CameraRequest.Fit(it) }
        }
    }

    fun startIdea() {
        val m = _mode.value as? Mode.Idea ?: return
        val route = m.route ?: return
        startNavigation(Target.Point(route.lats.last(), route.lons.last(), m.title), route)
    }

    // ---------- home
    fun goHome() = planTo(_home.value)

    fun setHome(lat: Double, lon: Double) {
        settings.homeLat = lat; settings.homeLon = lon; settings.homeExact = true
        _home.value = Target.Home(lat, lon, settings.homeLabel, true)
        _mode.value = Mode.Details(_home.value)
    }

    // ---------- stranded / SOS
    fun openSos() { _mode.value = Mode.Sos }

    /** Nearest public road (any road, for walking out) to a point: (name, distance m, bearing deg, lat, lon). */
    fun nearestRoad(lat: Double, lon: Double): Triple<String, Double, Pair<Double, Double>>? {
        val g = _data.value?.graph ?: return null
        val s = g.snap(lat, lon, 15000.0) { ca.bancroft.trails.data.Kind.isRoad(g.eKind[it].toInt()) && g.eKind[it].toInt() != ca.bancroft.trails.data.Kind.SERVICE } ?: return null
        return Triple(g.name(s.edge) ?: "a road", s.distM, s.lat to s.lon)
    }

    // ---------- your own trails
    fun deleteUserTrail(t: UserTrail) {
        _userTrails.value = _userTrails.value.filterNot { it.id == t.id }
        userTrailStore.save(_userTrails.value)
        router?.setUserTrails(_userTrails.value.map { it.pts })
        _mode.value = Mode.Browse
    }

    private fun learnTrails(live: RideRecorder.Live) {
        val g = _data.value?.graph ?: return
        val found = UserTrailStore.detect(live.points, g, _userTrails.value)
        if (found.isEmpty()) return
        _userTrails.value = _userTrails.value + found
        userTrailStore.save(_userTrails.value)
        router?.setUserTrails(_userTrails.value.map { it.pts })
    }

    private fun refreshRidden() {
        viewModelScope.launch(Dispatchers.IO) {
            _ridden.value = rideStore.list().map { r -> rideStore.points(r).filterIndexed { i, _ -> i % 3 == 0 } }
        }
    }

    fun setShowRidden(v: Boolean) { settings.showRidden = v; _showRidden.value = v }
    fun setAutoRecord(v: Boolean) { settings.autoRecord = v; _autoRecord.value = v }

    data class Stats(val rides: Int, val km: Double, val hours: Double, val maxKmh: Double, val yourTrailsKm: Double)
    fun stats(): Stats {
        val rs = _rides.value
        return Stats(rs.size, rs.sumOf { it.distanceM } / 1000.0, rs.sumOf { it.durationMs } / 3_600_000.0,
            rs.maxOfOrNull { it.maxKmh } ?: 0.0, _userTrails.value.sumOf { it.lengthM } / 1000.0)
    }

    /** "Leave by" for getting home before dark; recomputed at most every 5 min. */
    private fun updateLeaveBy(loc: Location) {
        val now = System.currentTimeMillis()
        if (now - leaveByCheckedAt < 5 * 60_000) return
        leaveByCheckedAt = now
        val sunset = Sun.sunset(loc.latitude, loc.longitude) ?: return
        if (sunset - now > 3 * 3600_000 || sunset < now) { _leaveBy.value = null; return }
        val r = router ?: return
        val h = _home.value
        if (distM(loc.latitude, loc.longitude, h.lat, h.lon) < 300) { _leaveBy.value = null; return }
        viewModelScope.launch {
            val route = withContext(Dispatchers.Default) { r.route(loc.latitude, loc.longitude, h.lat, h.lon, settings.routeMode, false) }
            _leaveBy.value = route?.let { sunset - (it.timeS * 1000).toLong() - 10 * 60_000 }
        }
    }

    // ---------- loops
    fun openLoop() {
        _mode.value = Mode.Loop(settings.loopKm, settings.routeMode.takeIf { it != RouteMode.FASTEST } ?: RouteMode.MOSTLY_TRAILS, null, false)
        computeLoop()
    }

    fun setLoop(km: Int? = null, mode: RouteMode? = null) {
        val m = _mode.value as? Mode.Loop ?: return
        km?.let { settings.loopKm = it }
        _mode.value = m.copy(km = km ?: m.km, mode = mode ?: m.mode)
        computeLoop()
    }

    private fun computeLoop() {
        val m = _mode.value as? Mode.Loop ?: return
        val r = router ?: return
        val loc = locationRepo.location.value
        if (loc == null) { _mode.value = m.copy(route = null, computing = false, noGps = true); return }
        _mode.value = m.copy(route = null, computing = true, noGps = false)
        planJob?.cancel()
        planJob = viewModelScope.launch {
            val blocked = r.blockedEdges(_pins.value.filter { it.type == PinType.BLOCKED }.map { it.lat to it.lon })
            val loop = withContext(Dispatchers.Default) { r.loop(loc.latitude, loc.longitude, m.km.toDouble(), m.mode, _avoidMaybe.value, blocked) }
            val cur = _mode.value as? Mode.Loop ?: return@launch
            _mode.value = cur.copy(route = loop, computing = false)
            loop?.let { _camera.value = CameraRequest.Fit(it) }
        }
    }

    fun startLoop() {
        val m = _mode.value as? Mode.Loop ?: return
        val route = m.route ?: return
        startNavigation(Target.Point(route.lats.last(), route.lons.last(), "Start of loop"), route)
    }

    // ---------- map style, units, fuel
    fun setMapStyle(s: MapStyle) { settings.mapStyle = s; _mapStyle.value = s }
    fun setImperial(v: Boolean) { settings.imperial = v; Units.imperial = v; _imperial.value = v }
    fun setFuelRange(km: Int) { settings.fuelRangeKm = km; _fuelRange.value = km }

    // ---------- ride recording
    fun startRecording() {
        RideRecorder.start()
        RideService.start(getApplication(), "Recording ride")
    }

    fun stopRecording(save: Boolean): Ride? {
        val live = RideRecorder.stop()
        autoStartedRecording = false
        if (_mode.value !is Mode.Navigating) RideService.stop(getApplication())
        val ride = if (save && live != null) rideStore.save(live) else null
        if (save && live != null) learnTrails(live)
        _rides.value = rideStore.list()
        _shownTrack.value = emptyList()
        refreshRidden()
        return ride
    }

    fun openRides() { _mode.value = Mode.Rides }
    fun viewRide(r: Ride) {
        _shownTrack.value = rideStore.points(r)
        _mode.value = Mode.RideView(r)
        _shownTrack.value.takeIf { it.size > 1 }?.let { pts ->
            _camera.value = CameraRequest.FitPoints(pts)
        }
    }
    fun deleteRide(r: Ride) { rideStore.delete(r); _rides.value = rideStore.list(); _shownTrack.value = emptyList(); _mode.value = Mode.Rides }
    fun rideFile(r: Ride) = rideStore.file(r)

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
        startNavigation(p.dest, route)
    }

    private fun startNavigation(dest: Target, route: Route) {
        navigator = Navigator(route)
        _mode.value = Mode.Navigating(dest, route, null)
        locationRepo.start(fastUpdates = true)
        if (settings.autoRecord && recording.value == null) { RideRecorder.start(); autoStartedRecording = true }
        RideService.start(getApplication(), "Navigating to ${dest.title}")
        _camera.value = CameraRequest.FollowMe
    }

    fun stopNav() {
        navigator = null
        _mode.value = Mode.Browse
        if (autoStartedRecording) stopRecording(save = true)
        if (recording.value == null) { RideService.stop(getApplication()); locationRepo.start(fastUpdates = false) }
    }

    /** Fast GPS while navigating or recording, slow while just browsing. */
    fun wantFastGps() = _mode.value is Mode.Navigating || recording.value != null

    /** Called for every GPS fix. */
    fun onLocation(loc: Location) {
        recording.value?.let { live -> _shownTrack.value = live.points.map { it.lat to it.lon } }
        updateLeaveBy(loc)
        val m = _mode.value as? Mode.Navigating ?: return
        val nav = navigator ?: return
        val st = nav.update(loc.latitude, loc.longitude)
        _mode.value = m.copy(state = st)
        if (st.offRouteM > 0 && !rerouting) reroute(m.dest, loc)
    }

    private fun reroute(dest: Target, loc: Location) {
        val r = router ?: return
        val old = (_mode.value as? Mode.Navigating)?.route ?: return
        val progress = (_mode.value as? Mode.Navigating)?.state?.progressIndex ?: 0
        rerouting = true
        viewModelScope.launch {
            val blocked = r.blockedEdges(_pins.value.filter { it.type == PinType.BLOCKED }.map { it.lat to it.lon })
            val route = withContext(Dispatchers.Default) {
                if (old.isLoop) {
                    // rejoin the loop ~800 m ahead instead of cutting straight home
                    val target = old.cumDist[progress] + 800.0
                    val j = old.cumDist.indexOfFirst { it >= target }.takeIf { it > 0 } ?: (old.lats.size - 1)
                    val back = r.route(loc.latitude, loc.longitude, old.lats[j], old.lons[j], old.mode, _avoidMaybe.value, blocked)
                    back?.let { r.concat(listOf(it, r.tail(old, j)), old.mode) }
                } else r.route(loc.latitude, loc.longitude, dest.lat, dest.lon, settings.routeMode, _avoidMaybe.value, blocked)
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
