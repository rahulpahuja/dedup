package com.rp.dedup.core.model

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scanned_videos")
data class ScannedVideoEntity(
    @PrimaryKey val uri: String,
    val name: String,
    val sizeInBytes: Long,
    val durationMs: Long,
    val mimeType: String,
    val frameHashes: List<Long> = emptyList(),
    val path: String? = null,
    val groupKey: String = ""
) {
    fun toScannedVideo() = ScannedVideo(
        uri = Uri.parse(uri),
        name = name,
        sizeInBytes = sizeInBytes,
        durationMs = durationMs,
        mimeType = mimeType,
        frameHashes = frameHashes,
        path = path
    )
}

fun ScannedVideo.toEntity(groupKey: String = "") = ScannedVideoEntity(
    uri = uri.toString(),
    name = name,
    sizeInBytes = sizeInBytes,
    durationMs = durationMs,
    mimeType = mimeType,
    frameHashes = frameHashes,
    path = path,
    groupKey = groupKey
)
