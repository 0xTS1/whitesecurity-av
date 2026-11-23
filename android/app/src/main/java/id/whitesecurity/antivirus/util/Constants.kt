package id.whitesecurity.antivirus.util

object Constants {
    const val NOTIF_CHANNEL_SCAN = "scan_results"
    const val PREFS_LICENSE = "whitesecurity_license_prefs"
}

enum class Severity(val weight: Int) {
    LOW(1), MEDIUM(2), HIGH(3), CRITICAL(4);

    companion object {
        fun fromString(value: String?): Severity =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MEDIUM
    }
}
