package id.whitesecurity.antivirus.premium

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class CheckItem(
    val title: String,
    val passed: Boolean,
    val detail: String,
    val weight: Int,
    /** Settings intent action that lets the user fix this, if there is one. */
    val fixAction: String? = null,
)

data class CheckupResult(val score: Int, val items: List<CheckItem>) {
    val issues: Int get() = items.count { !it.passed }
}

/**
 * Premium "Pemeriksaan Keamanan Perangkat": a weighted 0–100 score built
 * only from settings readable without special permissions. Each item says
 * what's wrong in plain language and, where Android has a settings screen
 * for it, offers a direct "Perbaiki" shortcut.
 */
object SecurityCheckup {

    private const val PATCH_MAX_AGE_DAYS = 90L

    suspend fun run(context: Context): CheckupResult = withContext(Dispatchers.IO) {
        val items = listOf(
            screenLock(context),
            usbDebugging(context),
            developerOptions(context),
            securityPatch(),
            root(),
            encryption(context),
            sideloadedApps(context),
        )
        val total = items.sumOf { it.weight }
        val earned = items.filter { it.passed }.sumOf { it.weight }
        CheckupResult(score = if (total == 0) 0 else earned * 100 / total, items = items)
    }

    private fun screenLock(context: Context): CheckItem {
        val secure = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
        return CheckItem(
            title = "Kunci layar",
            passed = secure,
            detail = if (secure) "PIN, pola, password, atau biometrik aktif."
            else "Tidak ada kunci layar — siapa pun yang memegang HP bisa membuka semua aplikasi.",
            weight = 25,
            fixAction = Settings.ACTION_SECURITY_SETTINGS,
        )
    }

    private fun usbDebugging(context: Context): CheckItem {
        val on = Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        return CheckItem(
            title = "USB debugging",
            passed = !on,
            detail = if (on) "Aktif — komputer yang tersambung bisa memasang aplikasi dan membaca data. Matikan jika tidak sedang dipakai."
            else "Nonaktif.",
            weight = 15,
            fixAction = Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
        )
    }

    private fun developerOptions(context: Context): CheckItem {
        val on = Settings.Global.getInt(
            context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0
        ) == 1
        return CheckItem(
            title = "Opsi pengembang",
            passed = !on,
            detail = if (on) "Aktif — sebaiknya dimatikan bila Anda bukan developer."
            else "Nonaktif.",
            weight = 5,
            fixAction = Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
        )
    }

    private fun securityPatch(): CheckItem {
        val patch = runCatching { LocalDate.parse(Build.VERSION.SECURITY_PATCH) }.getOrNull()
        val ageDays = patch?.let { ChronoUnit.DAYS.between(it, LocalDate.now()) }
        val passed = ageDays != null && ageDays <= PATCH_MAX_AGE_DAYS
        return CheckItem(
            title = "Patch keamanan Android",
            passed = passed,
            detail = when {
                patch == null -> "Tanggal patch tidak diketahui."
                passed -> "Patch ${Build.VERSION.SECURITY_PATCH} — masih baru."
                else -> "Patch ${Build.VERSION.SECURITY_PATCH} sudah $ageDays hari. Cek pembaruan sistem."
            },
            weight = 20,
            fixAction = Settings.ACTION_DEVICE_INFO_SETTINGS,
        )
    }

    private fun root(): CheckItem {
        val suPaths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/sd/xbin/su",
            "/data/local/xbin/su", "/data/local/bin/su", "/system/app/Superuser.apk",
        )
        val rooted = suPaths.any { runCatching { File(it).exists() }.getOrDefault(false) } ||
            Build.TAGS?.contains("test-keys") == true
        return CheckItem(
            title = "Integritas sistem (root)",
            passed = !rooted,
            detail = if (rooted) "Tanda-tanda root terdeteksi — aplikasi berbahaya bisa mendapat akses penuh ke sistem."
            else "Tidak ada tanda root.",
            weight = 20,
        )
    }

    private fun encryption(context: Context): CheckItem {
        val status = context.getSystemService(DevicePolicyManager::class.java)?.storageEncryptionStatus
        val encrypted = status == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE ||
            status == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER ||
            status == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY
        return CheckItem(
            title = "Enkripsi penyimpanan",
            passed = encrypted,
            detail = if (encrypted) "Data di HP terenkripsi." else "Penyimpanan tidak terenkripsi.",
            weight = 5,
            fixAction = Settings.ACTION_SECURITY_SETTINGS,
        )
    }

    private fun sideloadedApps(context: Context): CheckItem {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val count = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && it.packageName != context.packageName }
            .count { ScamShieldAnalyzer.isSideloaded(pm, it.packageName) }
        return CheckItem(
            title = "Aplikasi dari luar toko resmi",
            passed = count == 0,
            detail = if (count == 0) "Semua aplikasi berasal dari toko resmi."
            else "$count aplikasi dipasang dari file APK atau sumber tidak resmi. Tinjau di Perisai Anti-Penipuan.",
            weight = 10,
        )
    }
}
