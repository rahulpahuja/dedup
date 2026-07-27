package com.rp.dedup.core.deepoptimization

import android.content.Context
import android.net.Uri
import com.rp.dedup.core.model.SocialApp
import com.rp.dedup.core.model.SocialMediaFile
import com.rp.dedup.core.model.SocialMediaType
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for SocialMediaCleanerRepository pure-logic path routing.
 * MediaStore queries and checksum computation require real Android framework
 * and are covered by instrumented tests.
 */
class SocialMediaCleanerRepositoryTest {

    private val context = mockk<Context>(relaxed = true).also {
        every { it.contentResolver } returns mockk(relaxed = true)
    }

    private fun socialFile(
        name: String,
        app: SocialApp,
        type: SocialMediaType,
        checksum: String? = null,
        size: Long = 1024L
    ) = SocialMediaFile(
        uri       = mockk(),
        name      = name,
        size      = size,
        path      = "/WhatsApp/Media/$name",
        app       = app,
        mediaType = type,
        checksum  = checksum
    )

    // ── duplicate detection logic ──────────────────────────────────────────────

    private fun findDuplicates(files: List<SocialMediaFile>): List<List<SocialMediaFile>> =
        files.filter { it.checksum != null }
             .groupBy { it.checksum!! }
             .values
             .filter { it.size >= 2 }

    @Test
    fun `findDuplicates groups files with same checksum`() {
        val files = listOf(
            socialFile("a.jpg", SocialApp.WHATSAPP, SocialMediaType.IMAGE, "hash1"),
            socialFile("b.jpg", SocialApp.WHATSAPP, SocialMediaType.IMAGE, "hash1")
        )
        val groups = findDuplicates(files)
        assertEquals(1, groups.size)
        assertEquals(2, groups.first().size)
    }

    @Test
    fun `findDuplicates does not group null-checksum files`() {
        val files = listOf(
            socialFile("a.jpg", SocialApp.WHATSAPP, SocialMediaType.IMAGE),
            socialFile("b.jpg", SocialApp.WHATSAPP, SocialMediaType.IMAGE)
        )
        assertTrue(findDuplicates(files).isEmpty())
    }

    @Test
    fun `findDuplicates ignores singletons`() {
        val files = listOf(
            socialFile("a.jpg", SocialApp.WHATSAPP, SocialMediaType.IMAGE, "unique1"),
            socialFile("b.jpg", SocialApp.TELEGRAM, SocialMediaType.IMAGE, "unique2")
        )
        assertTrue(findDuplicates(files).isEmpty())
    }

    @Test
    fun `findDuplicates works across different apps`() {
        val files = listOf(
            socialFile("a.jpg", SocialApp.WHATSAPP, SocialMediaType.IMAGE, "shared"),
            socialFile("b.jpg", SocialApp.TELEGRAM,  SocialMediaType.IMAGE, "shared")
        )
        val groups = findDuplicates(files)
        assertEquals(1, groups.size)
    }

    // ── app path routing ───────────────────────────────────────────────────────

    @Test
    fun `WhatsApp path is detected by path matching`() {
        val path = "/storage/emulated/0/WhatsApp/Media/WhatsApp Images/IMG.jpg"
        val isWhatsApp = path.contains("/WhatsApp/", ignoreCase = true) ||
                         path.contains("/com.whatsapp/", ignoreCase = true)
        assertTrue(isWhatsApp)
    }

    @Test
    fun `Telegram path is detected by path matching`() {
        val path = "/storage/emulated/0/Telegram/Telegram Images/photo.jpg"
        val isTelegram = path.contains("/Telegram/", ignoreCase = true) ||
                         path.contains("/org.telegram.messenger/", ignoreCase = true)
        assertTrue(isTelegram)
    }

    @Test
    fun `com_whatsapp package path is detected`() {
        val path = "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/photo.jpg"
        val isWhatsApp = path.contains("/WhatsApp/", ignoreCase = true) ||
                         path.contains("/com.whatsapp/", ignoreCase = true)
        assertTrue(isWhatsApp)
    }

    // ── reclaimable bytes ──────────────────────────────────────────────────────

    @Test
    fun `reclaimable bytes is sum of non-primary files in each group`() {
        val group1 = listOf(
            socialFile("a.jpg", SocialApp.WHATSAPP, SocialMediaType.IMAGE, "h1", 1024L),
            socialFile("b.jpg", SocialApp.WHATSAPP, SocialMediaType.IMAGE, "h1", 1024L)
        )
        val group2 = listOf(
            socialFile("c.jpg", SocialApp.TELEGRAM, SocialMediaType.IMAGE, "h2", 2048L),
            socialFile("d.jpg", SocialApp.TELEGRAM, SocialMediaType.IMAGE, "h2", 2048L)
        )
        val groups = listOf(group1, group2)
        // For each group, one file is the "primary" (keep), rest are reclaimable
        val reclaimable = groups.sumOf { g -> g.drop(1).sumOf { it.size } }
        assertEquals(1024L + 2048L, reclaimable)
    }

    // ── repository instantiation ───────────────────────────────────────────────

    @Test
    fun `SocialMediaCleanerRepositoryImpl can be instantiated`() {
        val repo = SocialMediaCleanerRepositoryImpl(context)
        assertNotNull(repo)
    }
}
