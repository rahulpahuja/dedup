package com.rp.dedup.core.deepoptimization

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.rp.dedup.core.model.*
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

interface WhatsAppCleanerRepository {
    suspend fun scanAll(): WhatsAppScanResult
    suspend fun deleteFiles(uris: List<Uri>): Int
}

class WhatsAppCleanerRepositoryImpl(private val context: Context) : WhatsAppCleanerRepository {

    private val largeSizeThreshold = 10L * 1024 * 1024 // 10 MB

    override suspend fun scanAll(): WhatsAppScanResult {
        val allFiles = mutableListOf<WhatsAppFile>()
        allFiles += queryImages()
        allFiles += queryVideos()
        allFiles += queryDocuments()
        allFiles += scanStatuses()

        val withChecksums = computeChecksums(allFiles)

        val images    = withChecksums.filter { it.folder == WhatsAppFolder.IMAGES }
        val videos    = withChecksums.filter { it.folder == WhatsAppFolder.VIDEOS }
        val sentImg   = withChecksums.filter { it.folder == WhatsAppFolder.SENT_IMAGES }
        val sentVid   = withChecksums.filter { it.folder == WhatsAppFolder.SENT_VIDEOS }
        val docs      = withChecksums.filter { it.folder == WhatsAppFolder.DOCUMENTS }
        val sentDocs  = withChecksums.filter { it.folder == WhatsAppFolder.SENT_DOCS }
        val statuses  = withChecksums.filter { it.folder == WhatsAppFolder.STATUSES }

        return WhatsAppScanResult(
            duplicateMedia    = findGroups(images + videos),
            duplicateStatuses = findGroups(statuses),
            duplicateDocs     = findGroups(docs),
            largeFiles        = withChecksums.filter { it.size >= largeSizeThreshold }
                                             .sortedByDescending { it.size },
            sentReceivedMatches = matchSentReceived(sentImg, images) +
                                  matchSentReceived(sentVid, videos) +
                                  matchSentReceived(sentDocs, docs)
        )
    }

    private fun queryImages(): List<WhatsAppFile> =
        queryMediaStore(
            uri     = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            pattern = "%/WhatsApp/Media/WhatsApp Images%"
        ) { path -> if (path.contains("/Sent/")) WhatsAppFolder.SENT_IMAGES else WhatsAppFolder.IMAGES }

    private fun queryVideos(): List<WhatsAppFile> =
        queryMediaStore(
            uri     = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            pattern = "%/WhatsApp/Media/WhatsApp Video%"
        ) { path -> if (path.contains("/Sent/")) WhatsAppFolder.SENT_VIDEOS else WhatsAppFolder.VIDEOS }

    private fun queryDocuments(): List<WhatsAppFile> =
        queryMediaStore(
            uri     = MediaStore.Files.getContentUri("external"),
            pattern = "%/WhatsApp/Media/WhatsApp Documents%"
        ) { path -> if (path.contains("/Sent/")) WhatsAppFolder.SENT_DOCS else WhatsAppFolder.DOCUMENTS }

    private fun queryMediaStore(
        uri: Uri,
        pattern: String,
        categorize: (String) -> WhatsAppFolder
    ): List<WhatsAppFile> {
        val result = mutableListOf<WhatsAppFile>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATA
        )
        context.contentResolver.query(
            uri, projection,
            "${MediaStore.MediaColumns.DATA} LIKE ?",
            arrayOf(pattern), null
        )?.use { cursor ->
            val idIdx   = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dataIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            while (cursor.moveToNext()) {
                val path = cursor.getString(dataIdx) ?: continue
                result += WhatsAppFile(
                    uri    = Uri.withAppendedPath(uri, cursor.getLong(idIdx).toString()),
                    name   = cursor.getString(nameIdx) ?: path.substringAfterLast('/'),
                    size   = cursor.getLong(sizeIdx),
                    path   = path,
                    folder = categorize(path)
                )
            }
        }
        return result
    }

    private fun scanStatuses(): List<WhatsAppFile> {
        val result = mutableListOf<WhatsAppFile>()
        val dirs = listOf(
            File(Environment.getExternalStorageDirectory(), "WhatsApp/Media/.Statuses"),
            File(Environment.getExternalStorageDirectory(), "Android/media/com.whatsapp/WhatsApp/Media/.Statuses")
        )
        dirs.forEach { dir ->
            if (!dir.exists()) return@forEach
            dir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    result += WhatsAppFile(
                        uri    = Uri.fromFile(file),
                        name   = file.name,
                        size   = file.length(),
                        path   = file.absolutePath,
                        folder = WhatsAppFolder.STATUSES
                    )
                }
            }
        }
        return result
    }

    private fun computeChecksums(files: List<WhatsAppFile>): List<WhatsAppFile> {
        val bySize = files.groupBy { it.size }
        return files.map { f ->
            val needsHash = (bySize[f.size]?.size ?: 0) > 1 || f.size >= largeSizeThreshold
            if (needsHash) f.copy(checksum = hash(f)) else f
        }
    }

    private fun hash(file: WhatsAppFile): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        if (file.uri.scheme == "file") {
            FileInputStream(file.path).use { stream ->
                val buf = ByteArray(8192)
                var n: Int
                while (stream.read(buf).also { n = it } != -1) digest.update(buf, 0, n)
            }
        } else {
            context.contentResolver.openInputStream(file.uri)?.use { stream ->
                val buf = ByteArray(8192)
                var n: Int
                while (stream.read(buf).also { n = it } != -1) digest.update(buf, 0, n)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) { null }

    private fun findGroups(files: List<WhatsAppFile>): List<List<WhatsAppFile>> =
        files.filter { it.checksum != null }
             .groupBy { it.checksum!! }
             .values
             .filter { it.size >= 2 }

    private fun matchSentReceived(
        sent: List<WhatsAppFile>,
        received: List<WhatsAppFile>
    ): List<SentReceivedMatch> {
        val receivedByHash = received
            .filter { it.checksum != null }
            .associateBy { it.checksum!! }
        return sent
            .filter { it.checksum != null }
            .mapNotNull { s -> receivedByHash[s.checksum!!]?.let { r -> SentReceivedMatch(s, r) } }
    }

    override suspend fun deleteFiles(uris: List<Uri>): Int {
        var count = 0
        uris.forEach { uri ->
            try {
                if (uri.scheme == "file") {
                    val file = File(uri.path ?: return@forEach)
                    if (file.delete()) count++
                } else {
                    if (context.contentResolver.delete(uri, null, null) > 0) count++
                }
            } catch (_: Exception) {}
        }
        return count
    }
}
