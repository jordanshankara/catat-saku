package app.catatuang.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.catatuang.CatatUangApp
import app.catatuang.data.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Menerima alarm slot bab 9 dan aksi langsung dari notifikasi ("Hari ini beres", "Tandai selesai"). */
class AlarmReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_SLOT = "app.catatuang.SLOT"
        const val EXTRA_SLOT = "slot"
        const val EXTRA_NOTIF_ID = "notif_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? CatatUangApp ?: return
        val result = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val container = app.container
                when (intent.action) {
                    ACTION_SLOT -> {
                        val slot = runCatching { Slot.valueOf(intent.getStringExtra(EXTRA_SLOT) ?: "") }.getOrNull() ?: return@launch
                        if (slot == Slot.AT_2300) AutoBackup.runIfDue(context, container)
                        else NotificationRunner.run(context, container, slot)
                        Scheduler.schedule(context, slot, container.settings.current().notificationTime)
                    }
                    ACTION_DAY_DONE -> {
                        val ready = withTimeoutOrNull(5_000) { container.repository.state.filterIsInstance<AppState.Ready>().first() }
                        container.repository.markDayDone(ready?.today ?: container.clock.now())
                        Notifier.cancel(context, NotifIds.DAILY)
                    }
                    ACTION_CHECKLIST_DONE -> {
                        container.repository.setTransferChecklist(null)
                        Notifier.cancel(context, NotifIds.CHECKLIST)
                    }
                }
            } finally {
                result.finish()
            }
        }
    }
}

/** Jadwal ulang setelah boot / perubahan jam / zona waktu / update app. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? CatatUangApp ?: return
        val result = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                Scheduler.scheduleAll(context, app.container.settings.current().notificationTime)
            } finally {
                result.finish()
            }
        }
    }
}
