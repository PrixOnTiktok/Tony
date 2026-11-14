package ca.bancroft.trails

import android.app.Application
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import ca.bancroft.trails.data.ThemeMode
import ca.bancroft.trails.ui.MapScreen
import ca.bancroft.trails.ui.MapViewModel
import ca.bancroft.trails.ui.Mode
import ca.bancroft.trails.ui.TrailTheme
import org.maplibre.android.MapLibre
import java.util.Locale

class TrailApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this) // no API key or network needed: everything is bundled
        // keep satellite tiles you've looked at (online) available later with no signal
        runCatching { org.maplibre.android.offline.OfflineManager.getInstance(this).setMaximumAmbientCacheSize(400L * 1024 * 1024, null) }
    }
}

class MainActivity : ComponentActivity() {
    private val vm: MapViewModel by viewModels()
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = Locale.CANADA
        }
        setContent {
            val themeMode by vm.theme.collectAsState()
            val dark = when (themeMode) { ThemeMode.AUTO -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
            LaunchedEffect(dark) {
                // Edge-to-edge (mandatory on Android 16+): the map draws under the status and navigation bars.
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                )
            }
            val mode by vm.mode.collectAsState()
            val navigating = mode is Mode.Navigating
            LaunchedEffect(navigating) {
                if (navigating) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            TrailTheme(dark) {
                MapScreen(vm, dark, speak = ::speak)
            }
        }
    }

    private fun speak(text: String) {
        if (ttsReady && vm.voice.value) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav")
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}
