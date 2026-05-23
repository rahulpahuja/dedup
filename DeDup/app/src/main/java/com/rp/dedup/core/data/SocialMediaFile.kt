package com.rp.dedup.core.data

import android.net.Uri

data class SocialMediaFile(
    val uri: Uri,
    val name: String,
    val size: Long,
    val path: String,
    val app: SocialApp,
    val mediaType: SocialMediaType,
    val checksum: String? = null
)

enum class SocialApp(val displayName: String) {
    WHATSAPP("WhatsApp"),
    TELEGRAM("Telegram")
}

enum class SocialMediaType(val displayName: String) {
    IMAGE("Images"),
    VIDEO("Videos"),
    AUDIO("Audio"),
    DOCUMENT("Documents")
}
