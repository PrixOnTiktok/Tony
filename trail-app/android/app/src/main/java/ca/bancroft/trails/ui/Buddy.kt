package ca.bancroft.trails.ui

import android.location.Location
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.bancroft.trails.data.RouteMode
import ca.bancroft.trails.data.Verdict

private val GREETINGS = listOf(
    "Hey! Where are we ripping today?",
    "Machine's warm, coffee's cold. Let's plan a ride.",
    "Alright partner, tell me what you're after.",
    "Mud, views or a burger? Let's sort it out.",
)

/** Trail Buddy: three quick questions, then it builds the ride. */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BuddyPanel(vm: MapViewModel, loc: Location?) {
    val greeting = remember { GREETINGS.random() }
    var minutes by rememberSaveable { mutableIntStateOf(60) }
    var stops by remember { mutableStateOf(setOf<String>()) }
    var mode by rememberSaveable { mutableStateOf(RouteMode.MOSTLY_TRAILS) }
    var fromHome by rememberSaveable { mutableStateOf(loc == null) }
    val verdict = remember { vm.verdict() }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🤠", fontSize = 30.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Trail Buddy", style = MaterialTheme.typography.titleLarge)
                Text(greeting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton({ vm.close() }, Modifier.size(48.dp)) { Icon(Icons.Filled.Close, "Close") }
        }
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
            verdict?.let { VerdictCard(it, compact = true) }
            Spacer(Modifier.height(10.dp))
            Text("How long are we riding?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((m, l) in listOf(30 to "30 min", 60 to "1 hour", 120 to "2 hours", 240 to "Half day", 360 to "All day")) {
                    BuddyChip(l, minutes == m) { minutes = m }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("What do you want to hit?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((k, l) in listOf("gas" to "⛽ Gas", "food" to "🍔 Food", "views" to "📸 Views", "water" to "🌊 Water & falls", "history" to "🏚️ History")) {
                    BuddyChip(l, k in stops) { stops = if (k in stops) stops - k else stops + k }
                }
                BuddyChip("🌲 Just trails", stops.isEmpty()) { stops = emptySet() }
            }
            Spacer(Modifier.height(12.dp))
            Text("How rough?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val opts = listOf(RouteMode.FASTEST to "Easy roads", RouteMode.MOSTLY_TRAILS to "Mostly trails", RouteMode.TRAILS_ONLY to "Trails only")
                opts.forEachIndexed { i, (m, l) ->
                    SegmentedButton(mode == m, { mode = m }, SegmentedButtonDefaults.itemShape(i, opts.size), Modifier.heightIn(min = 52.dp)) { Text(l, maxLines = 1) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Start from", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                BuddyChip("📍 Here", !fromHome, enabled = loc != null) { fromHome = false }
                Spacer(Modifier.width(8.dp))
                BuddyChip("🏠 Home", fromHome) { fromHome = true }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button({ vm.buddyRide(minutes, stops, mode, fromHome) }, Modifier.fillMaxWidth().heightIn(min = 60.dp), shape = RoundedCornerShape(18.dp)) {
            Text("Build my ride", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BuddyChip(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.heightIn(min = 48.dp)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** The riding call: label, punchy headline, why, and what to wear. */
@Composable
fun VerdictCard(v: Verdict, compact: Boolean = false) {
    val tone = when {
        v.score >= 60 -> MaterialTheme.colorScheme.primaryContainer
        v.score >= 40 -> if (isDarkTheme()) androidx.compose.ui.graphics.Color(0xFF4A3B12) else androidx.compose.ui.graphics.Color(0xFFFFF1C6)
        else -> if (isDarkTheme()) androidx.compose.ui.graphics.Color(0xFF4A1F1F) else androidx.compose.ui.graphics.Color(0xFFFFDAD6)
    }
    Surface(shape = RoundedCornerShape(16.dp), color = tone, contentColor = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(v.emoji, fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Text("${v.label} · ${v.score}/100", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(v.headline, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
            if (!compact) {
                v.reasons.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp)) }
                if (v.gear.isNotEmpty()) Text("Wear/bring: ${v.gear.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
            } else if (v.reasons.isNotEmpty()) {
                Text(v.reasons.take(2).joinToString(" · "), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
