package ca.bancroft.trails.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** What a pin means. BLOCKED pins also make the router avoid that spot. */
enum class PinType(val label: String, val emoji: String) {
    NOTE("Note", "📍"),
    PARKING("Truck / trailer parking", "🛻"),
    GOOD("Good trail, rode it", "👍"),
    BLOCKED("Blocked / no ATVs", "⛔"),
    FOOD("Food / gas stop", "🍔"),
}

data class Pin(val id: Long, val lat: Double, val lon: Double, val name: String, val note: String, val type: PinType, val created: Long)

/** The rider's own pins, saved as a small JSON file in app storage (works offline, survives updates). */
class PinStore(ctx: Context) {
    private val file = File(ctx.filesDir, "pins.json")

    fun load(): List<Pin> = runCatching {
        if (!file.exists()) return emptyList()
        val a = JSONArray(file.readText())
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Pin(o.getLong("id"), o.getDouble("lat"), o.getDouble("lon"), o.optString("name"), o.optString("note"),
                runCatching { PinType.valueOf(o.optString("type")) }.getOrDefault(PinType.NOTE), o.optLong("created"))
        }
    }.getOrDefault(emptyList())

    fun save(pins: List<Pin>) {
        val a = JSONArray()
        pins.forEach { p ->
            a.put(JSONObject().put("id", p.id).put("lat", p.lat).put("lon", p.lon).put("name", p.name)
                .put("note", p.note).put("type", p.type.name).put("created", p.created))
        }
        val tmp = File(file.parentFile, "pins.json.tmp")
        tmp.writeText(a.toString())
        tmp.renameTo(file)
    }
}

enum class RouteMode(val label: String, val short: String) {
    FASTEST("Fastest", "Fastest"),
    MOSTLY_TRAILS("Mostly trails", "Mostly trails"),
    TRAILS_ONLY("Trails only", "Trails only"),
}

enum class ThemeMode { AUTO, LIGHT, DARK }

/** Small persisted settings. */
class Settings(ctx: Context) {
    private val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var routeMode: RouteMode
        get() = runCatching { RouteMode.valueOf(p.getString("routeMode", null)!!) }.getOrDefault(RouteMode.MOSTLY_TRAILS)
        set(v) = p.edit().putString("routeMode", v.name).apply()
    var avoidMaybe: Boolean
        get() = p.getBoolean("avoidMaybe", false)
        set(v) = p.edit().putBoolean("avoidMaybe", v).apply()
    var theme: ThemeMode
        get() = runCatching { ThemeMode.valueOf(p.getString("theme", null)!!) }.getOrDefault(ThemeMode.AUTO)
        set(v) = p.edit().putString("theme", v.name).apply()
    var voice: Boolean
        get() = p.getBoolean("voice", true)
        set(v) = p.edit().putBoolean("voice", v).apply()
    var showGas: Boolean
        get() = p.getBoolean("showGas", true)
        set(v) = p.edit().putBoolean("showGas", v).apply()
    var showFood: Boolean
        get() = p.getBoolean("showFood", true)
        set(v) = p.edit().putBoolean("showFood", v).apply()
    var showPoi: Boolean
        get() = p.getBoolean("showPoi", true)
        set(v) = p.edit().putBoolean("showPoi", v).apply()
    var showGates: Boolean
        get() = p.getBoolean("showGates", true)
        set(v) = p.edit().putBoolean("showGates", v).apply()
    var lastLat: Double
        get() = p.getFloat("lastLat", 45.0575f).toDouble()
        set(v) = p.edit().putFloat("lastLat", v.toFloat()).apply()
    var lastLon: Double
        get() = p.getFloat("lastLon", -77.8553f).toDouble()
        set(v) = p.edit().putFloat("lastLon", v.toFloat()).apply()
    var lastZoom: Double
        get() = p.getFloat("lastZoom", 11f).toDouble()
        set(v) = p.edit().putFloat("lastZoom", v.toFloat()).apply()
    var seenIntro: Boolean
        get() = p.getBoolean("seenIntro", false)
        set(v) = p.edit().putBoolean("seenIntro", v).apply()
}
