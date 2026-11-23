package id.whitesecurity.antivirus.scanner

import id.whitesecurity.antivirus.util.Severity

data class ScannedApp(
    val packageName: String,
    val appLabel: String,
    val isSystemApp: Boolean,
    val apkPath: String,
    val sha256: String?,
    val permissions: Set<String>,
    val signingFingerprints: List<String>,
    val isDebuggable: Boolean = false,
    val installerPackageName: String? = null,
    /** SHA-1 of signing certs, uppercase hex without separators (stalkerware IOC format). */
    val signingSha1: List<String> = emptyList(),
    /** False for apps with no launcher activity — i.e. hidden from the home screen. */
    val hasLauncherIcon: Boolean = true,
    /** Permissions guarding the app's own services (e.g. BIND_ACCESSIBILITY_SERVICE). */
    val servicePermissions: Set<String> = emptySet(),
)

enum class FindingSource { KNOWN_MALWARE_LOCAL, KNOWN_MALWARE_REMOTE, KNOWN_STALKERWARE, SUSPECTED_SPYWARE, HEURISTIC, CLEAN }

/** What kind of threat a finding is, for labelling + advice in the UI. */
enum class ThreatCategory(val label: String) {
    MALWARE("Malware"),
    STALKERWARE("Stalkerware"),
    SPYWARE("Dugaan Spyware"),
    RISKY("Berisiko"),
    CLEAN("Aman"),
}

data class ScanFinding(
    val app: ScannedApp,
    val malicious: Boolean,
    val threatName: String?,
    val severity: Severity,
    val heuristicScore: Int,
    val heuristicReasons: List<String>,
    val source: FindingSource,
    val category: ThreatCategory = when (source) {
        FindingSource.KNOWN_MALWARE_LOCAL, FindingSource.KNOWN_MALWARE_REMOTE -> ThreatCategory.MALWARE
        FindingSource.KNOWN_STALKERWARE -> ThreatCategory.STALKERWARE
        FindingSource.SUSPECTED_SPYWARE -> ThreatCategory.SPYWARE
        FindingSource.HEURISTIC -> ThreatCategory.RISKY
        FindingSource.CLEAN -> ThreatCategory.CLEAN
    },
)

data class ScanSummary(
    val totalScanned: Int,
    val findings: List<ScanFinding>,
    val scannedAtEpochMs: Long,
) {
    val threatCount: Int get() = findings.count { it.malicious }
}

/** Result of scanning a single user-picked APK file (Storage Access Framework). */
data class FileScanResult(
    val fileName: String,
    val sha256: String,
    val malicious: Boolean,
    val threatName: String?,
    val severity: Severity,
)
