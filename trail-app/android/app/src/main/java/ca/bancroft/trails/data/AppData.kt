package ca.bancroft.trails.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Reason texts, municipalities and data provenance (assets/meta.json). */
class Meta(json: JSONObject) {
    val reasons: List<String> = json.getJSONArray("reasons").strings()
    val kinds: List<String> = json.getJSONArray("kinds").strings()
    val classes: List<String> = json.getJSONArray("classes").strings()
    val municipalities: List<String> = json.getJSONArray("municipalities").strings()
    val osmDate: String = json.optString("osm_date").take(10)
    val lioDate: String = json.optString("lio_date")
    val rulesResearched: String = json.optString("rules_researched")
    val sources: List<Pair<String, String>> = json.getJSONArray("sources").let { a ->
        (0 until a.length()).map { a.getJSONObject(it).let { o -> o.getString("name") to "${o.getString("use")} (${o.getString("licence")})" } }
    }
    val muniRules: Map<String, JSONObject> = json.optJSONObject("muni_rules")?.let { o ->
        o.keys().asSequence().associateWith { o.getJSONObject(it) }
    } ?: emptyMap()

    fun reason(code: Int, note: String?): String =
        reasons.getOrElse(code) { "" }.replace("{note}", note ?: "")

    /** Find the researched road rule for a municipality (names differ slightly between sources). */
    fun ruleFor(muni: String?): JSONObject? {
        if (muni.isNullOrBlank()) return null
        val n = norm(muni)
        return muniRules.entries.firstOrNull { norm(it.key.replace("(Township)", "").replace("City of ", "")) == n }?.value
    }

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
}

data class Poi(val cat: String, val kind: String, val name: String, val lat: Double, val lon: Double, val ele: Double?) {
    val title: String get() = name.ifBlank { kindLabel(kind) }
}

fun kindLabel(kind: String): String = when (kind) {
    "fuel" -> "Gas station"
    "fast_food" -> "Fast food"
    "ice_cream" -> "Ice cream"
    "convenience" -> "Convenience store"
    "supermarket" -> "Grocery store"
    "general" -> "General store"
    "camp_site" -> "Campground"
    "caravan_site" -> "RV park"
    "picnic_site" -> "Picnic area"
    "drinking_water" -> "Drinking water"
    "viewpoint" -> "Lookout"
    "peak" -> "Hill / peak"
    "toilets" -> "Toilets"
    "parking" -> "Parking"
    "gate" -> "Gate / barrier"
    "slipway", "boat_ramp" -> "Boat launch"
    else -> kind.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

data class RidePoint(val name: String, val lat: Double, val lon: Double)

/** A recommended ride from published sources (facts only); the app routes it itself. */
data class RideIdea(
    val id: String, val name: String, val type: String, val km: Double?, val time: String?, val difficulty: String?,
    val season: String?, val pass: String?, val highlights: String?, val sources: List<String>, val points: List<RidePoint>,
) {
    val routable get() = points.size >= 2
    val typeLabel get() = when (type) { "loop" -> "Loop"; "out_back" -> "There and back"; else -> "One way" }
}

data class Place(val name: String, val lat: Double, val lon: Double, val what: String?, val note: String?, val sources: List<String>)

/** Everything bundled in the APK, loaded once. */
class AppData(val graph: Graph, val meta: Meta, val pois: List<Poi>, val rides: List<RideIdea> = emptyList(), val places: List<Place> = emptyList()) {
    companion object {
        fun load(ctx: Context): AppData {
            val am = ctx.assets
            val graph = Graph.parse(am.open("graph.bin").use { it.readBytes() })
            val meta = Meta(JSONObject(am.open("meta.json").bufferedReader().use { it.readText() }))
            val arr = JSONArray(am.open("pois.json").bufferedReader().use { it.readText() })
            val pois = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Poi(o.getString("cat"), o.getString("kind"), o.optString("name"), o.getDouble("lat"), o.getDouble("lon"),
                    if (o.has("ele") && !o.isNull("ele")) o.optDouble("ele") else null)
            }
            val (rides, places) = runCatching {
                val o = JSONObject(am.open("rides.json").bufferedReader().use { it.readText() })
                val ra = o.getJSONArray("rides"); val pa = o.getJSONArray("places")
                fun str(j: JSONObject, k: String) = if (j.isNull(k)) null else j.optString(k).takeIf { it.isNotBlank() && it != "null" }
                fun srcs(j: JSONObject) = j.optJSONArray("sources")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
                (0 until ra.length()).map { i ->
                    val r = ra.getJSONObject(i); val pts = r.getJSONArray("points")
                    RideIdea(r.getString("id"), r.getString("name"), r.getString("type"), if (r.isNull("km")) null else r.optDouble("km"),
                        str(r, "time"), str(r, "difficulty"), str(r, "season"), str(r, "pass"), str(r, "highlights"), srcs(r),
                        (0 until pts.length()).map { k -> pts.getJSONObject(k).let { p -> RidePoint(p.getString("name"), p.getDouble("lat"), p.getDouble("lon")) } })
                } to (0 until pa.length()).map { i ->
                    val p = pa.getJSONObject(i)
                    Place(p.getString("name"), p.getDouble("lat"), p.getDouble("lon"), str(p, "what"), str(p, "note"), srcs(p))
                }
            }.getOrDefault(emptyList<RideIdea>() to emptyList())
            return AppData(graph, meta, pois, rides, places)
        }
    }
}

private fun JSONArray.strings() = (0 until length()).map { getString(it) }
