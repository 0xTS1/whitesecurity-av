package id.whitesecurity.antivirus.data.remote

import id.whitesecurity.antivirus.data.remote.dto.AuthRequest
import id.whitesecurity.antivirus.data.remote.dto.AuthResponse
import id.whitesecurity.antivirus.data.remote.dto.BulkCheckRequest
import id.whitesecurity.antivirus.data.remote.dto.BulkCheckResponse
import id.whitesecurity.antivirus.data.remote.dto.LicenseRequest
import id.whitesecurity.antivirus.data.remote.dto.LicenseResponse
import id.whitesecurity.antivirus.data.remote.dto.MalwareCheckResponse
import id.whitesecurity.antivirus.data.remote.dto.MalwareUpdatesResponse
import id.whitesecurity.antivirus.data.remote.dto.MeResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @POST("api/v1/license/activate")
    suspend fun activateLicense(@Body body: LicenseRequest): Response<LicenseResponse>

    @POST("api/v1/license/verify")
    suspend fun verifyLicense(@Body body: LicenseRequest): Response<LicenseResponse>

    @GET("api/v1/malware/check/{sha256}")
    suspend fun checkHash(@Path("sha256") sha256: String): Response<MalwareCheckResponse>

    @POST("api/v1/malware/bulk-check")
    suspend fun bulkCheck(@Body body: BulkCheckRequest): Response<BulkCheckResponse>

    @GET("api/v1/malware/updates")
    suspend fun fetchMalwareUpdates(
        @Query("sinceId") sinceId: Long,
        @Query("limit") limit: Int = 500,
    ): Response<MalwareUpdatesResponse>

    @POST("api/v1/account/register")
    suspend fun register(@Body body: AuthRequest): Response<AuthResponse>

    @POST("api/v1/account/login")
    suspend fun login(@Body body: AuthRequest): Response<AuthResponse>

    @GET("api/v1/account/me")
    suspend fun me(
        @Header("Authorization") bearer: String,
        @Query("deviceId") deviceId: String,
    ): Response<MeResponse>

    @POST("api/v1/account/logout")
    suspend fun logout(@Header("Authorization") bearer: String): Response<Unit>
}
