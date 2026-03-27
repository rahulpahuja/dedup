package com.rp.dedup.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.rp.dedup.core.image.ScannedImage
import com.rp.dedup.core.image.ScannedImageDao

@Database(entities = [ScannedImage::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scannedImageDao(): ScannedImageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dedup_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}