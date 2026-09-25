package app.catatuang

import app.catatuang.engine.Defaults
import app.catatuang.engine.LedgerInput
import app.catatuang.engine.MonthPlan
import app.catatuang.engine.Onboarding
import app.catatuang.engine.Pot
import app.catatuang.engine.Slot as MealSlot
import app.catatuang.engine.Tx
import app.catatuang.engine.TxType
import app.catatuang.engine.Verdict
import app.catatuang.engine.VerdictKind
import app.catatuang.engine.computeLedger
import app.catatuang.feature.closing.ClosingStep
import app.catatuang.feature.closing.closingSteps
import app.catatuang.feature.closing.verdictTitle
import app.catatuang.notify.ACTION_CHECKLIST_DONE
import app.catatuang.notify.ACTION_DAY_DONE
import app.catatuang.notify.NotifIds
import app.catatuang.notify.Scheduler
import app.catatuang.notify.Slot
import app.catatuang.notify.dailyNotification
import app.catatuang.notify.reminderNotifications
import app.catatuang.notify.salaryReminderDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

/** Fase 4 (Tutup Buku) & Fase 5 (isi notifikasi bab 9). */
class Phase4LogicTest {
    private val cats = Defaults.categories
    private val nov = YearMonth.of(2026, 11)

    /** November dibiayai gaji tepat waktu, mulai 31 Okt. */
    private fun funded(vararg txs: Tx, plans: List<MonthPlan> = emptyList()): LedgerInput {
        val start = LocalDate.of(2026, 10, 31)
        val salary = Tx(1, start, TxType.SALARY, 3_300_000, refYearMonth = nov)
        return LedgerInput(cats, Onboarding(start, 0, 500_000, 1_000_000), listOf(salary) + txs.mapIndexed { i, t -> t.copy(id = i + 2L, createdAt = i + 2L) }, plans)
    }

    @Test fun dailyNotificationContent() {
        val today = LocalDate.of(2026, 11, 4) // Rabu
        val empty = funded()
        val none = dailyNotification(empty, computeLedger(empty, today), today, doneMarked = false)!!
        assertEquals("Belum ada catatan hari ini.", none.title)
        assertEquals(listOf("Makan", "Lainnya", "Hari ini beres"), none.actions.map { it.label })
        assertEquals(ACTION_DAY_DONE, none.actions.last().broadcast)
        assertNull("Hari ini beres menghentikan pengingat (R-66)", dailyNotification(empty, computeLedger(empty, today), today, doneMarked = true))

        val input = funded(
            Tx(0, today, TxType.EXPENSE, 60_000, categoryId = Defaults.ID_MAKAN, slot = MealSlot.SIANG),
            Tx(0, today, TxType.EXPENSE, 8_000, categoryId = Defaults.ID_BUAH),
        )
        val n = dailyNotification(input, computeLedger(input, today), today, false)!!
        assertEquals("Catatan hari ini", n.title)
        assertTrue(n.text, n.text.contains("Makan lebih Rp 10.000 · Buah hemat Rp 2.000"))
        assertTrue(n.text, n.text.contains("Hutang makan Rp 10.000"))
        assertTrue(n.text, n.text.contains("Saku Sisa"))

        val sunday = LocalDate.of(2026, 11, 8)
        val s = dailyNotification(input, computeLedger(input, sunday), sunday, false)!!
        assertTrue(s.text, s.text.contains("Minggu ini keluar Rp 68.000."))
        assertEquals("report", s.route)
        assertEquals("Transport", s.actions[1].label)
    }

    @Test fun largeDebtWarningInDaily() {
        val today = LocalDate.of(2026, 11, 4)
        val input = funded(Tx(0, LocalDate.of(2026, 11, 3), TxType.EXPENSE, 260_000, categoryId = Defaults.ID_MAKAN))
        val n = dailyNotification(input, computeLedger(input, today), today, false)!!
        assertTrue(n.text, n.text.contains("lebih dari 3× jatah"))
    }

    @Test fun closingAndSalaryReminders() {
        val first = LocalDate.of(2026, 12, 1)
        val input = funded()
        val r = reminderNotifications(Slot.AT_0700, input, computeLedger(input, first), first, null to 0, null)
        assertEquals("Oktober selesai. Yuk tutup buku.", r.single().title) // bulan tertua yang belum ditutup
        assertEquals("closing", r.single().route)
        val second = first.plusDays(1)
        assertTrue(reminderNotifications(Slot.AT_0700, input, computeLedger(input, second), second, null to 0, null).isEmpty())

        val d29 = LocalDate.of(2026, 11, 29)
        assertEquals("Gaji sudah masuk?", reminderNotifications(Slot.AT_1200, input, computeLedger(input, d29), d29, null to 0, null).single().title)
        val withDec = input.copy(transactions = input.transactions + Tx(99, LocalDate.of(2026, 11, 28), TxType.SALARY, 3_300_000, refYearMonth = nov.plusMonths(1)))
        assertTrue(reminderNotifications(Slot.AT_1200, withDec, computeLedger(withDec, d29), d29, null to 0, null).isEmpty())
        assertEquals(28, salaryReminderDay(YearMonth.of(2027, 2)))
    }

    @Test fun morningReminders() {
        val input = funded()
        val first = LocalDate.of(2026, 11, 1)
        val due = reminderNotifications(Slot.AT_0900, input, computeLedger(input, first), first, null to 0, null)
        assertEquals(setOf("Iuran Mess jatuh tempo. Bayar berapa?", "AI jatuh tempo. Bayar berapa?"), due.map { it.title }.toSet())
        assertTrue(due.any { it.route == "pay/${Defaults.ID_AI}" && it.id == NotifIds.fixed(Defaults.ID_AI) })

        // R-57: mulai esok hari, maks 3×.
        val routine = input.copy(transactions = input.transactions + Tx(50, LocalDate.of(2026, 11, 1), TxType.SAVING_DEPOSIT, 160_000, pot = Pot.TABUNGAN, routine = true, refYearMonth = nov))
        val d2 = LocalDate.of(2026, 11, 2)
        fun check(count: Int, since: LocalDate) = reminderNotifications(Slot.AT_0900, routine, computeLedger(routine, d2), d2, "2026-11" to count, since).filter { it.id == NotifIds.CHECKLIST }
        assertEquals("Udah transfer Rp 160.000 ke tabungan?", check(0, first).single().title)
        assertEquals(ACTION_CHECKLIST_DONE, check(0, first).single().actions.single().broadcast)
        assertTrue(check(3, first).isEmpty())
        assertTrue(check(0, d2).isEmpty())
    }

    @Test fun cadanganH5() {
        // Oktober 2026: Target 180rb; 26 Okt = H-5; tidak ada hemat → kurang penuh.
        val start = LocalDate.of(2026, 9, 30)
        val oct = YearMonth.of(2026, 10)
        val base = LedgerInput(cats, Onboarding(start, 0, 500_000, 1_000_000), listOf(Tx(1, start, TxType.SALARY, 3_300_000, refYearMonth = oct)))
        val spend = (1..26).flatMap { d ->
            listOf(Tx(0, LocalDate.of(2026, 10, d), TxType.EXPENSE, 50_000, categoryId = Defaults.ID_MAKAN), Tx(0, LocalDate.of(2026, 10, d), TxType.EXPENSE, 10_000, categoryId = Defaults.ID_BUAH))
        } + listOf(3, 10, 17, 24).map { Tx(0, LocalDate.of(2026, 10, it), TxType.EXPENSE, 120_000, categoryId = Defaults.ID_TRANSPORT) }
        val input = base.copy(transactions = base.transactions + spend.mapIndexed { i, t -> t.copy(id = i + 10L, createdAt = i + 10L) })
        val h5 = LocalDate.of(2026, 10, 26)
        val state = computeLedger(input, h5)
        val n = reminderNotifications(Slot.AT_0900, input, state, h5, null to 0, null).first { it.id == NotifIds.CADANGAN }
        val kurang = 180_000 - state.cadanganTerkumpul
        assertTrue(n.text, n.text.startsWith("Kurang Rp ${"%,d".format(kurang).replace(',', '.')}, hemat ±"))
        assertFalse(reminderNotifications(Slot.AT_0900, input, computeLedger(input, h5.minusDays(1)), h5.minusDays(1), null to 0, null).any { it.id == NotifIds.CADANGAN })
    }

    @Test fun schedulerNextTime() {
        val now = LocalDateTime.of(2026, 11, 4, 21, 30)
        assertEquals(LocalDateTime.of(2026, 11, 4, 22, 0), Scheduler.nextAt(now, LocalTime.of(22, 0)))
        assertEquals(LocalDateTime.of(2026, 11, 5, 9, 0), Scheduler.nextAt(now, LocalTime.of(9, 0)))
        assertEquals(LocalTime.of(21, 15), Scheduler.timeOf(Slot.DAILY, "21:15"))
        assertEquals(LocalTime.of(7, 0), Scheduler.timeOf(Slot.AT_0700, "22:00"))
    }

    @Test fun closingStepsAndVerdict() {
        val first = LocalDate.of(2026, 12, 1)
        val input = funded()
        val state = computeLedger(input, first)
        assertFalse("bulan onboarding tanpa langkah gaji", ClosingStep.SALARY in closingSteps(state.months.getValue(YearMonth.of(2026, 10))))
        assertFalse(ClosingStep.SALARY in closingSteps(state.months.getValue(nov)))
        val noSalary = LedgerInput(cats, Onboarding(LocalDate.of(2026, 10, 31), 0, 500_000, 1_000_000))
        assertTrue(ClosingStep.SALARY in closingSteps(computeLedger(noSalary, first).months.getValue(nov)))
        assertEquals("BONCOS Rp 40.000", verdictTitle(Verdict(VerdictKind.BONCOS, 40_000)))
        assertEquals("PAS-PASAN", verdictTitle(Verdict(VerdictKind.PAS_PASAN, 0)))
    }
}
