package ca.bancroft.trails.data

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/** Offline sunrise/sunset (NOAA approximation, ±2 min) so the app can warn before dark. */
object Sun {
    /** Sunset time in epoch millis for the given day, or null in polar day/night. */
    fun sunset(lat: Double, lon: Double, dayMillis: Long = System.currentTimeMillis()): Long? = event(lat, lon, dayMillis, false)
    fun sunrise(lat: Double, lon: Double, dayMillis: Long = System.currentTimeMillis()): Long? = event(lat, lon, dayMillis, true)

    private fun event(lat: Double, lon: Double, dayMillis: Long, rise: Boolean): Long? {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = dayMillis }
        val n = cal.get(Calendar.DAY_OF_YEAR)
        val lngHour = lon / 15.0
        val t = n + ((if (rise) 6.0 else 18.0) - lngHour) / 24.0
        val m = 0.9856 * t - 3.289
        var l = m + 1.916 * sin(Math.toRadians(m)) + 0.020 * sin(Math.toRadians(2 * m)) + 282.634
        l = ((l % 360) + 360) % 360
        var ra = Math.toDegrees(kotlin.math.atan(0.91764 * kotlin.math.tan(Math.toRadians(l))))
        ra = ((ra % 360) + 360) % 360
        ra += (Math.floor(l / 90) * 90 - Math.floor(ra / 90) * 90)
        ra /= 15.0
        val sinDec = 0.39782 * sin(Math.toRadians(l))
        val cosDec = cos(asin(sinDec))
        val cosH = (cos(Math.toRadians(90.833)) - sinDec * sin(Math.toRadians(lat))) / (cosDec * cos(Math.toRadians(lat)))
        if (cosH > 1 || cosH < -1) return null
        val h = (if (rise) 360 - Math.toDegrees(acos(cosH)) else Math.toDegrees(acos(cosH))) / 15.0
        val tLocal = h + ra - 0.06571 * t - 6.622
        val ut = (((tLocal - lngHour) % 24) + 24) % 24
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis + (ut * 3600_000).toLong()
    }
}
