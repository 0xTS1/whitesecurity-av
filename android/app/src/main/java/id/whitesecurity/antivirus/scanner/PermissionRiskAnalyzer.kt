package id.whitesecurity.antivirus.scanner

import id.whitesecurity.antivirus.util.Severity

/**
 * Permission-combination heuristics. This is NOT malware detection — it is a
 * coarse risk signal (same idea as "app requests a lot of dangerous
 * permissions together"). It will flag legitimate apps that genuinely need
 * these combinations (e.g. a real SMS-backup app). Always present heuristic
 * results as "worth reviewing", never as a confirmed verdict — that's only
 * for hash matches against a known-bad signature.
 */
object PermissionRiskAnalyzer {

    private data class Rule(
        val id: String,
        val description: String,
        val requiredAny: List<Set<String>>, // each entry: all perms in the set must be present
        val points: Int,
    )

    private val rules = listOf(
        Rule(
            id = "sms_exfiltration",
            description = "Mengakses & mengirim SMS serta kontak sekaligus — pola umum trojan pencuri OTP",
            requiredAny = listOf(
                setOf(
                    "android.permission.READ_SMS",
                    "android.permission.SEND_SMS",
                    "android.permission.READ_CONTACTS",
                )
            ),
            points = 40,
        ),
        Rule(
            id = "overlay_admin",
            description = "Overlay di atas aplikasi lain + hak admin perangkat — pola umum ransomware/phishing overlay",
            requiredAny = listOf(
                setOf(
                    "android.permission.SYSTEM_ALERT_WINDOW",
                    "android.permission.BIND_DEVICE_ADMIN",
                )
            ),
            points = 35,
        ),
        Rule(
            id = "accessibility_overlay",
            description = "Accessibility Service + overlay — pola umum keylogger/clickjacking",
            requiredAny = listOf(
                setOf(
                    "android.permission.BIND_ACCESSIBILITY_SERVICE",
                    "android.permission.SYSTEM_ALERT_WINDOW",
                )
            ),
            points = 30,
        ),
        Rule(
            id = "silent_installer",
            description = "Dapat memasang paket lain + query semua app terinstal — pola umum dropper",
            requiredAny = listOf(
                setOf(
                    "android.permission.REQUEST_INSTALL_PACKAGES",
                    "android.permission.QUERY_ALL_PACKAGES",
                )
            ),
            points = 20,
        ),
        Rule(
            id = "location_background_exfil",
            description = "Lokasi latar belakang + internet tanpa fitur peta/navigasi yang jelas",
            requiredAny = listOf(
                setOf(
                    "android.permission.ACCESS_BACKGROUND_LOCATION",
                    "android.permission.INTERNET",
                )
            ),
            points = 15,
        ),
        Rule(
            id = "call_log_contacts",
            description = "Mengakses log telepon dan kontak sekaligus",
            requiredAny = listOf(
                setOf(
                    "android.permission.READ_CALL_LOG",
                    "android.permission.READ_CONTACTS",
                )
            ),
            points = 15,
        ),
    )

    data class HeuristicResult(
        val score: Int,
        val severity: Severity,
        val matchedRuleDescriptions: List<String>,
    )

    fun analyze(requestedPermissions: Set<String>): HeuristicResult {
        var score = 0
        val matched = mutableListOf<String>()

        for (rule in rules) {
            val triggered = rule.requiredAny.any { requiredSet ->
                requiredSet.all { it in requestedPermissions }
            }
            if (triggered) {
                score += rule.points
                matched += rule.description
            }
        }

        return HeuristicResult(score, HeuristicScoring.severityFor(score), matched)
    }
}
