package id.whitesecurity.antivirus.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MalwareSignatureEntity::class, ScanHistoryEntity::class, TrustedSignerEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun malwareSignatureDao(): MalwareSignatureDao
    abstract fun scanHistoryDao(): ScanHistoryDao
    abstract fun trustedSignerDao(): TrustedSignerDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "whitesecurity.db",
                )
                    // Pre-release app, no exported schema to write a real
                    // migration against — simplest correct choice for the
                    // v1 -> v2 (trusted_signers table) bump.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
