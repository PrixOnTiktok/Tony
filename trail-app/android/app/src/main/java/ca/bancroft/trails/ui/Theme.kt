package ca.bancroft.trails.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import ca.bancroft.trails.data.Cls

/** Colours from the design brief: muted map, high-contrast UI, colour-blind-safe trail classes. */
object Palette {
    val navBanner = Color(0xFF0B3D2E)
    val route = Color(0xFF00B3FF)
    fun cls(c: Int, dark: Boolean): Color = when (c) {
        Cls.FREE -> if (dark) Color(0xFF6EF0B4) else Color(0xFF0A9A5E)
        Cls.MAYBE -> if (dark) Color(0xFFFFCF33) else Color(0xFFF2A900)
        Cls.ROAD -> if (dark) Color(0xFF6F8FFF) else Color(0xFF1F45D6)
        Cls.NO -> if (dark) Color(0xFFD6336C) else Color(0xFF7A1A5C)
        else -> Color.Gray
    }
    /** Text colour that stays readable on top of [cls]. */
    fun onCls(c: Int, dark: Boolean): Color = when {
        dark -> Color(0xFF101214)
        c == Cls.MAYBE -> Color(0xFF1A1A1A)
        else -> Color.White
    }
}

/** Accent text colours that stay readable in both themes. */
@Composable
fun isDarkTheme() = MaterialTheme.colorScheme.surface.let { 0.2126f * it.red + 0.7152f * it.green + 0.0722f * it.blue } < 0.5f
@Composable
fun rainColor() = if (isDarkTheme()) Color(0xFF8AB4FF) else Color(0xFF1565C0)
@Composable
fun warnColor() = if (isDarkTheme()) Color(0xFFFFB74D) else Color(0xFFB26A00)

fun clsLabel(c: Int) = when (c) {
    Cls.FREE -> "Free to ride"
    Cls.MAYBE -> "Maybe: pass or permission"
    Cls.ROAD -> "Road: ATVs allowed"
    Cls.NO -> "No ATVs"
    else -> "Unknown"
}

fun clsShort(c: Int) = when (c) {
    Cls.FREE -> "FREE"; Cls.MAYBE -> "MAYBE"; Cls.ROAD -> "ROAD OK"; Cls.NO -> "NO ATVs"; else -> "?"
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B6E4F), onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFE0), onPrimaryContainer = Color(0xFF00281A),
    secondary = Color(0xFF4A635A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2ECE6), onSecondaryContainer = Color(0xFF0E1F18),
    surface = Color(0xFFFCFBF8), onSurface = Color(0xFF151916),
    surfaceVariant = Color(0xFFEDEBE5), onSurfaceVariant = Color(0xFF3E4540),
    surfaceContainer = Color(0xFFF4F2EE), surfaceContainerHigh = Color(0xFFEEECE7), surfaceContainerHighest = Color(0xFFE7E5E0),
    outline = Color(0xFF6C736E), outlineVariant = Color(0xFFC6CBC6),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6EF0B4), onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF0B5039), onPrimaryContainer = Color(0xFFB8F5D8),
    secondary = Color(0xFFB2CCC0), onSecondary = Color(0xFF1D352C),
    secondaryContainer = Color(0xFF2A3A33), onSecondaryContainer = Color(0xFFCDE8DB),
    surface = Color(0xFF121416), onSurface = Color(0xFFE4E6E3),
    surfaceVariant = Color(0xFF2A2F2C), onSurfaceVariant = Color(0xFFC2C8C3),
    surfaceContainer = Color(0xFF1A1D1F), surfaceContainerHigh = Color(0xFF212527), surfaceContainerHighest = Color(0xFF2B2F31),
    outline = Color(0xFF8C938E), outlineVariant = Color(0xFF424844),
    error = Color(0xFFFFB4AB),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
        bodyLarge = t.bodyLarge.copy(fontSize = 17.sp, lineHeight = 24.sp),
        bodyMedium = t.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    )
}

val NavDistance = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold, lineHeight = 44.sp)
val NavStreet = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 27.sp)

@Composable
fun TrailTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = AppTypography, content = content)
}


/** A bottom sheet, or the same content inline (used for screenshot tests). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SheetOrInline(inline: Boolean, onDismiss: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    if (inline) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow, contentColor = MaterialTheme.colorScheme.onSurface,
        ) { androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(top = 20.dp)) { content() } }
    } else {
        androidx.compose.material3.ModalBottomSheet(onDismiss) { content() }
    }
}
