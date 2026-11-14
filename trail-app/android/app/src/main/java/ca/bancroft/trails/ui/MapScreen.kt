package ca.bancroft.trails.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.WbTwilight
import ca.bancroft.trails.data.MapStyle
import ca.bancroft.trails.data.RideRecorder
import ca.bancroft.trails.data.Sun
import ca.bancroft.trails.map.MapController
import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import ca.bancroft.trails.nav.formatDist
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView

@Composable
fun MapScreen(vm: MapViewModel, dark: Boolean, speak: (String) -> Unit) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val data by vm.data.collectAsState()
    val mode by vm.mode.collectAsState()
    val pins by vm.pins.collectAsState()
    val layers by vm.layers.collectAsState()
    val location by vm.locationRepo.location.collectAsState()
    val cameraReq by vm.camera.collectAsState()
    val mapStyle by vm.mapStyle.collectAsState()
    val track by vm.shownTrack.collectAsState()
    val recording by vm.recording.collectAsState()
    var online by remember { mutableStateOf(isOnline(ctx)) }
    var confirmStopRec by remember { mutableStateOf(false) }
    val home by vm.home.collectAsState()
    val userTrails by vm.userTrails.collectAsState()
    val ridden by vm.ridden.collectAsState()
    val showRidden by vm.showRidden.collectAsState()
    val leaveBy by vm.leaveBy.collectAsState()
    val heading = rememberHeading()
    val weather by vm.weather.collectAsState()
    var showWeather by remember { mutableStateOf(false) }

    var controller by remember { mutableStateOf<MapController?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    var following by remember { mutableStateOf(false) }
    var bearing by remember { mutableFloatStateOf(0f) }
    var panelPx by remember { mutableIntStateOf(0) }
    var topPx by remember { mutableIntStateOf(0) }
    var hasPermission by remember { mutableStateOf(hasLocationPermission(ctx)) }
    var showLayers by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showLegend by remember { mutableStateOf(false) }
    var showIntro by remember { mutableStateOf(!vm.settings.seenIntro) }
    var pinDraft by remember { mutableStateOf<Target?>(null) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        hasPermission = hasLocationPermission(ctx) || res.values.any { it }
    }
    fun askLocation() = permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun askNotifications() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    // network state, for the satellite layer
    DisposableEffect(Unit) {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { online = true }
            override fun onLost(network: Network) { online = isOnline(ctx) }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }
        onDispose { runCatching { cm.unregisterNetworkCallback(cb) } }
    }

    // ---- MapView with lifecycle
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        val opts = MapLibreMapOptions.createFromAttributes(ctx)
            .camera(CameraPosition.Builder().target(LatLng(vm.settings.lastLat, vm.settings.lastLon)).zoom(vm.settings.lastZoom).build())
            .attributionEnabled(true).logoEnabled(false).compassEnabled(false)
            .minZoomPreference(7.0).maxZoomPreference(18.0)
        MapView(ctx, opts).apply { onCreate(null) }
    }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> {
                    controller?.map?.cameraPosition?.let { c -> c.target?.let { vm.saveCamera(it.latitude, it.longitude, c.zoom) } }
                    mapView.onStop()
                }
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(Unit) {
        mapView.getMapAsync { map: MapLibreMap ->
            val c = MapController(ctx, map)
            map.uiSettings.isRotateGesturesEnabled = true
            map.uiSettings.isTiltGesturesEnabled = false
            map.uiSettings.setAttributionMargins(0, 0, 0, 0)
            map.addOnCameraMoveListener { bearing = map.cameraPosition.bearing.toFloat() }
            map.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) following = false
            }
            map.addOnMapClickListener { ll ->
                val hit = c.hitTest(map.projection.toScreenLocation(ll))
                val d = vm.data.value
                when (hit) {
                    is MapController.Hit.PinHit -> vm.pins.value.firstOrNull { it.id == hit.id }?.let { vm.select(Target.MyPin(it)) }
                    is MapController.Hit.HomeHit -> vm.select(vm.home.value)
                    is MapController.Hit.UserTrailHit -> vm.userTrails.value.firstOrNull { it.id == hit.id }?.let {
                        vm.select(Target.YourTrail(it, ll.latitude, ll.longitude))
                    }
                    is MapController.Hit.PoiHit -> {
                        val f = hit.f
                        val lat = f.getNumberProperty("lat").toDouble(); val lon = f.getNumberProperty("lon").toDouble()
                        d?.pois?.firstOrNull { it.lat == lat && it.lon == lon && it.kind == f.getStringProperty("kind") }
                            ?.let { vm.select(Target.Place(it)) }
                    }
                    is MapController.Hit.TrailHit -> {
                        val f = hit.f
                        val t = Target.Trail(ll.latitude, ll.longitude,
                            if (f.hasProperty("n")) f.getStringProperty("n") else null,
                            f.getNumberProperty("c").toInt(), f.getNumberProperty("k").toInt(), f.getNumberProperty("r").toInt(),
                            if (f.hasProperty("t")) f.getStringProperty("t") else null,
                            f.getNumberProperty("q").toInt(), f.getNumberProperty("f").toInt())
                        vm.select(t)
                        c.setSelection(t, f)
                    }
                    null -> if (vm.mode.value !is Mode.Navigating) vm.close()
                }
                true
            }
            map.addOnMapLongClickListener { ll ->
                if (vm.mode.value !is Mode.Navigating) vm.select(Target.Point(ll.latitude, ll.longitude))
                true
            }
            controller = c
        }
    }
    LaunchedEffect(controller, dark) {
        val c = controller ?: return@LaunchedEffect
        styleReady = false
        c.loadStyle(dark) { styleReady = true }
    }
    // feed content to the map
    LaunchedEffect(styleReady, data, layers) { if (styleReady) data?.let { controller?.setPois(it.pois, layers) } }
    LaunchedEffect(styleReady, pins) { if (styleReady) controller?.setPins(pins) }
    LaunchedEffect(online, data != null) { if (online) vm.refreshWeather(force = vm.weather.value == null) }
    LaunchedEffect(styleReady, mapStyle) { if (styleReady) controller?.setMapStyle(mapStyle) }
    LaunchedEffect(styleReady, track) { if (styleReady) controller?.setTrack(track) }
    LaunchedEffect(styleReady, home) { if (styleReady) controller?.setHome(home) }
    LaunchedEffect(styleReady, userTrails) { if (styleReady) controller?.setUserTrails(userTrails) }
    LaunchedEffect(styleReady, ridden, showRidden) { if (styleReady) controller?.setRidden(if (showRidden) ridden else emptyList()) }
    LaunchedEffect(styleReady, mode) {
        val c = controller ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        when (val m = mode) {
            is Mode.Plan -> { c.setRoutes(m.routes, m.selected); c.setDest(m.dest) }
            is Mode.Navigating -> { c.setRoutes(mapOf(m.route.mode to m.route), m.route.mode); c.setDest(m.dest) }
            is Mode.Details -> { c.setRoutes(emptyMap(), null); c.setDest(null); if (m.target !is Target.Trail) c.setSelection(null) }
            is Mode.Loop -> { c.setRoutes(m.route?.let { mapOf(m.mode to it) } ?: emptyMap(), m.mode); c.setDest(null); c.setSelection(null) }
            is Mode.Idea -> { c.setRoutes(m.route?.let { mapOf(it.mode to it) } ?: emptyMap(), m.route?.mode); c.setDest(null); c.setSelection(null) }
            else -> { c.setRoutes(emptyMap(), null); c.setDest(null); c.setSelection(null) }
        }
    }
    // location
    LaunchedEffect(hasPermission, styleReady, mode is Mode.Navigating, recording != null) {
        if (hasPermission) {
            vm.locationRepo.start(fastUpdates = vm.wantFastGps())
            if (styleReady) controller?.enableLocation { vm.showMe() }
        }
    }
    LaunchedEffect(location) {
        val loc = location ?: return@LaunchedEffect
        controller?.pushLocation(loc)
        vm.onLocation(loc)
    }
    // camera requests
    LaunchedEffect(cameraReq, styleReady) {
        val c = controller ?: return@LaunchedEffect
        val req = cameraReq ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        when (req) {
            is MapViewModel.CameraRequest.FlyTo -> { following = false; c.flyTo(req.lat, req.lon, req.zoom) }
            is MapViewModel.CameraRequest.Fit -> { following = false; c.fit(req.route, topPx, panelPx) }
            is MapViewModel.CameraRequest.FitPoints -> { following = false; c.fitPoints(req.pts, topPx, panelPx) }
            MapViewModel.CameraRequest.FollowMe -> { following = true; c.follow(vm.mode.value is Mode.Navigating, panelPx, topPx) }
        }
        vm.consumeCamera()
    }
    // navigation voice + haptics
    val navStage = remember { mutableStateOf(Triple(-1.0, 0, "")) } // (maneuver distance, stage, text)
    LaunchedEffect(mode) {
        val m = mode as? Mode.Navigating ?: return@LaunchedEffect
        val st = m.state ?: return@LaunchedEffect
        if (st.arrived) { speak("You have arrived."); vm.stopNav(); return@LaunchedEffect }
        val next = st.next ?: return@LaunchedEffect
        val (key, stage, _) = navStage.value
        val curStage = if (key == next.distFromStart) stage else 0
        val d = st.distToNextM
        if (d < 70 && curStage < 2) {
            speak(next.text); vibrate(ctx); navStage.value = Triple(next.distFromStart, 2, next.text)
        } else if (d in 70.0..450.0 && curStage < 1) {
            speak("In ${formatDist(d)}, ${next.text.replaceFirstChar { it.lowercase() }}"); navStage.value = Triple(next.distFromStart, 1, next.text)
        }
    }

    BackHandler(enabled = mode !is Mode.Browse || showLayers || showSettings || showLegend || pinDraft != null) {
        when {
            pinDraft != null -> pinDraft = null
            showLayers -> showLayers = false
            showSettings -> showSettings = false
            showLegend -> showLegend = false
            else -> vm.back()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        val navigating = mode as? Mode.Navigating
        // ---- top: offline chip / nav banner
        Column(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 12.dp, vertical = 8.dp)
                .onGloballyPositioned { topPx = it.size.height + it.positionInWindow().y.toInt() },
        ) {
            if (navigating != null) {
                NavBanner(navigating, dark)
            } else {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f, fill = false).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusChip(data != null, data?.meta?.osmDate, mapStyle, online)
                        weather?.takeIf { it.covers() }?.let { WeatherChip(it) { showWeather = true } }
                        recording?.let { RecChip(it) { confirmStopRec = true } }
                        SunsetChip(location, leaveBy)
                    }
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CompassWidget(heading, bearing) { controller?.resetNorth() }
                        Surface(onClick = { vm.openSos() }, shape = RoundedCornerShape(50), color = Color(0xFFB3261E), contentColor = Color.White,
                            shadowElevation = 4.dp, modifier = Modifier.size(width = 64.dp, height = 40.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text("SOS", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }

        // ---- bottom: floating buttons + panel
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing),
            horizontalAlignment = Alignment.End,
        ) {
            Column(Modifier.padding(end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.End) {
                if (navigating == null) {
                    RoundButton(onClick = { showLayers = true }, size = 56) { Icon(Icons.Filled.Layers, "Map style and layers") }
                }
                RoundButton(
                    onClick = {
                        when {
                            !hasPermission -> askLocation()
                            following && navigating == null -> vm.showMe()
                            else -> vm.followMe()
                        }
                    },
                    size = 64, primary = following,
                ) {
                    Icon(if (following) Icons.Filled.Explore else Icons.Filled.MyLocation, if (following) "Following you" else "Show my location", Modifier.size(30.dp))
                }
            }
            Box(Modifier.fillMaxWidth().onGloballyPositioned { panelPx = it.size.height }) {
                AnimatedContent(
                    targetState = mode,
                    contentKey = { it::class },
                    transitionSpec = { (slideInVertically { it / 3 } + fadeIn()) togetherWith (slideOutVertically { it / 3 } + fadeOut()) },
                    label = "panel",
                ) { m ->
                    when (m) {
                        is Mode.Browse -> BrowsePanel(vm, recording != null, onSettings = { showSettings = true }, onLegend = { showLegend = true },
                            onLoop = { if (!hasPermission) askLocation() else vm.openLoop() },
                            onRecord = {
                                if (recording != null) confirmStopRec = true
                                else if (!hasPermission) askLocation() else { askNotifications(); vm.startRecording() }
                            })
                        is Mode.Details -> DetailsPanel(vm, m.target, location, onSavePin = { pinDraft = it })
                        is Mode.Loop -> LoopPanel(vm, m, onStart = { askNotifications(); vm.startLoop() })
                        is Mode.Rides -> RidesPanel(vm)
                        is Mode.Sos -> SosPanel(vm, location, heading)
                        is Mode.Ideas -> IdeasPanel(vm, location)
                        is Mode.Idea -> IdeaPanel(vm, m, onStart = { askNotifications(); vm.startIdea() })
                        is Mode.RideView -> RideViewPanel(vm, m.ride)
                        is Mode.Nearby -> NearbyPanel(vm, m.category, location)
                        is Mode.Search -> SearchPanel(vm)
                        is Mode.Plan -> PlanPanel(vm, m, hasLocation = location != null, onNeedPermission = { askLocation() }, onStart = { askNotifications(); vm.startNav() })
                        is Mode.Navigating -> NavBottomBar(vm, m, onRecenter = { vm.followMe() }, following = following, speedKmh = (location?.speed ?: 0f) * 3.6)
                    }
                }
            }
        }

        if (data == null) {
            Surface(Modifier.align(Alignment.Center), shape = RoundedCornerShape(20.dp), tonalElevation = 6.dp, shadowElevation = 6.dp) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Loading trails…", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    if (showLayers) LayersSheet(vm, online, onDismiss = { showLayers = false })
    if (showWeather) WeatherSheet(vm, online, onDismiss = { showWeather = false })
    if (confirmStopRec) StopRecordingDialog(recording, onDismiss = { confirmStopRec = false }, onStop = { save ->
        confirmStopRec = false
        vm.stopRecording(save)?.let { vm.viewRide(it) }
    })
    if (showSettings) SettingsSheet(vm, onDismiss = { showSettings = false }, onLegend = { showSettings = false; showLegend = true })
    if (showLegend) LegendSheet(vm, dark, onDismiss = { showLegend = false })
    pinDraft?.let { t -> PinDialog(t, onDismiss = { pinDraft = null }, onSave = { type, name, note ->
        vm.addPin(t.lat, t.lon, type, name, note); pinDraft = null
    }) }
    if (showIntro) IntroDialog(onDone = {
        vm.settings.seenIntro = true; showIntro = false
        if (!hasPermission) askLocation()
    })
}

@Composable
fun StatusChip(ready: Boolean, osmDate: String?, style: MapStyle, online: Boolean) {
    val satNoSignal = style == MapStyle.SATELLITE && !online
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 3.dp) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (satNoSignal) Icons.Filled.SignalWifiOff else Icons.Outlined.CloudOff, null, Modifier.size(16.dp),
                tint = if (satNoSignal) warnColor() else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            val text = when {
                satNoSignal -> "Satellite needs signal · showing saved"
                style == MapStyle.SATELLITE -> "Satellite · trails offline"
                style == MapStyle.NETWORK -> "Trail network view"
                ready -> "Works offline · data ${osmDate ?: ""}"
                else -> "Works offline"
            }
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RecChip(live: RideRecorder.Live, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = Color(0xFFB3261E), contentColor = Color.White, shadowElevation = 3.dp) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.FiberManualRecord, null, Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            val mins = ((System.currentTimeMillis() - live.start) / 60000).toInt()
            Text("Recording · ${formatDist(live.distanceM)} · ${mins / 60}:${"%02d".format(mins % 60)}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Shows up in the last 90 minutes before sunset so you're not caught out after dark. */
@Composable
private fun SunsetChip(loc: android.location.Location?, leaveBy: Long?) {
    val lat = loc?.latitude ?: 45.0575; val lon = loc?.longitude ?: -77.8553
    val sunset = Sun.sunset(lat, lon) ?: return
    val left = (sunset - System.currentTimeMillis()) / 60000
    val leaveIn = leaveBy?.let { (it - System.currentTimeMillis()) / 60000 }
    // "leave by" shows up to an hour ahead so you get home before dark
    if (leaveIn != null && leaveIn in -60..60) {
        Surface(shape = RoundedCornerShape(50), color = Color(0xFF3E2723), contentColor = Color(0xFFFFCC80), shadowElevation = 3.dp) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.WbTwilight, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (leaveIn > 0) "Head home by ${SimpleDateFormat("h:mm a", Locale.CANADA).format(Date(leaveBy))} to beat dark"
                    else "Head home now to beat dark", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        return
    }
    if (left !in 0..90) return
    Surface(shape = RoundedCornerShape(50), color = Color(0xFF3E2723), contentColor = Color(0xFFFFCC80), shadowElevation = 3.dp) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.WbTwilight, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Sunset in $left min (${SimpleDateFormat("h:mm a", Locale.CANADA).format(Date(sunset))})",
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun isOnline(ctx: Context): Boolean {
    val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

@Composable
fun RoundButton(onClick: () -> Unit, size: Int, primary: Boolean = false, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick, shape = CircleShape, shadowElevation = 6.dp, tonalElevation = 2.dp,
        color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(size.dp),
    ) { Box(contentAlignment = Alignment.Center) { content() } }
}

private fun hasLocationPermission(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun vibrate(ctx: Context) {
    runCatching {
        val vm = ctx.getSystemService(VibratorManager::class.java)
        vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 120, 180), -1))
    }
}
