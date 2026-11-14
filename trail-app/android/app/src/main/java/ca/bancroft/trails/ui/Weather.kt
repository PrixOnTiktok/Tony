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
fun fmtWind(kmh: Double) = if (Units.imperial) "${Math.round(kmh / 1.609)} mph" else "${Math.round(kmh)} km/h"
private fun hourLabel(t: Long) = SimpleDateFormat("h a", Locale.CANADA).format(Date(t)).replace(".", "")
private fun ago(t: Long): String {
    val m = (System.currentTimeMillis() - t) / 60000
    return when { m < 2 -> "just now"; m < 60 -> "$m min ago"; m < 48 * 60 -> "${m / 60} h ago"; else -> "${m / 1440} days ago" }
}

/** Small chip on the map: temperature now (from the saved forecast if offline) + a rain heads-up. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun WeatherChip(f: Forecast, onClick: () -> Unit) {
    val now = f.now() ?: return
    val (_, emoji) = weatherLabel(now.code)
    val rain = f.rainSoon()
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 3.dp) {
        androidx.compose.foundation.layout.FlowRow(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.Center) {
            Text(emoji, fontSize = 16.sp)
            Spacer(Modifier.width(6.dp))
            Text(fmtTemp(now.temp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            val chill = now.windChill
            if (chill != null && now.temp - chill >= 2) Text(" · chill ${fmtTemp(chill)}", style = MaterialTheme.typography.labelMedium)
            else if (Math.abs(now.feels - now.temp) >= 3) Text(" · feels ${fmtTemp(now.feels)}", style = MaterialTheme.typography.labelMedium)
            if (now.wind >= 10) Text(" · 💨 ${fmtWind(now.wind)} ${ca.bancroft.trails.data.compassDir(now.windDir)}", style = MaterialTheme.typography.labelMedium)
            if (rain != null) Text(" · 🌧 ${if (rain.time <= System.currentTimeMillis()) "rain now" else "rain ~" + hourLabel(rain.time)}",
                style = MaterialTheme.typography.labelMedium, color = rainColor(), fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherSheet(vm: MapViewModel, online: Boolean, onDismiss: () -> Unit, inline: Boolean = false) {
    val f by vm.weather.collectAsState()
    SheetOrInline(inline, onDismiss) {
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
                        Text("$label · feels ${fmtTemp(now.feels)}", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("💨 Wind ${fmtWind(now.wind)} from the ${ca.bancroft.trails.data.compassDir(now.windDir).ifBlank { "—" }}" +
                            if (now.gust > now.wind + 5) ", gusts ${fmtWind(now.gust)}" else "", style = MaterialTheme.typography.titleMedium)
                        val chill = now.windChill
                        Text(if (chill != null) "Wind chill standing: ${fmtTemp(chill)}" else "No wind chill (too warm or calm)",
                            style = MaterialTheme.typography.bodyLarge)
                        val r30 = now.ridingChill(30.0); val r50 = now.ridingChill(50.0)
                        if (r30 != null && r50 != null) Text("Riding: ${fmtTemp(r30)} at ${fmtWind(30.0)} · ${fmtTemp(r50)} at ${fmtWind(50.0)}",
                            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        val worst = r50 ?: chill
                        if (worst != null) Text(chillAdvice(worst), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Text(if (h.rainProb > 0) "${h.rainProb}%" else " ", style = MaterialTheme.typography.labelSmall, color = rainColor())
                            Text("${Math.round(if (Units.imperial) h.wind / 1.609 else h.wind)} ${ca.bancroft.trails.data.compassDir(h.windDir)}",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    if (d.rainProb > 0) Text("${d.rainProb}%  ", style = MaterialTheme.typography.bodyMedium, color = rainColor())
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

/** Environment Canada's frostbite guidance for wind chill. */
fun chillAdvice(c: Double): String = when {
    c <= -40 -> "Frostbite in under 5 min on exposed skin. Not riding weather."
    c <= -28 -> "Frostbite possible in 10–30 min. Cover all skin, full face mask."
    c <= -10 -> "Cold: cover your face, wear warm gloves and layers."
    c <= 0 -> "Chilly on the machine: wear a jacket and gloves."
    else -> "Cool breeze on the machine."
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
        (next.first().ridingChill(40.0) ?: 99.0) <= -20 -> "🥶 Riding wind chill ${fmtTemp(next.first().ridingChill(40.0)!!)} at 40 km/h: cover all skin."
        next.maxOf { it.gust } >= 60 -> "💨 Gusts to ${fmtWind(next.maxOf { it.gust })}: watch for falling trees and branches."
        next.maxOf { it.wind } >= 40 -> "💨 Strong wind: watch for falling branches."
        else -> "👍 Good riding: dry for the next ${next.size} hours."
    }
}
