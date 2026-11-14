package ca.bancroft.trails

import ca.bancroft.trails.data.Graph
import ca.bancroft.trails.data.RouteMode
import ca.bancroft.trails.routing.Router
import org.junit.Test
import java.io.File

/** Writes sample routes as GeoJSON (used to render realistic map backgrounds for screenshots). */
class RouteExport {
    @Test fun export() {
        val g = Graph.parse(File("src/main/assets/graph.bin").readBytes())
        val r = Router(g)
        val out = File("build/sample-routes").apply { mkdirs() }
        for (m in RouteMode.entries) {
            val rt = r.route(45.0575, -77.8553, 44.8530, -77.8250, m, false) ?: continue
            val coords = rt.lats.indices.joinToString(",") { "[${rt.lons[it]},${rt.lats[it]}]" }
            File(out, "${m.name}.geojson").writeText("""{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[$coords]}}""")
        }
        r.loop(45.0575, -77.8553, 30.0, RouteMode.MOSTLY_TRAILS, false)?.let { rt ->
            val coords = rt.lats.indices.joinToString(",") { "[${rt.lons[it]},${rt.lats[it]}]" }
            File(out, "LOOP.geojson").writeText("""{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[$coords]}}""")
        }
    }
}
