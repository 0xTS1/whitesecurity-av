package id.whitesecurity.antivirus.scanner

import android.content.Context
import org.json.JSONObject

/**
 * Spyware / stalkerware detection, two layers:
 *
 * 1. Known stalkerware — package names and signing-certificate SHA-1s from
 *    Echap's stalkerware-indicators (CC BY 4.0, bundled as
 *    assets/stalkerware_indicators.json). Many of these deliberately mimic
 *    system package names, so a *package-name* hit only counts for
 *    user-installed apps; a *certificate* hit counts everywhere.
 * 2. Spyware behaviour — an app with no launcher icon (i.e. hidden from the
 *    user) that requests surveillance-grade access. Reported as "suspected",
 *    never as a confirmed detection.
 */
class SpywareDetector(context: Context) {

    data class KnownMatch(val family: String, val byCertificate: Boolean)

    private val byPackage = HashMap<String, String>()
    private val byCertificate = HashMap<String, String>()

    init {
        runCatching {
            val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            val families = JSONObject(text).getJSONArray("families")
            for (i in 0 until families.length()) {
                val f = families.getJSONObject(i)
                val name = f.getString("name")
                val pkgs = f.getJSONArray("packages")
                for (j in 0 until pkgs.length()) byPackage[pkgs.getString(j)] = name
                val certs = f.getJSONArray("certificates")
                for (j in 0 until certs.length()) byCertificate[certs.getString(j).uppercase()] = name
            }
        }
    }

    val indicatorCount: Int get() = byPackage.size + byCertificate.size

    fun matchKnown(app: ScannedApp): KnownMatch? {
        app.signingSha1.firstNotNullOfOrNull { byCertificate[it.uppercase()] }?.let {
            return KnownMatch(it, byCertificate = true)
        }
        if (!app.isSystemApp) {
            byPackage[app.packageName]?.let { return KnownMatch(it, byCertificate = false) }
        }
        return null
    }

    /** Descriptions of surveillance access held by a hidden app; empty when not suspicious. */
    fun hiddenSurveillanceSignals(app: ScannedApp): List<String> {
        if (app.isSystemApp || app.hasLauncherIcon) return emptyList()
        val held = app.permissions + app.servicePermissions
        val signals = SURVEILLANCE.filter { (perms, _) -> perms.any { it in held } }.map { it.second }
        return if (signals.size >= MIN_SIGNALS) listOf(HIDDEN_APP) + signals else emptyList()
    }

    companion object {
        private const val ASSET = "stalkerware_indicators.json"
        private const val MIN_SIGNALS = 2
        const val ATTRIBUTION = "Indikator stalkerware: Echap stalkerware-indicators (CC BY 4.0)"

        private const val HIDDEN_APP = "Tidak punya ikon di layar utama (tersembunyi dari pengguna)"
        private val SURVEILLANCE = listOf(
            setOf("android.permission.ACCESS_BACKGROUND_LOCATION", "android.permission.ACCESS_FINE_LOCATION") to "Melacak lokasi",
            setOf("android.permission.RECORD_AUDIO") to "Merekam suara lewat mikrofon",
            setOf("android.permission.CAMERA") to "Mengakses kamera",
            setOf("android.permission.READ_SMS", "android.permission.RECEIVE_SMS") to "Membaca SMS",
            setOf("android.permission.READ_CALL_LOG", "android.permission.PROCESS_OUTGOING_CALLS") to "Membaca riwayat panggilan",
            setOf("android.permission.READ_CONTACTS") to "Membaca kontak",
            setOf("android.permission.BIND_ACCESSIBILITY_SERVICE") to "Layanan Aksesibilitas (membaca layar)",
            setOf("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE") to "Membaca notifikasi",
        )
    }
}
