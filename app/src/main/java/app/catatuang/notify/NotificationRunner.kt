package app.catatuang.notify

import android.content.Context
import app.catatuang.AppContainer
import app.catatuang.data.AppState
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/** Hitung isi slot dari ledger terbaru lalu tampilkan (dipakai alarm dan tombol uji Mode Uji). */
object NotificationRunner {
    suspend fun run(context: Context, container: AppContainer, slot: Slot): List<NotifSpec> {
        val repo = container.repository
        val ready = withTimeoutOrNull(10_000) { repo.state.filterIsInstance<AppState.Ready>().first() } ?: return emptyList()
        val specs = when (slot) {
            Slot.DAILY -> {
                val done = repo.snapshot.first().dayMarks.any { it.date == ready.today.toString() && it.doneMarked }
                listOfNotNull(dailyNotification(ready.input, ready.ledger, ready.today, done))
            }
            else -> {
                val checklist = repo.transferChecklist.first()
                val since = repo.transferChecklistSince.first()?.let(LocalDate::parse)
                reminderNotifications(slot, ready.input, ready.ledger, ready.today, checklist, since)
            }
        }
        specs.forEach { spec ->
            Notifier.post(context, spec)
            if (spec.id == NotifIds.CHECKLIST) bumpChecklist(repo)
        }
        return specs
    }

    private suspend fun bumpChecklist(repo: app.catatuang.data.CatatRepository) {
        val (month, count) = repo.transferChecklist.first()
        if (month != null) repo.bumpChecklistReminder(month, count + 1)
    }
}
