package ca.bancroft.trails.nav

import ca.bancroft.trails.routing.Maneuver
import ca.bancroft.trails.routing.Route
import ca.bancroft.trails.routing.Turn
import kotlin.math.cos
import kotlin.math.hypot

data class NavState(
    val next: Maneuver?,
    val after: Maneuver?,          // shown as a preview when it comes soon after [next]
    val distToNextM: Double,
    val remainingM: Double,
    val remainingS: Double,
    val offRouteM: Double,
    val progressIndex: Int,
    val arrived: Boolean,
)

/**
 * Follows the rider along a [Route]: snaps GPS fixes to the route, finds the upcoming maneuver,
 * and detects when the rider has left the route (so the app can re-route).
 */
class Navigator(val route: Route) {
    private var lastIndex = 0
    private var offCount = 0

    fun update(lat: Double, lon: Double): NavState {
        val la = route.lats; val lo = route.lons
        val kx = 111320.0 * cos(Math.toRadians(lat)); val ky = 110540.0
        // search forward from the last position (and a little back) for the closest segment
        var best = Double.MAX_VALUE; var bestI = lastIndex; var bestT = 0.0
        val from = (lastIndex - 5).coerceAtLeast(0)
        val to = (lastIndex + 400).coerceAtMost(la.size - 2)
        for (i in from..to) {
            val ax = (lo[i] - lon) * kx; val ay = (la[i] - lat) * ky
            val bx = (lo[i + 1] - lon) * kx; val by = (la[i + 1] - lat) * ky
            val dx = bx - ax; val dy = by - ay
            val l2 = dx * dx + dy * dy
            val t = if (l2 == 0.0) 0.0 else ((-ax * dx - ay * dy) / l2).coerceIn(0.0, 1.0)
            val d = hypot(ax + t * dx, ay + t * dy)
            if (d < best) { best = d; bestI = i; bestT = t }
        }
        if (best < 60) lastIndex = bestI
        offCount = if (best > 45) offCount + 1 else 0
        val along = route.cumDist[bestI] + (route.cumDist[bestI + 1] - route.cumDist[bestI]) * bestT
        val next = route.maneuvers.firstOrNull { it.distFromStart > along + 5 && it.turn != Turn.START }
        val after = next?.let { n -> route.maneuvers.firstOrNull { it.distFromStart > n.distFromStart + 1 } }
            ?.takeIf { it.distFromStart - (next.distFromStart) < 150 }
        val remaining = (route.distanceM - along).coerceAtLeast(0.0)
        val frac = if (route.distanceM > 0) remaining / route.distanceM else 0.0
        return NavState(
            next = next, after = after,
            distToNextM = ((next?.distFromStart ?: route.distanceM) - along).coerceAtLeast(0.0),
            remainingM = remaining, remainingS = route.timeS * frac,
            offRouteM = if (offCount >= 3) best else 0.0,
            progressIndex = bestI,
            arrived = remaining < 25,
        )
    }
}

/** Set from settings; false = km/m, true = miles/feet. */
object Units { @Volatile var imperial = false }

fun formatDist(m: Double): String = if (Units.imperial) {
    val mi = m / 1609.344
    when {
        mi < 0.19 -> "${(Math.round(m * 3.28084 / 50.0) * 50).toInt()} ft"
        mi < 9.95 -> "%.1f mi".format(mi)
        else -> "${Math.round(mi)} mi"
    }
} else when {
    m < 950 -> "${(Math.round(m / 10.0) * 10).toInt()} m"
    m < 9950 -> "%.1f km".format(m / 1000)
    else -> "${Math.round(m / 1000)} km"
}

fun formatSpeed(kmh: Double): String = if (Units.imperial) "${Math.round(kmh / 1.609344)} mph" else "${Math.round(kmh)} km/h"

fun formatTime(s: Double): String {
    val min = Math.round(s / 60.0).toInt()
    return if (min < 60) "$min min" else "${min / 60} h ${min % 60} min"
}
