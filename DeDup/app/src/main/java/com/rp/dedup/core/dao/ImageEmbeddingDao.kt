package com.rp.dedup.core.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rp.dedup.core.search.ImageEmbeddingEntity

@Dao
interface ImageEmbeddingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(embeddings: List<ImageEmbeddingEntity>)

    @Query("SELECT * FROM image_embeddings")
    suspend fun getAll(): List<ImageEmbeddingEntity>

    @Query("SELECT COUNT(*) FROM image_embeddings")
    suspend fun count(): Int

    @Query("SELECT uri FROM image_embeddings")
    suspend fun getAllUris(): List<String>

    /** Purges rows whose URI is no longer present in MediaStore. */
    @Query("DELETE FROM image_embeddings WHERE uri NOT IN (:validUris)")
    suspend fun deleteStale(validUris: List<String>)

    @Query("DELETE FROM image_embeddings")
    suspend fun deleteAll()
}
