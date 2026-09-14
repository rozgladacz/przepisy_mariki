package pl.local.przepisy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF8C3F20),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCC),
    onPrimaryContainer = Color(0xFF351000),
    secondary = Color(0xFF76584C),
    secondaryContainer = Color(0xFFFFDBCC),
    background = Color(0xFFFFF8F5),
    surface = Color(0xFFFFF8F5),
    surfaceVariant = Color(0xFFF5DED5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB596),
    onPrimary = Color(0xFF55200A),
    primaryContainer = Color(0xFF713018),
    onPrimaryContainer = Color(0xFFFFDBCC),
    secondary = Color(0xFFE6BFAF),
    background = Color(0xFF201A18),
    surface = Color(0xFF201A18),
    surfaceVariant = Color(0xFF52443E),
)

@Composable
fun PrzepisyTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
