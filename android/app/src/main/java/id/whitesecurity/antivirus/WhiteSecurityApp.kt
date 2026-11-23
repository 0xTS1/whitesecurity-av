package id.whitesecurity.antivirus

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import id.whitesecurity.antivirus.account.AccountManager
import id.whitesecurity.antivirus.data.local.AppDatabase
import id.whitesecurity.antivirus.data.remote.RetrofitClient
import id.whitesecurity.antivirus.data.repository.LicenseRepository
import id.whitesecurity.antivirus.data.repository.MalwareRepository
import id.whitesecurity.antivirus.license.LicenseManager
import id.whitesecurity.antivirus.scanner.PackageScanner
import id.whitesecurity.antivirus.scanner.ScanEngine
import id.whitesecurity.antivirus.scanner.SpywareDetector
import id.whitesecurity.antivirus.update.UpdateNotifier
import id.whitesecurity.antivirus.update.UpdateWorker
import id.whitesecurity.antivirus.util.Constants
import id.whitesecurity.antivirus.work.ScanScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Hand-rolled dependency container — deliberately not using a DI framework
 * given the app's size; every screen/receiver reads what it needs from here.
 */
class WhiteSecurityApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var database: AppDatabase
        private set
    lateinit var licenseManager: LicenseManager
        private set
    lateinit var malwareRepository: MalwareRepository
        private set
    lateinit var scanEngine: ScanEngine
        private set
    lateinit var packageScanner: PackageScanner
        private set
    lateinit var accountManager: AccountManager
        private set

    override fun onCreate() {
        super.onCreate()

        database = AppDatabase.getInstance(this)
        val api = RetrofitClient.api
        val licenseRepository = LicenseRepository(api)
        licenseManager = LicenseManager(this, licenseRepository)
        accountManager = AccountManager(this, api) { licenseManager.deviceId }
        malwareRepository = MalwareRepository(this, database.malwareSignatureDao(), api)
        packageScanner = PackageScanner(this)
        scanEngine = ScanEngine(
            packageScanner,
            malwareRepository,
            database.scanHistoryDao(),
            database.trustedSignerDao(),
            licenseManager,
            SpywareDetector(this),
        )

        createNotificationChannel()
        UpdateNotifier.createChannel(this)
        ScanScheduler.schedulePeriodic(this)
        UpdateWorker.schedule(this)

        applicationScope.launch {
            licenseManager.refresh()
            if (licenseManager.isCloudHashLookupAllowed()) {
                malwareRepository.syncSignatureUpdates()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIF_CHANNEL_SCAN,
                getString(R.string.notif_channel_scan),
                NotificationManager.IMPORTANCE_HIGH,
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
