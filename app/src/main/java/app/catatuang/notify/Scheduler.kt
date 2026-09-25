package app.catatuang.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Jadwal bab 9 dengan AlarmManager inexact (`setWindow`, tanpa exact alarm). Tiap slot dijadwal ulang
 * setiap kali berbunyi, saat app dibuka, saat boot, dan saat jam/zona waktu berubah.
 */
object Scheduler {
    private const val WINDOW_MS = 10 * 60 * 1000L

    /** Waktu berikutnya untuk [time] setelah [now] (hari ini bila belum lewat, selain itu besok). */
    fun nextAt(now: LocalDateTime, time: LocalTime): LocalDateTime {
        val today = now.toLocalDate().atTime(time)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    fun timeOf(slot: Slot, dailyTime: String): LocalTime =
        slot.hour?.let { LocalTime.of(it, 0) } ?: runCatching { LocalTime.parse(dailyTime) }.getOrDefault(LocalTime.of(22, 0))

    private fun pending(context: Context, slot: Slot): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_SLOT).putExtra(AlarmReceiver.EXTRA_SLOT, slot.name)
        return PendingIntent.getBroadcast(context, slot.ordinal, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun schedule(context: Context, slot: Slot, dailyTime: String) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val at = nextAt(LocalDateTime.now(), timeOf(slot, dailyTime))
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Jendela dimulai sedikit sebelum jam target supaya notifikasi tidak telat jauh.
        am.setWindow(AlarmManager.RTC_WAKEUP, millis - 60_000, WINDOW_MS, pending(context, slot))
    }

    fun scheduleAll(context: Context, dailyTime: String) {
        Notifier.ensureChannels(context)
        Slot.entries.forEach { schedule(context, it, dailyTime) }
    }
}
