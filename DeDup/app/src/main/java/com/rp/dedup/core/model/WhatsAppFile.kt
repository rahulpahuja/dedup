package com.rp.dedup.core.model

import android.net.Uri

data class WhatsAppFile(
    val uri: Uri,
    val name: String,
    val size: Long,
    val path: String,
    val folder: WhatsAppFolder,
    val checksum: String? = null
)

enum class WhatsAppFolder(val label: String) {
    IMAGES("Images"),
    VIDEOS("Videos"),
    DOCUMENTS("Documents"),
    STATUSES("Statuses"),
    SENT_IMAGES("Sent Images"),
    SENT_VIDEOS("Sent Videos"),
    SENT_DOCS("Sent Docs")
}

enum class WhatsAppCleanerTab(val label: String) {
    DUPLICATE_MEDIA("Dup. Media"),
    DUPLICATE_STATUSES("Statuses"),
    DUPLICATE_DOCS("Documents"),
    LARGE_FILES("Large Files"),
    SENT_RECEIVED("Sent & Rcvd")
}

data class WhatsAppScanResult(
    val duplicateMedia: List<List<WhatsAppFile>>,
    val duplicateStatuses: List<List<WhatsAppFile>>,
    val duplicateDocs: List<List<WhatsAppFile>>,
    val largeFiles: List<WhatsAppFile>,
    val sentReceivedMatches: List<SentReceivedMatch>
)

data class SentReceivedMatch(
    val sent: WhatsAppFile,
    val received: WhatsAppFile
)
