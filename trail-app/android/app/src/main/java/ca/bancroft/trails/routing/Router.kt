package ca.bancroft.trails.routing

import ca.bancroft.trails.data.Cls
import ca.bancroft.trails.data.Flag
import ca.bancroft.trails.data.Graph
import ca.bancroft.trails.data.Kind
import ca.bancroft.trails.data.RouteMode
import java.util.PriorityQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sqrt

enum class Turn { START, STRAIGHT, SLIGHT_LEFT, LEFT, SHARP_LEFT, SLIGHT_RIGHT, RIGHT, SHARP_RIGHT, U_TURN, ARRIVE }

data class Maneuver(val pointIndex: Int, val distFromStart: Double, val turn: Turn, val text: String, val onto: String, val ontoCls: Int)

data class Route(
    val mode: RouteMode,
    val lats: DoubleArray, val lons: DoubleArray,
    val cumDist: DoubleArray,
    val distanceM: Double, val timeS: Double,
    val kmByCls: DoubleArray,          // [free, maybe, road, no]
    val trailShare: Double,            // fraction of distance off-road (trails + forest roads)
    val gates: Int,
    val maneuvers: List<Maneuver>,
    val edges: IntArray,               // edges used (for highlighting / info)
    val warnings: List<String>,
)

/** A* router over the bundled network with ATV-specific cost profiles. */
class Router(private val g: Graph) {

    private fun edgeSpeed(e: Int): Double {
        val kind = g.eKind[e].toInt()
        // rail trails / club trails tagged as paths are wide and graded
        if (kind == Kind.PATH && (g.eFlags[e] and Flag.CLUB) != 0) return 25.0
        return speedKmh(kind)
    }

    private fun speedKmh(kind: Int) = when (kind) {
        Kind.TRACK -> 20.0; Kind.PATH -> 10.0; Kind.FOREST -> 30.0; Kind.MINOR -> 35.0
        Kind.MAJOR -> 45.0; Kind.HWY -> 45.0; Kind.CLUB -> 20.0; Kind.SERVICE -> 15.0; else -> 20.0
    }

    /** Multiplier on travel time; encodes the riding preference. Always >= 1 so the heuristic stays admissible. */
    private fun factor(e: Int, mode: RouteMode, avoidMaybe: Boolean): Double {
        val cls = g.eCls[e].toInt(); val kind = g.eKind[e].toInt()
        val road = Kind.isRoad(kind)
        var f = when (mode) {
            RouteMode.FASTEST -> when (cls) { Cls.NO -> 40.0; Cls.MAYBE -> 1.3; else -> 1.0 }
            RouteMode.MOSTLY_TRAILS -> when {
                cls == Cls.NO -> 60.0
                road -> if (kind == Kind.HWY || kind == Kind.MAJOR) 3.5 else 2.5
                cls == Cls.MAYBE -> 1.4
                else -> 1.0
            }
            RouteMode.TRAILS_ONLY -> when {
                cls == Cls.NO -> 80.0
                road -> if (kind == Kind.HWY || kind == Kind.MAJOR) 15.0 else 10.0
                cls == Cls.MAYBE -> 1.5
                else -> 1.0
            }
        }
        if (avoidMaybe && cls == Cls.MAYBE) f *= 25.0
        if ((g.eFlags[e] and Flag.LOWCONF) != 0 && road) f *= 1.15
        return f
    }

    private fun edgeCost(e: Int, lenM: Double, mode: RouteMode, avoidMaybe: Boolean, blocked: Set<Int>): Double {
        var c = lenM / (edgeSpeed(e) / 3.6) * factor(e, mode, avoidMaybe)
        if ((g.eFlags[e] and Flag.GATE) != 0) c += 180.0
        if (e in blocked) c += 1e6
        return c
    }

    /** Edges near the rider's "blocked" pins. */
    fun blockedEdges(points: List<Pair<Double, Double>>): Set<Int> {
        val out = HashSet<Int>()
        for ((lat, lon) in points) {
            g.snap(lat, lon, 40.0)?.let { out.add(it.edge) }
        }
        return out
    }

    fun route(
        fromLat: Double, fromLon: Double, toLat: Double, toLon: Double,
        mode: RouteMode, avoidMaybe: Boolean, blocked: Set<Int> = emptySet(),
    ): Route? {
        val s = g.snap(fromLat, fromLon, 3000.0) { g.inMain(it) } ?: return null
        val t = g.snap(toLat, toLon, 3000.0) { g.inMain(it) } ?: return null
        val n = g.nodeCount
        val S = n; val T = n + 1
        val dist = DoubleArray(n + 2) { Double.POSITIVE_INFINITY }
        val prevNode = IntArray(n + 2) { -1 }
        val prevEdge = IntArray(n + 2) { -1 }
        val done = BooleanArray(n + 2)

        val sPre = prefixLen(s.edge, s.seg, s.t); val sLen = g.eLen[s.edge].toDouble()
        val tPre = prefixLen(t.edge, t.seg, t.t); val tLen = g.eLen[t.edge].toDouble()
        val vmax = 45.0 / 3.6
        val cosLat = cos(Math.toRadians(toLat))
        fun h(node: Int): Double {
            if (node == T) return 0.0
            val la = g.nodeLat[node] / 1e6; val lo = g.nodeLon[node] / 1e6
            val dx = (lo - toLon) * 111320.0 * cosLat; val dy = (la - toLat) * 110540.0
            return sqrt(dx * dx + dy * dy) / vmax
        }

        val pq = PriorityQueue<DoubleArray>(1024, compareBy { it[0] })
        fun relax(node: Int, cost: Double, from: Int, edge: Int) {
            if (cost < dist[node]) {
                dist[node] = cost; prevNode[node] = from; prevEdge[node] = edge
                pq.add(doubleArrayOf(cost + h(node), node.toDouble()))
            }
        }
        dist[S] = 0.0
        relax(g.eA[s.edge], edgeCost(s.edge, sPre, mode, avoidMaybe, blocked), S, s.edge)
        relax(g.eB[s.edge], edgeCost(s.edge, sLen - sPre, mode, avoidMaybe, blocked), S, s.edge)
        if (s.edge == t.edge) relax(T, edgeCost(s.edge, abs(tPre - sPre), mode, avoidMaybe, blocked), S, s.edge)

        while (pq.isNotEmpty()) {
            val top = pq.poll()!!
            val u = top[1].toInt()
            if (done[u]) continue
            done[u] = true
            if (u == T) break
            if (u >= n) continue
            if (u == g.eA[t.edge]) relax(T, dist[u] + edgeCost(t.edge, tPre, mode, avoidMaybe, blocked), u, t.edge)
            if (u == g.eB[t.edge]) relax(T, dist[u] + edgeCost(t.edge, tLen - tPre, mode, avoidMaybe, blocked), u, t.edge)
            for (k in g.adjStart[u] until g.adjStart[u + 1]) {
                val e = g.adjEdge[k]
                val v = g.other(e, u)
                if (done[v]) continue
                relax(v, dist[u] + edgeCost(e, g.eLen[e].toDouble(), mode, avoidMaybe, blocked), u, e)
            }
        }
        if (!done[T]) return null

        // walk back
        val hops = ArrayList<IntArray>() // (fromNode, toNode, edge)
        var cur = T
        while (cur != S) {
            hops.add(intArrayOf(prevNode[cur], cur, prevEdge[cur]))
            cur = prevNode[cur]
        }
        hops.reverse()
        return build(hops, s, t, sPre, tPre, mode, n)
    }

    private fun prefixLen(e: Int, seg: Int, t: Double): Double {
        val f = g.eFirst[e]
        var d = 0.0
        for (i in 0 until seg) d += segLen(f + i)
        return d + segLen(f + seg) * t
    }

    private fun segLen(i: Int): Double {
        val la1 = g.ptLat[i] / 1e6; val lo1 = g.ptLon[i] / 1e6; val la2 = g.ptLat[i + 1] / 1e6; val lo2 = g.ptLon[i + 1] / 1e6
        return hypot((lo2 - lo1) * 111320.0 * cos(Math.toRadians(la1)), (la2 - la1) * 110540.0)
    }

    /** Geometry of a hop; handles partial first/last edges using the snap points. */
    private fun hopGeometry(h: IntArray, s: Graph.Snap, t: Graph.Snap, n: Int): List<DoubleArray> {
        val (from, to, e) = Triple(h[0], h[1], h[2])
        val full = g.geometry(e) // A -> B
        fun cutAt(snap: Graph.Snap): Pair<List<DoubleArray>, List<DoubleArray>> {
            val a = full.subList(0, snap.seg + 1) + listOf(doubleArrayOf(snap.lat, snap.lon))
            val b = listOf(doubleArrayOf(snap.lat, snap.lon)) + full.subList(snap.seg + 1, full.size)
            return a to b
        }
        return when {
            from == n && to == n + 1 -> { // same edge
                val (lo, hi) = if (s.seg + s.t <= t.seg + t.t) s to t else t to s
                val mid = full.subList(lo.seg + 1, hi.seg + 1)
                val pts = listOf(doubleArrayOf(lo.lat, lo.lon)) + mid + listOf(doubleArrayOf(hi.lat, hi.lon))
                if (lo === s) pts else pts.reversed()
            }
            from == n -> { val (a, b) = cutAt(s); if (to == g.eA[e]) a.reversed() else b }
            to == n + 1 -> { val (a, b) = cutAt(t); if (from == g.eA[e]) a else b.reversed() }
            else -> if (from == g.eA[e]) full else full.reversed()
        }
    }

    private fun build(hops: List<IntArray>, s: Graph.Snap, t: Graph.Snap, sPre: Double, tPre: Double, mode: RouteMode, n: Int): Route {
        val lats = ArrayList<Double>(); val lons = ArrayList<Double>()
        val kmByCls = DoubleArray(4)
        var offroad = 0.0; var total = 0.0; var time = 0.0; var gates = 0
        val hopStartIdx = IntArray(hops.size)
        val edges = IntArray(hops.size)
        for ((i, h) in hops.withIndex()) {
            val geom = hopGeometry(h, s, t, n)
            hopStartIdx[i] = maxOf(0, lats.size - 1)
            for ((k, p) in geom.withIndex()) {
                if (k == 0 && lats.isNotEmpty()) continue
                lats.add(p[0]); lons.add(p[1])
            }
            val e = h[2]; edges[i] = e
            var len = 0.0
            for (k in 0 until geom.size - 1) len += hypot((geom[k + 1][1] - geom[k][1]) * 111320.0 * cos(Math.toRadians(geom[k][0])), (geom[k + 1][0] - geom[k][0]) * 110540.0)
            total += len
            kmByCls[g.eCls[e].toInt()] += len / 1000.0
            if (!Kind.isRoad(g.eKind[e].toInt())) offroad += len
            time += len / (edgeSpeed(e) / 3.6)
            if ((g.eFlags[e] and Flag.GATE) != 0 && (i == 0 || hops[i - 1][2] != e)) gates++
        }
        val la = lats.toDoubleArray(); val lo = lons.toDoubleArray()
        val cum = DoubleArray(la.size)
        for (i in 1 until la.size) cum[i] = cum[i - 1] + hypot((lo[i] - lo[i - 1]) * 111320.0 * cos(Math.toRadians(la[i])), (la[i] - la[i - 1]) * 110540.0)
        val man = maneuvers(hops, hopStartIdx, la, lo, cum, n)
        val warnings = buildList {
            if (kmByCls[Cls.NO] > 0.05) add("%.1f km where ATVs aren't allowed. No legal way around was found.".format(kmByCls[Cls.NO]))
            if (kmByCls[Cls.MAYBE] > 0.05) add("%.1f km on \"maybe\" trails: a pass or permission might be needed.".format(kmByCls[Cls.MAYBE]))
            if (gates > 0) add("$gates gate${if (gates > 1) "s" else ""} or barrier${if (gates > 1) "s" else ""} on forest roads, may be closed.")
        }
        return Route(mode, la, lo, cum, total, time, kmByCls, if (total > 0) offroad / total else 0.0, gates, man, edges, warnings)
    }

    private fun bearing(la1: Double, lo1: Double, la2: Double, lo2: Double): Double {
        val dx = (lo2 - lo1) * cos(Math.toRadians(la1)); val dy = la2 - la1
        return atan2(dx, dy) * 180.0 / PI
    }

    /** Point index ~[back] metres before / after index i along the route. */
    private fun along(cum: DoubleArray, i: Int, delta: Double): Int {
        val target = cum[i] + delta
        var j = i
        if (delta < 0) { while (j > 0 && cum[j] > target) j-- } else { while (j < cum.size - 1 && cum[j] < target) j++ }
        return j
    }

    private fun label(e: Int): String = g.name(e) ?: when (g.eKind[e].toInt()) {
        Kind.TRACK -> "the trail"; Kind.PATH -> "the narrow trail"; Kind.FOREST -> "the forest road"
        Kind.CLUB -> "the club trail"; Kind.SERVICE -> "the lane"; else -> "the road"
    }

    private fun maneuvers(hops: List<IntArray>, startIdx: IntArray, la: DoubleArray, lo: DoubleArray, cum: DoubleArray, n: Int): List<Maneuver> {
        val out = ArrayList<Maneuver>()
        val first = hops.first()[2]
        out.add(Maneuver(0, 0.0, Turn.START, "Head out on ${label(first)}", label(first), g.eCls[first].toInt()))
        for (i in 1 until hops.size) {
            val node = hops[i][0]
            val ePrev = hops[i - 1][2]; val eNext = hops[i][2]
            if (ePrev == eNext || node >= n) continue
            val deg = g.adjStart[node + 1] - g.adjStart[node]
            val idx = startIdx[i]
            val a = along(cum, idx, -25.0); val b = along(cum, idx, 25.0)
            if (a == idx || b == idx) continue
            val bin = bearing(la[a], lo[a], la[idx], lo[idx]); val bout = bearing(la[idx], lo[idx], la[b], lo[b])
            var d = bout - bin
            while (d > 180) d -= 360
            while (d < -180) d += 360
            val roadPrev = Kind.isRoad(g.eKind[ePrev].toInt()); val roadNext = Kind.isRoad(g.eKind[eNext].toInt())
            val nameChange = (g.name(ePrev) ?: "") != (g.name(eNext) ?: "")
            val turn = when {
                abs(d) < 25 -> Turn.STRAIGHT
                d in 25.0..60.0 -> Turn.SLIGHT_RIGHT; d in 60.0..135.0 -> Turn.RIGHT; d in 135.0..170.0 -> Turn.SHARP_RIGHT
                d in -60.0..-25.0 -> Turn.SLIGHT_LEFT; d in -135.0..-60.0 -> Turn.LEFT; d in -170.0..-135.0 -> Turn.SHARP_LEFT
                else -> Turn.U_TURN
            }
            val significant = deg >= 3 && (turn != Turn.STRAIGHT || roadPrev != roadNext || (nameChange && g.name(eNext) != null))
            if (!significant) continue
            val onto = label(eNext)
            val verb = when (turn) {
                Turn.STRAIGHT -> "Continue onto"; Turn.SLIGHT_LEFT -> "Keep left onto"; Turn.SLIGHT_RIGHT -> "Keep right onto"
                Turn.LEFT -> "Turn left onto"; Turn.RIGHT -> "Turn right onto"; Turn.SHARP_LEFT -> "Sharp left onto"
                Turn.SHARP_RIGHT -> "Sharp right onto"; Turn.U_TURN -> "Turn around onto"; else -> "Continue onto"
            }
            // merge with a maneuver that is very close (avoid double announcements)
            if (out.isNotEmpty() && cum[idx] - out.last().distFromStart < 15 && out.last().turn != Turn.START) out.removeAt(out.size - 1)
            out.add(Maneuver(idx, cum[idx], turn, "$verb $onto", onto, g.eCls[eNext].toInt()))
        }
        out.add(Maneuver(la.size - 1, cum.last(), Turn.ARRIVE, "Arrive at destination", "", -1))
        return out
    }
}
