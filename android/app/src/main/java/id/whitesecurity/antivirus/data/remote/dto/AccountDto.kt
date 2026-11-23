package id.whitesecurity.antivirus.data.remote.dto

// Account API (/api/v1/account/*). Fields are nullable/defaulted because Gson
// bypasses Kotlin null-safety when the server omits a field.

data class AuthRequest(
    val email: String,
    val password: String,
    val name: String? = null,
    val deviceId: String,
    val deviceLabel: String,
)

data class UserDto(
    val id: Long = 0,
    val email: String = "",
    val name: String? = null,
    val createdAt: String? = null,
    val isAdmin: Boolean = false,
)

data class AuthResponse(
    val token: String? = null,
    val user: UserDto? = null,
    val error: String? = null,
    val message: String? = null,
)

data class AccountDeviceDto(
    val device: String = "",
    val thisDevice: Boolean = false,
    val activatedAt: String? = null,
    val lastSeenAt: String? = null,
)

data class AccountLicenseDto(
    val licenseKey: String = "",
    val plan: String = "",
    val maxDevices: Int = 1,
    val expiresAt: String? = null,
    val state: String = "",
    val activeOnThisDevice: Boolean = false,
    val devices: List<AccountDeviceDto>? = null,
)

data class AccountOrderDto(
    val orderId: String = "",
    val product: String = "",
    val amount: Long = 0,
    val status: String = "",
    val createdAt: String? = null,
    val paidAt: String? = null,
)

data class MeResponse(
    val user: UserDto? = null,
    val licenses: List<AccountLicenseDto>? = null,
    val orders: List<AccountOrderDto>? = null,
)
