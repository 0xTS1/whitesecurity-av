package id.whitesecurity.antivirus.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import id.whitesecurity.antivirus.WhiteSecurityApp
import id.whitesecurity.antivirus.monitor.ScanNotifier

/**
 * Background counterpart of the manual "Scan Aplikasi Terinstal" button —
 * scheduled by ScanScheduler. Reads everything off `applicationContext`
 * (no constructor deps), same pattern PackageAddedReceiver already uses, so
 * no custom WorkManager Configuration.Provider is needed.
 */
class ScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as WhiteSecurityApp
        return try {
            if (app.licenseManager.isCloudHashLookupAllowed()) {
                app.malwareRepository.syncSignatureUpdates()
            }
            val summary = app.scanEngine.scanInstalledApps()
            ScanNotifier.notifyScanSummary(applicationContext, summary)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
