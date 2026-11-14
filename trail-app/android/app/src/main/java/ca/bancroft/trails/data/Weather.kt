package ca.bancroft.trails.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

data class WeatherHour(val time: Long, val temp: Double, val feels: Double, val rainProb: Int, val rain: Double, val code: Int, val wind: Double)
data class WeatherDay(val date: Long, val max: Double, val min: Double, val rainProb: Int, val rain: Double, val code: Int)

/** A saved forecast. Works offline: "now" is read from the hourly forecast for the current hour. */
data class Forecast(
    val fetchedAt: Long, val lat: Double, val lon: Double,
    val currentTemp: Double?, val currentFeels: Double?, val currentCode: Int?, val currentWind: Double?, val currentGust: Double?,
    val hours: List<WeatherHour>, val days: List<WeatherDay>,
) {
    /** The best "right now": live reading if it's fresh (< 90 min), else the forecast hour we're in. */
    fun now(at: Long = System.currentTimeMillis()): WeatherHour? {
        val fresh = at - fetchedAt < 90 * 60_000
        val hour = hours.lastOrNull { it.time <= at } ?: hours.firstOrNull()
        return if (fresh && currentTemp != null) WeatherHour(at, currentTemp, currentFeels ?: currentTemp, hour?.rainProb ?: 0, hour?.rain ?: 0.0,
            currentCode ?: hour?.code ?: 0, currentWind ?: hour?.wind ?: 0.0) else hour
    }

    fun upcoming(at: Long = System.currentTimeMillis(), count: Int = 24) = hours.filter { it.time >= at - 3600_000 }.take(count)

    /** Covers [at] (forecast runs out after ~3 days). */
    fun covers(at: Long = System.currentTimeMillis()) = hours.isNotEmpty() && hours.last().time >= at - 3600_000

    /** First hour in the next [withinH] hours with rain likely (>= 60 %), or null. */
    fun rainSoon(at: Long = System.currentTimeMillis(), withinH: Int = 6): WeatherHour? =
        hours.firstOrNull { it.time in (at - 3600_000)..(at + withinH * 3600_000L) && it.rainProb >= 60 }

    companion object {
        fun parse(text: String, fetchedAt: Long): Forecast {
            val o = JSONObject(text)
            if (o.optBoolean("error")) throw IllegalStateException(o.optString("reason"))
            val offset = o.optInt("utc_offset_seconds", 0)
            fun t(s: String): Long = LocalDateTime.parse(s).toEpochSecond(ZoneOffset.ofTotalSeconds(offset)) * 1000
            fun d(s: String): Long = LocalDate.parse(s).atStartOfDay().toEpochSecond(ZoneOffset.ofTotalSeconds(offset)) * 1000
            val cur = o.optJSONObject("current")
            val h = o.getJSONObject("hourly")
            val ht = h.getJSONArray("time")
            fun num(a: org.json.JSONArray?, i: Int, def: Double = 0.0) = if (a == null || a.isNull(i)) def else a.optDouble(i, def)
            val hours = (0 until ht.length()).map { i ->
                WeatherHour(t(ht.getString(i)), num(h.optJSONArray("temperature_2m"), i), num(h.optJSONArray("apparent_temperature"), i, num(h.optJSONArray("temperature_2m"), i)),
                    num(h.optJSONArray("precipitation_probability"), i).toInt(), num(h.optJSONArray("precipitation"), i),
                    num(h.optJSONArray("weather_code"), i).toInt(), num(h.optJSONArray("wind_speed_10m"), i))
            }
            val days = o.optJSONObject("daily")?.let { dl ->
                val dt = dl.getJSONArray("time")
                (0 until dt.length()).map { i ->
                    WeatherDay(d(dt.getString(i)), num(dl.optJSONArray("temperature_2m_max"), i), num(dl.optJSONArray("temperature_2m_min"), i),
                        num(dl.optJSONArray("precipitation_probability_max"), i).toInt(), num(dl.optJSONArray("precipitation_sum"), i),
                        num(dl.optJSONArray("weather_code"), i).toInt())
                }
            } ?: emptyList()
            return Forecast(fetchedAt, o.optDouble("latitude"), o.optDouble("longitude"),
                cur?.takeIf { it.has("temperature_2m") }?.optDouble("temperature_2m"), cur?.optDouble("apparent_temperature"),
                cur?.optInt("weather_code"), cur?.optDouble("wind_speed_10m"), cur?.optDouble("wind_gusts_10m"), hours, days)
        }
    }
}

/**
 * Downloads a 3-day hourly forecast (Open-Meteo, free, no account; uses Environment Canada's GEM model) and keeps it
 * on the phone so the app still shows weather with no signal.
 */
class WeatherStore(ctx: Context) {
    private val file = File(ctx.filesDir, "weather.json")
    private val metaFile = File(ctx.filesDir, "weather.meta")

    fun load(): Forecast? = runCatching {
        val fetched = metaFile.readText().trim().toLong()
        Forecast.parse(file.readText(), fetched)
    }.getOrNull()

    /** Network call: run off the main thread. Saves on success and returns the new forecast. */
    fun fetch(lat: Double, lon: Double): Forecast {
        val url = URL("https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f".format(java.util.Locale.US, lat, lon) +
            "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,wind_gusts_10m,precipitation" +
            "&hourly=temperature_2m,apparent_temperature,precipitation_probability,precipitation,weather_code,wind_speed_10m" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum" +
            "&timezone=auto&forecast_days=3&models=gem_seamless")
        val c = url.openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "BancroftTrails/1.0 (offline ATV map)")
        try {
            val text = (if (c.responseCode in 200..299) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
            val now = System.currentTimeMillis()
            val f = Forecast.parse(text, now) // throws on API error, so a bad reply never replaces a good saved one
            file.writeText(text); metaFile.writeText(now.toString())
            return f
        } finally { c.disconnect() }
    }
}

/** WMO weather codes → short label + emoji. */
fun weatherLabel(code: Int): Pair<String, String> = when (code) {
    0 -> "Clear" to "☀️"
    1 -> "Mostly clear" to "🌤️"
    2 -> "Partly cloudy" to "⛅"
    3 -> "Cloudy" to "☁️"
    45, 48 -> "Fog" to "🌫️"
    51, 53, 55 -> "Drizzle" to "🌦️"
    56, 57 -> "Freezing drizzle" to "🌧️"
    61 -> "Light rain" to "🌦️"
    63 -> "Rain" to "🌧️"
    65 -> "Heavy rain" to "🌧️"
    66, 67 -> "Freezing rain" to "🧊"
    71 -> "Light snow" to "🌨️"
    73 -> "Snow" to "🌨️"
    75 -> "Heavy snow" to "❄️"
    77 -> "Snow grains" to "🌨️"
    80 -> "Showers" to "🌦️"
    81 -> "Showers" to "🌧️"
    82 -> "Heavy showers" to "⛈️"
    85, 86 -> "Snow showers" to "🌨️"
    95 -> "Thunderstorm" to "⛈️"
    96, 99 -> "Thunderstorm, hail" to "⛈️"
    else -> "—" to "🌡️"
}
