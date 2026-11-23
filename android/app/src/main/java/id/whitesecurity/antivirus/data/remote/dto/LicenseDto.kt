package id.whitesecurity.antivirus.data.remote.dto

data class LicenseRequest(
    val licenseKey: String,
    val deviceId: String,
)

data class LicenseResponse(
    val valid: Boolean,
    val plan: String? = null,
    val expiresAt: String? = null,
    val maxDevices: Int? = null,
    val reason: String? = null,
)
