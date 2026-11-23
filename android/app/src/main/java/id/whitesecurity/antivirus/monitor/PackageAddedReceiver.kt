package id.whitesecurity.antivirus.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import id.whitesecurity.antivirus.WhiteSecurityApp
import id.whitesecurity.antivirus.scanner.FindingSource
import id.whitesecurity.antivirus.scanner.PackageScanner
import kotlinx.coroutines.launch

/**
 * Fires on every app install/update system-wide (manifest-declared receivers
 * for this action are exempt from Android 8+'s implicit-broadcast
 * restrictions). This is the "real-time protection" feature — free for all
 * users.
 */
class PackageAddedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_PACKAGE_ADDED && action != Intent.ACTION_PACKAGE_REPLACED) return
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false) && action == Intent.ACTION_PACKAGE_ADDED) return

        val packageName = intent.data?.schemeSpecificPart ?: return
        val app = context.applicationContext as WhiteSecurityApp
        if (!app.licenseManager.isRealtimeMonitoringAllowed()) return

        // Don't scan ourselves.
        if (packageName == context.packageName) return

        val pendingResult = goAsync()
        app.applicationScope.launch {
            try {
                val scanned = PackageScanner(context).scanInstalledPackage(packageName) ?: return@launch
                val finding = app.scanEngine.scanSingleFile(scanned)
                val alarming = finding.malicious || finding.source == FindingSource.SUSPECTED_SPYWARE
                ScanNotifier.notifySingleResult(context, finding.app.appLabel, alarming, finding.threatName)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
