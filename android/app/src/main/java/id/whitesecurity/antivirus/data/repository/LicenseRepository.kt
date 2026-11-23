package id.whitesecurity.antivirus.data.repository

import com.google.gson.Gson
import id.whitesecurity.antivirus.data.remote.ApiService
import id.whitesecurity.antivirus.data.remote.dto.LicenseRequest
import id.whitesecurity.antivirus.data.remote.dto.LicenseResponse
import retrofit2.Response

sealed class LicenseOutcome {
    data class Success(val response: LicenseResponse) : LicenseOutcome()
    data class Rejected(val reason: String) : LicenseOutcome()
    data class NetworkError(val message: String) : LicenseOutcome()
}

class LicenseRepository(private val api: ApiService) {

    private val gson = Gson()

    suspend fun activate(licenseKey: String, deviceId: String): LicenseOutcome =
        call { api.activateLicense(LicenseRequest(licenseKey, deviceId)) }

    suspend fun verify(licenseKey: String, deviceId: String): LicenseOutcome =
        call { api.verifyLicense(LicenseRequest(licenseKey, deviceId)) }

    private suspend fun call(block: suspend () -> Response<LicenseResponse>): LicenseOutcome {
        return try {
            val response = block()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.valid) LicenseOutcome.Success(body)
                else LicenseOutcome.Rejected(body?.reason ?: "unknown")
            } else {
                val errorBody = response.errorBody()?.string()
                val parsed = errorBody?.let {
                    runCatching { gson.fromJson(it, LicenseResponse::class.java) }.getOrNull()
                }
                LicenseOutcome.Rejected(parsed?.reason ?: "http_${response.code()}")
            }
        } catch (e: Exception) {
            LicenseOutcome.NetworkError(e.message ?: "network_error")
        }
    }
}
