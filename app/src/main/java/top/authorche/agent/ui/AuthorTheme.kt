package top.authorche.agent.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AuthorLightColors = lightColorScheme(
    primary = Color(0xFF668875),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCEADF),
    onPrimaryContainer = Color(0xFF1F382B),
    secondary = Color(0xFF7D748D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8E2F0),
    tertiary = Color(0xFF9B6E62),
    tertiaryContainer = Color(0xFFF4DDD6),
    background = Color(0xFFF7F4F1),
    onBackground = Color(0xFF2E2925),
    surface = Color(0xFFFFFBF7),
    onSurface = Color(0xFF2E2925),
    surfaceVariant = Color(0xFFEDE7E1),
    outline = Color(0xFF8B817A),
    error = Color(0xFF9E3D39),
)

private val AuthorDarkColors = darkColorScheme(
    primary = Color(0xFFA7C7B2),
    onPrimary = Color(0xFF123526),
    primaryContainer = Color(0xFF345844),
    secondary = Color(0xFFC9C1D7),
    secondaryContainer = Color(0xFF4A4356),
    tertiary = Color(0xFFE8BCAF),
    tertiaryContainer = Color(0xFF67453C),
    background = Color(0xFF191A18),
    onBackground = Color(0xFFEAE5DF),
    surface = Color(0xFF20211F),
    onSurface = Color(0xFFEAE5DF),
    surfaceVariant = Color(0xFF353531),
    outline = Color(0xFF9A938C),
    error = Color(0xFFFFB4AB),
)

@Composable
fun AuthorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) AuthorDarkColors else AuthorLightColors,
        typography = androidx.compose.material3.Typography(),
        content = content,
    )
}
