package ca.bancroft.trails

import android.app.Application
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import ca.bancroft.trails.data.Cls
import ca.bancroft.trails.data.Flag
import ca.bancroft.trails.data.Kind
import ca.bancroft.trails.data.RouteMode
import ca.bancroft.trails.nav.Navigator
import ca.bancroft.trails.routing.Router
import ca.bancroft.trails.ui.BrowsePanel
import ca.bancroft.trails.ui.DetailsPanel
import ca.bancroft.trails.ui.LoopPanel
import ca.bancroft.trails.ui.SosPanel
import ca.bancroft.trails.ui.CompassWidget
import ca.bancroft.trails.ui.MapViewModel
import ca.bancroft.trails.ui.Mode
import ca.bancroft.trails.ui.NavBanner
import ca.bancroft.trails.ui.NavBottomBar
import ca.bancroft.trails.ui.NearbyPanel
import ca.bancroft.trails.ui.PlanPanel
import ca.bancroft.trails.ui.RoundButton
import ca.bancroft.trails.ui.Target
import ca.bancroft.trails.ui.TrailTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the real Compose panels over map backgrounds (rendered from the same bundled style in a browser)
 * at Pixel 11 Pro size: ~412 x 915 dp.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-port-xhdpi", application = Application::class)
class ScreenshotTest {
    private val bgDir = System.getProperty("screenshot.bg") ?: "../../preview"
    private val out = File("build/screenshots").apply { mkdirs() }

    private fun vm(): MapViewModel {
        val vm = MapViewModel(ApplicationProvider.getApplicationContext())
        val t0 = System.currentTimeMillis()
        while (vm.data.value == null && System.currentTimeMillis() - t0 < 60000) Thread.sleep(50)
        return vm
    }

    @Composable
    private fun Phone(bg: String, dark: Boolean, top: @Composable () -> Unit = {}, fabs: Boolean = true, content: @Composable BoxScope.() -> Unit) {
        TrailTheme(dark) {
            Box(Modifier.fillMaxSize()) {
                val f = File(bgDir, bg)
                if (f.exists()) Image(BitmapFactory.decodeFile(f.path).asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Column(Modifier.fillMaxWidth().padding(top = 40.dp, start = 12.dp, end = 12.dp)) { top() }
                if (fabs) Column(Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 12.dp), horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CompassWidget(32f, 0f) {}
                    Surface(shape = RoundedCornerShape(50), color = androidx.compose.ui.graphics.Color(0xFFB3261E), contentColor = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(width = 64.dp, height = 40.dp)) { Box(contentAlignment = Alignment.Center) { Text("SOS", fontWeight = FontWeight.Bold) } }
                }
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 20.dp), horizontalAlignment = Alignment.End) {
                    if (fabs) Column(Modifier.padding(end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.End) {
                        RoundButton({}, 56) { Icon(Icons.Filled.Layers, null) }
                        RoundButton({}, 64) { Icon(Icons.Filled.MyLocation, null, Modifier.size(30.dp)) }
                    }
                    Box(Modifier.fillMaxWidth()) { content() }
                }
            }
        }
    }

    @Composable
    private fun Chip(date: String) {
        Row {
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), shadowElevation = 3.dp) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CloudOff, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(6.dp))
                    Text("Offline · data $date", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    @Test fun browse() {
        val vm = vm()
        captureRoboImage(File(out, "1_browse.png").path) {
            Phone("bg_browse.png", false, top = { Chip(vm.data.value!!.meta.osmDate) }) { BrowsePanel(vm, false, {}, {}, {}, {}) }
        }
    }

    @Test fun networkView() {
        val vm = vm()
        captureRoboImage(File(out, "7_trail_network.png").path) {
            Phone("bg_network.png", false, top = { NetChip() }) { BrowsePanel(vm, false, {}, {}, {}, {}) }
        }
    }

    @Test fun satellite() {
        val vm = vm()
        captureRoboImage(File(out, "8_satellite.png").path) {
            Phone("bg_satellite.png", false, top = { SatChip() }) { BrowsePanel(vm, true, {}, {}, {}, {}) }
        }
    }

    @Test fun loop() {
        val vm = vm()
        val r = Router(vm.data.value!!.graph).loop(45.0575, -77.8553, 30.0, RouteMode.MOSTLY_TRAILS, false)
        captureRoboImage(File(out, "9_loop.png").path) {
            Phone("bg_loop.png", false, fabs = false) { LoopPanel(vm, Mode.Loop(30, RouteMode.MOSTLY_TRAILS, r, false)) {} }
        }
    }

    @Test fun myLocation() {
        val vm = vm()
        captureRoboImage(File(out, "10_my_location.png").path) {
            Phone("bg_browse.png", false, fabs = false) {
                DetailsPanel(vm, Target.Me(45.0575, -77.8553, 6f, 330.0, 0.0), null) {}
            }
        }
    }

    @Test fun sos() {
        val vm = vm()
        val loc = android.location.Location("gps").apply { latitude = 45.1200; longitude = -77.6200; accuracy = 8f }
        captureRoboImage(File(out, "11_stranded.png").path) {
            Phone("bg_network.png", false, fabs = false) { SosPanel(vm, loc, 40f) }
        }
    }

    @Test fun home() {
        val vm = vm()
        captureRoboImage(File(out, "12_home.png").path) {
            Phone("bg_browse.png", false, fabs = false) { DetailsPanel(vm, vm.home.value, null) {} }
        }
    }

    @Composable private fun NetChip() = Pill("Trail network view")
    @Composable private fun SatChip() = Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Pill("Satellite · trails offline")
        Surface(shape = RoundedCornerShape(50), color = androidx.compose.ui.graphics.Color(0xFFB3261E), contentColor = androidx.compose.ui.graphics.Color.White) {
            Text("Recording · 12.4 km · 0:48", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }

    @Composable
    private fun Pill(t: String) {
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), shadowElevation = 3.dp) {
            Text(t, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }

    @Test fun browseDark() {
        val vm = vm()
        captureRoboImage(File(out, "6_browse_dark.png").path) {
            Phone("bg_browse_dark.png", true, top = { Chip(vm.data.value!!.meta.osmDate) }) { BrowsePanel(vm, false, {}, {}, {}, {}) }
        }
    }

    @Test fun trailDetails() {
        val vm = vm()
        val t = Target.Trail(45.03, -77.835, "Hastings Heritage Trail", Cls.MAYBE, Kind.PATH, 2, "Eastern Ontario Trails Alliance", 2,
            Flag.OSM or Flag.OTN or Flag.CLUB)
        captureRoboImage(File(out, "2_trail_details.png").path) {
            Phone("bg_detail.png", false) { DetailsPanel(vm, t, null) {} }
        }
    }

    @Test fun gasNearby() {
        val vm = vm()
        captureRoboImage(File(out, "3_gas_nearby.png").path) {
            Phone("bg_browse.png", false, fabs = false) { NearbyPanel(vm, "gas", null) }
        }
    }

    private fun routes(vm: MapViewModel) = Router(vm.data.value!!.graph).let { r ->
        RouteMode.entries.associateWith { r.route(45.0575, -77.8553, 44.8530, -77.8250, it, false) }
    }

    @Test fun plan() {
        val vm = vm()
        val dest = Target.Place(vm.data.value!!.pois.first { it.name.contains("Coe Hill") || it.cat == "food" })
        val m = Mode.Plan(dest, routes(vm), RouteMode.MOSTLY_TRAILS, computing = false)
        captureRoboImage(File(out, "4_route_plan.png").path) {
            Phone("bg_plan.png", false, fabs = false) { PlanPanel(vm, m, hasLocation = true, onNeedPermission = {}, onStart = {}) }
        }
    }

    @Test fun navigating() {
        val vm = vm()
        val r = routes(vm)[RouteMode.MOSTLY_TRAILS]!!
        val nav = Navigator(r)
        val i = (r.lats.size * 0.12).toInt()
        var st = nav.update(r.lats[0], r.lons[0])
        for (k in 0..i step 3) st = nav.update(r.lats[k], r.lons[k])
        val m = Mode.Navigating(Target.Point(44.853, -77.825), r, st)
        captureRoboImage(File(out, "5_navigating.png").path) {
            Phone("bg_nav.png", true, top = { NavBanner(m, true) }, fabs = false) { NavBottomBar(vm, m, {}, following = true) }
        }
    }
}
