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

    @Test fun avoidMaybeReducesMaybe() {
        val a = r.route(bancroft.first, bancroft.second, weslemkoon.first, weslemkoon.second, RouteMode.TRAILS_ONLY, false)!!
        val b = r.route(bancroft.first, bancroft.second, weslemkoon.first, weslemkoon.second, RouteMode.TRAILS_ONLY, true)!!
        println("maybe km: normal=%.1f avoid=%.1f".format(a.kmByCls[Cls.MAYBE], b.kmByCls[Cls.MAYBE]))
        assertTrue(b.kmByCls[Cls.MAYBE] <= a.kmByCls[Cls.MAYBE] + 0.01)
    }
}
