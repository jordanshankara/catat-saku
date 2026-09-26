package app.catatuang.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.res.ResourcesCompat
import app.catatuang.R
import app.catatuang.engine.Cell
import app.catatuang.engine.Sheet
import java.io.ByteArrayOutputStream

/**
 * Laporan PDF (bab 10): A4 portrait lewat `PdfDocument` bawaan Android. Isi: judul & rentang, verdict
 * (bulan), ringkasan, per pos + grafik batang sederhana, dan daftar transaksi dengan paginasi otomatis.
 * Warna tema terang.
 */
object PdfReport {
    private const val W = 595
    private const val H = 842
    private const val M = 40f
    private val PRIMARY = 0xFF3563E9.toInt()
    private val TEXT = 0xFF1E1E2D.toInt()
    private val MUTED = 0xFF5F6478.toInt()
    private val DIVIDER = 0xFFD5D8E4.toInt()
    private val TRACK = 0xFFEEF0F8.toInt()
    private val DANGER = 0xFFB3243F.toInt()

    private fun rp(v: Long): String {
        val s = kotlin.math.abs(v).toString().reversed().chunked(3).joinToString(".").reversed()
        return if (v < 0) "−Rp $s" else "Rp $s"
    }

    private fun cellText(c: Cell?): String = when (c) {
        is Cell.Text -> c.value
        is Cell.Num -> rp(c.value)
        null -> ""
    }

    private class Writer(val doc: PdfDocument, val regular: Typeface, val bold: Typeface, val footer: String) {
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = 0f
        var number = 0

        fun paint(size: Float, color: Int = TEXT, strong: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color; typeface = if (strong) bold else regular
        }

        fun newPage() {
            finish()
            number++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(W, H, number).create())
            page = p; canvas = p.canvas; y = M
        }

        fun finish() {
            page?.let { p ->
                p.canvas.drawText("$footer · hal $number", M, H - 20f, paint(8f, MUTED))
                doc.finishPage(p)
            }
            page = null
        }

        fun ensure(height: Float) { if (page == null || y + height > H - M) newPage() }

        fun text(s: String, size: Float, color: Int = TEXT, strong: Boolean = false, x: Float = M) {
            ensure(size + 6); canvas!!.drawText(s, x, y + size, paint(size, color, strong)); y += size + 6
        }

        fun row(left: String, right: String, size: Float = 10f, strong: Boolean = false, rightColor: Int = TEXT) {
            ensure(size + 6)
            canvas!!.drawText(left, M, y + size, paint(size, if (strong) TEXT else MUTED, strong))
            val p = paint(size, rightColor, true)
            canvas!!.drawText(right, W - M - p.measureText(right), y + size, p)
            y += size + 6
        }

        fun gap(h: Float) { y += h }

        fun line() { ensure(4f); canvas!!.drawLine(M, y, W - M, y, paint(1f, DIVIDER)); y += 6 }
    }

    fun render(context: Context, title: String, period: String, verdict: Pair<String, Boolean>?, sheets: List<Sheet>): ByteArray {
        val regular = ResourcesCompat.getFont(context, R.font.plus_jakarta_sans_regular) ?: Typeface.DEFAULT
        val bold = ResourcesCompat.getFont(context, R.font.plus_jakarta_sans_bold) ?: Typeface.DEFAULT_BOLD
        val doc = PdfDocument()
        val w = Writer(doc, regular, bold, "Catat Uang · $title")
        w.newPage()
        w.text("Catat Uang", 11f, PRIMARY, strong = true)
        w.text(title, 20f, strong = true)
        w.text(period, 10f, MUTED)
        w.gap(6f)

        verdict?.let { (label, provisional) ->
            w.ensure(56f)
            val c = w.canvas!!
            c.drawRoundRect(RectF(M, w.y, W - M, w.y + 48f), 10f, 10f, w.paint(1f, TRACK))
            c.drawText("Verdict" + if (provisional) " (sementara)" else "", M + 12, w.y + 17, w.paint(9f, MUTED))
            c.drawText(label, M + 12, w.y + 38, w.paint(16f, if (label.startsWith("BONCOS")) DANGER else TEXT, strong = true))
            w.gap(58f)
        }

        val byName = sheets.associateBy { it.name }
        byName["Ringkasan"]?.let { s ->
            w.text("Ringkasan", 13f, strong = true)
            s.rows.drop(2).filter { it.size >= 2 && it[0] is Cell.Text && (it[0] as Cell.Text).value != "Verdict" }.forEach { r -> w.row(cellText(r[0]), cellText(r[1])) }
            w.gap(8f)
        }

        byName["Per Kategori"]?.let { s ->
            w.text("Per pos", 13f, strong = true)
            val rows = s.rows.drop(1)
            val max = rows.maxOfOrNull { r -> maxOf((r.getOrNull(1) as? Cell.Num)?.value ?: 0, (r.getOrNull(2) as? Cell.Num)?.value ?: 0) }?.coerceAtLeast(1) ?: 1
            rows.forEach { r ->
                val name = cellText(r[0])
                val budget = (r.getOrNull(1) as? Cell.Num)?.value
                val used = (r.getOrNull(2) as? Cell.Num)?.value ?: 0
                val over = budget != null && used > budget
                w.row(name, if (budget != null) "${rp(used)} / ${rp(budget)}" else rp(used), rightColor = if (over) DANGER else TEXT)
                // Grafik batang sederhana: jejak = budget, isi = terpakai (skala bersama).
                w.ensure(12f)
                val c = w.canvas!!
                val full = W - 2 * M
                if (budget != null) c.drawRoundRect(RectF(M, w.y, M + full * budget / max, w.y + 6f), 3f, 3f, w.paint(1f, TRACK))
                c.drawRoundRect(RectF(M, w.y, M + (full * used.coerceAtLeast(0) / max).coerceAtLeast(2f), w.y + 6f), 3f, 3f, w.paint(1f, if (over) DANGER else PRIMARY))
                w.gap(12f)
            }
            w.gap(6f)
        }

        byName["Transaksi"]?.let { s ->
            w.text("Transaksi (${s.rows.size - 1})", 13f, strong = true)
            val cols = floatArrayOf(M, M + 62, M + 160, M + 250, W - M)
            fun header() {
                w.ensure(16f)
                listOf("Tanggal", "Jenis", "Pos", "Catatan").forEachIndexed { i, h -> w.canvas!!.drawText(h, cols[i], w.y + 9, w.paint(8f, MUTED, true)) }
                val p = w.paint(8f, MUTED, true)
                w.canvas!!.drawText("Nominal", W - M - p.measureText("Nominal"), w.y + 9, p)
                w.gap(14f); w.line()
            }
            header()
            s.rows.drop(1).forEach { r ->
                if (w.y + 14f > H - M) { w.newPage(); header() }
                val c = w.canvas!!
                val small = w.paint(8.5f)
                c.drawText(cellText(r[0]), cols[0], w.y + 9, small)
                c.drawText(cellText(r[1]).take(18), cols[1], w.y + 9, small)
                c.drawText(cellText(r[2]).take(16), cols[2], w.y + 9, small)
                c.drawText(cellText(r.getOrNull(5)).take(34), cols[3], w.y + 9, w.paint(8.5f, MUTED))
                val amount = (r[4] as? Cell.Num)?.value ?: 0
                val p = w.paint(8.5f, if (amount < 0) TEXT else 0xFF17865A.toInt(), true)
                val s2 = rp(amount)
                c.drawText(s2, W - M - p.measureText(s2), w.y + 9, p)
                w.gap(13f)
            }
        }
        w.finish()
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }
}
