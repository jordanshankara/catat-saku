package app.catatuang.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Radius & jarak CLAUDE.md bagian 12. */
object CatatShapes {
    val hero = RoundedCornerShape(24.dp)
    val card = RoundedCornerShape(20.dp)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val button = RoundedCornerShape(16.dp)
    val chip = RoundedCornerShape(12.dp)
    val screenPadding = 20.dp
    val minTouch = 44.dp
    val numpadKey = 50.dp
}

@Composable
fun CatatUangTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    val c = if (dark) DarkCatatColors else LightCatatColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.primary, onPrimary = Color.White,
            background = c.background, onBackground = c.textPrimary,
            surface = c.surface, onSurface = c.textPrimary,
            onSurfaceVariant = c.textSecondary, outline = c.divider,
            error = c.danger,
        )
    } else {
        lightColorScheme(
            primary = c.primary, onPrimary = Color.White,
            background = c.background, onBackground = c.textPrimary,
            surface = c.surface, onSurface = c.textPrimary,
            onSurfaceVariant = c.textSecondary, outline = c.divider,
            error = c.danger,
        )
    }
    CompositionLocalProvider(LocalCatatColors provides c) {
        MaterialTheme(
            colorScheme = scheme,
            typography = CatatTypography,
            shapes = Shapes(
                small = CatatShapes.chip,
                medium = CatatShapes.button,
                large = CatatShapes.card,
                extraLarge = CatatShapes.sheet,
            ),
            content = content,
        )
    }
}

object CatatTheme {
    val colors: CatatColors
        @Composable get() = LocalCatatColors.current
}
