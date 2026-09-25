package app.catatuang.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.catatuang.R

// Plus Jakarta Sans (OFL): satu file statis per bobot, diturunkan dari variable font resmi (D-20).
val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
)

private const val TABULAR = "tnum"

object CatatType {
    val heroAmount = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, fontFeatureSettings = TABULAR)
    val inputAmount = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 48.sp, fontFeatureSettings = TABULAR)
    val screenTitle = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
    val cardTitle = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    val body = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    val bodySmall = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    val caption = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    val captionSmall = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
    val money = TextStyle(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 15.sp, fontFeatureSettings = TABULAR)
}

val CatatTypography = Typography(
    headlineLarge = CatatType.heroAmount,
    titleLarge = CatatType.screenTitle,
    titleMedium = CatatType.cardTitle,
    bodyLarge = CatatType.body,
    bodyMedium = CatatType.bodySmall,
    labelLarge = CatatType.caption.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
    labelMedium = CatatType.caption,
    labelSmall = CatatType.captionSmall,
)
