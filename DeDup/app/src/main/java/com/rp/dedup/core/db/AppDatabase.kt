package com.rp.dedup.core.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rp.dedup.core.common.Constants
import com.rp.dedup.core.model.ScannedImage
import com.rp.dedup.core.dao.ScannedImageDao
import com.rp.dedup.core.model.ScanHistory
import com.rp.dedup.core.dao.ScanHistoryDao
import com.rp.dedup.core.security.KeyManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

@Database(entities = [ScannedImage::class, ScanHistory::class], version = 4, exportSchema = false)
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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE scanned_images ADD COLUMN exactHash INTEGER NOT NULL DEFAULT -1"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE scanned_images ADD COLUMN groupKey TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            val dbName = Constants.DATABASE_NAME
            val builder = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                dbName
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration(dropAllTables = true)

            return try {
                val key = KeyManager.getOrCreateDatabaseKey(context)
                builder.openHelperFactory(SupportOpenHelperFactory(key))
                val db = builder.build()
                // Force opening the database to catch encryption issues early
                db.openHelper.writableDatabase
                db
            } catch (e: Exception) {
                Log.e("AppDatabase", "Database initialization failed, attempting recovery", e)
                
                // Close any potential connections and delete the database file
                try {
                    context.applicationContext.deleteDatabase(dbName)
                    // Also delete auxiliary files explicitly
                    val dbFile = context.getDatabasePath(dbName)
                    File("${dbFile.path}-wal").delete()
                    File("${dbFile.path}-shm").delete()
                    File("${dbFile.path}-journal").delete()
                } catch (ex: Exception) {
                    Log.e("AppDatabase", "Error deleting database files", ex)
                }

                // Try one more time with a fresh start (KeyManager recovery should have happened)
                val key = KeyManager.getOrCreateDatabaseKey(context)
                builder.openHelperFactory(SupportOpenHelperFactory(key))
                builder.build()
            }
        }
    }
}
