package id.whitesecurity.antivirus.scanner

import id.whitesecurity.antivirus.data.local.ScanHistoryDao
import id.whitesecurity.antivirus.data.local.ScanHistoryEntity
import id.whitesecurity.antivirus.data.local.TrustedSignerDao
import id.whitesecurity.antivirus.data.local.TrustedSignerEntity
import id.whitesecurity.antivirus.data.repository.MalwareRepository
import id.whitesecurity.antivirus.license.LicenseManager
import id.whitesecurity.antivirus.util.Severity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScanEngine(
    private val packageScanner: PackageScanner,
    private val malwareRepository: MalwareRepository,
    private val scanHistoryDao: ScanHistoryDao,
    private val trustedSignerDao: TrustedSignerDao,
    private val licenseManager: LicenseManager,
    private val spywareDetector: SpywareDetector,
) {

    suspend fun scanInstalledApps(
        includeSystemApps: Boolean = false,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> },
    ): ScanSummary = withContext(Dispatchers.Default) {
        val apps = packageScanner.listInstalledApps(includeSystemApps, onProgress)
        val findings = evaluate(apps)
        val summary = ScanSummary(
            totalScanned = apps.size,
            findings = findings,
            scannedAtEpochMs = System.currentTimeMillis(),
        )
        recordHistory(summary)
        summary
    }

    suspend fun scanSingleFile(app: ScannedApp): ScanFinding {
        return evaluate(listOf(app)).first()
    }

    private suspend fun evaluate(apps: List<ScannedApp>): List<ScanFinding> {
        // 1. Local DB pass (always available, no network).
        val localHits = apps.associate { app ->
            app.packageName to app.sha256?.let { sha -> malwareRepository.checkLocal(sha) }
        }

        // 2. Remote DB pass — only for apps not already flagged locally, and
        //    only when the user has premium cloud lookup enabled.
        val remoteHits = if (licenseManager.isCloudHashLookupAllowed()) {
            val pending = apps.filter { localHits[it.packageName] == null && it.sha256 != null }
                .mapNotNull { it.sha256 }
            malwareRepository.checkRemoteBulk(pending)
        } else {
            emptyMap()
        }

        return apps.map { app ->
            val local = app.sha256?.let { localHits[app.packageName] }
            val remote = app.sha256?.let { remoteHits[it] }
            val permissionHeuristic = PermissionRiskAnalyzer.analyze(app.permissions + app.servicePermissions)
            val trustSignal = resolveTrustSignal(app)

            val score = permissionHeuristic.score + trustSignal.score
            val reasons = permissionHeuristic.matchedRuleDescriptions + trustSignal.reasons
            val severity = HeuristicScoring.severityFor(score)
            val stalkerware = spywareDetector.matchKnown(app)
            val hiddenSpyware = spywareDetector.hiddenSurveillanceSignals(app)

            when {
                local != null -> ScanFinding(
                    app = app,
                    malicious = true,
                    threatName = local.threatName,
                    severity = Severity.fromString(local.severity),
                    heuristicScore = score,
                    heuristicReasons = reasons,
                    source = FindingSource.KNOWN_MALWARE_LOCAL,
                )
                remote != null && remote.malicious -> ScanFinding(
                    app = app,
                    malicious = true,
                    threatName = remote.threatName,
                    severity = Severity.fromString(remote.severity),
                    heuristicScore = score,
                    heuristicReasons = reasons,
                    source = FindingSource.KNOWN_MALWARE_REMOTE,
                )
                stalkerware != null -> ScanFinding(
                    app = app,
                    malicious = true,
                    threatName = "Stalkerware: ${stalkerware.family}",
                    severity = Severity.CRITICAL,
                    heuristicScore = score,
                    heuristicReasons = listOf(
                        if (stalkerware.byCertificate) "Ditandatangani sertifikat milik ${stalkerware.family}"
                        else "Nama paket cocok dengan ${stalkerware.family}"
                    ) + reasons,
                    source = FindingSource.KNOWN_STALKERWARE,
                )
                hiddenSpyware.isNotEmpty() -> ScanFinding(
                    app = app,
                    malicious = false,
                    threatName = "Dugaan spyware",
                    severity = Severity.HIGH,
                    heuristicScore = score,
                    heuristicReasons = hiddenSpyware + reasons,
                    source = FindingSource.SUSPECTED_SPYWARE,
                )
                score >= HEURISTIC_FLAG_THRESHOLD -> ScanFinding(
                    app = app,
                    malicious = false,
                    threatName = null,
                    severity = severity,
                    heuristicScore = score,
                    heuristicReasons = reasons,
                    source = FindingSource.HEURISTIC,
                )
                else -> ScanFinding(
                    app = app,
                    malicious = false,
                    threatName = null,
                    severity = Severity.LOW,
                    heuristicScore = score,
                    heuristicReasons = emptyList(),
                    source = FindingSource.CLEAN,
                )
            }
        }
    }

    /**
     * Trust-on-first-use: the first time we see a package, we record its
     * current signing fingerprint as the trusted baseline (nothing to
     * compare against yet, so no signal). Every later scan compares against
     * that stored baseline via TrustSignalAnalyzer.
     */
    private suspend fun resolveTrustSignal(app: ScannedApp): TrustSignalAnalyzer.Result {
        val trusted = trustedSignerDao.find(app.packageName)
        if (trusted == null && app.signingFingerprints.isNotEmpty()) {
            trustedSignerDao.upsert(TrustedSignerEntity(app.packageName, app.signingFingerprints.first()))
        }
        return TrustSignalAnalyzer.evaluate(app, trusted?.sha256Fingerprint)
    }

    private suspend fun recordHistory(summary: ScanSummary) {
        scanHistoryDao.insert(
            ScanHistoryEntity(
                scannedAtEpochMs = summary.scannedAtEpochMs,
                totalScanned = summary.totalScanned,
                threatCount = summary.threatCount,
            )
        )
        scanHistoryDao.trimTo(licenseManager.scanHistoryLimit())
    }

    companion object {
        private const val HEURISTIC_FLAG_THRESHOLD = 15
    }
}
