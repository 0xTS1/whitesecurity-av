package id.whitesecurity.antivirus.license

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import id.whitesecurity.antivirus.data.repository.LicenseOutcome
import id.whitesecurity.antivirus.data.repository.LicenseRepository
import id.whitesecurity.antivirus.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

sealed class ActivationResult {
    object Success : ActivationResult()
    data class Rejected(val reason: String) : ActivationResult()
    data class NetworkError(val message: String) : ActivationResult()
}

/**
 * Local cache of license state + feature gating. The server is always the
 * source of truth (see LicenseRepository) — this just avoids hitting the
 * network on every screen open and lets premium features keep working
 * briefly offline using the last verified state.
 */
class LicenseManager(
    context: Context,
    private val repository: LicenseRepository,
) {
    private val prefs = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            Constants.PREFS_LICENSE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    val deviceId: String by lazy {
        prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }
    }

    private val _isPremium = MutableStateFlow(readCachedValid())
    val isPremium: StateFlow<Boolean> = _isPremium

    private fun readCachedValid(): Boolean {
        val valid = prefs.getBoolean(KEY_VALID, false)
        val expiresAt = prefs.getString(KEY_EXPIRES_AT, null)
        if (valid && expiresAt != null) {
            val expiryMs = id.whitesecurity.antivirus.util.ServerDates.parse(expiresAt)?.toEpochMilli()
            if (expiryMs != null && expiryMs < System.currentTimeMillis()) return false
        }
        return valid
    }

    fun cachedLicenseKey(): String? = prefs.getString(KEY_LICENSE_KEY, null)

    suspend fun activate(licenseKey: String): ActivationResult {
        return when (val outcome = repository.activate(licenseKey, deviceId)) {
            is LicenseOutcome.Success -> {
                persist(licenseKey, outcome.response.plan, outcome.response.expiresAt, valid = true)
                ActivationResult.Success
            }
            is LicenseOutcome.Rejected -> {
                persist(licenseKey, null, null, valid = false)
                ActivationResult.Rejected(outcome.reason)
            }
            is LicenseOutcome.NetworkError -> ActivationResult.NetworkError(outcome.message)
        }
    }

    /** Re-checks the currently stored license with the server; no-op if none stored. */
    suspend fun refresh() {
        val key = cachedLicenseKey() ?: return
        when (val outcome = repository.verify(key, deviceId)) {
            is LicenseOutcome.Success ->
                persist(key, outcome.response.plan, outcome.response.expiresAt, valid = true)
            is LicenseOutcome.Rejected ->
                persist(key, null, null, valid = false)
            is LicenseOutcome.NetworkError -> Unit // keep last-known-good state offline
        }
    }

    private fun persist(licenseKey: String, plan: String?, expiresAt: String?, valid: Boolean) {
        prefs.edit()
            .putString(KEY_LICENSE_KEY, licenseKey)
            .putString(KEY_PLAN, plan)
            .putString(KEY_EXPIRES_AT, expiresAt)
            .putBoolean(KEY_VALID, valid)
            .apply()
        _isPremium.value = valid
    }

    fun cachedExpiresAt(): String? = prefs.getString(KEY_EXPIRES_AT, null)

    // --- Feature gates ---
    // Core protection is free for everyone: real-time install monitoring,
    // cloud hash lookup + signature sync, and unlimited scan history.
    fun isRealtimeMonitoringAllowed(): Boolean = true
    fun isCloudHashLookupAllowed(): Boolean = true
    fun scanHistoryLimit(): Int = Int.MAX_VALUE

    // Premium: deeper analysis on top of the free core.
    fun isScamShieldAllowed(): Boolean = _isPremium.value
    fun isSecurityCheckupAllowed(): Boolean = _isPremium.value
    fun isDeepScanAllowed(): Boolean = _isPremium.value

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_LICENSE_KEY = "license_key"
        private const val KEY_PLAN = "plan"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_VALID = "valid"
    }
}
