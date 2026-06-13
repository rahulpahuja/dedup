package com.rp.dedup.core.search

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "image_embeddings")
data class ImageEmbeddingEntity(
    @PrimaryKey val uri: String,
    val displayName: String,
    val bucketName: String,
    val description: String,   // text that was embedded — useful for debugging
    val embedding: FloatArray, // stored as BLOB via FloatArrayConverter
    val indexedAt: Long = System.currentTimeMillis()
) {
    // FloatArray breaks structural equality — define manually so Room's conflict
    // resolution (REPLACE) and set deduplication work on uri alone.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImageEmbeddingEntity) return false
        return uri == other.uri
    }
    override fun hashCode(): Int = uri.hashCode()
}
