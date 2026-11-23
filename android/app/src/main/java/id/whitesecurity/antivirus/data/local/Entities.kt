package id.whitesecurity.antivirus.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "malware_signatures")
data class MalwareSignatureEntity(
    @PrimaryKey val sha256: String,
    val threatName: String,
    val severity: String,
    val source: String?,
)

@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scannedAtEpochMs: Long,
    val totalScanned: Int,
    val threatCount: Int,
)

/**
 * Trust-on-first-use baseline: the signing certificate fingerprint we saw
 * the first time we scanned this package. A later scan whose fingerprint
 * doesn't match is a strong signal the app was repackaged/trojaned — see
 * TrustSignalAnalyzer.
 */
@Entity(tableName = "trusted_signers")
data class TrustedSignerEntity(
    @PrimaryKey val packageName: String,
    val sha256Fingerprint: String,
)
