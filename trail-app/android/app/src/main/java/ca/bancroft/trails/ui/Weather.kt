package ca.bancroft.trails.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.bancroft.trails.data.Forecast
import ca.bancroft.trails.data.weatherLabel
import ca.bancroft.trails.nav.Units
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun fmtTemp(c: Double): String = if (Units.imperial) "${Math.round(c * 9 / 5 + 32)}°" else "${Math.round(c)}°"
private fun fmtWind(kmh: Double) = if (Units.imperial) "${Math.round(kmh / 1.609)} mph" else "${Math.round(kmh)} km/h"
private fun hourLabel(t: Long) = SimpleDateFormat("h a", Locale.CANADA).format(Date(t)).replace(".", "")
private fun ago(t: Long): String {
    val m = (System.currentTimeMillis() - t) / 60000
    return when { m < 2 -> "just now"; m < 60 -> "$m min ago"; m < 48 * 60 -> "${m / 60} h ago"; else -> "${m / 1440} days ago" }
}

/** Small chip on the map: temperature now (from the saved forecast if offline) + a rain heads-up. */
@Composable
fun WeatherChip(f: Forecast, onClick: () -> Unit) {
    val now = f.now() ?: return
    val (_, emoji) = weatherLabel(now.code)
    val rain = f.rainSoon()
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), shadowElevation = 3.dp) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 16.sp)
            Spacer(Modifier.width(6.dp))
            Text(fmtTemp(now.temp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            if (Math.abs(now.feels - now.temp) >= 3) Text(" · feels ${fmtTemp(now.feels)}", style = MaterialTheme.typography.labelMedium)
            if (rain != null) Text(" · 🌧 ${if (rain.time <= System.currentTimeMillis()) "rain now" else "rain ~" + hourLabel(rain.time)}",
                style = MaterialTheme.typography.labelMedium, color = Color(0xFF1565C0), fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherSheet(vm: MapViewModel, online: Boolean, onDismiss: () -> Unit) {
    val f by vm.weather.collectAsState()
    ModalBottomSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            val fc = f
            if (fc == null) {
                Text("Weather", style = MaterialTheme.typography.titleLarge)
                Text(if (online) "Getting the forecast…" else "No forecast saved yet. It downloads automatically next time you have signal or Wi-Fi.",
                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                return@Column
            }
            val now = fc.now()
            if (now != null) {
                val (label, emoji) = weatherLabel(now.code)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(emoji, fontSize = 44.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(fmtTemp(now.temp), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        Text("$label · feels ${fmtTemp(now.feels)} · wind ${fmtWind(now.wind)}", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            val status = when {
                !fc.covers() -> "Saved forecast has run out (${ago(fc.fetchedAt)}). It updates when you have signal."
                online -> "Updated ${ago(fc.fetchedAt)} · saved for offline"
                else -> "Offline · showing the forecast saved ${ago(fc.fetchedAt)}"
            }
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(10.dp))
            Text(rideTip(fc), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Text("Next 24 hours", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(fc.upcoming()) { h ->
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(hourLabel(h.time), style = MaterialTheme.typography.labelMedium)
                            Text(weatherLabel(h.code).second, fontSize = 20.sp)
                            Text(fmtTemp(h.temp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text(if (h.rainProb > 0) "${h.rainProb}%" else " ", style = MaterialTheme.typography.labelSmall, color = Color(0xFF1565C0))
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            fc.days.forEach { d ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(SimpleDateFormat("EEE", Locale.CANADA).format(Date(d.date + 12 * 3600_000)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(52.dp))
                    Text(weatherLabel(d.code).second, fontSize = 20.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(weatherLabel(d.code).first, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (d.rainProb > 0) Text("${d.rainProb}%  ", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF1565C0))
                    Text("${fmtTemp(d.max)} / ${fmtTemp(d.min)}", style = MaterialTheme.typography.titleMedium)
                }
                HorizontalDivider()
            }
            if (online) TextButton({ vm.refreshWeather(force = true) }) { Text("Refresh now") }
            Text("Forecast: Open-Meteo.com (Environment Canada GEM model), CC BY 4.0.", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** One-line riding advice from the next few hours. */
fun rideTip(f: Forecast, at: Long = System.currentTimeMillis()): String {
    val next = f.upcoming(at, 8)
    if (next.isEmpty()) return "No forecast for right now."
    val storm = next.firstOrNull { it.code >= 95 }
    if (storm != null) return "⛈ Thunderstorms possible around ${hourLabel(storm.time)}: get off exposed trails."
    val wet = next.firstOrNull { it.rainProb >= 60 }
    val cold = next.minOf { it.feels }
    return when {
        wet != null && wet.time <= at + 3600_000 -> "🌧 Rain now or soon: trails will be slick."
        wet != null -> "Dry until about ${hourLabel(wet.time)}, then rain likely."
        cold <= -15 -> "🥶 Feels like ${fmtTemp(cold)}: dress for frostbite risk."
        next.maxOf { it.wind } >= 50 -> "💨 Strong wind: watch for falling branches."
        else -> "👍 Good riding: dry for the next ${next.size} hours."
    }
}
