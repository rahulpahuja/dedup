package com.rp.dedup.core.db

import android.content.Context
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
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(entities = [ScannedImage::class, ScanHistory::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scannedImageDao(): ScannedImageDao
    abstract fun scanHistoryDao(): ScanHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // In a real app, this should be securely fetched from Android Keystore
        private const val DB_PASSPHRASE = "secure_dedup_passphrase"

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
                
                val factory = SupportOpenHelperFactory(DB_PASSPHRASE.toByteArray())
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    Constants.DATABASE_NAME
                )
                    .openHelperFactory(factory)
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(dropAllTables = true)

                val instance = try {
                    val db = builder.build()
                    // Force opening the database to catch encryption issues early
                    db.openHelper.writableDatabase
                    db
                } catch (e: Exception) {
                    if (e.message?.contains("file is not a database", ignoreCase = true) == true) {
                        // This happens if the database was previously unencrypted or has a different password
                        context.applicationContext.deleteDatabase(Constants.DATABASE_NAME)
                        builder.build()
                    } else {
                        throw e
                    }
                }

                INSTANCE = instance
                instance
            }
        }
    }
}
