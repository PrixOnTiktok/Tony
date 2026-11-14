package ca.bancroft.trails.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * GPS without Google Play Services: uses the platform fused provider (Android 12+), falling back to raw GPS.
 * Fast updates (1 s) while navigating, slower (3 s) while browsing to save battery.
 */
class LocationRepo private constructor(ctx: Context) {
    companion object {
        @Volatile private var instance: LocationRepo? = null
        /** One shared GPS feed for the map, navigation and ride recording. */
        fun get(ctx: Context) = instance ?: synchronized(this) { instance ?: LocationRepo(ctx.applicationContext).also { instance = it } }
    }

    private val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location
    private var running = false
    private var fast = false
    val isFast get() = running && fast

    private val listener = LocationListener { loc ->
        val prev = _location.value
        // ignore stale / much worse fixes
        if (prev == null || loc.time >= prev.time || loc.accuracy < prev.accuracy) _location.value = loc
    }

    @SuppressLint("MissingPermission")
    fun start(fastUpdates: Boolean) {
        if (running && fast == fastUpdates) return
        stop()
        fast = fastUpdates
        val interval = if (fastUpdates) 1000L else 3000L
        val providers = lm.getProviders(true)
        val provider = when {
            LocationManager.FUSED_PROVIDER in providers -> LocationManager.FUSED_PROVIDER
            LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
            else -> providers.firstOrNull() ?: return
        }
        runCatching {
            lm.getLastKnownLocation(provider)?.let { if (_location.value == null) _location.value = it }
            lm.requestLocationUpdates(provider, interval, 0f, listener, Looper.getMainLooper())
            // raw GPS too while navigating: best accuracy in the bush, no network needed
            if (fastUpdates && provider != LocationManager.GPS_PROVIDER && LocationManager.GPS_PROVIDER in providers) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, interval, 0f, listener, Looper.getMainLooper())
            }
            running = true
        }
    }

    fun stop() {
        if (running) runCatching { lm.removeUpdates(listener) }
        running = false
    }
}
