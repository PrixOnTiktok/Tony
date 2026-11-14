package ca.bancroft.trails.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A trail the rider actually rode that isn't on any map. Shown on the map and used by the router. */
data class UserTrail(val id: Long, val created: Long, val pts: List<Pair<Double, Double>>, val lengthM: Double)

class UserTrailStore(ctx: Context) {
    private val file = File(ctx.filesDir, "user_trails.json")

    fun load(): List<UserTrail> = runCatching {
        val a = JSONArray(file.readText())
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val p = o.getJSONArray("p")
            val pts = (0 until p.length() / 2).map { p.getDouble(it * 2) to p.getDouble(it * 2 + 1) }
            UserTrail(o.getLong("id"), o.getLong("created"), pts, o.getDouble("len"))
        }
    }.getOrDefault(emptyList())

    fun save(list: List<UserTrail>) {
        val a = JSONArray()
        list.forEach { t ->
            val p = JSONArray()
            t.pts.forEach { p.put(Math.round(it.first * 1e6) / 1e6); p.put(Math.round(it.second * 1e6) / 1e6) }
            a.put(JSONObject().put("id", t.id).put("created", t.created).put("len", t.lengthM).put("p", p))
        }
        file.writeText(a.toString())
    }

    companion object {
        /**
         * Finds stretches of a ride that were away from every known trail/road (> [offM] for at least [minRunM]),
         * includes one point either side so the new trail joins the network, and drops ones already saved.
         */
        fun detect(track: List<TrackPoint>, graph: Graph, existing: List<UserTrail>, offM: Double = 35.0, minRunM: Double = 120.0): List<UserTrail> {
            if (track.size < 3) return emptyList()
            val off = BooleanArray(track.size) { graph.snap(track[it].lat, track[it].lon, offM) == null }
            val out = ArrayList<UserTrail>()
            var i = 0
            while (i < track.size) {
                if (!off[i]) { i++; continue }
                var j = i
                while (j + 1 < track.size && off[j + 1]) j++
                val from = (i - 1).coerceAtLeast(0); val to = (j + 1).coerceAtMost(track.size - 1)
                val pts = (from..to).map { track[it].lat to track[it].lon }
                var len = 0.0
                for (k in 0 until pts.size - 1) len += RideRecorder.dist(pts[k].first, pts[k].second, pts[k + 1].first, pts[k + 1].second)
                if (len >= minRunM && !covered(pts, existing + out)) {
                    out.add(UserTrail(System.currentTimeMillis() + out.size, System.currentTimeMillis(), simplify(pts), len))
                }
                i = j + 1
            }
            return out
        }

        private fun covered(pts: List<Pair<Double, Double>>, trails: List<UserTrail>): Boolean {
            if (trails.isEmpty()) return false
            val near = pts.count { p -> trails.any { t -> t.pts.any { q -> RideRecorder.dist(p.first, p.second, q.first, q.second) < 30 } } }
            return near > pts.size * 0.7
        }

        /** Keep a point at least every ~8 m: GPS jitter makes raw tracks heavy. */
        private fun simplify(pts: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
            val out = ArrayList<Pair<Double, Double>>()
            for (p in pts) {
                val last = out.lastOrNull()
                if (last == null || RideRecorder.dist(last.first, last.second, p.first, p.second) >= 8 || p === pts.last()) out.add(p)
            }
            return out
        }
    }
}
