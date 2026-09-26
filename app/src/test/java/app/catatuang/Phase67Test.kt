package app.catatuang

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.catatuang.engine.Cell
import app.catatuang.engine.Defaults
import app.catatuang.engine.Sheet
import app.catatuang.engine.Slot
import app.catatuang.engine.Tx
import app.catatuang.engine.TxType
import app.catatuang.engine.computeLedger
import app.catatuang.engine.store.BackupCodec
import app.catatuang.engine.store.BackupDocument
import app.catatuang.engine.store.BackupReadResult
import app.catatuang.engine.store.toLedgerInput
import app.catatuang.export.ExportFormat
import app.catatuang.export.Ranges
import app.catatuang.export.Xlsx
import app.catatuang.export.buildExport
import app.catatuang.export.exportFileName
import app.catatuang.feature.report.ExportContent
import app.catatuang.ui.theme.CatatTheme
import app.catatuang.ui.theme.CatatUangTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Fase 6 (Laporan & Export) dan Fase 7 (Pulihkan). */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w390dp-h844dp-xxhdpi")
class Phase67Test {
    @get:Rule val rule = createComposeRule()
    private val envs = mutableListOf<TestEnv>()

    private fun env(today: LocalDate) = TestEnv(ApplicationProvider.getApplicationContext(), today).also { envs += it }

    @After fun tearDown() = envs.forEach { it.close() }

    /** November berisi data realistis; hari ini 12 Nov 2026 (Kamis). */
    private fun novEnv(): TestEnv {
        val e = env(LocalDate.of(2026, 11, 12))
        e.onboard(LocalDate.of(2026, 10, 31), 0)
        e.salary(LocalDate.of(2026, 10, 31), YearMonth.of(2026, 11))
        fun x(day: Int, cat: Long, amount: Long, note: String? = null) =
            Tx(0, LocalDate.of(2026, 11, day), TxType.EXPENSE, amount, categoryId = cat, note = note, slot = if (cat == Defaults.ID_MAKAN) Slot.SIANG else null)
        e.add(
            x(2, Defaults.ID_MAKAN, 65_000), x(3, Defaults.ID_MAKAN, 40_000), x(4, Defaults.ID_MAKAN, 52_000), x(5, Defaults.ID_MAKAN, 30_000),
            x(9, Defaults.ID_MAKAN, 48_000), x(10, Defaults.ID_MAKAN, 70_000), x(11, Defaults.ID_MAKAN, 45_000), x(12, Defaults.ID_MAKAN, 20_000),
            x(2, Defaults.ID_BUAH, 10_000), x(4, Defaults.ID_BUAH, 8_000), x(10, Defaults.ID_BUAH, 12_000),
            x(7, Defaults.ID_TRANSPORT, 105_000), x(4, Defaults.ID_PROTEIN, 93_000), x(6, Defaults.ID_LAIN, 45_000, "sabun & pulsa"),
            Tx(0, LocalDate.of(2026, 11, 1), TxType.FIXED_PAYMENT, 390_000, categoryId = Defaults.ID_AI),
        )
        return e
    }

    private fun unzip(bytes: ByteArray): Map<String, String> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            while (true) { val e = z.nextEntry ?: break; put(e.name, z.readBytes().decodeToString()) }
        }
    }

    @Test fun xlsxIsWellFormed() {
        val sheets = listOf(Sheet("Harian Makan & Buah", listOf(listOf(Cell.Text("Pos <A>"), Cell.Text("Nominal")), listOf(Cell.Text("Makan"), Cell.Num(-65_000)))))
        val files = unzip(Xlsx.write(sheets))
        assertTrue(files.keys.containsAll(listOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/styles.xml", "xl/worksheets/sheet1.xml")))
        val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        files.values.forEach { parser.parse(ByteArrayInputStream(it.toByteArray())) } // melempar bila XML rusak
        assertTrue(files.getValue("xl/workbook.xml").contains("Harian Makan &amp; Buah"))
        assertTrue(files.getValue("xl/worksheets/sheet1.xml").contains("<c r=\"B2\" s=\"1\"><v>-65000</v></c>"))
        assertEquals("AA", Xlsx.column(26))
    }

    @Test fun exportFilesForMonth() {
        val e = novEnv()
        val r = e.ready()
        val range = Ranges.month(YearMonth.of(2026, 11))
        assertEquals("catatuang-2026-11-20261112.xlsx", exportFileName(range, ExportFormat.XLSX, r.today))
        assertEquals("catatuang-minggu-20261109-20261112.pdf", exportFileName(Ranges.week(r.today), ExportFormat.PDF, r.today))
        val context = ApplicationProvider.getApplicationContext<Application>()
        val xlsx = buildExport(context, r.input, r.ledger, range, ExportFormat.XLSX)
        assertEquals(7, unzip(xlsx).count { it.key.startsWith("xl/worksheets/") || it.key == "xl/workbook.xml" || it.key == "xl/styles.xml" })
        val dir = File(System.getProperty("robolectric.screenshotDir") ?: "build/screenshots").apply { mkdirs() }
        File(dir, "f6-export.xlsx").writeBytes(xlsx)
        val pdf = runCatching { buildExport(context, r.input, r.ledger, range, ExportFormat.PDF) }.getOrElse {
            // Robolectric tidak punya backend native PdfDocument ("document is closed!"); PDF diuji di HP.
            org.junit.Assume.assumeTrue("PdfDocument tidak tersedia di Robolectric", it.message != "document is closed!")
            throw it
        }
        assertTrue("PDF diawali %PDF", pdf.copyOfRange(0, 4).decodeToString() == "%PDF")
        File(dir, "f6-export.pdf").writeBytes(pdf)
    }

    @Test fun reportScreens() {
        val e = novEnv()
        e.lock.markUnlocked()
        e.skipAutoClosing()
        rule.setContent { CatatUangTheme { CatatRoot(e.repo, e.settings, e.lock, e.clock) } }
        rule.waitText("Halo, Ko")
        rule.onNodeWithText("Laporan").performClick()
        rule.waitText("Total pengeluaran")
        rule.waitText("Harian vs jatah")
        rule.shot("f6-laporan-mingguan")
        rule.onNodeWithText("Bulanan").performClick()
        rule.waitText("Budget vs realisasi")
        rule.shot("f6-laporan-bulanan")
        val ready = e.ready()
        rule.onAllNodes(hasText("Export"))[0].performClick()
        rule.waitText("Export laporan")
        rule.shot("f6-export", containing = "Export laporan")
        assertTrue(ready.ledger.current != null)
    }

    @Test fun exportSheetContent() {
        val e = novEnv()
        val ready = e.ready()
        rule.setContent { CatatUangTheme { Surface(color = CatatTheme.colors.surface) { ExportContent(ready, onDone = {}) } } }
        rule.waitText("catatuang-2026-11-20261112.pdf")
        rule.onNodeWithText("Excel (.xlsx)").performClick()
        rule.waitText("catatuang-2026-11-20261112.xlsx")
        rule.shot("f6-export-sheet")
    }

    /** Bab 11 / T-25 lewat app: backup → HP baru → Pulihkan → buat PIN baru → ledger identik. */
    @Test fun restoreOnNewPhone() {
        val source = novEnv()
        val text = runBlocking { BackupCodec.encode(BackupDocument.of(source.repo.exportSnapshot(), "0.5.0", LocalDateTime.of(2026, 11, 12, 21, 0))) }
        val result = BackupCodec.decode(text) as BackupReadResult.Ok
        assertEquals("2 bulan data · 17 transaksi · terakhir 12 Nov 2026", BackupCodec.previewText(result.preview))

        val phone = env(LocalDate.of(2026, 11, 12))
        rule.setContent { CatatUangTheme { CatatRoot(phone.repo, phone.settings, phone.lock, phone.clock) } }
        rule.waitText("Pulihkan dari Backup")
        runBlocking { phone.repo.restoreFromBackup(result.document.toSnapshot()) }
        rule.waitText("Buat PIN baru")
        rule.shot("f7-pin-baru")
        "12341234".forEach { rule.onNodeWithText(it.toString()).performClick() }
        rule.waitText("Tutup Buku Oktober") // data pulihan punya Oktober yang belum ditutup → terbuka otomatis

        val restored = runBlocking { phone.repo.snapshot.first() }.toLedgerInput()!!
        val original = runBlocking { source.repo.snapshot.first() }.toLedgerInput()!!
        assertEquals(computeLedger(original, LocalDate.of(2026, 11, 12)), computeLedger(restored, LocalDate.of(2026, 11, 12)))
        assertTrue(runBlocking { phone.settings.pin.first() } != null)
    }
}
