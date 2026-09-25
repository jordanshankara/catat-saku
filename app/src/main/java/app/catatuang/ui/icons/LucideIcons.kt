package app.catatuang.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Ikon garis gaya Lucide (lisensi ISC, lihat licenses/Lucide-ISC.txt), viewport 24×24, stroke 2.
 * Warna diganti lewat `tint` di Icon().
 */
object LucideIcons {
    val Home by lazy { icon("home", "M3 10l9-7 9 7v10a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z") }
    val History by lazy { icon("history", "M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01") }
    val Report by lazy { icon("report", "M3 3v18h18", "M8 17V10M13 17V6M18 17v-4") }
    val Settings by lazy {
        icon(
            "settings",
            "M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12M20 18h0",
            circle(16f, 6f, 2f), circle(10f, 12f, 2f), circle(18f, 18f, 2f),
        )
    }
    val Plus by lazy { icon("plus", "M12 5v14M5 12h14") }
    val Bell by lazy { icon("bell", "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9", "M10.3 21a1.94 1.94 0 0 0 3.4 0") }
    val Wallet by lazy { icon("wallet", "M5 6h14a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z", "M3 10h18", "M16 14h2") }
    val TrendingUp by lazy { icon("trending-up", "M3 17l6-6 4 4 8-8", "M15 7h6v6") }
    val Bowl by lazy { icon("makan", "M3 11h18a9 9 0 0 1-18 0z", "M8 7c0-1.5 1-2 1-3.5M12 7c0-1.5 1-2 1-3.5M16 7c0-1.5 1-2 1-3.5") }
    val Fruit by lazy { icon("buah", "M12 7c-2-1.5-6-1-6 4 0 4 3 9 6 8 3 1 6-4 6-8 0-5-4-5.5-6-4z", "M12 7c0-2 1-4 3-4") }
    val Bus by lazy {
        icon(
            "transport",
            "M7 3h10a3 3 0 0 1 3 3v9a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3V6a3 3 0 0 1 3-3z",
            "M4 11h16M8 18v3M16 18v3",
            circle(8f, 14.5f, 1f), circle(16f, 14.5f, 1f),
        )
    }
    val Dumbbell by lazy { icon("protein", "M6 7v10M3 9.5v5M18 7v10M21 9.5v5M6 12h12") }
    val Package by lazy { icon("lain", "M21 8l-9-5-9 5v8l9 5 9-5z", "M3 8l9 5 9-5M12 13v8") }
    val ChevronRight by lazy { icon("chevron-right", "M9 6l6 6-6 6") }
    val ArrowLeft by lazy { icon("arrow-left", "M19 12H5M11 18l-6-6 6-6") }
    val Check by lazy { icon("check", "M5 12l5 5 9-10") }
    val AlertTriangle by lazy { icon("alert", "M12 3l10 18H2z", "M12 10v5M12 18h.01") }
    val Calendar by lazy { icon("calendar", "M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z", "M3 10h18M8 3v4M16 3v4") }
    val Close by lazy { icon("close", "M18 6L6 18M6 6l12 12") }
    val Delete by lazy { icon("delete", "M21 5H9l-6 7 6 7h12a1 1 0 0 0 1-1V6a1 1 0 0 0-1-1z", "M12 9l6 6M18 9l-6 6") }

    private fun circle(cx: Float, cy: Float, r: Float): String =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    private fun icon(name: String, vararg paths: String, strokeWidth: Float = 2f): ImageVector {
        val builder = ImageVector.Builder(
            name = "lucide-$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        paths.forEach { data ->
            builder.addPath(
                pathData = addPathNodes(data),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }
}
