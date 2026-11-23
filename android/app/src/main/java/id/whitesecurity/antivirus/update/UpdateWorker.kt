package id.whitesecurity.antivirus.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Background update check every 12 hours on any network. If a newer build
 * exists it is downloaded, verified and handed to PackageInstaller straight
 * away; when Android still needs a tap (first self-update, Android < 12, or
 * the install-apps permission isn't granted yet) the user gets a notification.
 */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val info = AppUpdater.checkForUpdate() ?: run {
            UpdateState.available.value = null
            return Result.success()
        }
        UpdateState.available.value = info
        return when (AppUpdater.downloadAndInstall(applicationContext, info)) {
            is UpdateResult.NeedsInstallPermission -> {
                UpdateNotifier.notifyAvailable(applicationContext, info)
                Result.success()
            }
            is UpdateResult.Failed -> if (runAttemptCount < 3) Result.retry() else Result.success()
            else -> Result.success()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "app_update_check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request,
            )
        }
    }
}
