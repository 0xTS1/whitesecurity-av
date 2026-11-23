package id.whitesecurity.antivirus.account

import android.content.Context
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import id.whitesecurity.antivirus.data.remote.ApiService
import id.whitesecurity.antivirus.data.remote.dto.AuthRequest
import id.whitesecurity.antivirus.data.remote.dto.AuthResponse
import id.whitesecurity.antivirus.data.remote.dto.MeResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import retrofit2.Response

data class AccountUser(val email: String, val name: String?)

sealed class AccountResult<out T> {
    data class Ok<T>(val value: T) : AccountResult<T>()
    data class Error(val message: String) : AccountResult<Nothing>()
    /** Token expired/revoked (e.g. admin "Paksa keluar") — the user is now signed out locally too. */
    data object SignedOut : AccountResult<Nothing>()
}

/**
 * WhiteSecurity account (same account as whitesecurity.id/antivirus). Holds
 * the app bearer token in EncryptedSharedPreferences; the password is only
 * ever sent once, at login/register, and never stored.
 */
class AccountManager(
    context: Context,
    private val api: ApiService,
    private val deviceId: () -> String,
) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    private val gson = Gson()

    private val _user = MutableStateFlow(readUser())
    val user: StateFlow<AccountUser?> = _user

    private fun readUser(): AccountUser? {
        if (prefs.getString(KEY_TOKEN, null) == null) return null
        return AccountUser(prefs.getString(KEY_EMAIL, "") ?: "", prefs.getString(KEY_NAME, null))
    }

    private val bearer: String? get() = prefs.getString(KEY_TOKEN, null)?.let { "Bearer $it" }

    private val deviceLabel: String
        get() = listOf(Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, Build.MODEL)
            .distinct().joinToString(" ").take(100)

    suspend fun login(email: String, password: String): AccountResult<AccountUser> =
        auth { api.login(AuthRequest(email.trim(), password, null, deviceId(), deviceLabel)) }

    suspend fun register(name: String, email: String, password: String): AccountResult<AccountUser> =
        auth { api.register(AuthRequest(email.trim(), password, name.trim().ifBlank { null }, deviceId(), deviceLabel)) }

    private suspend fun auth(call: suspend () -> Response<AuthResponse>): AccountResult<AccountUser> = try {
        val res = call()
        val body = if (res.isSuccessful) res.body() else parseError(res)
        val token = body?.token
        val u = body?.user
        if (res.isSuccessful && token != null && u != null) {
            prefs.edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_EMAIL, u.email)
                .putString(KEY_NAME, u.name)
                .apply()
            AccountUser(u.email, u.name).also { _user.value = it }.let { AccountResult.Ok(it) }
        } else {
            AccountResult.Error(body?.message ?: errorText(res.code()))
        }
    } catch (e: Exception) {
        AccountResult.Error(NETWORK_ERROR)
    }

    suspend fun fetchMe(): AccountResult<MeResponse> {
        val auth = bearer ?: return AccountResult.SignedOut
        return try {
            val res = api.me(auth, deviceId())
            when {
                res.isSuccessful && res.body() != null -> AccountResult.Ok(res.body()!!)
                res.code() == 401 -> { clearLocal(); AccountResult.SignedOut }
                else -> AccountResult.Error(errorText(res.code()))
            }
        } catch (e: Exception) {
            AccountResult.Error(NETWORK_ERROR)
        }
    }

    suspend fun logout() {
        bearer?.let { runCatching { api.logout(it) } }
        clearLocal()
    }

    private fun clearLocal() {
        prefs.edit().clear().apply()
        _user.value = null
    }

    private fun parseError(res: Response<*>): AuthResponse? =
        runCatching { gson.fromJson(res.errorBody()?.string(), AuthResponse::class.java) }.getOrNull()

    private fun errorText(code: Int) = when (code) {
        429 -> "Terlalu banyak percobaan. Coba lagi dalam satu menit."
        in 500..599 -> "Server sedang bermasalah. Coba lagi nanti."
        else -> "Permintaan gagal (HTTP $code)."
    }

    companion object {
        private const val PREFS = "whitesecurity_account_prefs"
        private const val KEY_TOKEN = "token"
        private const val KEY_EMAIL = "email"
        private const val KEY_NAME = "name"
        private const val NETWORK_ERROR = "Tidak dapat terhubung ke server. Periksa koneksi internet."
    }
}
