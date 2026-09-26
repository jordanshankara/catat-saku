package app.catatuang

import android.app.Application
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import app.catatuang.data.CatatRepository.PinChange
import app.catatuang.data.SettingsStore
import app.catatuang.engine.Defaults
import app.catatuang.engine.Slot
import app.catatuang.engine.Tx
import app.catatuang.engine.TxType
import app.catatuang.notify.Notifier
import app.catatuang.notify.isKnownRoute
import app.catatuang.security.LinkToken
import app.catatuang.security.PinHasher
import app.catatuang.ui.theme.CatatUangTheme
import app.catatuang.widget.widgetData
import app.catatuang.widget.widgetStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/** Fase 8 (pos, tema gelap, ganti PIN, widget, aksesibilitas) + perbaikan keamanan. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w390dp-h844dp-xxhdpi")
class Phase8Test {
    @get:Rule val rule = createComposeRule()
    private val envs = mutableListOf<TestEnv>()
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    private fun env(today: LocalDate = LocalDate.of(2026, 11, 12)): TestEnv =
        TestEnv(context, today).also { envs += it; it.onboard(LocalDate.of(2026, 11, 1), 2_000_000) }

    @After fun tearDown() = envs.forEach { it.close() }

    private fun show(e: TestEnv) {
        e.lock.markUnlocked()
        e.skipAutoClosing()
        rule.setContent { CatatUangTheme { CatatRoot(e.repo, e.settings, e.lock, e.clock) } }
        rule.waitText("Halo, Ko")
    }

    /** Aksesibilitas (bab 12): setiap elemen yang bisa diketuk punya teks atau contentDescription. */
    private fun assertClickablesLabelled(screen: String) {
        rule.waitForIdle()
        val unlabeled = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter { n ->
            val c = n.config
            c.getOrNull(SemanticsProperties.Text).isNullOrEmpty() &&
                c.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty() &&
                c.getOrNull(SemanticsProperties.EditableText) == null
        }
        assertTrue("$screen: ${unlabeled.size} elemen tanpa label: ${unlabeled.map { it.boundsInRoot }}", unlabeled.isEmpty())
    }

    // ---------- Keamanan ----------

    @Test fun deepLinkRoutesWhitelisted() {
        listOf("home", "picker", "salary", "closing", "report", "input/1", "pay/7").forEach { assertTrue(it, isKnownRoute(it)) }
        listOf("", "settings", "input/", "input/abc", "input/1/2", "pay/-1", "closing/2026-10", "savings", "input/12345678").forEach { assertFalse(it, isKnownRoute(it)) }
    }

    @Test fun linkTokenRequired() {
        val t = LinkToken.get(context)
        assertTrue(t.length >= 16)
        assertEquals(t, LinkToken.get(context))
        assertTrue(LinkToken.matches(context, t))
        assertFalse(LinkToken.matches(context, null))
        assertFalse(LinkToken.matches(context, t.dropLast(1) + "x"))
        val intent: Intent = Notifier.routeIntent(context, "input/1")
        assertEquals("input/1", intent.getStringExtra(Notifier.EXTRA_ROUTE))
        assertTrue(LinkToken.matches(context, intent.getStringExtra(LinkToken.EXTRA)))
        assertEquals(context.packageName, intent.component?.packageName)
    }

    @Test fun pinAttemptsPersistAcrossRestart() = runBlocking {
        val e = env()
        e.settings.setPinAttempts(5, 123_456L)
        assertEquals(5 to 123_456L, e.settings.pinAttempts.first())
        e.settings.setPin(PinHasher.hash("1111"))
        assertEquals(0 to 0L, e.settings.pinAttempts.first()) // PIN baru → hitungan bersih
    }

    @Test fun pinMigratesToSecureStore() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        fun tmp() = File.createTempFile("store", ".preferences_pb").apply { delete() }
        val old = PreferenceDataStoreFactory.create(scope = scope) { tmp() }
        val secure = PreferenceDataStoreFactory.create(scope = scope) { tmp() }
        val pin = PinHasher.hash("2468")
        old.edit { it[stringPreferencesKey("pin_hash")] = pin.hash; it[stringPreferencesKey("pin_salt")] = pin.salt }
        val s = SettingsStore(old, secure)
        assertEquals(pin, s.pin.first()) // terbaca sebelum migrasi (tidak pernah tampak "tanpa PIN")
        s.migrateSecure()
        assertEquals(pin, s.pin.first())
        assertNull(old.data.first()[stringPreferencesKey("pin_hash")])
        assertEquals(pin.hash, secure.data.first()[stringPreferencesKey("pin_hash")])
    }

    @Test fun changePinChecksOldPinWithLockout() = runBlocking {
        val e = env()
        assertEquals(PinChange.OK, e.repo.changePin("4821", "1357"))
        assertTrue(PinHasher.verify("1357", e.settings.pin.first()!!))
        repeat(4) { assertEquals(PinChange.WRONG, e.repo.changePin("0000", "9999")) }
        assertEquals(PinChange.LOCKED, e.repo.changePin("0000", "9999"))
        assertEquals(PinChange.LOCKED, e.repo.changePin("1357", "9999")) // PIN benar pun ditolak selama jeda
        assertTrue(PinHasher.verify("1357", e.settings.pin.first()!!))
        assertEquals(5, e.settings.pinAttempts.first().first)
    }

    // ---------- Fitur ----------

    @Test fun widgetShowsDailyRemainingOnly() {
        val e = env()
        e.add(Tx(0, LocalDate.of(2026, 11, 12), TxType.EXPENSE, 60_000, categoryId = Defaults.ID_MAKAN, slot = Slot.SIANG))
        val data = widgetData(e.ready()) as app.catatuang.widget.WidgetData.Lines
        assertEquals(listOf("Makan" to -10_000L, "Buah" to 10_000L), data.lines.map { it.name to it.remaining })
        assertEquals("Lebih Rp 10.000", widgetStatus(-10_000))
        assertEquals("Sisa Rp 10.000", widgetStatus(10_000))
        assertEquals("Pas jatah", widgetStatus(0))
    }

    @Test fun categoriesScreenEditAndAdd() {
        val e = env()
        show(e)
        rule.onNodeWithText("Pengaturan").performClick()
        rule.onNodeWithText("Pos & nominal").performScrollTo().performClick()
        rule.waitText("Perubahan berlaku mulai", substring = true)
        rule.shot("f8-pos")
        assertClickablesLabelled("Pos & nominal")
        rule.onNodeWithText("Protein").performClick()
        rule.waitText("Simpan")
        rule.shot("f8-pos-edit", containing = "Simpan")
        rule.onNodeWithText("Tambah pos").assertExists()
        rule.onAllNodesWithText("Simpan")[0].performClick()
        rule.waitForIdle()

        // Tambah pos lewat VM (setara dengan sheet) lalu cek berlaku bulan depan.
        val vm = app.catatuang.feature.common.LedgerViewModel(e.repo)
        runBlocking {
            val id = vm.nextCategoryId()
            e.repo.saveCategory(app.catatuang.engine.newCategory(id, "Galon", app.catatuang.engine.CategoryKind.STOCK, 40_000, YearMonth.of(2026, 12), 99, null))
        }
        val ready = runBlocking {
            e.repo.state.first { r -> r is app.catatuang.data.AppState.Ready && r.input.categories.any { it.name == "Galon" } } as app.catatuang.data.AppState.Ready
        }
        val galon = ready.input.categories.first { it.name == "Galon" }
        assertFalse(galon.isActiveIn(YearMonth.of(2026, 11)))
        assertTrue(galon.isActiveIn(YearMonth.of(2026, 12)))
        rule.waitText("Baru · Rp 40.000 / bulan mulai Desember", substring = true)
    }

    @Test fun darkThemeAndA11y() {
        val e = env()
        e.add(Tx(0, LocalDate.of(2026, 11, 12), TxType.EXPENSE, 60_000, categoryId = Defaults.ID_MAKAN, slot = Slot.SIANG))
        runBlocking { e.settings.update { it.copy(theme = "DARK") } }
        e.lock.markUnlocked()
        e.skipAutoClosing()
        rule.setContent { CatatUangTheme(dark = true) { CatatRoot(e.repo, e.settings, e.lock, e.clock) } }
        rule.waitText("Halo, Ko")
        rule.shot("f8-gelap-beranda")
        assertClickablesLabelled("Beranda")
        rule.onNodeWithText("Riwayat").performClick()
        rule.waitForIdle()
        assertClickablesLabelled("Riwayat")
        rule.onNodeWithText("Laporan").performClick()
        rule.waitText("Total pengeluaran")
        rule.shot("f8-gelap-laporan")
        assertClickablesLabelled("Laporan")
        rule.onNodeWithText("Pengaturan").performClick()
        rule.waitText("Tema gelap")
        rule.shot("f8-gelap-pengaturan")
        assertClickablesLabelled("Pengaturan")
        rule.onNodeWithText("Ganti PIN").performScrollTo().performClick()
        rule.waitText("Masukkan PIN lama")
        rule.shot("f8-ganti-pin", containing = "PIN lama")
        "4821".forEach { rule.onAllNodesWithText(it.toString()).let { n -> n[n.fetchSemanticsNodes().lastIndex] }.performClick() }
        rule.waitText("PIN baru")
        "13571357".forEach { rule.onAllNodesWithText(it.toString()).let { n -> n[n.fetchSemanticsNodes().lastIndex] }.performClick() }
        rule.waitText("PIN berhasil diganti")
        assertNotNull(runBlocking { e.settings.pin.first() }?.takeIf { PinHasher.verify("1357", it) })
    }
}
