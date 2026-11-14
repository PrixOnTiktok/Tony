package ca.bancroft.trails.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Trail/road status class of a segment (matches pipeline/build_data.py). */
object Cls {
    const val FREE = 0
    const val MAYBE = 1
    const val ROAD = 2
    const val NO = 3
}

object Kind {
    const val TRACK = 0
    const val PATH = 1
    const val FOREST = 2
    const val MINOR = 3
    const val MAJOR = 4
    const val HWY = 5
    const val CLUB = 6
    const val SERVICE = 7
    /** dotted link where open data is missing the bit joining a trail to the network */
    const val GAP = 8
    fun isRoad(k: Int) = k == MINOR || k == MAJOR || k == HWY || k == SERVICE
}

object Flag {
    const val CROWN = 1
    const val PARK = 2
    const val OSM = 4
    const val MNRF = 8
    const val OTN = 16
    const val GATE = 32
    const val BRIDGE = 64
    const val LOWCONF = 128
    const val PRIVATE = 256
    const val CLUB = 512
    const val HIKING = 1024
    const val RESTRICTED = 2048
    const val UNPAVED = 4096
}

/**
 * The routable road + trail network, loaded from assets/graph.bin.
 * Coordinates are stored as micro-degrees (Int) to keep memory small.
 */
class Graph private constructor(
    val nodeLat: IntArray, val nodeLon: IntArray,
    val eA: IntArray, val eB: IntArray, val eLen: FloatArray,
    val eFirst: IntArray, val eCount: IntArray,
    val eCls: ByteArray, val eKind: ByteArray, val eFlags: IntArray,
    val eReason: ByteArray, val eConf: ByteArray, val eMuni: ShortArray,
    val eName: IntArray, val eNote: IntArray,
    val ptLat: IntArray, val ptLon: IntArray,
    val strings: Array<String>,
) {
    val nodeCount get() = nodeLat.size
    val edgeCount get() = eA.size

    // CSR adjacency: for node n, edges adjEdge[adjStart[n] until adjStart[n+1]]
    val adjStart = IntArray(nodeCount + 1)
    val adjEdge: IntArray

    init {
        for (e in 0 until edgeCount) { adjStart[eA[e] + 1]++; adjStart[eB[e] + 1]++ }
        for (i in 0 until nodeCount) adjStart[i + 1] += adjStart[i]
        adjEdge = IntArray(adjStart[nodeCount])
        val fill = adjStart.copyOf()
        for (e in 0 until edgeCount) { adjEdge[fill[eA[e]]++] = e; adjEdge[fill[eB[e]]++] = e }
    }

    /** Connected-component id per node; routing snaps only onto the main (largest) network. */
    val component: IntArray = IntArray(nodeCount) { -1 }
    val mainComponent: Int

    init {
        var next = 0
        val sizes = ArrayList<Int>()
        val stack = IntArrayList()
        for (start in 0 until nodeCount) {
            if (component[start] != -1) continue
            var size = 0
            stack.clear(); stack.add(start); component[start] = next
            while (stack.size > 0) {
                val u = stack.pop(); size++
                for (k in adjStart[u] until adjStart[u + 1]) {
                    val v = other(adjEdge[k], u)
                    if (component[v] == -1) { component[v] = next; stack.add(v) }
                }
            }
            sizes.add(size); next++
        }
        mainComponent = sizes.indices.maxByOrNull { sizes[it] } ?: 0
    }

    fun inMain(e: Int) = component[eA[e]] == mainComponent

    fun other(e: Int, n: Int) = if (eA[e] == n) eB[e] else eA[e]
    fun name(e: Int): String? = eName[e].takeIf { it >= 0 }?.let { strings[it] }
    fun note(e: Int): String? = eNote[e].takeIf { it >= 0 }?.let { strings[it] }

    /** Edge geometry as (lat, lon) degree pairs, oriented from node [from]. */
    fun geometry(e: Int, from: Int = eA[e]): List<DoubleArray> {
        val out = ArrayList<DoubleArray>(eCount[e])
        val f = eFirst[e]
        for (i in 0 until eCount[e]) out.add(doubleArrayOf(ptLat[f + i] / 1e6, ptLon[f + i] / 1e6))
        if (from != eA[e]) out.reverse()
        return out
    }

    // ---- spatial index (grid of ~1 km cells) for snapping a tap / GPS fix to the nearest segment
    private val cell = 0.01
    private val grid = HashMap<Long, IntArrayList>()

    init {
        for (e in 0 until edgeCount) {
            val f = eFirst[e]
            var minLat = Int.MAX_VALUE; var maxLat = Int.MIN_VALUE; var minLon = Int.MAX_VALUE; var maxLon = Int.MIN_VALUE
            for (i in 0 until eCount[e]) {
                minLat = min(minLat, ptLat[f + i]); maxLat = max(maxLat, ptLat[f + i])
                minLon = min(minLon, ptLon[f + i]); maxLon = max(maxLon, ptLon[f + i])
            }
            val r0 = floor(minLat / 1e6 / cell).toInt(); val r1 = floor(maxLat / 1e6 / cell).toInt()
            val c0 = floor(minLon / 1e6 / cell).toInt(); val c1 = floor(maxLon / 1e6 / cell).toInt()
            for (r in r0..r1) for (c in c0..c1) grid.getOrPut(key(r, c)) { IntArrayList() }.add(e)
        }
    }

    private fun key(r: Int, c: Int) = (r.toLong() shl 32) or (c.toLong() and 0xffffffffL)

    data class Snap(val edge: Int, val seg: Int, val t: Double, val lat: Double, val lon: Double, val distM: Double)

    /** Nearest point on any edge within [maxM] metres, optionally filtered. */
    fun snap(lat: Double, lon: Double, maxM: Double = 300.0, accept: (Int) -> Boolean = { true }): Snap? {
        val kx = 111320.0 * cos(Math.toRadians(lat)); val ky = 110540.0
        val rad = (maxM / 111000.0 / cell).toInt() + 1
        val r0 = floor(lat / cell).toInt(); val c0 = floor(lon / cell).toInt()
        var best: Snap? = null
        val seen = HashSet<Int>()
        for (r in r0 - rad..r0 + rad) for (c in c0 - rad..c0 + rad) {
            val list = grid[key(r, c)] ?: continue
            for (k in 0 until list.size) {
                val e = list[k]
                if (!seen.add(e) || !accept(e)) continue
                val f = eFirst[e]
                for (i in 0 until eCount[e] - 1) {
                    val ax = (ptLon[f + i] / 1e6 - lon) * kx; val ay = (ptLat[f + i] / 1e6 - lat) * ky
                    val bx = (ptLon[f + i + 1] / 1e6 - lon) * kx; val by = (ptLat[f + i + 1] / 1e6 - lat) * ky
                    val dx = bx - ax; val dy = by - ay
                    val l2 = dx * dx + dy * dy
                    val t = if (l2 == 0.0) 0.0 else ((-ax * dx - ay * dy) / l2).coerceIn(0.0, 1.0)
                    val px = ax + t * dx; val py = ay + t * dy
                    val d = hypot(px, py)
                    if (d <= maxM && (best == null || d < best.distM)) {
                        best = Snap(e, i, t, lat + py / ky, lon + px / kx, d)
                    }
                }
            }
        }
        return best
    }

    companion object {
        fun parse(bytes: ByteArray): Graph {
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(4).also { b.get(it) }
            require(String(magic) == "ATVG") { "bad graph file" }
            b.short // version
            val nN = b.int; val nE = b.int; val nP = b.int; val nS = b.int
            val nodeLat = IntArray(nN); val nodeLon = IntArray(nN)
            for (i in 0 until nN) { nodeLat[i] = b.int; nodeLon[i] = b.int }
            val eA = IntArray(nE); val eB = IntArray(nE); val eLen = FloatArray(nE)
            val eFirst = IntArray(nE); val eCount = IntArray(nE)
            val eCls = ByteArray(nE); val eKind = ByteArray(nE); val eFlags = IntArray(nE)
            val eReason = ByteArray(nE); val eConf = ByteArray(nE); val eMuni = ShortArray(nE)
            val eName = IntArray(nE); val eNote = IntArray(nE)
            for (i in 0 until nE) {
                eA[i] = b.int; eB[i] = b.int; eLen[i] = b.float; eFirst[i] = b.int
                eCount[i] = b.short.toInt() and 0xffff
                eCls[i] = b.get(); eKind[i] = b.get(); eFlags[i] = b.short.toInt() and 0xffff
                eReason[i] = b.get(); eConf[i] = b.get(); eMuni[i] = b.short
                eName[i] = b.int; eNote[i] = b.int
            }
            val ptLat = IntArray(nP); val ptLon = IntArray(nP)
            for (i in 0 until nP) { ptLat[i] = b.int; ptLon[i] = b.int }
            val strings = Array(nS) {
                val len = b.short.toInt() and 0xffff
                val s = ByteArray(len); b.get(s); String(s, Charsets.UTF_8)
            }
            return Graph(nodeLat, nodeLon, eA, eB, eLen, eFirst, eCount, eCls, eKind, eFlags, eReason, eConf, eMuni,
                eName, eNote, ptLat, ptLon, strings)
        }
    }
}

/** Minimal growable int list (avoids boxing for the spatial index). */
class IntArrayList {
    private var a = IntArray(4)
    var size = 0; private set
    fun add(v: Int) { if (size == a.size) a = a.copyOf(size * 2); a[size++] = v }
    operator fun get(i: Int) = a[i]
    fun pop(): Int = a[--size]
    fun clear() { size = 0 }
}
