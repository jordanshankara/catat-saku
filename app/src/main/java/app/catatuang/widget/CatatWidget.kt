package app.catatuang.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.catatuang.CatatUangApp
import app.catatuang.data.AppState
import app.catatuang.engine.CategoryKind
import app.catatuang.notify.Notifier
import app.catatuang.ui.format.rp
import kotlinx.coroutines.flow.first

/** Satu baris widget: sisa jatah hari ini pos HARIAN (tanpa Saku Sisa/saldo — layar utama HP tidak terkunci PIN). */
data class WidgetLine(val categoryId: Long, val name: String, val remaining: Long)

sealed interface WidgetData {
    data object NotReady : WidgetData
    data class Lines(val lines: List<WidgetLine>) : WidgetData
}

fun widgetData(state: AppState): WidgetData = when (state) {
    is AppState.Ready -> WidgetData.Lines(
        state.ledger.daily.mapNotNull { d ->
            val cat = state.input.categories.firstOrNull { it.id == d.categoryId && it.kind == CategoryKind.DAILY } ?: return@mapNotNull null
            WidgetLine(cat.id, cat.name, d.remaining)
        },
    )
    else -> WidgetData.NotReady
}

/** Kalimat status berteks (prinsip 3): "Sisa Rp 20.000" / "Pas" / "Lebih Rp 10.000". */
fun widgetStatus(remaining: Long): String = when {
    remaining > 0 -> "Sisa ${rp(remaining)}"
    remaining == 0L -> "Pas jatah"
    else -> "Lebih ${rp(-remaining)}"
}

private val Bg = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF1C2140))
private val Text1 = ColorProvider(day = Color(0xFF1E1E2D), night = Color(0xFFF2F4FF))
private val Text2 = ColorProvider(day = Color(0xFF5F6478), night = Color(0xFFA3A9C7))
private val RowBg = ColorProvider(day = Color(0xFFEEF0F8), night = Color(0xFF2C3360))
private val Ok = ColorProvider(day = Color(0xFF17865A), night = Color(0xFF3DD598))
private val Over = ColorProvider(day = Color(0xFFB3243F), night = Color(0xFFFF8FA3))
private val AddBg = ColorProvider(day = Color(0xFF17865A), night = Color(0xFF17865A))
private val White = ColorProvider(day = Color.White, night = Color.White)

/** Widget Beranda (Jetpack Glance): catat cepat. Semua ketukan melewati kunci PIN (8.14). */
class CatatWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as CatatUangApp).container
        val data = widgetData(container.repository.state.first { it !is AppState.Loading })
        provideContent { Content(context, data) }
    }

    @Composable
    private fun Content(context: Context, data: WidgetData) {
        Column(GlanceModifier.fillMaxSize().background(Bg).cornerRadius(20.dp).padding(12.dp)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Catat Uang", style = TextStyle(color = Text1, fontSize = 14.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.defaultWeight())
                Text(
                    "+ Catat",
                    style = TextStyle(color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.background(AddBg).cornerRadius(12.dp).padding(horizontal = 12.dp, vertical = 8.dp)
                        .clickable(actionStartActivity(Notifier.routeIntent(context, "picker")))
                        .semantics { contentDescription = "Catat pengeluaran" },
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            when (data) {
                WidgetData.NotReady -> Text("Buka app untuk mulai.", style = TextStyle(color = Text2, fontSize = 13.sp))
                is WidgetData.Lines -> data.lines.forEach { line ->
                    Row(
                        GlanceModifier.fillMaxWidth().background(RowBg).cornerRadius(12.dp).padding(horizontal = 12.dp, vertical = 8.dp)
                            .clickable(actionStartActivity(Notifier.routeIntent(context, "input/${line.categoryId}"))),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(line.name, style = TextStyle(color = Text1, fontSize = 13.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.defaultWeight())
                        Text(widgetStatus(line.remaining), style = TextStyle(color = if (line.remaining < 0) Over else Ok, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                    }
                    Spacer(GlanceModifier.height(6.dp).width(1.dp))
                }
            }
        }
    }

    companion object {
        suspend fun refresh(context: Context) = runCatching { CatatWidget().updateAll(context) }
    }
}

class CatatWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CatatWidget()
}
