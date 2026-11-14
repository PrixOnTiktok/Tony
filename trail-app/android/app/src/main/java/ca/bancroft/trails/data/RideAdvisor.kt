package ca.bancroft.trails.data

import kotlin.math.roundToInt

/** How good is it to ride right now? Built from the saved forecast, works offline. */
data class Verdict(
    val score: Int,                 // 0..100
    val label: String,              // Great / Good / Meh / Rough / Stay home
    val emoji: String,
    val headline: String,           // one punchy line, with a bit of personality
    val reasons: List<String>,      // what drove the call
    val gear: List<String>,         // what to wear / bring
)

object RideAdvisor {
    private val SNOW = setOf(71, 73, 75, 77, 85, 86)
    private val FREEZING = setOf(56, 57, 66, 67)
    private val RAIN = setOf(51, 53, 55, 61, 63, 65, 80, 81, 82)
    private val THUNDER = setOf(95, 96, 99)
    private val FOG = setOf(45, 48)

    private fun t(c: Double) = if (ca.bancroft.trails.nav.Units.imperial) "${(c * 9 / 5 + 32).roundToInt()}°F" else "${c.roundToInt()}°"
    private fun hr(ms: Long) = java.text.SimpleDateFormat("h a", java.util.Locale.CANADA).format(java.util.Date(ms)).replace(".", "")

    /**
     * Looks at the next [hoursAhead] hours: what's falling now, what's coming, freeze/thaw, riding wind chill,
     * gusts, fog and daylight. [sunset] lets it warn about riding into the dark.
     */
    fun advise(f: Forecast, at: Long = System.currentTimeMillis(), hoursAhead: Int = 6, ridingKmh: Double = 35.0, sunset: Long? = null): Verdict? {
        val now = f.now(at) ?: return null
        val next = f.upcoming(at, hoursAhead).ifEmpty { listOf(now) }
        val reasons = ArrayList<String>(); val gear = LinkedHashSet<String>()
        var score = 100
        var headline: String? = null
        fun head(s: String) { if (headline == null) headline = s }

        val tMin = next.minOf { it.temp }; val tMax = next.maxOf { it.temp }
        val wetSoon = next.firstOrNull { it.time > at && (it.code in RAIN || (it.rainProb >= 50 && it.code !in SNOW && it.code !in FREEZING)) }
        val snowSoon = next.firstOrNull { it.time > at && (it.code in SNOW || (it.rainProb >= 50 && it.temp <= 0.5 && it.code !in FREEZING && it.code !in RAIN)) }
        val freezingAny = next.firstOrNull { it.code in FREEZING || (it.rainProb >= 40 && it.temp in -2.0..0.5 && it.code !in SNOW) }
        val thunder = next.firstOrNull { it.code in THUNDER }
        val gust = next.maxOf { it.gust }
        val chill = now.ridingChill(ridingKmh) ?: now.temp
        val precipTotal = next.sumOf { it.rain }

        // What's falling right now
        when {
            now.code in THUNDER -> { score -= 70; head("Thunderstorm overhead. You're sitting on a metal machine in the bush. Hard pass."); reasons += "Thunderstorm now" }
            now.code in FREEZING -> { score -= 65; head("Freezing rain. Everything is a skating rink, including the trail."); reasons += "Freezing rain/drizzle now (${t(now.temp)})" }
            now.code in SNOW -> {
                score -= 20
                reasons += "Snowing now (${t(now.temp)})"
                gear += "Goggles (snow in the eyes is no fun)"
                head(if (tMax >= 1.0 && precipTotal > 0.5) "Snow now, slush later. Classic Bancroft." else "Fresh snow: fun, but it hides rocks and holes. Take it easy.")
            }
            now.code in RAIN -> { score -= 45; head("It's raining. Mud season is open for business."); reasons += "Raining now: slick rocks, roots and bridges"; gear += "Rain jacket" }
            now.code in FOG -> { score -= 12; reasons += "Fog: low visibility on trails and road crossings" }
        }
        // What's coming
        if (thunder != null && now.code !in THUNDER) { score -= 45; head("Storms rolling in around ${hr(thunder.time)}. Ride early or not at all."); reasons += "Thunderstorms possible around ${hr(thunder.time)}" }
        if (freezingAny != null && now.code !in FREEZING) { score -= 35; head("Freezing rain risk around ${hr(freezingAny.time)}. Ice on trails and bridges."); reasons += "Freezing rain/ice risk around ${hr(freezingAny.time)}" }
        // Freeze-thaw: snow or ice now, warming above zero with moisture -> slush, mud, slippery
        if ((now.code in SNOW || now.temp <= 0.5) && tMax >= 1.0 && (precipTotal > 0.3 || wetSoon != null)) {
            score -= 20
            val warmAt = next.firstOrNull { it.temp >= 1.0 }?.time
            reasons += "Warming to ${t(tMax)}${warmAt?.let { " by ${hr(it)}" } ?: ""}: snow turns to slush and rain, trails get greasy"
            gear += "Waterproof layer"
        }
        if (wetSoon != null && now.code !in RAIN && now.code !in SNOW && now.code !in FREEZING) {
            val inH = (wetSoon.time - at) / 3600_000.0
            score -= if (inH < 2) 20 else 10
            reasons += "Rain likely from about ${hr(wetSoon.time)} (${wetSoon.rainProb}%)"
            head(if (inH < 2) "Rain on the way within the hour or two. Short ride or rain gear." else "Dry until about ${hr(wetSoon.time)}. Plenty of time for a good rip.")
            gear += "Rain jacket"
        }
        if (snowSoon != null && now.code !in SNOW) { score -= 10; reasons += "Snow likely from about ${hr(snowSoon.time)}" ; gear += "Goggles" }
        if (tMin <= 0.5 && tMin >= -3 && precipTotal < 0.2 && now.code !in SNOW) reasons += "Around freezing: frost and icy patches in the shade and on bridges"

        // Cold on the machine
        when {
            chill <= -30 -> { score -= 65; head("Riding wind chill ${t(chill)}. Frostbite in minutes. Maybe just warm up the machine and admire it."); gear += "Full face mask / balaclava"; gear += "Heated grips or hand warmers" }
            chill <= -20 -> { score -= 35; head("Riding wind chill ${t(chill)}. Your ATV will love it. Your face won't."); gear += "Balaclava"; gear += "Hand warmers" }
            chill <= -10 -> { score -= 12; gear += "Balaclava or neck warmer"; gear += "Winter gloves" }
            chill <= 2 -> { score -= 4; gear += "Warm gloves" }
        }
        if (chill <= 2) reasons += "Feels like ${t(chill)} at ${ridingKmh.roundToInt()} km/h"
        if (gust >= 60) { score -= 20; reasons += "Gusts to ${gust.roundToInt()} km/h: watch for falling trees"; head("Windy enough to drop branches on the trail. Eyes up.") }
        else if (gust >= 45) { score -= 8; reasons += "Gusty (${gust.roundToInt()} km/h)" }
        if (now.temp >= 30) { score -= 10; reasons += "Hot (${t(now.temp)}): bring water, watch the machine temp"; gear += "Lots of water" }
        sunset?.let { s ->
            val left = (s - at) / 60000
            if (left in 0..60) { score -= 10; reasons += "Sunset in $left min"; gear += "Headlights on, headlamp in the bag" }
            else if (left < 0) { score -= 15; reasons += "It's dark out"; gear += "Headlights on, headlamp in the bag" }
        }

        // never call it "good" while stuff is falling from the sky
        if (now.code in RAIN || now.code in SNOW || now.code in FREEZING || now.code in THUNDER) score = minOf(score, 58)
        if (now.code in FREEZING) score = minOf(score, 15)
        if (now.code in THUNDER) score = minOf(score, 10)
        score = score.coerceIn(0, 100)
        val (label, emoji) = when {
            score >= 80 -> "Great riding" to "🤩"
            score >= 60 -> "Good riding" to "👍"
            score >= 40 -> "Meh, rideable" to "😐"
            score >= 20 -> "Rough out there" to "😬"
            else -> "Stay home" to "🛋️"
        }
        head(when {
            score >= 90 -> "Couldn't order better weather. Go ride."
            score >= 80 -> "Looks great out there. Gas up and go."
            score >= 60 -> "Pretty decent. Dress for it and you're golden."
            else -> "Not the prettiest day. Short ride, warm gear."
        })
        if (gear.isEmpty() && now.temp < 15) gear += "A warm layer under the jacket"
        return Verdict(score, label, emoji, headline!!, reasons.take(5), gear.toList().take(4))
    }
}
