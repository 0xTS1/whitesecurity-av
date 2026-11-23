package id.whitesecurity.antivirus.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MalwareSignatureDao {
    @Query("SELECT * FROM malware_signatures WHERE sha256 = :sha256")
    suspend fun findBySha256(sha256: String): MalwareSignatureEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(signatures: List<MalwareSignatureEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(signature: MalwareSignatureEntity)

    @Query("SELECT COUNT(*) FROM malware_signatures")
    suspend fun count(): Int
}

@Dao
interface ScanHistoryDao {
    @Insert
    suspend fun insert(entry: ScanHistoryEntity): Long

    @Query("SELECT * FROM scan_history ORDER BY scannedAtEpochMs DESC")
    fun observeAll(): Flow<List<ScanHistoryEntity>>

    @Query("SELECT * FROM scan_history ORDER BY scannedAtEpochMs DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ScanHistoryEntity>

    @Query(
        "DELETE FROM scan_history WHERE id NOT IN " +
        "(SELECT id FROM scan_history ORDER BY scannedAtEpochMs DESC LIMIT :keep)"
    )
    suspend fun trimTo(keep: Int)
}

@Dao
interface TrustedSignerDao {
    @Query("SELECT * FROM trusted_signers WHERE packageName = :packageName")
    suspend fun find(packageName: String): TrustedSignerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TrustedSignerEntity)
}
