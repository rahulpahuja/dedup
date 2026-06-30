package com.rp.dedup.core.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "trash_items")
data class TrashItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val originalUri: String,
    val originalPath: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val mediaType: String, // "IMAGE" | "VIDEO" | "FILE"
    val trashedAtMs: Long,
    val expiresAtMs: Long,
    val trashFileName: String // filename inside filesDir/trash/
)
