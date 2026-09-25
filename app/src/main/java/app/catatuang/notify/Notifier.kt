package app.catatuang.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.catatuang.MainActivity
import app.catatuang.R

/** Pasang channel & tampilkan [NotifSpec] (bab 9: VISIBILITY_PRIVATE + versi publik tanpa nominal). */
object Notifier {
    const val EXTRA_ROUTE = "route"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        Channel.entries.forEach { ch ->
            val importance = if (ch == Channel.HARIAN) NotificationManager.IMPORTANCE_DEFAULT else NotificationManager.IMPORTANCE_DEFAULT
            nm.createNotificationChannel(NotificationChannel(ch.id, ch.label, importance).apply {
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            })
        }
    }

    private fun openIntent(context: Context, route: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction("app.catatuang.OPEN.$route")
            .putExtra(EXTRA_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun broadcastIntent(context: Context, action: String, notifId: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(action).putExtra(AlarmReceiver.EXTRA_NOTIF_ID, notifId)
        return PendingIntent.getBroadcast(context, notifId * 10 + action.length, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun post(context: Context, spec: NotifSpec) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        ensureChannels(context)
        val public = NotificationCompat.Builder(context, spec.channel.id)
            .setSmallIcon(R.drawable.ic_stat_wallet)
            .setContentTitle("Catat Uang")
            .setContentText(PUBLIC_TEXT)
            .build()
        val builder = NotificationCompat.Builder(context, spec.channel.id)
            .setSmallIcon(R.drawable.ic_stat_wallet)
            .setColor(0xFF3563E9.toInt())
            .setContentTitle(spec.title)
            .setContentText(spec.text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(spec.text))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setAutoCancel(true)
            .setContentIntent(openIntent(context, spec.route, spec.id * 10))
        spec.actions.forEachIndexed { i, a ->
            val pi = when {
                a.broadcast != null -> broadcastIntent(context, a.broadcast, spec.id)
                else -> openIntent(context, a.route ?: spec.route, spec.id * 10 + i + 1)
            }
            builder.addAction(0, a.label, pi)
        }
        try {
            nm.notify(spec.id, builder.build())
        } catch (_: SecurityException) {
            // Izin POST_NOTIFICATIONS dicabut; tidak ada yang bisa dilakukan.
        }
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)
}
