package me.bitlinker.walkee.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Material 3 with a yellow brand accent (ADR 0004). Dynamic color is deliberately off so the
// accent stays yellow on every device.

private val Yellow40 = Color(0xFF6D5E00)
private val Yellow80 = Color(0xFFFFD600)
private val Yellow90 = Color(0xFFFFE97A)
private val Yellow10 = Color(0xFF221B00)
private val Yellow20 = Color(0xFF3A3000)
private val Yellow30 = Color(0xFF544600)

private val Neutral10 = Color(0xFF1B1B1F)
private val Neutral20 = Color(0xFF303034)
private val Neutral90 = Color(0xFFE4E2E6)
private val Neutral95 = Color(0xFFF2F0F4)
private val Neutral99 = Color(0xFFFDFBFF)

private val LightColors = lightColorScheme(
    primary = Yellow40,
    onPrimary = Color.White,
    primaryContainer = Yellow90,
    onPrimaryContainer = Yellow10,
    secondary = Color(0xFF655F41),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE3BD),
    onSecondaryContainer = Color(0xFF201C05),
    tertiary = Color(0xFF41664A),
    onTertiary = Color.White,
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = Color(0xFFEAE2CF),
    onSurfaceVariant = Color(0xFF4B4739),
    outline = Color(0xFF7C7767),
)

private val DarkColors = darkColorScheme(
    primary = Yellow80,
    onPrimary = Yellow20,
    primaryContainer = Yellow30,
    onPrimaryContainer = Yellow90,
    secondary = Color(0xFFD0C6A1),
    onSecondary = Color(0xFF363016),
    secondaryContainer = Color(0xFF4D472B),
    onSecondaryContainer = Color(0xFFEDE3BD),
    tertiary = Color(0xFFA7D0AE),
    onTertiary = Color(0xFF12371E),
    background = Neutral10,
    onBackground = Neutral90,
    surface = Neutral10,
    onSurface = Neutral90,
    surfaceVariant = Color(0xFF4B4739),
    onSurfaceVariant = Color(0xFFCEC6B4),
    outline = Color(0xFF979080),
)

@Composable
fun WalkeeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
