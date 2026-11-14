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
    val isLoop: Boolean = false,
    /** Straight cross-country bits at the start / end (not on any mapped trail); drawn dashed. */
    val offStartM: Double = 0.0,
    val offEndM: Double = 0.0,
)

const val OFF_TRAIL_M = 35.0
const val OFF_TRAIL_KMH = 8.0

/** A* router over the bundled network with ATV-specific cost profiles. */
class Router(private val g: Graph) {

    // ---- the rider's own trails (ridden where no map has a trail), added on top of the bundled network
    class UserEdge(val a: Int, val b: Int, val len: Double, val pts: List<DoubleArray>)

    private var user: List<UserEdge> = emptyList()
    private var userAdj: Map<Int, List<Int>> = emptyMap()

    /** Each trail is a polyline (lat, lon); its ends are joined to the nearest network node within 120 m. */
    fun setUserTrails(trails: List<List<Pair<Double, Double>>>) {
        val edges = ArrayList<UserEdge>()
        val adj = HashMap<Int, MutableList<Int>>()
        for (t in trails) {
            if (t.size < 2) continue
            val a = nearestNode(t.first().first, t.first().second) ?: continue
            val b = nearestNode(t.last().first, t.last().second) ?: continue
            if (a == b) continue
            val pts = ArrayList<DoubleArray>()
            pts.add(doubleArrayOf(g.nodeLat[a] / 1e6, g.nodeLon[a] / 1e6))
            t.forEach { pts.add(doubleArrayOf(it.first, it.second)) }
            pts.add(doubleArrayOf(g.nodeLat[b] / 1e6, g.nodeLon[b] / 1e6))
            var len = 0.0
            for (i in 0 until pts.size - 1) len += hypot((pts[i + 1][1] - pts[i][1]) * 111320.0 * cos(Math.toRadians(pts[i][0])), (pts[i + 1][0] - pts[i][0]) * 110540.0)
            val id = g.edgeCount + edges.size
            edges.add(UserEdge(a, b, len, pts))
            adj.getOrPut(a) { ArrayList() }.add(id); adj.getOrPut(b) { ArrayList() }.add(id)
        }
        user = edges; userAdj = adj
    }

    private fun nearestNode(lat: Double, lon: Double): Int? {
        val s = g.snap(lat, lon, 120.0) { g.inMain(it) } ?: return null
        val da = hypot((g.nodeLon[g.eA[s.edge]] / 1e6 - lon) * 111320.0 * cos(Math.toRadians(lat)), (g.nodeLat[g.eA[s.edge]] / 1e6 - lat) * 110540.0)
        val db = hypot((g.nodeLon[g.eB[s.edge]] / 1e6 - lon) * 111320.0 * cos(Math.toRadians(lat)), (g.nodeLat[g.eB[s.edge]] / 1e6 - lat) * 110540.0)
        return if (da <= db) g.eA[s.edge] else g.eB[s.edge]
    }

    private fun isUser(e: Int) = e >= g.edgeCount
    private fun ue(e: Int) = user[e - g.edgeCount]
    fun eCls(e: Int): Int = if (isUser(e)) Cls.FREE else g.eCls[e].toInt()
    fun eKind(e: Int): Int = if (isUser(e)) Kind.TRACK else g.eKind[e].toInt()
    private fun eFlags(e: Int): Int = if (isUser(e)) 0 else g.eFlags[e]
    private fun eLen(e: Int): Double = if (isUser(e)) ue(e).len else g.eLen[e].toDouble()
    private fun eA(e: Int): Int = if (isUser(e)) ue(e).a else g.eA[e]
    private fun eB(e: Int): Int = if (isUser(e)) ue(e).b else g.eB[e]
    private fun geom(e: Int): List<DoubleArray> = if (isUser(e)) ue(e).pts else g.geometry(e)
    fun eName(e: Int): String? = if (isUser(e)) "your trail" else g.name(e)
    private fun other(e: Int, n: Int) = if (eA(e) == n) eB(e) else eA(e)

    private fun edgeSpeed(e: Int): Double {
        val kind = eKind(e)
        // rail trails / club trails tagged as paths are wide and graded
        if (kind == Kind.PATH && (eFlags(e) and Flag.CLUB) != 0) return 25.0
        return speedKmh(kind)
    }

    private fun speedKmh(kind: Int) = when (kind) {
        Kind.TRACK -> 20.0; Kind.PATH -> 10.0; Kind.FOREST -> 30.0; Kind.MINOR -> 35.0
        Kind.MAJOR -> 45.0; Kind.HWY -> 45.0; Kind.CLUB -> 20.0; Kind.SERVICE -> 15.0; Kind.GAP -> 8.0; else -> 20.0
    }

    /** Multiplier on travel time; encodes the riding preference. Always >= 1 so the heuristic stays admissible. */
    private fun factor(e: Int, mode: RouteMode, avoidMaybe: Boolean): Double {
        val cls = eCls(e); val kind = eKind(e)
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
        if (kind == Kind.GAP) f *= 2.0 // prefer real trails; use a gap only when it saves a lot
        if ((eFlags(e) and Flag.LOWCONF) != 0 && road) f *= 1.15
        return f
    }

    /** Edges already used by earlier legs of a loop; re-riding them costs more so the loop comes back another way. */
    private var reused: Set<Int> = emptySet()

    private fun edgeCost(e: Int, lenM: Double, mode: RouteMode, avoidMaybe: Boolean, blocked: Set<Int>): Double {
        var c = lenM / (edgeSpeed(e) / 3.6) * factor(e, mode, avoidMaybe)
        if ((eFlags(e) and Flag.GATE) != 0) c += 180.0
        if (e in blocked) c += 1e6
        if (e in reused) c *= 6.0
        return c
    }

    /**
     * A loop of roughly [targetKm] that starts and ends at the rider: tries 8 directions, rides out through two
     * waypoints and back while avoiding the trails already used, and keeps the loop closest to the wanted length
     * with the least backtracking (and the most trail, in the trail modes).
     */
    fun loop(lat: Double, lon: Double, targetKm: Double, mode: RouteMode, avoidMaybe: Boolean, blocked: Set<Int> = emptySet()): Route? {
        val baseSide = targetKm * 1000.0 / 3.0 / 1.35 // straight-line side of a triangle; trails wander ~35 %
        var best: Route? = null; var bestScore = Double.MAX_VALUE
        for (scale in listOf(0.75, 1.0, 1.25)) for (dir in 0 until 8) for (turn in listOf(70.0, -70.0)) {
            val side = baseSide * scale
            val b1 = dir * 45.0
            val (la1, lo1) = offset(lat, lon, b1, side)
            val (la2, lo2) = offset(la1, lo1, b1 + turn * 1.7, side)
            val legs = ArrayList<Route>()
            val used = HashSet<Int>()
            var ok = true
            for ((a, b) in listOf((lat to lon) to (la1 to lo1), (la1 to lo1) to (la2 to lo2), (la2 to lo2) to (lat to lon))) {
                reused = used
                val r = route(a.first, a.second, b.first, b.second, mode, avoidMaybe, blocked)
                reused = emptySet()
                if (r == null || r.distanceM < 200) { ok = false; break }
                legs.add(r); used.addAll(r.edges.toList())
            }
            if (!ok) continue
            val loop = concat(legs, mode)
            val counts = HashMap<Int, Int>()
            loop.edges.forEach { counts[it] = (counts[it] ?: 0) + 1 }
            val reuse = counts.values.count { it > 1 }.toDouble() / counts.size.coerceAtLeast(1)
            val score = kotlin.math.abs(loop.distanceM / 1000.0 - targetKm) / targetKm + 1.5 * reuse +
                (if (mode == RouteMode.FASTEST) 0.0 else 0.4 * (1 - loop.trailShare)) +
                (if (loop.kmByCls[Cls.NO] > 0.05) 1.0 else 0.0) + (if (avoidMaybe) 0.0 else 0.15 * loop.kmByCls[Cls.MAYBE] / (loop.distanceM / 1000.0 + 0.1))
            if (score < bestScore) { bestScore = score; best = loop }
        }
        return best
    }

    private fun offset(lat: Double, lon: Double, bearingDeg: Double, m: Double): Pair<Double, Double> {
        val br = Math.toRadians(bearingDeg)
        return (lat + m * kotlin.math.cos(br) / 110540.0) to (lon + m * kotlin.math.sin(br) / (111320.0 * cos(Math.toRadians(lat))))
    }

    /** Route through [points] in order (each a lat/lon pair); null if any leg fails. */
    fun via(points: List<Pair<Double, Double>>, mode: RouteMode, avoidMaybe: Boolean, blocked: Set<Int> = emptySet(), loop: Boolean = false): Route? {
        if (points.size < 2) return null
        val legs = ArrayList<Route>()
        val used = HashSet<Int>()
        for (i in 0 until points.size - 1) {
            reused = if (loop) used else emptySet()
            val r = route(points[i].first, points[i].second, points[i + 1].first, points[i + 1].second, mode, avoidMaybe, blocked)
            reused = emptySet()
            if (r == null) return null
            if (r.distanceM < 20) continue
            legs.add(r); used.addAll(r.edges.toList())
        }
        if (legs.isEmpty()) return null
        return if (legs.size == 1) legs[0] else concat(legs, mode).copy(isLoop = loop)
    }

    /** A there-and-back ride: [out] then the same way home. */
    fun outAndBack(out: Route): Route {
        val back = out.copy(lats = out.lats.reversedArray(), lons = out.lons.reversedArray(),
            cumDist = DoubleArray(out.cumDist.size) { out.distanceM - out.cumDist[out.cumDist.size - 1 - it] },
            maneuvers = listOf(Maneuver(0, 0.0, Turn.START, "Turn around and head back", "", -1),
                Maneuver(out.lats.size - 1, out.distanceM, Turn.ARRIVE, "Back where you started", "", -1)),
            edges = out.edges.reversedArray(), offStartM = out.offEndM, offEndM = out.offStartM)
        return concat(listOf(out, back), out.mode).copy(isLoop = true)
    }

    /** The rest of [r] from point [from] on (used to rejoin a loop after a detour). Class split is approximate. */
    fun tail(r: Route, from: Int): Route {
        val i0 = from.coerceIn(0, r.lats.size - 2)
        val la = r.lats.copyOfRange(i0, r.lats.size); val lo = r.lons.copyOfRange(i0, r.lons.size)
        val base = r.cumDist[i0]
        val cum = DoubleArray(la.size) { r.cumDist[i0 + it] - base }
        val f = if (r.distanceM > 0) cum.last() / r.distanceM else 0.0
        val man = r.maneuvers.filter { it.pointIndex >= i0 && it.turn != Turn.START }
            .map { it.copy(pointIndex = it.pointIndex - i0, distFromStart = it.distFromStart - base) }
        return r.copy(lats = la, lons = lo, cumDist = cum, distanceM = cum.last(), timeS = r.timeS * f,
            kmByCls = DoubleArray(4) { r.kmByCls[it] * f }, maneuvers = man)
    }

    /** Join consecutive legs into one route (waypoints become "continue" points, not arrivals). */
    fun concat(legs: List<Route>, mode: RouteMode): Route {
        val la = ArrayList<Double>(); val lo = ArrayList<Double>(); val man = ArrayList<Maneuver>()
        val kmByCls = DoubleArray(4); var dist = 0.0; var time = 0.0; var offroad = 0.0; var gates = 0
        val edges = ArrayList<Int>()
        for ((li, r) in legs.withIndex()) {
            val base = maxOf(0, la.size - 1)
            for (i in r.lats.indices) { if (i == 0 && la.isNotEmpty()) continue; la.add(r.lats[i]); lo.add(r.lons[i]) }
            for (m in r.maneuvers) {
                if (m.turn == Turn.START && li > 0) continue
                if (m.turn == Turn.ARRIVE && li < legs.size - 1) continue
                man.add(m.copy(pointIndex = base + m.pointIndex, distFromStart = dist + m.distFromStart,
                    text = if (m.turn == Turn.ARRIVE) "Back where you started" else m.text))
            }
            for (c in 0..3) kmByCls[c] += r.kmByCls[c]
            offroad += r.trailShare * r.distanceM; dist += r.distanceM; time += r.timeS; gates += r.gates
            edges.addAll(r.edges.toList())
        }
        val laA = la.toDoubleArray(); val loA = lo.toDoubleArray()
        val cum = DoubleArray(laA.size)
        for (i in 1 until laA.size) cum[i] = cum[i - 1] + hypot((loA[i] - loA[i - 1]) * 111320.0 * cos(Math.toRadians(laA[i])), (laA[i] - laA[i - 1]) * 110540.0)
        val fixed = man.map { it.copy(distFromStart = cum[it.pointIndex.coerceIn(0, cum.size - 1)]) }
        val warnings = buildList {
            if (kmByCls[Cls.NO] > 0.05) add("%.1f km where ATVs aren't allowed. No legal way around was found.".format(kmByCls[Cls.NO]))
            if (kmByCls[Cls.MAYBE] > 0.05) add("%.1f km on \"maybe\" trails: a pass or permission might be needed.".format(kmByCls[Cls.MAYBE]))
            if (gates > 0) add("$gates gate${if (gates > 1) "s" else ""} on forest roads, may be closed.")
        }
        return Route(mode, laA, loA, cum, cum.last(), time, kmByCls, if (dist > 0) offroad / dist else 0.0, gates, fixed, edges.toIntArray(), warnings, isLoop = true)
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

        val sPre = prefixLen(s.edge, s.seg, s.t); val sLen = eLen(s.edge)
        val tPre = prefixLen(t.edge, t.seg, t.t); val tLen = eLen(t.edge)
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
        relax(eA(s.edge), edgeCost(s.edge, sPre, mode, avoidMaybe, blocked), S, s.edge)
        relax(eB(s.edge), edgeCost(s.edge, sLen - sPre, mode, avoidMaybe, blocked), S, s.edge)
        if (s.edge == t.edge) relax(T, edgeCost(s.edge, abs(tPre - sPre), mode, avoidMaybe, blocked), S, s.edge)

        while (pq.isNotEmpty()) {
            val top = pq.poll()!!
            val u = top[1].toInt()
            if (done[u]) continue
            done[u] = true
            if (u == T) break
            if (u >= n) continue
            if (u == eA(t.edge)) relax(T, dist[u] + edgeCost(t.edge, tPre, mode, avoidMaybe, blocked), u, t.edge)
            if (u == eB(t.edge)) relax(T, dist[u] + edgeCost(t.edge, tLen - tPre, mode, avoidMaybe, blocked), u, t.edge)
            for (k in g.adjStart[u] until g.adjStart[u + 1]) {
                val e = g.adjEdge[k]
                val v = other(e, u)
                if (done[v]) continue
                relax(v, dist[u] + edgeCost(e, eLen(e), mode, avoidMaybe, blocked), u, e)
            }
            userAdj[u]?.forEach { e ->
                val v = other(e, u)
                if (!done[v]) relax(v, dist[u] + edgeCost(e, eLen(e), mode, avoidMaybe, blocked), u, e)
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
        return build(hops, s, t, mode, n, fromLat, fromLon, toLat, toLon)
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
        val full = geom(e) // A -> B
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
            from == n -> { val (a, b) = cutAt(s); if (to == eA(e)) a.reversed() else b }
            to == n + 1 -> { val (a, b) = cutAt(t); if (from == eA(e)) a else b.reversed() }
            else -> if (from == eA(e)) full else full.reversed()
        }
    }

    private fun build(hops: List<IntArray>, s: Graph.Snap, t: Graph.Snap, mode: RouteMode, n: Int,
                      fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Route {
        val lats = ArrayList<Double>(); val lons = ArrayList<Double>()
        // you may start (or want to end) off the mapped network: ride straight to/from the nearest trail
        val offStart = if (s.distM > OFF_TRAIL_M) s.distM else 0.0
        val offEnd = if (t.distM > OFF_TRAIL_M) t.distM else 0.0
        if (offStart > 0) { lats.add(fromLat); lons.add(fromLon) }
        val kmByCls = DoubleArray(4)
        var offroad = 0.0; var total = 0.0; var time = 0.0; var gates = 0
        val hopStartIdx = IntArray(hops.size)
        val edges = IntArray(hops.size)
        for ((i, h) in hops.withIndex()) {
            val geom = hopGeometry(h, s, t, n)
            hopStartIdx[i] = if (i == 0) lats.size else lats.size - 1
            for ((k, p) in geom.withIndex()) {
                if (k == 0 && i > 0) continue
                lats.add(p[0]); lons.add(p[1])
            }
            val e = h[2]; edges[i] = e
            var len = 0.0
            for (k in 0 until geom.size - 1) len += hypot((geom[k + 1][1] - geom[k][1]) * 111320.0 * cos(Math.toRadians(geom[k][0])), (geom[k + 1][0] - geom[k][0]) * 110540.0)
            total += len
            kmByCls[eCls(e)] += len / 1000.0
            if (!Kind.isRoad(eKind(e))) offroad += len
            time += len / (edgeSpeed(e) / 3.6)
            if ((eFlags(e) and Flag.GATE) != 0 && (i == 0 || hops[i - 1][2] != e)) gates++
        }
        if (offEnd > 0) { lats.add(toLat); lons.add(toLon) }
        total += offStart + offEnd
        time += (offStart + offEnd) / (OFF_TRAIL_KMH / 3.6)
        val la = lats.toDoubleArray(); val lo = lons.toDoubleArray()
        val cum = DoubleArray(la.size)
        for (i in 1 until la.size) cum[i] = cum[i - 1] + hypot((lo[i] - lo[i - 1]) * 111320.0 * cos(Math.toRadians(la[i])), (la[i] - la[i - 1]) * 110540.0)
        val man = maneuvers(hops, hopStartIdx, la, lo, cum, n).toMutableList()
        if (offStart > 0) man[0] = man[0].copy(text = "Head ${formatOff(offStart)} cross-country to ${man[0].onto}")
        if (offEnd > 0) {
            val i = la.size - 2
            man.add(man.size - 1, Maneuver(i, cum[i], Turn.STRAIGHT, "Leave the trail: ${formatOff(offEnd)} cross-country to the destination", "off-trail", Cls.MAYBE))
        }
        val warnings = buildList {
            if (kmByCls[Cls.NO] > 0.05) add("%.1f km where ATVs aren't allowed. No legal way around was found.".format(kmByCls[Cls.NO]))
            if (kmByCls[Cls.MAYBE] > 0.05) add("%.1f km on \"maybe\" trails: a pass or permission might be needed.".format(kmByCls[Cls.MAYBE]))
            if (gates > 0) add("$gates gate${if (gates > 1) "s" else ""} or barrier${if (gates > 1) "s" else ""} on forest roads, may be closed.")
        }
        val w = warnings.toMutableList()
        if (offStart + offEnd > 0) w.add("${formatOff(offStart + offEnd)} is off any mapped trail (dashed). Only ride where you're allowed.")
        return Route(mode, la, lo, cum, total, time, kmByCls, if (total > 0) (offroad + offStart + offEnd) / total else 0.0, gates, man, edges, w,
            offStartM = offStart, offEndM = offEnd)
    }

    private fun formatOff(m: Double) = ca.bancroft.trails.nav.formatDist(m)

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

    private fun label(e: Int): String = eName(e) ?: when (eKind(e)) {
        Kind.TRACK -> "the trail"; Kind.PATH -> "the narrow trail"; Kind.FOREST -> "the forest road"
        Kind.CLUB -> "the club trail"; Kind.SERVICE -> "the lane"; else -> "the road"
    }

    private fun maneuvers(hops: List<IntArray>, startIdx: IntArray, la: DoubleArray, lo: DoubleArray, cum: DoubleArray, n: Int): List<Maneuver> {
        val out = ArrayList<Maneuver>()
        val first = hops.first()[2]
        out.add(Maneuver(0, 0.0, Turn.START, "Head out on ${label(first)}", label(first), eCls(first)))
        for (i in 1 until hops.size) {
            val node = hops[i][0]
            val ePrev = hops[i - 1][2]; val eNext = hops[i][2]
            if (ePrev == eNext || node >= n) continue
            val deg = g.adjStart[node + 1] - g.adjStart[node] + (userAdj[node]?.size ?: 0)
            val idx = startIdx[i]
            val a = along(cum, idx, -25.0); val b = along(cum, idx, 25.0)
            if (a == idx || b == idx) continue
            val bin = bearing(la[a], lo[a], la[idx], lo[idx]); val bout = bearing(la[idx], lo[idx], la[b], lo[b])
            var d = bout - bin
            while (d > 180) d -= 360
            while (d < -180) d += 360
            val roadPrev = Kind.isRoad(eKind(ePrev)); val roadNext = Kind.isRoad(eKind(eNext))
            val nameChange = (eName(ePrev) ?: "") != (eName(eNext) ?: "")
            val turn = when {
                abs(d) < 25 -> Turn.STRAIGHT
                d in 25.0..60.0 -> Turn.SLIGHT_RIGHT; d in 60.0..135.0 -> Turn.RIGHT; d in 135.0..170.0 -> Turn.SHARP_RIGHT
                d in -60.0..-25.0 -> Turn.SLIGHT_LEFT; d in -135.0..-60.0 -> Turn.LEFT; d in -170.0..-135.0 -> Turn.SHARP_LEFT
                else -> Turn.U_TURN
            }
            val significant = deg >= 3 && (turn != Turn.STRAIGHT || roadPrev != roadNext || (nameChange && eName(eNext) != null))
            if (!significant) continue
            val onto = label(eNext)
            val verb = when (turn) {
                Turn.STRAIGHT -> "Continue onto"; Turn.SLIGHT_LEFT -> "Keep left onto"; Turn.SLIGHT_RIGHT -> "Keep right onto"
                Turn.LEFT -> "Turn left onto"; Turn.RIGHT -> "Turn right onto"; Turn.SHARP_LEFT -> "Sharp left onto"
                Turn.SHARP_RIGHT -> "Sharp right onto"; Turn.U_TURN -> "Turn around onto"; else -> "Continue onto"
            }
            // merge with a maneuver that is very close (avoid double announcements)
            if (out.isNotEmpty() && cum[idx] - out.last().distFromStart < 15 && out.last().turn != Turn.START) out.removeAt(out.size - 1)
            out.add(Maneuver(idx, cum[idx], turn, "$verb $onto", onto, eCls(eNext)))
        }
        out.add(Maneuver(la.size - 1, cum.last(), Turn.ARRIVE, "Arrive at destination", "", -1))
        return out
    }
}
