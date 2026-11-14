package ca.bancroft.trails

import ca.bancroft.trails.data.Forecast
import ca.bancroft.trails.data.RideAdvisor
import ca.bancroft.trails.data.WeatherHour
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisorTest {
    private val t0 = 1_800_000_000_000L
    private fun fc(vararg h: Triple<Double, Int, Int>, wind: Double = 10.0): Forecast {
        val hours = h.mapIndexed { i, (temp, code, prob) ->
            WeatherHour(t0 + i * 3600_000L, temp, temp - 3, prob, if (prob >= 50) 0.8 else 0.0, code, wind, wind * 1.6, 300.0) }
        return Forecast(t0, 45.0, -77.8, null, null, null, null, null, null, hours, emptyList())
    }
    private fun show(name: String, f: Forecast) = RideAdvisor.advise(f, t0 + 60_000)!!.also {
        println("$name -> ${it.score} ${it.label}: ${it.headline} | ${it.reasons.joinToString(" / ")} | gear: ${it.gear.joinToString()}")
    }

    @Test fun snowTurningToSlush() {
        // snowing at -1, warming to +3 with rain later: the rider's exact complaint
        val v = show("snow->slush", fc(Triple(-1.0, 73, 80), Triple(0.0, 71, 70), Triple(1.0, 61, 60), Triple(2.5, 63, 70), Triple(3.0, 63, 60), Triple(3.0, 3, 30)))
        assertTrue("must not call snow 'good riding'", v.score < 60)
        assertTrue(v.reasons.any { "slush" in it })
    }

    @Test fun clearMildDay() {
        val v = show("clear", fc(Triple(16.0, 0, 0), Triple(18.0, 1, 5), Triple(19.0, 1, 5), Triple(19.0, 2, 10), Triple(18.0, 2, 10), Triple(16.0, 1, 5)))
        assertTrue(v.score >= 80)
    }

    @Test fun rainNow() = assertTrue(show("rain", fc(Triple(8.0, 63, 90), Triple(8.0, 61, 80), Triple(9.0, 3, 40))).score < 60)

    @Test fun freezingRain() = assertTrue(show("freezing rain", fc(Triple(-0.5, 66, 80), Triple(0.0, 67, 70), Triple(0.5, 61, 60))).score < 20)

    @Test fun bitterCold() = assertTrue(show("bitter cold", fc(Triple(-22.0, 0, 0), Triple(-21.0, 0, 0), Triple(-20.0, 1, 0), wind = 20.0)).score < 40)
}
