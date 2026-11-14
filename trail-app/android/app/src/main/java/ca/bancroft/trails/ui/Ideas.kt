package ca.bancroft.trails.ui

import android.content.Intent
import android.location.Location
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.bancroft.trails.nav.formatDist
import ca.bancroft.trails.nav.formatTime

/** "Ride ideas": quick timed rides from here, popular published routes, and places worth riding to. */
@Composable
fun IdeasPanel(vm: MapViewModel, loc: Location?) {
    val data by vm.data.collectAsState()
    val home by vm.home.collectAsState()
    val lat = loc?.latitude ?: home.lat; val lon = loc?.longitude ?: home.lon
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ride ideas", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton({ vm.close() }, Modifier.size(48.dp)) { Icon(Icons.Filled.Close, "Close") }
        }
        Surface(onClick = { vm.openBuddy() }, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(bottom = 10.dp)) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🤠", fontSize = 26.sp); Spacer(Modifier.width(10.dp))
                Column { Text("Ask Trail Buddy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Pick a length and stops, I'll build the ride", style = MaterialTheme.typography.bodySmall) }
            }
        }
        Text("Quick ride from ${if (loc != null) "here" else "home"}", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((m, label) in listOf(10 to "10 min", 20 to "20 min", 60 to "1 hour", 120 to "2 hours")) {
                Surface(onClick = { vm.quickRide(m) }, modifier = Modifier.weight(1f).height(60.dp), shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        TextButton({ vm.openLoop() }, Modifier.heightIn(min = 48.dp)) { Icon(Icons.Filled.Loop, null); Spacer(Modifier.width(6.dp)); Text("Custom loop by distance") }
        HorizontalDivider()
        val d = data ?: return@Panel
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            item { Text("Popular rides", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) }
            val rides = d.rides.sortedBy { MapViewModel.distM(lat, lon, it.points.first().lat, it.points.first().lon) }
            items(rides, key = { it.id }) { r ->
                val away = MapViewModel.distM(lat, lon, r.points.first().lat, r.points.first().lon)
                IdeaRow(r.name, listOfNotNull(r.typeLabel, r.km?.let { "${it.toInt()} km" }, passShort(r.pass)).joinToString(" · "),
                    "${formatDist(away)} to start") { vm.openRide(r) }
            }
            item { Text("Worth the ride", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)) }
            val places = d.places.map { it to MapViewModel.distM(lat, lon, it.lat, it.lon) }.sortedBy { it.second }
            items(places, key = { "p" + it.first.name }) { (p, dist) ->
                IdeaRow(p.name, p.what ?: "", formatDist(dist)) {
                    val t = Target.Point(p.lat, p.lon, p.name)
                    vm.select(t); vm.flyTo(t, 14.0)
                }
            }
        }
    }
}

private fun passShort(p: String?): String? {
    val s = p?.lowercase() ?: return null
    return when {
        s.startsWith("unknown") -> "pass: check"
        "eota" in s -> "EOTA pass"; "hatva" in s -> "HATVA pass"; "rcatv" in s || "ofatv" in s -> "club pass"
        "included" in s -> "event"; "unknown" in s -> "pass: check"; else -> null
    }
}

@Composable
private fun IdeaRow(title: String, sub: String, trailing: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Spacer(Modifier.width(8.dp))
            Text(trailing, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun IdeaPanel(vm: MapViewModel, m: Mode.Idea, onStart: () -> Unit) {
    val ctx = LocalContext.current
    val dark = MaterialTheme.colorScheme.surface.luminance2() < 0.5f
    Panel {
        Row(verticalAlignment = Alignment.Top) {
            IconButton({ vm.back() }, Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to ideas") }
            Column(Modifier.weight(1f).padding(top = 6.dp)) {
                Text(m.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(m.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            if (m.lines.isNotEmpty() && !m.computing) {
                m.lines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 3.dp)) }
                Spacer(Modifier.height(6.dp))
            }
            m.ride?.let { r ->
                val facts = listOfNotNull(r.km?.let { "${it.toInt()} km published" }, r.time, r.difficulty?.substringBefore(" ("), r.season, r.pass?.let { "Pass: $it" })
                facts.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                r.highlights?.let { Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp)) }
            }
            Spacer(Modifier.height(10.dp))
            val r = m.route
            when {
                m.computing -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Working out the ride…", Modifier.padding(top = 8.dp)) }
                r == null -> Text(m.note ?: "Couldn't route this ride from the map data.", style = MaterialTheme.typography.bodyLarge)
                else -> {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(formatTime(r.timeS), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(10.dp))
                        Text("${formatDist(r.distanceM)} · ${(r.trailShare * 100).toInt()}% off-road", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
                    }
                    Spacer(Modifier.height(8.dp)); ClassBar(r, dark)
                    m.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp)) }
                    r.warnings.take(2).forEach { Text("⚠ $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
                }
            }
            m.ride?.sources?.takeIf { it.isNotEmpty() }?.let { srcs ->
                Text("Sources", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                srcs.forEach { u ->
                    Text(Uri.parse(u).host ?: u, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 2.dp).clickable { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) } })
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(onStart, Modifier.fillMaxWidth().heightIn(min = 60.dp), enabled = m.route != null && !m.computing, shape = RoundedCornerShape(18.dp)) {
            Icon(Icons.Filled.Directions, null); Spacer(Modifier.width(10.dp)); Text("Ride it", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun androidx.compose.ui.graphics.Color.luminance2(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

