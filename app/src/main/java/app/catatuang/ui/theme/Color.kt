package app.catatuang.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Token warna CLAUDE.md bagian 12. */
@Immutable
data class CatatColors(
    val primary: Color,
    val gradientStart: Color,
    val gradientEnd: Color,
    val success: Color,
    val successBg: Color,
    val warning: Color,
    val warningText: Color,
    val warningBg: Color,
    val danger: Color,
    val dangerText: Color,
    val dangerBg: Color,
    val savings: Color,
    val savingsBg: Color,
    val emergency: Color,
    val emergencyBg: Color,
    val gold: Color,
    val goldBg: Color,
    val goldText: Color,
    val goldButton: Color,
    val background: Color,
    val surface: Color,
    val track: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val isDark: Boolean,
) {
    val heroGradient: Brush
        get() = Brush.linearGradient(listOf(gradientStart, gradientEnd))
}

val LightCatatColors = CatatColors(
    primary = Color(0xFF3563E9),
    gradientStart = Color(0xFF3E6BF2),
    gradientEnd = Color(0xFF1E3A9E),
    success = Color(0xFF17865A),
    successBg = Color(0xFFDDF5EA),
    warning = Color(0xFFF2A93B),
    warningText = Color(0xFF8A5200),
    warningBg = Color(0xFFFFF1D6),
    danger = Color(0xFFD93A5A),
    dangerText = Color(0xFFB3243F),
    dangerBg = Color(0xFFFDE2E7),
    savings = Color(0xFF6A3FD0),
    savingsBg = Color(0xFFEFE7FF),
    emergency = Color(0xFF0E7490),
    emergencyBg = Color(0xFFDDF3F8),
    gold = Color(0xFFF2C14E),
    goldBg = Color(0xFFFFF4DB),
    goldText = Color(0xFF7A5200),
    goldButton = Color(0xFF8A5A00),
    background = Color(0xFFEEF0F8),
    surface = Color(0xFFFFFFFF),
    track = Color(0xFFEEF0F8),
    textPrimary = Color(0xFF1E1E2D),
    textSecondary = Color(0xFF5F6478),
    divider = Color(0xFFD5D8E4),
    isDark = false,
)

val DarkCatatColors = LightCatatColors.copy(
    primary = Color(0xFF5B8CFF),
    success = Color(0xFF3DD598),
    danger = Color(0xFFFF6B81),
    dangerText = Color(0xFFFF8FA3),
    dangerBg = Color(0xFF2B1C30),
    background = Color(0xFF12152A),
    surface = Color(0xFF1C2140),
    track = Color(0xFF2C3360),
    textPrimary = Color(0xFFF2F4FF),
    textSecondary = Color(0xFFA3A9C7),
    isDark = true,
)

/** Warna ikon pos (latar / ikon). */
object CategoryPalette {
    val makan = Color(0xFFFFE9D6) to Color(0xFFB85A00)
    val buah = Color(0xFFDDF5EA) to Color(0xFF17865A)
    val transport = Color(0xFFE3E9FF) to Color(0xFF3563E9)
    val protein = Color(0xFFEFE7FF) to Color(0xFF6A3FD0)
    val lain = Color(0xFFEEF0F4) to Color(0xFF5F6478)
}

val LocalCatatColors = staticCompositionLocalOf { LightCatatColors }
