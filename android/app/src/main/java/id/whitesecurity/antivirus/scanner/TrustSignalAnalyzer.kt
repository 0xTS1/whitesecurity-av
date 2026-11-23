package id.whitesecurity.antivirus.scanner

/**
 * Non-permission heuristic signals: signing-certificate changes, install
 * source, and debuggable builds. Like PermissionRiskAnalyzer, these are
 * coarse risk signals, not a verdict — a signer change can also happen for
 * a legitimate reason (lost keystore, company acquisition), and plenty of
 * real apps sideload fine. Always combined into the same "worth reviewing"
 * bucket as permission heuristics, never used to set `malicious = true` on
 * their own.
 */
object TrustSignalAnalyzer {

    data class Result(val score: Int, val reasons: List<String>)

    private const val SIGNER_MISMATCH_POINTS = 50
    private const val SIDELOAD_POINTS = 10
    private const val DEBUGGABLE_POINTS = 10
    private const val TRUSTED_INSTALLER = "com.android.vending"

    fun evaluate(app: ScannedApp, trustedFingerprint: String?): Result {
        val reasons = mutableListOf<String>()
        var score = 0

        if (trustedFingerprint != null &&
            app.signingFingerprints.isNotEmpty() &&
            trustedFingerprint !in app.signingFingerprints
        ) {
            score += SIGNER_MISMATCH_POINTS
            reasons += "Sertifikat penandatanganan berubah dari yang terakhir dipercaya — indikasi app di-repack atau trojan"
        }

        if (!app.isSystemApp && app.installerPackageName != TRUSTED_INSTALLER) {
            score += SIDELOAD_POINTS
            reasons += if (app.installerPackageName != null) {
                "Diinstal bukan dari Play Store (installer: ${app.installerPackageName})"
            } else {
                "Sumber instalasi tidak diketahui — kemungkinan APK sideload manual"
            }
        }

        if (app.isDebuggable && !app.isSystemApp) {
            score += DEBUGGABLE_POINTS
            reasons += "APK memiliki flag debuggable aktif — tidak lazim untuk rilis resmi"
        }

        return Result(score, reasons)
    }
}
