package ca.bancroft.trails

import ca.bancroft.trails.data.Cls
import ca.bancroft.trails.data.Graph
import ca.bancroft.trails.data.RouteMode
import ca.bancroft.trails.nav.Navigator
import ca.bancroft.trails.nav.formatDist
import ca.bancroft.trails.nav.formatTime
import ca.bancroft.trails.routing.Router
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/** Routes real trips on the bundled data and checks the trail preferences behave. */
class RouterTest {
    companion object {
        lateinit var g: Graph
        lateinit var r: Router

        @BeforeClass @JvmStatic
        fun load() {
            val t0 = System.currentTimeMillis()
            g = Graph.parse(File("src/main/assets/graph.bin").readBytes())
            r = Router(g)
            println("loaded ${g.nodeCount} nodes, ${g.edgeCount} edges in ${System.currentTimeMillis() - t0} ms")
        }
    }

    private val bancroft = 45.0575 to -77.8553
    private val coeHill = 44.8530 to -77.8250
    private val weslemkoon = 45.0230 to -77.4200
    private val maynooth = 45.2280 to -77.9440

    private fun trip(name: String, a: Pair<Double, Double>, b: Pair<Double, Double>) {
        val res = RouteMode.entries.associateWith { m ->
            val t0 = System.currentTimeMillis()
            val rt = r.route(a.first, a.second, b.first, b.second, m, avoidMaybe = false)
            println("$name [$m] ${System.currentTimeMillis() - t0} ms")
            rt
        }
        for ((m, rt) in res) {
            assertNotNull("$name $m", rt); rt!!
            println("  $m: ${formatDist(rt.distanceM)} ${formatTime(rt.timeS)} offroad=${(rt.trailShare * 100).toInt()}% " +
                "free=%.1f maybe=%.1f road=%.1f no=%.1f gates=${rt.gates} turns=${rt.maneuvers.size}".format(*rt.kmByCls.toTypedArray()))
            rt.maneuvers.take(6).forEach { println("     ${formatDist(it.distFromStart)}: ${it.text}") }
            rt.warnings.forEach { println("     ! $it") }
        }
        val fast = res[RouteMode.FASTEST]!!; val mostly = res[RouteMode.MOSTLY_TRAILS]!!; val only = res[RouteMode.TRAILS_ONLY]!!
        assertTrue("fastest should be quickest", fast.timeS <= mostly.timeS + 1 && fast.timeS <= only.timeS + 1)
        assertTrue("trails-only should be at least as off-road", only.trailShare + 1e-6 >= fast.trailShare)
        // navigator sanity: walking the route reaches the end
        val nav = Navigator(mostly)
        var last = nav.update(mostly.lats[0], mostly.lons[0])
        for (i in mostly.lats.indices step 5) last = nav.update(mostly.lats[i], mostly.lons[i])
        last = nav.update(mostly.lats.last(), mostly.lons.last())
        assertTrue("arrives", last.arrived)
    }

    @Test fun bancroftToCoeHill() = trip("Bancroft→Coe Hill", bancroft, coeHill)
    @Test fun bancroftToWeslemkoon() = trip("Bancroft→Weslemkoon", bancroft, weslemkoon)
    @Test fun bancroftToMaynooth() = trip("Bancroft→Maynooth", bancroft, maynooth)

    @Test fun loops() {
        for ((name, start) in listOf("Bancroft" to bancroft, "Coe Hill" to coeHill)) for (km in listOf(20, 50)) {
            val t0 = System.currentTimeMillis()
            val l = r.loop(start.first, start.second, km.toDouble(), RouteMode.MOSTLY_TRAILS, false)
            assertNotNull("$name loop $km", l); l!!
            val back = ca.bancroft.trails.ui.MapViewModel.distM(l.lats.last(), l.lons.last(), l.lats[0], l.lons[0])
            val counts = HashMap<Int, Int>(); l.edges.forEach { counts[it] = (counts[it] ?: 0) + 1 }
            val reuse = counts.values.count { it > 1 }.toDouble() / counts.size
            println("loop $name $km km -> ${formatDist(l.distanceM)} ${formatTime(l.timeS)} offroad=${(l.trailShare * 100).toInt()}% reuse=${(reuse * 100).toInt()}% " +
                "gap=${back.toInt()} m in ${System.currentTimeMillis() - t0} ms")
            assertTrue("ends near start", back < 50)
            assertTrue("roughly the asked length", l.distanceM / 1000 in km * 0.5..km * 1.8)
            // navigator over a loop must not "arrive" at the start
            val nav = Navigator(l)
            var st = nav.update(l.lats[0], l.lons[0])
            for (i in l.lats.indices step 4) st = nav.update(l.lats[i], l.lons[i])
            assertTrue(nav.update(l.lats.last(), l.lons.last()).arrived)
        }
    }

    @Test fun offTrailDestination() {
        // a spot ~400 m into the bush east of a forest road
        val base = r.route(bancroft.first, bancroft.second, weslemkoon.first, weslemkoon.second, RouteMode.FASTEST, false)!!
        val dLat = base.lats.last(); val dLon = base.lons.last() + 400.0 / (111320.0 * Math.cos(Math.toRadians(dLat)))
        val rt = r.route(bancroft.first, bancroft.second, dLat, dLon, RouteMode.FASTEST, false)!!
        println("off-trail end: ${formatDist(rt.offEndM)}  last=${rt.maneuvers.dropLast(1).last().text}")
        assertTrue(rt.offEndM > 35)
        assertTrue(Math.abs(rt.lats.last() - dLat) < 1e-9 && Math.abs(rt.lons.last() - dLon) < 1e-9)
    }

    @Test fun userTrailsAreUsedAndDetected() {
        val base = r.route(bancroft.first, bancroft.second, coeHill.first, coeHill.second, RouteMode.FASTEST, false)!!
        val i = base.lats.size / 4; val j = base.lats.size * 3 / 4
        // a made-up shortcut straight across between two points of the route
        val shortcut = (0..20).map { k -> val f = k / 20.0
            (base.lats[i] + f * (base.lats[j] - base.lats[i])) to (base.lons[i] + f * (base.lons[j] - base.lons[i])) }
        val before = r.route(bancroft.first, bancroft.second, coeHill.first, coeHill.second, RouteMode.MOSTLY_TRAILS, false)!!
        r.setUserTrails(listOf(shortcut))
        val withTrail = r.route(bancroft.first, bancroft.second, coeHill.first, coeHill.second, RouteMode.MOSTLY_TRAILS, false)!!
        r.setUserTrails(emptyList())
        println("user trail: ${formatDist(before.distanceM)} -> ${formatDist(withTrail.distanceM)}, uses it: ${withTrail.edges.any { it >= g.edgeCount }}")
        assertTrue(withTrail.edges.any { it >= g.edgeCount })

        // detection: ride the route but swing 150 m north for a stretch in the middle
        val track = base.lats.indices.map { k ->
            val off = if (k in i..(i + 40).coerceAtMost(j)) 150.0 / 110540.0 else 0.0
            ca.bancroft.trails.data.TrackPoint(base.lats[k] + off, base.lons[k], k * 1000L, null, 10f)
        }
        val found = ca.bancroft.trails.data.UserTrailStore.detect(track, g, emptyList())
        println("detected ${found.size} new trail(s): ${found.joinToString { formatDist(it.lengthM) }}")
        assertTrue(found.isNotEmpty())
    }

    @Test fun avoidMaybeReducesMaybe() {
        val a = r.route(bancroft.first, bancroft.second, weslemkoon.first, weslemkoon.second, RouteMode.TRAILS_ONLY, false)!!
        val b = r.route(bancroft.first, bancroft.second, weslemkoon.first, weslemkoon.second, RouteMode.TRAILS_ONLY, true)!!
        println("maybe km: normal=%.1f avoid=%.1f".format(a.kmByCls[Cls.MAYBE], b.kmByCls[Cls.MAYBE]))
        assertTrue(b.kmByCls[Cls.MAYBE] <= a.kmByCls[Cls.MAYBE] + 0.01)
    }
}
