package ca.bancroft.trails.data

import android.content.Context
import android.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.hypot

data class TrackPoint(val lat: Double, val lon: Double, val time: Long, val ele: Double?, val speed: Float)

data class Ride(val id: Long, val start: Long, val end: Long, val distanceM: Double, val maxKmh: Double, val file: String) {
    val durationMs get() = end - start
}

/** Live ride being recorded. Fed by RideService so it keeps going with the screen off. */
object RideRecorder {
    data class Live(val start: Long, val points: List<TrackPoint>, val distanceM: Double, val maxKmh: Double)

    private val _live = MutableStateFlow<Live?>(null)
    val live: StateFlow<Live?> = _live

    fun start() { if (_live.value == null) _live.value = Live(System.currentTimeMillis(), emptyList(), 0.0, 0.0) }

    fun add(loc: Location) {
        val cur = _live.value ?: return
        if (loc.hasAccuracy() && loc.accuracy > 35f) return // skip poor fixes
        val last = cur.points.lastOrNull()
        val p = TrackPoint(loc.latitude, loc.longitude, loc.time, if (loc.hasAltitude()) loc.altitude else null, loc.speed)
        val step = last?.let { dist(it.lat, it.lon, p.lat, p.lon) } ?: 0.0
        if (last != null && step < 4.0) return // standing still: don't pile up points
        _live.value = cur.copy(points = cur.points + p, distanceM = cur.distanceM + step, maxKmh = maxOf(cur.maxKmh, loc.speed * 3.6))
    }

    fun stop(): Live? = _live.value.also { _live.value = null }

    fun dist(la1: Double, lo1: Double, la2: Double, lo2: Double) =
        hypot((lo2 - lo1) * 111320.0 * cos(Math.toRadians(la1)), (la2 - la1) * 110540.0)
}

/** Saved rides: one GPX per ride plus a small index. GPX opens in any mapping app. */
class RideStore(private val ctx: Context) {
    private val dir = File(ctx.filesDir, "rides").apply { mkdirs() }
    private val index = File(dir, "index.json")

    fun list(): List<Ride> = runCatching {
        val a = JSONArray(index.readText())
        (0 until a.length()).map { a.getJSONObject(it) }.map {
            Ride(it.getLong("id"), it.getLong("start"), it.getLong("end"), it.getDouble("dist"), it.optDouble("max", 0.0), it.getString("file"))
        }.sortedByDescending { it.start }
    }.getOrDefault(emptyList())

    fun save(live: RideRecorder.Live): Ride? {
        if (live.points.size < 2) return null
        val id = live.start
        val name = "ride-" + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.CANADA).format(Date(id)) + ".gpx"
        File(dir, name).writeText(gpx(live, name))
        val ride = Ride(id, live.start, live.points.last().time, live.distanceM, live.maxKmh, name)
        writeIndex(list() + ride)
        return ride
    }

    fun delete(r: Ride) {
        File(dir, r.file).delete()
        writeIndex(list().filterNot { it.id == r.id })
    }

    fun file(r: Ride) = File(dir, r.file)

    fun points(r: Ride): List<Pair<Double, Double>> = runCatching {
        Regex("<trkpt lat=\"([-0-9.]+)\" lon=\"([-0-9.]+)\"").findAll(file(r).readText())
            .map { it.groupValues[1].toDouble() to it.groupValues[2].toDouble() }.toList()
    }.getOrDefault(emptyList())

    private fun writeIndex(rides: List<Ride>) {
        val a = JSONArray()
        rides.forEach { a.put(JSONObject().put("id", it.id).put("start", it.start).put("end", it.end).put("dist", it.distanceM).put("max", it.maxKmh).put("file", it.file)) }
        index.writeText(a.toString())
    }

    private fun gpx(live: RideRecorder.Live, name: String): String {
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gpx version=\"1.1\" creator=\"Bancroft Trails\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        sb.append("<trk><name>${name.removeSuffix(".gpx")}</name><trkseg>\n")
        for (p in live.points) {
            sb.append("<trkpt lat=\"${p.lat}\" lon=\"${p.lon}\">")
            p.ele?.let { sb.append("<ele>%.1f</ele>".format(Locale.US, it)) }
            sb.append("<time>${iso.format(Date(p.time))}</time></trkpt>\n")
        }
        sb.append("</trkseg></trk>\n</gpx>\n")
        return sb.toString()
    }
}
