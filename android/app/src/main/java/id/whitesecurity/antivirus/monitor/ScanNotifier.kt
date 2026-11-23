package id.whitesecurity.antivirus.monitor

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import id.whitesecurity.antivirus.MainActivity
import id.whitesecurity.antivirus.R
import id.whitesecurity.antivirus.scanner.ScanSummary
import id.whitesecurity.antivirus.util.Constants

/** Shared notification logic for both the real-time install monitor and the periodic scan worker. */
object ScanNotifier {

    private fun hasNotificationPermission(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun notifySingleResult(context: Context, appLabel: String, malicious: Boolean, threatName: String?) {
        if (!hasNotificationPermission(context)) return

        val title = if (malicious) {
            context.getString(R.string.notif_threat_detected, threatName ?: appLabel)
        } else {
            context.getString(R.string.notif_new_app_scanned, appLabel)
        }

        val notification = NotificationCompat.Builder(context, Constants.NOTIF_CHANNEL_SCAN)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(appLabel)
            .setPriority(if (malicious) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(appLabel.hashCode(), notification)
    }

    fun notifyScanSummary(context: Context, summary: ScanSummary) {
        if (!hasNotificationPermission(context)) return

        val threatCount = summary.threatCount
        val title = if (threatCount > 0) {
            context.getString(R.string.notif_scan_summary_threats, threatCount)
        } else {
            context.getString(R.string.notif_scan_summary_clean)
        }

        val notification = NotificationCompat.Builder(context, Constants.NOTIF_CHANNEL_SCAN)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notif_scan_summary_text, summary.totalScanned))
            .setPriority(if (threatCount > 0) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(SCAN_SUMMARY_NOTIFICATION_ID, notification)
    }

    private const val SCAN_SUMMARY_NOTIFICATION_ID = 1001
}
