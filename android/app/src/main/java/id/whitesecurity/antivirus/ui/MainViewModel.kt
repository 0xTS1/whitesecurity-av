package id.whitesecurity.antivirus.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.whitesecurity.antivirus.WhiteSecurityApp
import id.whitesecurity.antivirus.account.AccountResult
import id.whitesecurity.antivirus.account.AccountUser
import id.whitesecurity.antivirus.data.local.ScanHistoryEntity
import id.whitesecurity.antivirus.data.remote.dto.MeResponse
import id.whitesecurity.antivirus.license.ActivationResult
import id.whitesecurity.antivirus.premium.CheckupResult
import id.whitesecurity.antivirus.premium.ScamRiskApp
import id.whitesecurity.antivirus.premium.ScamShieldAnalyzer
import id.whitesecurity.antivirus.premium.SecurityCheckup
import id.whitesecurity.antivirus.update.AppUpdater
import id.whitesecurity.antivirus.update.UpdateInfo
import id.whitesecurity.antivirus.update.UpdateResult
import id.whitesecurity.antivirus.update.UpdateState
import id.whitesecurity.antivirus.scanner.ScanFinding
import id.whitesecurity.antivirus.scanner.ScanSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class ScanUiState {
    data object Idle : ScanUiState()
    data class Scanning(val current: Int, val total: Int, val label: String) : ScanUiState()
    data class Done(val summary: ScanSummary) : ScanUiState()
    data class Failed(val message: String) : ScanUiState()
}

/** Shared loading/result wrapper for the premium tools. */
sealed class ToolState<out T> {
    data object Idle : ToolState<Nothing>()
    data object Running : ToolState<Nothing>()
    data class Done<T>(val result: T) : ToolState<T>()
    data class Failed(val message: String) : ToolState<Nothing>()
}

sealed class LicenseUiState {
    data object Idle : LicenseUiState()
    data object Loading : LicenseUiState()
    data object Success : LicenseUiState()
    data class Error(val message: String) : LicenseUiState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as WhiteSecurityApp

    val isPremium: StateFlow<Boolean> = app.licenseManager.isPremium

    private val _scanState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanState: StateFlow<ScanUiState> = _scanState.asStateFlow()

    private val _fileScanResult = MutableStateFlow<ScanFinding?>(null)
    val fileScanResult: StateFlow<ScanFinding?> = _fileScanResult.asStateFlow()

    private val _licenseState = MutableStateFlow<LicenseUiState>(LicenseUiState.Idle)
    val licenseState: StateFlow<LicenseUiState> = _licenseState.asStateFlow()

    private val _deepScan = MutableStateFlow(false)
    /** Premium: include system/preinstalled apps in the installed-apps scan. */
    val deepScan: StateFlow<Boolean> = _deepScan.asStateFlow()

    private val _scamShield = MutableStateFlow<ToolState<List<ScamRiskApp>>>(ToolState.Idle)
    val scamShield: StateFlow<ToolState<List<ScamRiskApp>>> = _scamShield.asStateFlow()

    private val _checkup = MutableStateFlow<ToolState<CheckupResult>>(ToolState.Idle)
    val checkup: StateFlow<ToolState<CheckupResult>> = _checkup.asStateFlow()

    val updateAvailable: StateFlow<UpdateInfo?> = UpdateState.available
    val updateBusy: StateFlow<Boolean> = UpdateState.busy

    private val _updateMessage = MutableStateFlow<String?>(null)
    val updateMessage: StateFlow<String?> = _updateMessage.asStateFlow()

    /** Set when the user must allow "install unknown apps" for us; the UI opens Settings. */
    private val _needsInstallPermission = MutableStateFlow(false)
    val needsInstallPermission: StateFlow<Boolean> = _needsInstallPermission.asStateFlow()

    private var lastUpdateCheckMs = 0L

    // --- Account ---
    val accountUser: StateFlow<AccountUser?> = app.accountManager.user

    private val _authState = MutableStateFlow<ToolState<Unit>>(ToolState.Idle)
    val authState: StateFlow<ToolState<Unit>> = _authState.asStateFlow()

    private val _me = MutableStateFlow<ToolState<MeResponse>>(ToolState.Idle)
    val me: StateFlow<ToolState<MeResponse>> = _me.asStateFlow()

    /** License key currently being activated from the account screen, for its spinner. */
    private val _activatingKey = MutableStateFlow<String?>(null)
    val activatingKey: StateFlow<String?> = _activatingKey.asStateFlow()

    private val _accountMessage = MutableStateFlow<String?>(null)
    val accountMessage: StateFlow<String?> = _accountMessage.asStateFlow()

    val licenseKey: String? get() = app.licenseManager.cachedLicenseKey()
    val licenseExpiresAt: String? get() = app.licenseManager.cachedExpiresAt()

    val scanHistory: StateFlow<List<ScanHistoryEntity>> = app.database.scanHistoryDao()
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setDeepScan(enabled: Boolean) {
        _deepScan.value = enabled && app.licenseManager.isDeepScanAllowed()
    }

    fun scanInstalledApps() {
        if (_scanState.value is ScanUiState.Scanning) return
        val includeSystemApps = _deepScan.value && app.licenseManager.isDeepScanAllowed()
        viewModelScope.launch {
            _scanState.value = ScanUiState.Scanning(0, 0, "")
            try {
                val summary = app.scanEngine.scanInstalledApps(includeSystemApps) { current, total, label ->
                    _scanState.value = ScanUiState.Scanning(current, total, label)
                }
                _scanState.value = ScanUiState.Done(summary)
            } catch (e: Exception) {
                _scanState.value = ScanUiState.Failed(e.message ?: "scan_failed")
            }
        }
    }

    fun scanPickedFile(uri: Uri) {
        viewModelScope.launch {
            _fileScanResult.value = null
            val scanned = app.packageScanner.scanPickedApk(uri) ?: return@launch
            _fileScanResult.value = app.scanEngine.scanSingleFile(scanned)
        }
    }

    fun clearFileScanResult() {
        _fileScanResult.value = null
    }

    fun activateLicense(licenseKey: String) {
        viewModelScope.launch {
            _licenseState.value = LicenseUiState.Loading
            _licenseState.value = when (val result = app.licenseManager.activate(licenseKey.trim())) {
                is ActivationResult.Success -> LicenseUiState.Success
                is ActivationResult.Rejected -> LicenseUiState.Error(result.reason)
                is ActivationResult.NetworkError -> LicenseUiState.Error(result.message)
            }
        }
    }

    fun resetLicenseState() {
        _licenseState.value = LicenseUiState.Idle
    }

    fun login(email: String, password: String) = authenticate { app.accountManager.login(email, password) }

    fun register(name: String, email: String, password: String) =
        authenticate { app.accountManager.register(name, email, password) }

    private fun authenticate(block: suspend () -> AccountResult<AccountUser>) {
        if (_authState.value is ToolState.Running) return
        viewModelScope.launch {
            _authState.value = ToolState.Running
            _authState.value = when (val r = block()) {
                is AccountResult.Ok -> { loadAccount(); ToolState.Done(Unit) }
                is AccountResult.Error -> ToolState.Failed(r.message)
                AccountResult.SignedOut -> ToolState.Failed("Sesi berakhir, silakan masuk lagi.")
            }
        }
    }

    fun resetAuthState() {
        _authState.value = ToolState.Idle
    }

    fun loadAccount() {
        if (_me.value is ToolState.Running) return
        viewModelScope.launch {
            _me.value = ToolState.Running
            _me.value = when (val r = app.accountManager.fetchMe()) {
                is AccountResult.Ok -> ToolState.Done(r.value)
                is AccountResult.Error -> ToolState.Failed(r.message)
                AccountResult.SignedOut -> ToolState.Idle
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            app.accountManager.logout()
            _me.value = ToolState.Idle
            _authState.value = ToolState.Idle
        }
    }

    /** One-tap Premium: binds one of the account's licenses to this phone. */
    fun activateFromAccount(licenseKey: String) {
        if (_activatingKey.value != null) return
        viewModelScope.launch {
            _activatingKey.value = licenseKey
            _accountMessage.value = when (val r = app.licenseManager.activate(licenseKey)) {
                is ActivationResult.Success -> "Premium aktif di HP ini ✓"
                is ActivationResult.Rejected -> when (r.reason) {
                    "device_limit_reached" -> "Batas perangkat lisensi ini sudah penuh."
                    "expired" -> "Lisensi sudah kedaluwarsa."
                    "revoked" -> "Lisensi sudah dicabut."
                    else -> "Aktivasi ditolak (${r.reason})."
                }
                is ActivationResult.NetworkError -> "Tidak dapat terhubung ke server."
            }
            _activatingKey.value = null
            loadAccount()
        }
    }

    fun clearAccountMessage() {
        _accountMessage.value = null
    }

    fun checkForUpdate() {
        val now = System.currentTimeMillis()
        if (now - lastUpdateCheckMs < 10 * 60_000) return
        lastUpdateCheckMs = now
        viewModelScope.launch { UpdateState.available.value = AppUpdater.checkForUpdate() }
    }

    fun installUpdate() {
        val info = UpdateState.available.value ?: return
        if (UpdateState.busy.value) return
        viewModelScope.launch {
            UpdateState.busy.value = true
            _updateMessage.value = null
            UpdateState.lastError = null
            when (val result = AppUpdater.downloadAndInstall(app, info)) {
                is UpdateResult.NeedsInstallPermission -> _needsInstallPermission.value = true
                is UpdateResult.Failed -> _updateMessage.value = result.reason
                else -> Unit
            }
            UpdateState.busy.value = false
        }
    }

    fun onInstallPermissionHandled() {
        _needsInstallPermission.value = false
    }

    fun runScamShield() {
        if (!app.licenseManager.isScamShieldAllowed() || _scamShield.value is ToolState.Running) return
        viewModelScope.launch {
            _scamShield.value = ToolState.Running
            _scamShield.value = runCatching { ScamShieldAnalyzer(app).analyze() }
                .fold({ ToolState.Done(it) }, { ToolState.Failed(it.message ?: "gagal") })
        }
    }

    fun runCheckup() {
        if (!app.licenseManager.isSecurityCheckupAllowed() || _checkup.value is ToolState.Running) return
        viewModelScope.launch {
            _checkup.value = ToolState.Running
            _checkup.value = runCatching { SecurityCheckup.run(app) }
                .fold({ ToolState.Done(it) }, { ToolState.Failed(it.message ?: "gagal") })
        }
    }
}
