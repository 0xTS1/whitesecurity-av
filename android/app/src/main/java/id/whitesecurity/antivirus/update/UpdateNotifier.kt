package id.whitesecurity.antivirus.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import id.whitesecurity.antivirus.MainActivity
import id.whitesecurity.antivirus.R

object UpdateNotifier {

    const val CHANNEL_UPDATES = "app_updates"
    private const val NOTIFICATION_ID = 2001

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_UPDATES,
                    context.getString(R.string.notif_channel_updates),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
        }
    }

    private fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** "Update available — tap to install": opens the app, whose banner drives the install. */
    fun notifyAvailable(context: Context, info: UpdateInfo) {
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        post(context, context.getString(R.string.update_notif_available, info.versionName),
            context.getString(R.string.update_notif_available_text), open)
    }

    /** System install confirmation the user still has to tap. */
    fun notifyConfirmInstall(context: Context, confirm: Intent) {
        val pending = PendingIntent.getActivity(
            context, 1, confirm,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val version = UpdateState.available.value?.versionName ?: ""
        post(context, context.getString(R.string.update_notif_ready, version),
            context.getString(R.string.update_notif_ready_text), pending)
    }

    private fun post(context: Context, title: String, text: String, intent: PendingIntent) {
        if (!allowed(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
