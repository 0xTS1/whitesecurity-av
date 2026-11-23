package id.whitesecurity.antivirus.scanner

import android.content.pm.PackageManager
import android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
import android.os.Build
import java.security.MessageDigest

object SignatureVerifier {

    /**
     * Returns the SHA-256 fingerprints of every certificate that signed this
     * package. Multiple entries usually mean a signing-certificate rotation
     * (normal) — some callers may want to flag a signer that doesn't match
     * any previously trusted fingerprint for that package name, which is a
     * stronger signal than permissions alone (a known-good app suddenly
     * signed by a different key is a classic repackaging/trojan pattern).
     */
    fun signingCertSha256Fingerprints(pm: PackageManager, packageName: String): List<String> =
        signingCerts(pm, packageName).map { cert ->
            MessageDigest.getInstance("SHA-256").digest(cert).joinToString(":") { "%02X".format(it) }
        }

    /** SHA-1 of each signing cert as plain uppercase hex — the format stalkerware IOC lists use. */
    fun signingCertSha1(pm: PackageManager, packageName: String): List<String> =
        signingCerts(pm, packageName).map { cert ->
            MessageDigest.getInstance("SHA-1").digest(cert).joinToString("") { "%02X".format(it) }
        }

    @Suppress("DEPRECATION")
    private fun signingCerts(pm: PackageManager, packageName: String): List<ByteArray> {
        return try {
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(packageName, GET_SIGNING_CERTIFICATES)
                val signingInfo = info.signingInfo ?: return emptyList()
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            } else {
                val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                info.signatures ?: return emptyList()
            }

            signatures.orEmpty().map { it.toByteArray() }
        } catch (e: PackageManager.NameNotFoundException) {
            emptyList()
        }
    }
}
