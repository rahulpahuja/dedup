package com.rp.dedup.core.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rp.dedup.core.common.Constants
import com.rp.dedup.core.dao.ImageEmbeddingDao
import com.rp.dedup.core.dao.ScannedImageDao
import com.rp.dedup.core.dao.ScannedVideoDao
import com.rp.dedup.core.dao.ScanHistoryDao
import com.rp.dedup.core.model.ScannedImage
import com.rp.dedup.core.model.ScannedVideoEntity
import com.rp.dedup.core.model.ScanHistory
import com.rp.dedup.core.search.FloatArrayConverter
import com.rp.dedup.core.search.ImageEmbeddingEntity
import com.rp.dedup.core.search.LongListConverter
import com.rp.dedup.core.security.KeyManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

@Database(
    entities = [ScannedImage::class, ScanHistory::class, ImageEmbeddingEntity::class, ScannedVideoEntity::class],
    version = 6,
    exportSchema = false
)
@TypeConverters(FloatArrayConverter::class, LongListConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scannedImageDao(): ScannedImageDao
    abstract fun scannedVideoDao(): ScannedVideoDao
    abstract fun scanHistoryDao(): ScanHistoryDao
    abstract fun imageEmbeddingDao(): ImageEmbeddingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        private val MIGRATION_1_2_SQL =    """
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

        private val MIGRATION_2_3_SQL ="ALTER TABLE scanned_images ADD COLUMN exactHash INTEGER NOT NULL DEFAULT -1"
        private val MIGRATION_3_4_SQL ="ALTER TABLE scanned_images ADD COLUMN groupKey TEXT NOT NULL DEFAULT ''"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(MIGRATION_1_2_SQL)
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(MIGRATION_2_3_SQL)
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(MIGRATION_3_4_SQL)
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `image_embeddings` (
                        `uri`         TEXT    NOT NULL,
                        `displayName` TEXT    NOT NULL,
                        `bucketName`  TEXT    NOT NULL,
                        `description` TEXT    NOT NULL,
                        `embedding`   BLOB    NOT NULL,
                        `indexedAt`   INTEGER NOT NULL,
                        PRIMARY KEY(`uri`)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `scanned_videos` (
                        `uri`          TEXT    NOT NULL,
                        `name`         TEXT    NOT NULL,
                        `sizeInBytes`  INTEGER NOT NULL,
                        `durationMs`   INTEGER NOT NULL,
                        `mimeType`     TEXT    NOT NULL,
                        `frameHashes`  TEXT    NOT NULL DEFAULT '',
                        `path`         TEXT,
                        `groupKey`     TEXT    NOT NULL DEFAULT '',
                        PRIMARY KEY(`uri`)
                    )
                    """.trimIndent()
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
            return try {
                createBuilder(context, dbName).build().also { db ->
                    // Force opening the database to catch encryption issues early
                    db.openHelper.writableDatabase
                }
            } catch (e: Exception) {
                Log.e("AppDatabase", "Database initialization failed, attempting file-level recovery", e)

                // Only attempt file-level recovery: delete corrupted DB files and retry
                // with the same key. If the failure was in KeyManager itself (KeyStore
                // unavailable / corrupted master key), this second call will also throw
                // and the exception propagates to the caller — do NOT silently reset keys.
                try {
                    context.applicationContext.deleteDatabase(dbName)
                    val dbFile = context.getDatabasePath(dbName)
                    File("${dbFile.path}-wal").delete()
                    File("${dbFile.path}-shm").delete()
                    File("${dbFile.path}-journal").delete()
                } catch (ex: Exception) {
                    Log.e("AppDatabase", "Error deleting database files during recovery", ex)
                }

                // Fresh builder — the old one was already built and cannot be reused.
                createBuilder(context, dbName).build()
            }
        }

        /**
         * Creates a fresh, unbuilt RoomDatabase.Builder with encryption and migrations.
         * Must be called once per build attempt — Room forbids calling build() twice.
         */
        private fun createBuilder(context: Context, dbName: String): RoomDatabase.Builder<AppDatabase> {
            val key = KeyManager.getOrCreateDatabaseKey(context)
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                dbName
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .apply {
                    if (com.rp.dedup.BuildConfig.DEBUG) {
                        fallbackToDestructiveMigration(dropAllTables = true)
                    }
                }
                .openHelperFactory(SupportOpenHelperFactory(key))
        }
    }
}
