package id.whitesecurity.antivirus.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import id.whitesecurity.antivirus.BuildConfig
import id.whitesecurity.antivirus.scanner.HashUtil
import id.whitesecurity.antivirus.scanner.SignatureVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val sha256: String,
    val sizeBytes: Long,
    val apkUrl: String,
)

sealed class UpdateResult {
    data object UpToDate : UpdateResult()
    /** Install session committed; Android either installs silently or asks the user. */
    data class Installing(val info: UpdateInfo) : UpdateResult()
    /** User hasn't allowed this app to install packages yet (Settings → Install unknown apps). */
    data class NeedsInstallPermission(val info: UpdateInfo) : UpdateResult()
    data class Failed(val reason: String) : UpdateResult()
}

/**
 * Self-update for the web-distributed APK (no Play Store). Pulls the
 * manifest the server's /antivirus/download page publishes, and only
 * installs a build that (1) has a higher versionCode, (2) matches the
 * published SHA-256, (3) is this same package and (4) is signed by the same
 * certificate as the running app. Android re-checks (3)+(4) itself on
 * install, but checking first avoids prompting the user for a doomed update.
 *
 * On Android 12+ the session asks for USER_ACTION_NOT_REQUIRED: once this app
 * has installed itself once (making it the installer of record) later
 * updates go through without a prompt. Before that — and on Android 8–11 —
 * the system shows one "Update?" confirmation.
 */
object AppUpdater {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val manifestUrl: String
        get() = BuildConfig.UPDATE_BASE_URL.trimEnd('/') + "/antivirus/download/latest.json"

    suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(manifestUrl).build()).execute().use { res ->
                if (!res.isSuccessful) return@use null
                val json = JSONObject(res.body?.string() ?: return@use null)
                val code = json.optInt("versionCode", -1)
                val sha = json.optString("sha256").lowercase()
                if (code <= 0 || !SHA256_RE.matches(sha)) return@use null
                val path = json.optString("url", "/antivirus/download/apk")
                UpdateInfo(
                    versionCode = code,
                    versionName = json.optString("version", code.toString()),
                    sha256 = sha,
                    sizeBytes = json.optLong("size", 0),
                    apkUrl = BuildConfig.UPDATE_BASE_URL.trimEnd('/') + "/" + path.trimStart('/'),
                )
            }
        }.getOrNull()
    }

    /** Non-null only when the server has a build newer than the one running. */
    suspend fun checkForUpdate(): UpdateInfo? =
        fetchLatest()?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    suspend fun downloadAndInstall(context: Context, info: UpdateInfo): UpdateResult {
        if (!canInstall(context)) return UpdateResult.NeedsInstallPermission(info)
        val apk = download(context, info) ?: return UpdateResult.Failed("Unduhan gagal atau file tidak cocok")
        verifyApk(context, apk, info)?.let { reason ->
            apk.delete()
            return UpdateResult.Failed(reason)
        }
        return runCatching {
            install(context, apk)
            UpdateResult.Installing(info)
        }.getOrElse { UpdateResult.Failed(it.message ?: "Gagal memasang update") }
    }

    private suspend fun download(context: Context, info: UpdateInfo): File? = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // drop older/partial downloads
        val target = File(dir, "whitesecurity-${info.versionCode}.apk")
        runCatching {
            http.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { res ->
                if (!res.isSuccessful) return@use null
                val body = res.body ?: return@use null
                val digest = MessageDigest.getInstance("SHA-256")
                target.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            digest.update(buf, 0, n)
                            out.write(buf, 0, n)
                        }
                    }
                }
                val sha = digest.digest().joinToString("") { "%02x".format(it) }
                if (sha != info.sha256) {
                    target.delete()
                    null
                } else {
                    target
                }
            }
        }.getOrNull()
    }

    /** Returns a human-readable reason if the downloaded APK must not be installed, else null. */
    @Suppress("DEPRECATION")
    private fun verifyApk(context: Context, apk: File, info: UpdateInfo): String? {
        if (HashUtil.sha256(apk) != info.sha256) return "Checksum tidak cocok"
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val archive: PackageInfo = pm.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: return "File update rusak"
        if (archive.packageName != context.packageName) return "Paket tidak sesuai"
        val archiveCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) archive.longVersionCode else archive.versionCode.toLong()
        if (archiveCode <= BuildConfig.VERSION_CODE) return "Versi tidak lebih baru"

        val installed = SignatureVerifier.signingCertSha256Fingerprints(pm, context.packageName).toSet()
        val incoming = archiveFingerprints(archive)
        if (installed.isEmpty() || incoming.none { it in installed }) return "Tanda tangan aplikasi tidak cocok"
        return null
    }

    @Suppress("DEPRECATION")
    private fun archiveFingerprints(info: PackageInfo): List<String> {
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
        } else {
            info.signatures
        }
        return sigs.orEmpty().map { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString(":") { "%02X".format(it) }
        }
    }

    private fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val callback = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0),
            )
            session.commit(callback.intentSender)
        }
    }

    private val SHA256_RE = Regex("^[a-f0-9]{64}$")
}
