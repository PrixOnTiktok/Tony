package ca.bancroft.trails.ui

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.Location
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.bancroft.trails.data.Sun
import ca.bancroft.trails.nav.formatDist
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos

// ------------------------------------------------------------------ compass

/** Device heading in degrees (0 = north), from the rotation-vector sensor, lightly smoothed. */
@Composable
fun rememberHeading(): Float {
    val ctx = LocalContext.current
    var heading by remember { mutableFloatStateOf(0f) }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val rot = FloatArray(9); val ori = FloatArray(3)
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                SensorManager.getOrientation(rot, ori)
                val az = ((Math.toDegrees(ori[0].toDouble()) + 360) % 360).toFloat()
                var d = az - heading
                if (d > 180) d -= 360
                if (d < -180) d += 360
                heading = (heading + d * 0.2f + 360) % 360
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm?.unregisterListener(l) }
    }
    return heading
}

fun cardinal(deg: Double): String {
    val names = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return names[(((deg % 360) + 360) % 360 / 45.0 + 0.5).toInt() % 8]
}

/** Always-on compass: needle points north, label shows which way you're facing. Tap to reset the map north. */
@Composable
fun CompassWidget(heading: Float, mapBearing: Float, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, shadowElevation = 6.dp, color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.size(64.dp).semantics { contentDescription = "Compass, facing ${cardinal(heading.toDouble())}" }) {
        Box(contentAlignment = Alignment.Center) {
            val north = Color(0xFFD32F2F); val south = MaterialTheme.colorScheme.onSurfaceVariant
            Canvas(Modifier.size(40.dp).rotate(-heading)) {
                val c = Offset(size.width / 2, size.height / 2)
                val r = size.minDimension / 2
                val n = Path().apply { moveTo(c.x, c.y - r); lineTo(c.x - r * 0.28f, c.y); lineTo(c.x + r * 0.28f, c.y); close() }
                val s = Path().apply { moveTo(c.x, c.y + r); lineTo(c.x - r * 0.28f, c.y); lineTo(c.x + r * 0.28f, c.y); close() }
                drawPath(n, north); drawPath(s, south)
            }
            Text(cardinal(heading.toDouble()), Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            if (mapBearing != 0f) Icon(Icons.Filled.Navigation, null, Modifier.align(Alignment.TopEnd).size(14.dp).rotate(-mapBearing),
                tint = MaterialTheme.colorScheme.primary)
        }
    }
}

// ------------------------------------------------------------------ hold buttons

/** Needs a [holdMs] press so a bump or glove can't trigger it. */
@Composable
fun HoldButton(label: String, sub: String, color: Color, holdMs: Long, modifier: Modifier, icon: @Composable (() -> Unit)? = null, onDone: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    Box(
        modifier.background(color, RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    job = scope.launch {
                        val start = System.currentTimeMillis()
                        while (progress < 1f) { progress = ((System.currentTimeMillis() - start) / holdMs.toFloat()).coerceAtMost(1f); delay(16) }
                        onDone()
                    }
                    tryAwaitRelease()
                    job?.cancel(); progress = 0f
                })
            }
            .semantics { contentDescription = "$label, $sub" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(progress).background(Color.Black.copy(alpha = 0.22f), RoundedCornerShape(16.dp)))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp)) {
            icon?.invoke()
            if (icon != null) Spacer(Modifier.width(6.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(sub, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Small 911 button: hold for 2 seconds to open the dialer with 911 filled in. */
@Composable
fun Hold911(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    HoldButton("911", "hold 2 s", Color(0xFFB3261E), 2000, modifier.size(width = 92.dp, height = 52.dp),
        icon = { Icon(Icons.Filled.Call, null, Modifier.size(18.dp), tint = Color.White) }) {
        ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:911")))
    }
}

// ------------------------------------------------------------------ stranded panel

@Composable
fun SosPanel(vm: MapViewModel, loc: Location?, heading: Float) {
    val ctx = LocalContext.current
    val home by vm.home.collectAsState()
    var torch by remember { mutableStateOf(false) }
    var whistle by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var whistleJob by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(Unit) { onDispose { setTorch(ctx, false); whistleJob?.cancel() } }

    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Stranded? Stay with your machine.", style = MaterialTheme.typography.titleLarge)
                Text("Everything here works without signal.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton({ vm.close() }, Modifier.size(48.dp)) { Icon(Icons.Filled.Close, "Close") }
        }
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))
            if (loc == null) Text("Waiting for GPS… go to an open spot if you can.", style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    loc?.let { l ->
                        Text("%.5f, %.5f".format(l.latitude, l.longitude), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("±${l.accuracy.toInt()} m · tell 911 these numbers", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Hold911()
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SosTile(if (torch) "Light on" else "Flashlight", if (torch) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff, torch, Modifier.weight(1f)) {
                    torch = !torch; setTorch(ctx, torch)
                }
                SosTile(if (whistle) "Stop signal" else "Signal (3 blasts)", Icons.Filled.Campaign, whistle, Modifier.weight(1f)) {
                    whistle = !whistle
                    whistleJob?.cancel()
                    if (whistle) whistleJob = scope.launch { distressSignal() }
                }
                SosTile("Share spot", Icons.Filled.Share, false, Modifier.weight(1f)) { loc?.let { shareLocation(ctx, it, vm) } }
            }
            Spacer(Modifier.height(12.dp))
            loc?.let { l ->
                vm.nearestRoad(l.latitude, l.longitude)?.let { (name, d, p) ->
                    DirectionRow("Nearest road", "$name · ${formatDist(d)}", bearingTo(l.latitude, l.longitude, p.first, p.second), heading) {
                        vm.planTo(Target.Point(p.first, p.second, "Nearest road: $name"))
                    }
                }
                DirectionRow("Home", "${formatDist(MapViewModel.distM(l.latitude, l.longitude, home.lat, home.lon))} · ${home.label}",
                    bearingTo(l.latitude, l.longitude, home.lat, home.lon), heading) { vm.goHome() }
            }
            Spacer(Modifier.height(10.dp))
            val sunset = Sun.sunset(loc?.latitude ?: 45.06, loc?.longitude ?: -77.86)
            val battery = remember { batteryPct(ctx) }
            Text(buildString {
                sunset?.let { s ->
                    val left = (s - System.currentTimeMillis()) / 60000
                    append(if (left > 0) "Sunset ${SimpleDateFormat("h:mm a", Locale.CANADA).format(Date(s))} (${left / 60} h ${left % 60} min of light)" else "After sunset")
                }
                if (battery >= 0) append(" · Battery $battery%")
            }, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            listOf(
                "Save battery: dim the screen, turn on Battery Saver, close other apps.",
                "No signal? A text often gets through when a call won't. Walk to higher ground.",
                "Three of anything (blasts, flashes, fires) is the signal for help.",
                "If someone knows your route, stay put. Searchers find machines faster than people.",
            ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp)) }
        }
    }
}

@Composable
private fun SosTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 76.dp), shape = RoundedCornerShape(16.dp),
        color = if (active) Color(0xFFFFCF33) else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (active) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, Modifier.size(26.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 2)
        }
    }
}

@Composable
private fun DirectionRow(title: String, sub: String, bearing: Double, heading: Float, onRoute: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        // arrow points toward the target relative to where the phone is facing
        Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Navigation, null, Modifier.size(28.dp).rotate((bearing - heading).toFloat()), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("$title · ${cardinal(bearing)}", style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        IconButton(onRoute, Modifier.size(52.dp)) {
            Icon(if (title == "Home") Icons.Filled.Home else Icons.Filled.Directions, "Route to $title")
        }
    }
}

fun bearingTo(la1: Double, lo1: Double, la2: Double, lo2: Double): Double {
    val dx = (lo2 - lo1) * cos(Math.toRadians(la1)); val dy = la2 - la1
    return (Math.toDegrees(atan2(dx, dy)) + 360) % 360
}

fun shareLocation(ctx: Context, l: Location, vm: MapViewModel) {
    val road = vm.nearestRoad(l.latitude, l.longitude)
    val text = "I need help. I'm at %.5f, %.5f (±%d m), on an ATV near %s. https://maps.google.com/?q=%.5f,%.5f".format(
        l.latitude, l.longitude, l.accuracy.toInt(), road?.first ?: "Bancroft area", l.latitude, l.longitude)
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share location"))
}

private fun setTorch(ctx: Context, on: Boolean) {
    runCatching {
        val cm = ctx.getSystemService(CameraManager::class.java)
        val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } ?: return
        cm.setTorchMode(id, on)
    }
}

/** International distress signal: three loud blasts, pause, repeat. */
private suspend fun distressSignal() {
    val tg = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME) }.getOrNull() ?: return
    try {
        while (kotlin.coroutines.coroutineContext.isActive) {
            repeat(3) { tg.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 600); delay(900) }
            delay(1800)
        }
    } finally { tg.release() }
}

private fun batteryPct(ctx: Context): Int =
    runCatching { ctx.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) }.getOrDefault(-1)

