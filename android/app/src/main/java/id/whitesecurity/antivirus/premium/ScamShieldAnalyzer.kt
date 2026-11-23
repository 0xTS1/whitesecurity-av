package id.whitesecurity.antivirus.premium

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ScamRiskLevel { HIGH, REVIEW }

data class ScamRiskApp(
    val packageName: String,
    val label: String,
    val sideloaded: Boolean,
    val signals: List<String>,
    val level: ScamRiskLevel,
)

/**
 * Premium "Perisai Anti-Penipuan". Unlike PermissionRiskAnalyzer (which only
 * looks at what an app *asks* for), this looks at access the user has
 * actually *switched on*: enabled accessibility services, notification
 * listeners, active device admins and granted SMS permission — the exact
 * toolkit the fake "undangan.apk"/"kurir.apk" banking trojans need to read
 * OTPs and drive the screen. Plenty of legitimate apps hold one of these
 * (password managers, smartwatch companions), so results are framed as
 * "review this", and only escalate to HIGH when combined with sideloading or
 * stacked with each other.
 */
class ScamShieldAnalyzer(private val context: Context) {

    suspend fun analyze(): List<ScamRiskApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val accessibility = enabledComponentPackages(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val notificationListeners = enabledComponentPackages(KEY_NOTIFICATION_LISTENERS)
        val deviceAdmins = runCatching {
            context.getSystemService(DevicePolicyManager::class.java)
                ?.activeAdmins?.map { it.packageName }?.toSet()
        }.getOrNull() ?: emptySet()

        @Suppress("DEPRECATION")
        val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)

        packages.mapNotNull { pkg ->
            val appInfo = pkg.applicationInfo ?: return@mapNotNull null
            if (pkg.packageName == context.packageName) return@mapNotNull null
            if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) return@mapNotNull null

            val signals = mutableListOf<String>()
            if (pkg.packageName in accessibility) signals += SIGNAL_ACCESSIBILITY
            if (pkg.packageName in notificationListeners) signals += SIGNAL_NOTIFICATIONS
            if (pkg.packageName in deviceAdmins) signals += SIGNAL_DEVICE_ADMIN
            val granted = grantedPermissions(pkg)
            if (SMS_PERMISSIONS.any { it in granted }) signals += SIGNAL_SMS
            if (signals.isEmpty()) return@mapNotNull null

            val sideloaded = isSideloaded(pm, pkg.packageName)
            val level = if (signals.size >= 2 || sideloaded) ScamRiskLevel.HIGH else ScamRiskLevel.REVIEW
            ScamRiskApp(
                packageName = pkg.packageName,
                label = appInfo.loadLabel(pm).toString(),
                sideloaded = sideloaded,
                signals = signals,
                level = level,
            )
        }.sortedWith(compareBy<ScamRiskApp> { it.level.ordinal }.thenByDescending { it.signals.size })
    }

    /** Package names behind a ':'-separated list of flattened ComponentNames in Settings.Secure. */
    private fun enabledComponentPackages(key: String): Set<String> = runCatching {
        Settings.Secure.getString(context.contentResolver, key)
            ?.split(':')
            ?.mapNotNull { ComponentName.unflattenFromString(it)?.packageName }
            ?.toSet()
    }.getOrNull() ?: emptySet()

    private fun grantedPermissions(pkg: PackageInfo): Set<String> {
        val names = pkg.requestedPermissions ?: return emptySet()
        val flags = pkg.requestedPermissionsFlags ?: return emptySet()
        return names.indices
            .filter { (flags[it] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0 }
            .map { names[it] }
            .toSet()
    }

    companion object {
        // Not exposed as a public Settings.Secure constant, but readable.
        private const val KEY_NOTIFICATION_LISTENERS = "enabled_notification_listeners"

        private val SMS_PERMISSIONS = setOf(
            "android.permission.READ_SMS",
            "android.permission.RECEIVE_SMS",
        )

        const val SIGNAL_ACCESSIBILITY = "Layanan Aksesibilitas aktif — dapat membaca isi layar dan menekan tombol atas nama Anda"
        const val SIGNAL_NOTIFICATIONS = "Membaca semua notifikasi — termasuk kode OTP dan pesan chat"
        const val SIGNAL_DEVICE_ADMIN = "Admin perangkat aktif — bisa mengunci layar atau mempersulit uninstall"
        const val SIGNAL_SMS = "Izin membaca SMS diberikan — kode OTP bank bisa terbaca"

        /** Stores we treat as a legitimate install source; anything else (incl. the plain package installer) is a sideload. */
        private val TRUSTED_INSTALLERS = setOf(
            "com.android.vending",
            "com.google.android.feedback",
            "com.sec.android.app.samsungapps",
            "com.huawei.appmarket",
            "com.xiaomi.market",
            "com.xiaomi.mipicks",
            "com.heytap.market",
            "com.oppo.market",
            "com.vivo.appstore",
            "com.bbk.appstore",
            "com.amazon.venezia",
        )

        @Suppress("DEPRECATION")
        fun installerOf(pm: PackageManager, packageName: String): String? = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(packageName).installingPackageName
            } else {
                pm.getInstallerPackageName(packageName)
            }
        }.getOrNull()

        fun isSideloaded(pm: PackageManager, packageName: String): Boolean {
            val installer = installerOf(pm, packageName)
            return installer == null || installer !in TRUSTED_INSTALLERS
        }
    }
}
