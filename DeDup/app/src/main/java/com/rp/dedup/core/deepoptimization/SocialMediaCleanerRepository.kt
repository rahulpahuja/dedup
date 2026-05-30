package com.rp.dedup.core.deepoptimization

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.rp.dedup.core.model.SocialApp
import com.rp.dedup.core.model.SocialMediaFile
import com.rp.dedup.core.model.SocialMediaType
import com.rp.dedup.core.utils.ChecksumUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

interface SocialMediaCleanerRepository {
    fun scanMedia(): Flow<SocialMediaFile>
    suspend fun computeChecksum(file: SocialMediaFile): String?
    suspend fun deleteFiles(uris: List<Uri>): Int
}

class SocialMediaCleanerRepositoryImpl(private val context: Context) : SocialMediaCleanerRepository {

    override fun scanMedia(): Flow<SocialMediaFile> = flow {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.MEDIA_TYPE
        )
        val selection = """
            (${MediaStore.Files.FileColumns.DATA} LIKE ?
             OR ${MediaStore.Files.FileColumns.DATA} LIKE ?
             OR ${MediaStore.Files.FileColumns.DATA} LIKE ?
             OR ${MediaStore.Files.FileColumns.DATA} LIKE ?)
            AND ${MediaStore.Files.FileColumns.SIZE} > 0
        """.trimIndent()
        val args = arrayOf(
            "%/WhatsApp/Media/%",
            "%/com.whatsapp/WhatsApp/Media/%",
            "%/Telegram/%",
            "%/org.telegram.messenger/%"
        )

        context.contentResolver.query(
            collection, projection, selection, args,
            "${MediaStore.Files.FileColumns.SIZE} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)

            while (cursor.moveToNext()) {
                val path = cursor.getString(dataCol) ?: continue
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: continue
                val size = cursor.getLong(sizeCol)
                val mediaTypeInt = cursor.getInt(typeCol)
                val uri = ContentUris.withAppendedId(collection, id)

                val app = when {
                    path.contains("/WhatsApp/", ignoreCase = true) ||
                    path.contains("/com.whatsapp/", ignoreCase = true) -> SocialApp.WHATSAPP
                    path.contains("/Telegram/", ignoreCase = true) ||
                    path.contains("/org.telegram.messenger/", ignoreCase = true) -> SocialApp.TELEGRAM
                    else -> continue
                }

                val mediaType = when (mediaTypeInt) {
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> SocialMediaType.IMAGE
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> SocialMediaType.VIDEO
                    MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO -> SocialMediaType.AUDIO
                    else -> {
                        val ext = name.substringAfterLast('.', "").lowercase()
                        when (ext) {
                            "jpg", "jpeg", "png", "gif", "webp", "heic" -> SocialMediaType.IMAGE
                            "mp4", "mkv", "avi", "mov", "3gp" -> SocialMediaType.VIDEO
                            "mp3", "ogg", "aac", "opus", "m4a" -> SocialMediaType.AUDIO
                            else -> SocialMediaType.DOCUMENT
                        }
                    }
                }

                emit(SocialMediaFile(uri, name, size, path, app, mediaType))
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun computeChecksum(file: SocialMediaFile): String? =
        ChecksumUtils.calculateSHA256(context, file.uri)

    override suspend fun deleteFiles(uris: List<Uri>): Int {
        var deleted = 0
        uris.forEach { uri ->
            try {
                if (context.contentResolver.delete(uri, null, null) > 0) deleted++
            } catch (_: Exception) {}
        }
        return deleted
    }
}
