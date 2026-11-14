package ca.bancroft.trails.ui

import android.location.Location
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.bancroft.trails.data.Cls
import ca.bancroft.trails.data.Flag
import ca.bancroft.trails.data.PinType
import ca.bancroft.trails.data.RouteMode
import ca.bancroft.trails.data.ThemeMode
import ca.bancroft.trails.data.kindLabel
import ca.bancroft.trails.data.MapStyle
import ca.bancroft.trails.data.Ride
import ca.bancroft.trails.nav.formatSpeed
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Satellite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.Explore
import ca.bancroft.trails.nav.formatDist
import ca.bancroft.trails.nav.formatTime
import ca.bancroft.trails.routing.Route
import ca.bancroft.trails.routing.Turn
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ------------------------------------------------------------------ building blocks

@Composable
fun Panel(content: @Composable () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 8.dp),
        shape = RoundedCornerShape(28.dp), tonalElevation = 3.dp, shadowElevation = 10.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
fun ClsBadge(cls: Int, dark: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
    val bg = Palette.cls(cls, dark)
    Surface(shape = RoundedCornerShape(8.dp), color = bg) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when (cls) { Cls.FREE -> Icons.Filled.Star; Cls.MAYBE -> Icons.Filled.Warning; Cls.ROAD -> Icons.Filled.Directions; else -> Icons.Filled.Close },
                null, Modifier.size(16.dp), tint = Palette.onCls(cls, dark),
            )
            Spacer(Modifier.width(6.dp))
            Text(clsLabel(cls), color = Palette.onCls(cls, dark), style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/** Line sample matching the map style (solid / dashed / dotted) so the key is readable without colour. */
@Composable
fun LineSample(cls: Int, dark: Boolean, modifier: Modifier = Modifier.size(width = 56.dp, height = 20.dp)) {
    val color = Palette.cls(cls, dark)
    val casing = if (dark) Color.Black else Color(0xB31A1A1A)
    Canvas(modifier) {
        val y = size.height / 2
        when (cls) {
            Cls.FREE -> {
                drawLine(casing, Offset(4f, y), Offset(size.width - 4f, y), strokeWidth = 11.dp.toPx(), cap = StrokeCap.Round)
                drawLine(color, Offset(4f, y), Offset(size.width - 4f, y), strokeWidth = 7.dp.toPx(), cap = StrokeCap.Round)
            }
            Cls.MAYBE -> {
                drawLine(casing, Offset(4f, y), Offset(size.width - 4f, y), strokeWidth = 11.dp.toPx(), cap = StrokeCap.Round)
                drawLine(color, Offset(4f, y), Offset(size.width - 4f, y), strokeWidth = 7.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(18.dp.toPx(), 9.dp.toPx())))
            }
            Cls.ROAD -> {
                drawLine(color, Offset(4f, y), Offset(size.width - 4f, y), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
                drawLine(if (dark) Color(0xFF1C1F22) else Color.White, Offset(4f, y), Offset(size.width - 4f, y), strokeWidth = 1.5.dp.toPx())
            }
            else -> {
                drawLine(color, Offset(4f, y), Offset(size.width - 4f, y), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.5f, 9.dp.toPx())))
            }
        }
    }
}

/** Proportional bar of how a route splits between classes. */
@Composable
fun ClassBar(r: Route, dark: Boolean) {
    val total = r.kmByCls.sum().coerceAtLeast(0.001)
    Row(Modifier.fillMaxWidth().height(12.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))) {
        for (c in listOf(Cls.FREE, Cls.ROAD, Cls.MAYBE, Cls.NO)) {
            val f = (r.kmByCls[c] / total).toFloat()
            if (f > 0.005f) Box(Modifier.weight(f).fillMaxHeight().background(Palette.cls(c, dark)))
        }
    }
}

@Composable
private fun BigButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick, modifier.heightIn(min = 60.dp), enabled = enabled, shape = RoundedCornerShape(18.dp)) {
        if (icon != null) { Icon(icon, null, Modifier.size(24.dp)); Spacer(Modifier.width(10.dp)) }
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PanelHeader(title: String, subtitle: String? = null, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClose, Modifier.size(48.dp)) { Icon(Icons.Filled.Close, "Close") }
    }
}

private fun distLabel(loc: Location?, lat: Double, lon: Double): String? =
    loc?.let { formatDist(MapViewModel.distM(it.latitude, it.longitude, lat, lon)) + " away" }

// ------------------------------------------------------------------ browse

@Composable
fun BrowsePanel(vm: MapViewModel, recording: Boolean, onSettings: () -> Unit, onLegend: () -> Unit, onLoop: () -> Unit, onRecord: () -> Unit) {
    Panel {
        Surface(
            onClick = { vm.openSearch() }, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Search, null)
                Spacer(Modifier.width(12.dp))
                Text("Search gas, food, trails, pins", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryButton("Gas", Icons.Filled.LocalGasStation, Color(0xFFE8710A), Modifier.weight(1f)) { vm.showNearby("gas") }
            CategoryButton("Food", Icons.Filled.Restaurant, Color(0xFFC2185B), Modifier.weight(1f)) { vm.showNearby("food") }
            CategoryButton("Places", Icons.Filled.Place, Color(0xFF6A4FB3), Modifier.weight(1f)) { vm.showNearby("poi") }
            CategoryButton("Pins", Icons.Filled.PushPin, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f)) { vm.showNearby("pins") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton({ vm.goHome() }, Modifier.weight(0.8f).heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) {
                Icon(Icons.Filled.Home, null); Spacer(Modifier.width(6.dp)); Text("Home", fontSize = 16.sp)
            }
            FilledTonalButton({ vm.openIdeas() }, Modifier.weight(0.9f).heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) {
                Icon(Icons.Filled.Explore, null); Spacer(Modifier.width(6.dp)); Text("Rides", fontSize = 16.sp)
            }
            FilledTonalButton(onRecord, Modifier.weight(1.1f).heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                colors = if (recording) ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFB3261E), contentColor = Color.White)
                else ButtonDefaults.filledTonalButtonColors()) {
                Icon(if (recording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord, null, tint = if (recording) Color.White else Color(0xFFD32F2F))
                Spacer(Modifier.width(6.dp)); Text(if (recording) "Stop" else "Record", fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onLegend, Modifier.heightIn(min = 48.dp)) { Icon(Icons.Outlined.Map, null); Spacer(Modifier.width(6.dp)); Text("Map key") }
            TextButton({ vm.openRides() }, Modifier.heightIn(min = 48.dp)) { Icon(Icons.Filled.History, null); Spacer(Modifier.width(6.dp)); Text("My rides") }
            Spacer(Modifier.weight(1f))
            IconButton(onSettings, Modifier.size(48.dp)) { Icon(Icons.Filled.Settings, "Settings") }
        }
    }
}

@Composable
private fun CategoryButton(label: String, icon: ImageVector, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.height(64.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ------------------------------------------------------------------ details

@Composable
fun DetailsPanel(vm: MapViewModel, t: Target, loc: Location?, onSavePin: (Target) -> Unit) {
    val data by vm.data.collectAsState()
    val meta = data?.meta
    Panel {
        val subtitle = when (t) {
            is Target.Trail -> if (t.kind == ca.bancroft.trails.data.Kind.PATH && t.flags and Flag.CLUB != 0) "Rail / club trail" else meta?.kinds?.getOrNull(t.kind)
            is Target.Place -> kindLabel(t.poi.kind)
            is Target.MyPin -> "My pin · ${t.pin.type.label}"
            is Target.Point -> "%.5f, %.5f".format(t.lat, t.lon)
            is Target.Me -> "±${t.accuracyM.toInt()} m GPS"
            is Target.Home -> t.label + if (t.exact) "" else " (estimated spot)"
            is Target.YourTrail -> "Ridden ${SimpleDateFormat("MMM d, yyyy", Locale.CANADA).format(Date(t.trail.created))} · ${formatDist(t.trail.lengthM)}"
        }
        PanelHeader(t.title, listOfNotNull(subtitle, distLabel(loc, t.lat, t.lon)).joinToString(" · ")) { vm.close() }
        Spacer(Modifier.height(10.dp))
        when (t) {
            is Target.Trail -> {
                ClsBadge(t.cls)
                Spacer(Modifier.height(10.dp))
                Text(meta?.reason(t.reason, t.note) ?: "", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                SourcesRow(t.flags, t.conf)
                if (t.flags and Flag.GATE != 0) WarnLine("Gate or barrier recorded nearby (MNRF). It may be locked.")
                if (t.kind >= 3 && t.kind != 6) {
                    val rule = meta?.ruleFor(t.note)
                    rule?.optString("bylaw")?.takeIf { it.isNotBlank() && it != "null" }?.let {
                        Text("By-law: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("Always follow posted signs. They overrule this map.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
            is Target.Place -> {
                t.poi.ele?.let { Text("Elevation ${it.toInt()} m", style = MaterialTheme.typography.bodyMedium) }
            }
            is Target.MyPin -> {
                if (t.pin.note.isNotBlank()) Text(t.pin.note, style = MaterialTheme.typography.bodyLarge)
                Text("Saved " + SimpleDateFormat("MMM d, yyyy", Locale.CANADA).format(Date(t.pin.created)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (t.pin.type == PinType.BLOCKED) WarnLine("Routes avoid this spot.")
            }
            is Target.Point -> {}
            is Target.Me -> { MyLocationDetails(vm, t); return@Panel }
            is Target.Home -> {
                if (!t.exact) Text("Placed from the address (rural numbers are ~10 m apart), so it may be off a bit. " +
                    "Next time you're in the driveway, tap \"I'm here\".", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton("Route home", Icons.Filled.Home, { vm.goHome() }, Modifier.weight(1f))
                    OutlinedButton({ loc?.let { vm.setHome(it.latitude, it.longitude) } }, Modifier.heightIn(min = 60.dp),
                        shape = RoundedCornerShape(18.dp), enabled = loc != null) { Icon(Icons.Filled.MyLocation, null); Spacer(Modifier.width(6.dp)); Text("I'm here") }
                }
                return@Panel
            }
            is Target.YourTrail -> {
                Text("You rode this where no map shows a trail, so it's saved as your own and routes can use it. " +
                    "Make sure you're allowed there (Crown land or landowner's OK).", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton("Route here", Icons.Filled.Directions, { vm.planTo(t) }, Modifier.weight(1f))
                    OutlinedButton({ vm.deleteUserTrail(t.trail) }, Modifier.heightIn(min = 60.dp), shape = RoundedCornerShape(18.dp)) {
                        Icon(Icons.Filled.Delete, null); Spacer(Modifier.width(6.dp)); Text("Remove")
                    }
                }
                return@Panel
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            BigButton("Route here", Icons.Filled.Directions, { vm.planTo(t) }, Modifier.weight(1f))
            when (t) {
                is Target.MyPin -> OutlinedButton({ vm.deletePin(t.pin) }, Modifier.heightIn(min = 60.dp), shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Filled.Delete, null); Spacer(Modifier.width(6.dp)); Text("Delete")
                }
                else -> FilledTonalButton({ onSavePin(t) }, Modifier.heightIn(min = 60.dp), shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Filled.PushPin, null); Spacer(Modifier.width(6.dp)); Text("Pin")
                }
            }
        }
        if (t is Target.Point) TextButton({ vm.setHome(t.lat, t.lon) }, Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Filled.Home, null); Spacer(Modifier.width(6.dp)); Text("Make this home")
        }
    }
}

@Composable
private fun SourcesRow(flags: Int, conf: Int) {
    val items = buildList {
        if (flags and Flag.OSM != 0) add("OpenStreetMap")
        if (flags and Flag.MNRF != 0) add("MNRF forest roads")
        if (flags and Flag.OTN != 0) add("Ontario Trail Network")
        if (flags and Flag.CROWN != 0) add("on Crown land")
        if (flags and Flag.PRIVATE != 0) add("not Crown land")
        if (flags and Flag.PARK != 0) add("inside park/reserve")
    }
    val confText = when (conf) { 2 -> "High confidence"; 1 -> "Medium confidence"; else -> "Low confidence" }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Info, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text("$confText · ${items.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WarnLine(text: String) {
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Warning, null, Modifier.size(18.dp), tint = Color(0xFFB26A00))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MyLocationDetails(vm: MapViewModel, t: Target.Me) {
    val ctx = LocalContext.current
    val data by vm.data.collectAsState()
    val road = remember(t) { data?.graph?.snap(t.lat, t.lon, 1500.0) { data!!.graph.name(it) != null } }
    Text("%.5f, %.5f".format(t.lat, t.lon), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(toDms(t.lat, t.lon), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    road?.let { Text("Near ${data!!.graph.name(it.edge)} (${formatDist(it.distM)})", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp)) }
    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        t.altitudeM?.let { Text("Elevation ${it.toInt()} m", style = MaterialTheme.typography.bodyMedium) }
        Text(formatSpeed(t.speedKmh), style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        BigButton("Share", Icons.Filled.Share, {
            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,
                "I'm at %.5f, %.5f (ATV, near %s). https://maps.google.com/?q=%.5f,%.5f".format(
                    t.lat, t.lon, road?.let { data!!.graph.name(it.edge) } ?: "Bancroft area", t.lat, t.lon)), "Share location"))
        }, Modifier.weight(1f))
        FilledTonalButton({ vm.openSos() }, Modifier.heightIn(min = 60.dp), shape = RoundedCornerShape(18.dp)) {
            Icon(Icons.Filled.Sos, null); Spacer(Modifier.width(6.dp)); Text("Stranded?")
        }
    }
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Pins: long-press the map. Home: open Home and tap \"I'm here\".", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Hold911()
    }
}

private fun toDms(lat: Double, lon: Double): String {
    fun f(v: Double, pos: String, neg: String): String {
        val a = kotlin.math.abs(v); val d = a.toInt(); val m = ((a - d) * 60).toInt(); val s = (((a - d) * 60 - m) * 60)
        return "$d°$m'${"%.1f".format(s)}\" ${if (v >= 0) pos else neg}"
    }
    return f(lat, "N", "S") + "  " + f(lon, "E", "W")
}

// ------------------------------------------------------------------ nearby + search

@Composable
fun NearbyPanel(vm: MapViewModel, category: String, loc: Location?) {
    val pins by vm.pins.collectAsState()
    val title = when (category) { "gas" -> "Gas nearby"; "food" -> "Food nearby"; "poi" -> "Places to see"; else -> "My pins" }
    val lat = loc?.latitude ?: vm.settings.lastLat
    val lon = loc?.longitude ?: vm.settings.lastLon
    Panel {
        PanelHeader(title, if (loc == null) "Distances from the map centre (no GPS yet)" else "Closest first", onClose = { vm.close() })
        Spacer(Modifier.height(6.dp))
        LazyColumn(Modifier.heightIn(max = 380.dp)) {
            if (category == "pins") {
                val list = pins.map { it to MapViewModel.distM(lat, lon, it.lat, it.lon) }.sortedBy { it.second }
                if (list.isEmpty()) item { Text("No pins yet. Long-press the map to add one.", Modifier.padding(vertical = 16.dp)) }
                items(list, key = { it.first.id }) { (p, d) ->
                    ResultRow(p.type.emoji, p.name.ifBlank { p.type.label }, p.type.label, formatDist(d)) {
                        vm.select(Target.MyPin(p)); vm.flyTo(Target.MyPin(p))
                    }
                }
            } else {
                val list = vm.nearby(category, lat, lon)
                items(list, key = { "${it.first.lat},${it.first.lon},${it.first.kind}" }) { (p, d) ->
                    ResultRow(
                        when (p.cat) { "gas" -> "⛽"; "food" -> "🍴"; else -> "★" },
                        p.title, kindLabel(p.kind), formatDist(d),
                    ) { vm.select(Target.Place(p)); vm.flyTo(Target.Place(p)) }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(icon: String, title: String, subtitle: String, trailing: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = 20.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SearchPanel(vm: MapViewModel) {
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    var results by remember { mutableStateOf<List<Target>>(emptyList()) }
    LaunchedEffect(q) { delay(150); results = vm.search(q) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ vm.close() }, Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            OutlinedTextField(
                q, { q = it }, Modifier.weight(1f).focusRequester(focus), singleLine = true,
                placeholder = { Text("Gas, Coe Hill, trail name…") }, shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = {}),
            )
        }
        LazyColumn(Modifier.heightIn(max = 340.dp)) {
            items(results) { t ->
                val (icon, sub) = when (t) {
                    is Target.Place -> (if (t.poi.cat == "gas") "⛽" else if (t.poi.cat == "food") "🍴" else "★") to kindLabel(t.poi.kind)
                    is Target.MyPin -> t.pin.type.emoji to "My pin"
                    is Target.Trail -> "〰" to clsLabel(t.cls)
                    is Target.Point -> "•" to ""
                    is Target.Me -> "◉" to ""
                    is Target.Home -> "🏠" to ""
                    is Target.YourTrail -> "〰" to "Your trail"
                }
                ResultRow(icon, t.title, sub, null) { vm.select(t); vm.flyTo(t, if (t is Target.Trail) 14.0 else 15.0) }
            }
        }
    }
}

// ------------------------------------------------------------------ route planning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanPanel(vm: MapViewModel, m: Mode.Plan, hasLocation: Boolean, onNeedPermission: () -> Unit, onStart: () -> Unit) {
    val avoidMaybe by vm.avoidMaybe.collectAsState()
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Panel {
        PanelHeader("To ${m.dest.title}", null) { vm.back() }
        if (!hasLocation) {
            Text("Waiting for your GPS location. Make sure location is on and you're outside.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onNeedPermission, Modifier.heightIn(min = 56.dp)) { Icon(Icons.Filled.MyLocation, null); Spacer(Modifier.width(6.dp)); Text("Allow location") }
                Button({ vm.planTo(m.dest) }, Modifier.heightIn(min = 56.dp)) { Text("Try again") }
            }
            return@Panel
        }
        Spacer(Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            RouteMode.entries.forEachIndexed { i, mode ->
                val r = m.routes[mode]
                SegmentedButton(
                    selected = m.selected == mode, onClick = { vm.chooseMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(i, RouteMode.entries.size),
                    modifier = Modifier.heightIn(min = 64.dp), icon = {},
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(mode.short, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        Text(r?.let { formatTime(it.timeS) } ?: if (m.computing) "…" else "—", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val r = m.routes[m.selected]
        when {
            m.computing -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Finding the best way…", Modifier.padding(top = 8.dp)) }
            r == null -> Text("No route found. The destination may be off the mapped network.", style = MaterialTheme.typography.bodyLarge)
            else -> {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(formatTime(r.timeS), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Text("${formatDist(r.distanceM)} · ${(r.trailShare * 100).toInt()}% off-road", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
                }
                Spacer(Modifier.height(8.dp))
                ClassBar(r, dark)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (c in listOf(Cls.FREE, Cls.ROAD, Cls.MAYBE, Cls.NO)) if (r.kmByCls[c] > 0.05) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(Palette.cls(c, dark), CircleShape))
                            Spacer(Modifier.width(4.dp))
                            Text("${clsShort(c).lowercase().replaceFirstChar { it.uppercase() }} %.1f km".format(r.kmByCls[c]), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                r.warnings.forEach { WarnLine(it) }
                FuelLine(vm, r)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().clickable { vm.setAvoidMaybe(!avoidMaybe) }.heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Avoid \"maybe\" trails", style = MaterialTheme.typography.titleMedium)
                Text("Skip trails that might need a pass or permission", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(avoidMaybe, { vm.setAvoidMaybe(it) })
        }
        val useSnow by vm.useSnow.collectAsState()
        Row(Modifier.fillMaxWidth().clickable { vm.setUseSnow(!useSnow) }.heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Use snowmobile trails", style = MaterialTheme.typography.titleMedium)
                Text("Many are winter-only or on private land in summer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(useSnow, { vm.setUseSnow(it) })
        }
        Spacer(Modifier.height(8.dp))
        BigButton("Start", Icons.Filled.Directions, onStart, Modifier.fillMaxWidth(), enabled = r != null && !m.computing)
    }
}

/** Compares the route with the tank range set in Settings (hidden when no range is set). */
@Composable
private fun FuelLine(vm: MapViewModel, r: Route) {
    val range by vm.fuelRange.collectAsState()
    if (range <= 0) return
    val km = r.distanceM / 1000.0
    val pct = km / range
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.LocalGasStation, null, Modifier.size(18.dp), tint = if (pct > 0.8) Color(0xFFB26A00) else MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Text(
            if (pct > 0.8) "Uses ${(pct * 100).toInt()}% of your ${range} km tank. Plan a gas stop." else "Fuel OK: ${(pct * 100).toInt()}% of your ${range} km tank",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

// ------------------------------------------------------------------ loops

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoopPanel(vm: MapViewModel, m: Mode.Loop, onStart: () -> Unit) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val avoidMaybe by vm.avoidMaybe.collectAsState()
    Panel {
        PanelHeader("Plan a loop", "Starts and ends where you are, without riding the same trail twice where possible") { vm.close() }
        Spacer(Modifier.height(8.dp))
        Text("How far?", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (km in listOf(10, 20, 30, 50, 80)) {
                val sel = m.km == km
                Surface(onClick = { vm.setLoop(km = km) }, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(14.dp),
                    color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) {
                    Box(contentAlignment = Alignment.Center) { Text(if (ca.bancroft.trails.nav.Units.imperial) "${(km / 1.609).toInt()} mi" else "$km km", style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val modes = listOf(RouteMode.MOSTLY_TRAILS, RouteMode.TRAILS_ONLY)
            modes.forEachIndexed { i, mode ->
                SegmentedButton(m.mode == mode, { vm.setLoop(mode = mode) }, SegmentedButtonDefaults.itemShape(i, modes.size), Modifier.heightIn(min = 52.dp)) {
                    Text(mode.label)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        val r = m.route
        when {
            m.noGps -> Text("Waiting for GPS. A loop starts from where you are.", style = MaterialTheme.typography.bodyLarge)
            m.computing -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Trying loops in every direction…", Modifier.padding(top = 8.dp)) }
            r == null -> Text("Couldn't find a loop from here. Try another length.", style = MaterialTheme.typography.bodyLarge)
            else -> {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(formatDist(r.distanceM), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Text("${formatTime(r.timeS)} · ${(r.trailShare * 100).toInt()}% off-road", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
                }
                Spacer(Modifier.height(8.dp)); ClassBar(r, dark)
                r.warnings.forEach { WarnLine(it) }
                FuelLine(vm, r)
            }
        }
        Row(Modifier.fillMaxWidth().clickable { vm.setAvoidMaybe(!avoidMaybe); vm.setLoop() }.heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Avoid \"maybe\" trails", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(avoidMaybe, { vm.setAvoidMaybe(it); vm.setLoop() })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton({ vm.setLoop() }, Modifier.heightIn(min = 60.dp), shape = RoundedCornerShape(18.dp), enabled = !m.computing) {
                Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Again")
            }
            BigButton("Ride it", Icons.Filled.Directions, onStart, Modifier.weight(1f), enabled = r != null && !m.computing)
        }
    }
}

// ------------------------------------------------------------------ rides

@Composable
fun RidesPanel(vm: MapViewModel) {
    val rides by vm.rides.collectAsState()
    Panel {
        PanelHeader("My rides", null) { vm.close() }
        if (rides.isNotEmpty()) {
            val st = vm.stats()
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                StatBox("${st.rides}", "rides"); StatBox(formatDist(st.km * 1000), "ridden")
                StatBox("%.1f h".format(st.hours), "riding"); StatBox(formatSpeed(st.maxKmh), "top speed")
            }
            if (st.yourTrailsKm > 0) Text("You've found ${formatDist(st.yourTrailsKm * 1000)} of trail no map has (teal dashes).",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(Modifier.heightIn(max = 380.dp)) {
            if (rides.isEmpty()) item { Text("No rides yet. Tap \"Record ride\" before you head out.", Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.bodyLarge) }
            items(rides, key = { it.id }) { r ->
                ResultRow("🏁", SimpleDateFormat("EEE MMM d, h:mm a", Locale.CANADA).format(Date(r.start)),
                    "${formatTime(r.durationMs / 1000.0)} · max ${formatSpeed(r.maxKmh)}", formatDist(r.distanceM)) { vm.viewRide(r) }
            }
        }
    }
}

@Composable
private fun StatBox(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun RideViewPanel(vm: MapViewModel, r: Ride) {
    val ctx = LocalContext.current
    Panel {
        PanelHeader(SimpleDateFormat("EEEE MMM d", Locale.CANADA).format(Date(r.start)),
            "${formatDist(r.distanceM)} · ${formatTime(r.durationMs / 1000.0)} · avg ${formatSpeed(if (r.durationMs > 0) r.distanceM / 1000.0 / (r.durationMs / 3_600_000.0) else 0.0)}") { vm.back() }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton("Share GPX", Icons.Filled.Share, {
                val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", vm.rideFile(r))
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/gpx+xml")
                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share ride"))
            }, Modifier.weight(1f))
            OutlinedButton({ vm.deleteRide(r) }, Modifier.heightIn(min = 60.dp), shape = RoundedCornerShape(18.dp)) {
                Icon(Icons.Filled.Delete, null); Spacer(Modifier.width(6.dp)); Text("Delete")
            }
        }
    }
}

@Composable
fun StopRecordingDialog(live: ca.bancroft.trails.data.RideRecorder.Live?, onDismiss: () -> Unit, onStop: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stop recording?") },
        text = { Text(live?.let { "${formatDist(it.distanceM)} recorded." } ?: "") },
        confirmButton = { Button({ onStop(true) }, Modifier.heightIn(min = 48.dp)) { Text("Save ride") } },
        dismissButton = {
            Row {
                TextButton({ onStop(false) }, Modifier.heightIn(min = 48.dp)) { Text("Discard") }
                TextButton(onDismiss, Modifier.heightIn(min = 48.dp)) { Text("Keep going") }
            }
        },
    )
}

// ------------------------------------------------------------------ navigation

fun turnIcon(t: Turn?): ImageVector = when (t) {
    Turn.LEFT -> Icons.Filled.TurnLeft; Turn.RIGHT -> Icons.Filled.TurnRight
    Turn.SLIGHT_LEFT -> Icons.Filled.TurnSlightLeft; Turn.SLIGHT_RIGHT -> Icons.Filled.TurnSlightRight
    Turn.SHARP_LEFT -> Icons.Filled.TurnSharpLeft; Turn.SHARP_RIGHT -> Icons.Filled.TurnSharpRight
    Turn.U_TURN -> Icons.Filled.UTurnLeft; Turn.ARRIVE -> Icons.Filled.Flag
    else -> Icons.Filled.Straight
}

@Composable
fun NavBanner(m: Mode.Navigating, dark: Boolean) {
    val st = m.state
    val next = st?.next
    Surface(shape = RoundedCornerShape(24.dp), color = Palette.navBanner, contentColor = Color.White, shadowElevation = 10.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(turnIcon(next?.turn), null, Modifier.size(56.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (st == null) "Getting GPS…" else formatDist(st.distToNextM), style = NavDistance)
                    Text(next?.let { if (it.turn == Turn.ARRIVE) "Destination" else it.onto } ?: m.dest.title,
                        style = NavStreet, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (next != null && next.ontoCls >= 0) {
                    Box(Modifier.size(width = 10.dp, height = 56.dp).background(Palette.cls(next.ontoCls, true), RoundedCornerShape(5.dp))
                        .semantics { contentDescription = clsLabel(next.ontoCls) })
                }
            }
            st?.after?.let { a ->
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = 0.25f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Then", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Icon(turnIcon(a.turn), null, Modifier.size(28.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(a.onto, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if ((st?.offRouteM ?: 0.0) > 0) {
                Text("Off route, finding a new way…", style = MaterialTheme.typography.titleMedium, color = Color(0xFFFFCF33), modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
fun NavBottomBar(vm: MapViewModel, m: Mode.Navigating, onRecenter: () -> Unit, following: Boolean, speedKmh: Double = 0.0) {
    val st = m.state
    val eta = st?.let { System.currentTimeMillis() + (it.remainingS * 1000).toLong() }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(eta?.let { SimpleDateFormat("h:mm a", Locale.CANADA).format(Date(it)) } ?: "--:--",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(st?.let { "${formatDist(it.remainingM)} · ${formatTime(it.remainingS)}" } ?: formatDist(m.route.distanceM),
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(formatSpeed(speedKmh).substringBefore(' '), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(formatSpeed(speedKmh).substringAfter(' '), style = MaterialTheme.typography.labelMedium)
            }
            if (!following) {
                FilledTonalButton(onRecenter, Modifier.heightIn(min = 60.dp), shape = RoundedCornerShape(18.dp)) { Text("Re-centre") }
                Spacer(Modifier.width(10.dp))
            }
            HoldToStopButton { vm.stopNav() }
        }
    }
}

/** Ending navigation needs a 1-second hold so a bump on the trail can't cancel it. */
@Composable
private fun HoldToStopButton(onStop: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    Box(
        Modifier.size(width = 112.dp, height = 60.dp)
            .background(MaterialTheme.colorScheme.errorContainerSafe(), RoundedCornerShape(18.dp))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    job = scope.launch {
                        val start = System.currentTimeMillis()
                        while (progress < 1f) { progress = ((System.currentTimeMillis() - start) / 1000f).coerceAtMost(1f); delay(16) }
                        onStop()
                    }
                    tryAwaitRelease()
                    job?.cancel(); progress = 0f
                })
            }
            .semantics { contentDescription = "Hold to stop navigation" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(progress).background(Color.Black.copy(alpha = 0.18f), RoundedCornerShape(18.dp)))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Stop", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text("hold", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun androidx.compose.material3.ColorScheme.errorContainerSafe() = Color(0xFFB3261E)

// ------------------------------------------------------------------ sheets & dialogs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayersSheet(vm: MapViewModel, online: Boolean, onDismiss: () -> Unit) {
    val l by vm.layers.collectAsState()
    val style by vm.mapStyle.collectAsState()
    val theme by vm.theme.collectAsState()
    ModalBottomSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text("Map", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StyleTile("Standard", "Offline map", Icons.Outlined.Map, style == MapStyle.STANDARD, Modifier.weight(1f)) { vm.setMapStyle(MapStyle.STANDARD) }
                StyleTile("Satellite", if (online) "Uses data / Wi-Fi" else "No signal now", Icons.Filled.Satellite, style == MapStyle.SATELLITE, Modifier.weight(1f)) { vm.setMapStyle(MapStyle.SATELLITE) }
                StyleTile("Trail network", "How trails connect", Icons.Filled.Hub, style == MapStyle.NETWORK, Modifier.weight(1f)) { vm.setMapStyle(MapStyle.NETWORK) }
            }
            if (style == MapStyle.NETWORK) {
                Text("Each colour is a connected trail system you can ride without touching a road. " +
                    "Blue lines are roads you're allowed on that link systems together. White dots are trail junctions, blue dots are where trails meet those roads.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
            }
            if (style == MapStyle.SATELLITE) {
                Text("Satellite photos load over Wi-Fi or data and stay cached for places you've looked at. Trails, routing and everything else still work offline.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, t ->
                    SegmentedButton(theme == t, { vm.setTheme(t) }, SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size), Modifier.heightIn(min = 52.dp)) {
                        Text(when (t) { ThemeMode.AUTO -> "Auto"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Show on map", style = MaterialTheme.typography.titleMedium)
            ToggleRow("⛽  Gas stations", l.gas) { vm.setLayers(l.copy(gas = it)) }
            ToggleRow("🍴  Food & stores", l.food) { vm.setLayers(l.copy(food = it)) }
            ToggleRow("★  Places to see, parking", l.poi) { vm.setLayers(l.copy(poi = it)) }
            ToggleRow("⛔  Forest road gates", l.gates) { vm.setLayers(l.copy(gates = it)) }
            val ridden by vm.showRidden.collectAsState()
            ToggleRow("〰  Where I've ridden", ridden) { vm.setShowRidden(it) }
        }
    }
}

@Composable
private fun StyleTile(title: String, sub: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 104.dp), shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, Modifier.size(30.dp))
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable { onChange(!value) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(value, onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(vm: MapViewModel, onDismiss: () -> Unit, onLegend: () -> Unit) {
    val theme by vm.theme.collectAsState()
    val voice by vm.voice.collectAsState()
    val data by vm.data.collectAsState()
    ModalBottomSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text("Settings", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Text("Map theme", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, t ->
                    SegmentedButton(theme == t, { vm.setTheme(t) }, SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size), Modifier.heightIn(min = 52.dp)) {
                        Text(when (t) { ThemeMode.AUTO -> "Auto"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            ToggleRow("Voice turn directions", voice) { vm.setVoice(it) }
            val snow by vm.useSnow.collectAsState()
            ToggleRow("Routes can use snowmobile trails", snow) { vm.setUseSnow(it) }
            val autoRec by vm.autoRecord.collectAsState()
            ToggleRow("Record every ride I navigate", autoRec) { vm.setAutoRecord(it) }
            val imperial by vm.imperial.collectAsState()
            ToggleRow("Use miles", imperial) { vm.setImperial(it) }
            val range by vm.fuelRange.collectAsState()
            Text("Tank range (warns on long routes)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (km in listOf(0, 80, 120, 160, 200)) {
                    val sel = range == km
                    Surface(onClick = { vm.setFuelRange(km) }, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp),
                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) {
                        Box(contentAlignment = Alignment.Center) { Text(if (km == 0) "Off" else "$km km", style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            TextButton(onLegend, Modifier.heightIn(min = 52.dp)) { Text("What do the trail colours mean?") }
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))
            Text("About the data", style = MaterialTheme.typography.titleMedium)
            val meta = data?.meta
            Text("Everything is stored on your phone and works with no signal. Map & trails from OpenStreetMap (${meta?.osmDate ?: "?"}), " +
                "Ontario government open data (${meta?.lioDate ?: "?"}), and researched township ATV by-laws (${meta?.rulesResearched ?: "?"}).",
                style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            meta?.sources?.forEach { (name, use) ->
                Text("• $name: $use", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Text("Trail status is a best guess from public data, not legal advice. Signs, gates and landowners always win. " +
                "Ride registered, insured, helmeted, and only where you're allowed.",
                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("Map data © OpenStreetMap contributors (ODbL) · Protomaps · Contains information licensed under the Open Government Licence – Ontario.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegendSheet(vm: MapViewModel, dark: Boolean, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Map key", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            LegendRow(Cls.FREE, dark, "Free to ride", "Trails and forest roads on Crown land. No pass needed as far as the data shows.")
            LegendRow(Cls.ROAD, dark, "Road: ATVs allowed", "The township by-law or provincial rules allow ATVs. Ride the shoulder, 20–50 km/h.")
            LegendRow(Cls.MAYBE, dark, "Maybe", "Club trails (pass likely), private land, restricted forest roads, or rules not confirmed.")
            LegendRow(Cls.NO, dark, "No ATVs", "Provincial parks, highways not open to ORVs, hiking-only trails.")
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.Canvas(Modifier.size(width = 56.dp, height = 20.dp)) {
                    drawLine(if (dark) Color(0xFF7FD3FF) else Color(0xFF0288D1), Offset(4f, size.height / 2), Offset(size.width - 4f, size.height / 2),
                        strokeWidth = 6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12.dp.toPx(), 7.dp.toPx())))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Snowmobile trail", style = MaterialTheme.typography.titleMedium)
                    Text("Shown for reference. Most are winter-only or private land in summer; routes skip them unless you turn them on.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.Canvas(Modifier.size(width = 56.dp, height = 20.dp)) {
                    drawLine(Color(0xFF7A7A7A), Offset(4f, size.height / 2), Offset(size.width - 4f, size.height / 2),
                        strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.5f, 7.dp.toPx())))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Unmapped gap", style = MaterialTheme.typography.titleMedium)
                    Text("A trail probably connects here but nobody mapped the link.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 56.dp, height = 8.dp).background(Palette.route, RoundedCornerShape(4.dp)))
                Spacer(Modifier.width(12.dp)); Text("Your route", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            Text("Tap any trail to see why it has its colour and which sources agree.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LegendRow(cls: Int, dark: Boolean, title: String, body: String) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        LineSample(cls, dark)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PinDialog(t: Target, onDismiss: () -> Unit, onSave: (PinType, String, String) -> Unit) {
    var type by remember { mutableStateOf(PinType.NOTE) }
    var name by remember { mutableStateOf(if (t is Target.Point) "" else t.title) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save a pin") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PinType.entries.forEach { p ->
                    Surface(
                        onClick = { type = p }, shape = RoundedCornerShape(14.dp),
                        color = if (type == p) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(p.emoji, fontSize = 20.sp); Spacer(Modifier.width(10.dp)); Text(p.label, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button({ onSave(type, name.trim(), note.trim()) }, Modifier.heightIn(min = 48.dp)) { Text("Save") } },
        dismissButton = { TextButton(onDismiss, Modifier.heightIn(min = 48.dp)) { Text("Cancel") } },
    )
}

@Composable
fun IntroDialog(onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Bancroft Trails") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Free, offline ATV map for 100 km around Bancroft. No account, no signal needed.", style = MaterialTheme.typography.bodyLarge)
                Text("Green = free · Blue = road you can ride · Yellow dashed = maybe (pass or permission) · Dotted = no ATVs.",
                    style = MaterialTheme.typography.bodyMedium)
                Text("This map is a best guess from public data. Posted signs always win.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("Next, allow location so the map can show where you are.", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { Button(onDone, Modifier.heightIn(min = 52.dp), colors = ButtonDefaults.buttonColors()) { Text("Let's ride") } },
    )
}
