package com.rp.dedup.core.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rp.dedup.core.common.Constants
import com.rp.dedup.core.data.ScannedImage
import com.rp.dedup.core.dao.ScannedImageDao
import com.rp.dedup.core.data.ScanHistory
import com.rp.dedup.core.dao.ScanHistoryDao
import com.rp.dedup.core.security.KeyManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(entities = [ScannedImage::class, ScanHistory::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scannedImageDao(): ScannedImageDao
    abstract fun scanHistoryDao(): ScanHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `scan_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `scanType` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `totalScanned` INTEGER NOT NULL,
                        `duplicateGroups` INTEGER NOT NULL,
                        `totalDuplicates` INTEGER NOT NULL,
                        `reclaimableBytes` INTEGER NOT NULL,
                        `status` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // Initialize SQLCipher libraries
                try {
                    System.loadLibrary("sqlcipher")
                } catch (_: Exception) {
                    // Native library already loaded or unavailable
                }
                
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    Constants.DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(dropAllTables = true)

                val instance = try {
                    val key = KeyManager.getOrCreateDatabaseKey(context)
                    builder.openHelperFactory(SupportOpenHelperFactory(key))
                    val db = builder.build()
                    // Force opening the database to catch encryption issues early
                    db.openHelper.writableDatabase
                    db
                } catch (e: Exception) {
                    Log.e("AppDatabase", "Database initialization failed, attempting recovery", e)
                    // This happens if KeyManager failed, the database was previously unencrypted, 
                    // has a different password, or is corrupted.
                    context.applicationContext.deleteDatabase(Constants.DATABASE_NAME)
                    
                    // Try one more time with a fresh key (KeyManager recovery should have happened)
                    val key = KeyManager.getOrCreateDatabaseKey(context)
                    builder.openHelperFactory(SupportOpenHelperFactory(key))
                    builder.build()
                }

                INSTANCE = instance
                instance
            }
        }
    }
}
