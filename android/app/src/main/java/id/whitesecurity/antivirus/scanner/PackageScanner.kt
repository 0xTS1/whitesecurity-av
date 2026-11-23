package id.whitesecurity.antivirus.scanner

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Enumerates installed packages via PackageManager. Requires
 * QUERY_ALL_PACKAGES (declared in the manifest) to see every app rather than
 * just ones visible under Android 11+'s default package-visibility rules.
 */
class PackageScanner(private val context: Context) {

    suspend fun listInstalledApps(
        includeSystemApps: Boolean = false,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> },
    ): List<ScannedApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES)
        val launchable = launchablePackages(pm)

        val filtered = packages.filter { pkg ->
            val appInfo = pkg.applicationInfo ?: return@filter false
            includeSystemApps || (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0
        }

        filtered.mapIndexed { index, pkg ->
            onProgress(index + 1, filtered.size, pkg.applicationInfo!!.loadLabel(pm).toString())
            buildScannedApp(pm, pkg, pkg.packageName in launchable)
        }
    }

    private fun launchablePackages(pm: PackageManager): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
    }

    /** Used by the real-time monitor to scan the single package that was just installed. */
    suspend fun scanInstalledPackage(packageName: String): ScannedApp? = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val pkg = runCatching {
            pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES)
        }.getOrNull() ?: return@withContext null
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName)
        @Suppress("DEPRECATION")
        val hasIcon = pm.queryIntentActivities(launcher, 0).isNotEmpty()
        buildScannedApp(pm, pkg, hasIcon)
    }

    private fun buildScannedApp(pm: PackageManager, pkg: PackageInfo, hasLauncherIcon: Boolean): ScannedApp {
        val appInfo = pkg.applicationInfo!!
        val apkPath = appInfo.sourceDir
        val sha256 = runCatching { HashUtil.sha256(File(apkPath)) }.getOrNull()
        val permissions = pkg.requestedPermissions?.toSet() ?: emptySet()
        val fingerprints = runCatching {
            SignatureVerifier.signingCertSha256Fingerprints(pm, pkg.packageName)
        }.getOrDefault(emptyList())
        val sha1 = runCatching {
            SignatureVerifier.signingCertSha1(pm, pkg.packageName)
        }.getOrDefault(emptyList())
        val servicePermissions = pkg.services.orEmpty().mapNotNull { it.permission }.toSet()
        val isDebuggable = (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val installerPackageName = installerOf(pm, pkg.packageName)

        return ScannedApp(
            packageName = pkg.packageName,
            appLabel = appInfo.loadLabel(pm).toString(),
            isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            apkPath = apkPath,
            sha256 = sha256,
            permissions = permissions,
            signingFingerprints = fingerprints,
            isDebuggable = isDebuggable,
            installerPackageName = installerPackageName,
            signingSha1 = sha1,
            hasLauncherIcon = hasLauncherIcon,
            servicePermissions = servicePermissions,
        )
    }

    /** Who installed this package — null/absent commonly means a manually sideloaded APK. */
    @Suppress("DEPRECATION")
    private fun installerOf(pm: PackageManager, packageName: String): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(packageName).installingPackageName
        } else {
            pm.getInstallerPackageName(packageName)
        }
    }.getOrNull()

    /**
     * Scans a user-picked APK file (Storage Access Framework URI, e.g. from
     * the Downloads folder) — this covers the "scan before you install"
     * use case, the most useful one on a non-rooted device. The content URI
     * is copied to a cache file first because APK manifest parsing
     * (getPackageArchiveInfo) needs a real filesystem path.
     */
    suspend fun scanPickedApk(uri: Uri): ScannedApp? = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(uri) ?: "picked.apk"
        val tempFile = File(context.cacheDir, "scan_${System.currentTimeMillis()}_$displayName")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null

            val sha256 = HashUtil.sha256(tempFile)
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val archiveInfo = pm.getPackageArchiveInfo(tempFile.absolutePath, PackageManager.GET_PERMISSIONS)

            if (archiveInfo != null) {
                archiveInfo.applicationInfo?.apply {
                    sourceDir = tempFile.absolutePath
                    publicSourceDir = tempFile.absolutePath
                }
                val label = archiveInfo.applicationInfo?.loadLabel(pm)?.toString() ?: displayName
                ScannedApp(
                    packageName = archiveInfo.packageName ?: displayName,
                    appLabel = label,
                    isSystemApp = false,
                    apkPath = tempFile.absolutePath,
                    sha256 = sha256,
                    permissions = archiveInfo.requestedPermissions?.toSet() ?: emptySet(),
                    signingFingerprints = emptyList(), // not installed yet — no signing-cert history to compare
                )
            } else {
                // Not a parseable APK manifest, but we can still hash-check the file.
                ScannedApp(
                    packageName = displayName,
                    appLabel = displayName,
                    isSystemApp = false,
                    apkPath = tempFile.absolutePath,
                    sha256 = sha256,
                    permissions = emptySet(),
                    signingFingerprints = emptyList(),
                )
            }
        } finally {
            tempFile.delete()
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }
}
