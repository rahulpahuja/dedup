package com.rp.dedup.core.drive

import android.content.Context
import android.util.Log
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages interactions with Google Drive API for scanning and cleaning duplicates.
 */
class GoogleDriveManager(private val context: Context) {

    private val gsonFactory = GsonFactory.getDefaultInstance()
    private val httpTransport = AndroidHttp.newCompatibleTransport()

    /**
     * Scans for duplicate files in Google Drive.
     * @param credential The authorized GoogleAccountCredential.
     */
    suspend fun scanForDuplicates(credential: GoogleAccountCredential): List<List<File>> = withContext(Dispatchers.IO) {
        val driveService = Drive.Builder(httpTransport, gsonFactory, credential)
            .setApplicationName("DeDup")
            .build()

        val allFiles = mutableListOf<File>()
        var pageToken: String? = null

        try {
            do {
                val result = driveService.files().list()
                    .setQ("trashed = false and mimeType != 'application/vnd.google-apps.folder'")
                    .setSpaces("drive")
                    .setFields("nextPageToken, files(id, name, size, md5Checksum, mimeType, thumbnailLink)")
                    .setPageToken(pageToken)
                    .execute()

                allFiles.addAll(result.files)
                pageToken = result.nextPageToken
            } while (pageToken != null)

            // Group files by MD5 Checksum to find duplicates
            val groups = allFiles.filter { it.md5Checksum != null }
                .groupBy { it.md5Checksum }
                .values
                .filter { it.size > 1 }

            return@withContext groups
        } catch (e: UserRecoverableAuthIOException) {
            throw e
        } catch (e: Exception) {
            Log.e("GoogleDriveManager", "Error scanning Google Drive", e)
            return@withContext emptyList()
        }
    }

    /**
     * Deletes a file from Google Drive (moves to trash).
     */
    suspend fun deleteFile(credential: GoogleAccountCredential, fileId: String): Boolean = withContext(Dispatchers.IO) {
        val driveService = Drive.Builder(httpTransport, gsonFactory, credential)
            .setApplicationName("DeDup")
            .build()

        return@withContext try {
            val content = File()
            content.trashed = true
            driveService.files().update(fileId, content).execute()
            true
        } catch (e: UserRecoverableAuthIOException) {
            throw e
        } catch (e: Exception) {
            Log.e("GoogleDriveManager", "Error deleting file $fileId", e)
            false
        }
    }

    companion object {
        val SCOPES = listOf(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_METADATA_READONLY)
    }
}
