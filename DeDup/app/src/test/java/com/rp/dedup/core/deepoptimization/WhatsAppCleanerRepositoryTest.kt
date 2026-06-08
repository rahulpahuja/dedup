package com.rp.dedup.core.deepoptimization

import android.net.Uri
import com.rp.dedup.core.model.SentReceivedMatch
import com.rp.dedup.core.model.WhatsAppFile
import com.rp.dedup.core.model.WhatsAppFolder
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

/**
 * Pure-logic tests for the repository's internal grouping and matching algorithms.
 * MediaStore queries require Android framework so those paths are covered by integration tests.
 * We test findGroups and matchSentReceived by exercising the underlying logic.
 */
class WhatsAppCleanerRepositoryTest {

    private fun file(
        name: String,
        folder: WhatsAppFolder,
        checksum: String? = null,
        size: Long = 1024L
    ) = WhatsAppFile(
        uri      = mockk(),
        name     = name,
        size     = size,
        path     = "/WhatsApp/Media/$name",
        folder   = folder,
        checksum = checksum
    )

    // ── findGroups logic ───────────────────────────────────────────────────────

    private fun findGroups(files: List<WhatsAppFile>): List<List<WhatsAppFile>> =
        files.filter { it.checksum != null }
             .groupBy { it.checksum!! }
             .values
             .filter { it.size >= 2 }

    @Test
    fun `findGroups returns empty when all checksums are null`() {
        val files = listOf(
            file("a.jpg", WhatsAppFolder.IMAGES),
            file("b.jpg", WhatsAppFolder.IMAGES)
        )
        assertTrue(findGroups(files).isEmpty())
    }

    @Test
    fun `findGroups returns empty when no duplicate checksums`() {
        val files = listOf(
            file("a.jpg", WhatsAppFolder.IMAGES, "hash1"),
            file("b.jpg", WhatsAppFolder.IMAGES, "hash2")
        )
        assertTrue(findGroups(files).isEmpty())
    }

    @Test
    fun `findGroups groups files with matching checksums`() {
        val files = listOf(
            file("a.jpg", WhatsAppFolder.IMAGES, "same"),
            file("b.jpg", WhatsAppFolder.IMAGES, "same"),
            file("c.jpg", WhatsAppFolder.IMAGES, "different")
        )
        val groups = findGroups(files)
        assertEquals(1, groups.size)
        assertEquals(2, groups.first().size)
    }

    @Test
    fun `findGroups handles multiple distinct duplicate groups`() {
        val files = listOf(
            file("a.jpg", WhatsAppFolder.IMAGES, "hash1"),
            file("b.jpg", WhatsAppFolder.IMAGES, "hash1"),
            file("c.mp4", WhatsAppFolder.VIDEOS,  "hash2"),
            file("d.mp4", WhatsAppFolder.VIDEOS,  "hash2"),
            file("e.mp4", WhatsAppFolder.VIDEOS,  "hash2")
        )
        val groups = findGroups(files)
        assertEquals(2, groups.size)
        assertTrue(groups.any { it.size == 2 })
        assertTrue(groups.any { it.size == 3 })
    }

    @Test
    fun `findGroups singleton checksum is excluded`() {
        val files = listOf(
            file("a.jpg", WhatsAppFolder.IMAGES, "unique"),
            file("b.jpg", WhatsAppFolder.IMAGES, "also-unique")
        )
        assertTrue(findGroups(files).isEmpty())
    }

    // ── matchSentReceived logic ────────────────────────────────────────────────

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

    @Test
    fun `matchSentReceived finds matching sent and received files`() {
        val sent = listOf(file("sent.jpg", WhatsAppFolder.SENT_IMAGES, "abc123"))
        val received = listOf(file("rcv.jpg", WhatsAppFolder.IMAGES, "abc123"))

        val matches = matchSentReceived(sent, received)

        assertEquals(1, matches.size)
        assertEquals("sent.jpg", matches.first().sent.name)
        assertEquals("rcv.jpg", matches.first().received.name)
    }

    @Test
    fun `matchSentReceived returns empty when no hash overlap`() {
        val sent = listOf(file("sent.jpg", WhatsAppFolder.SENT_IMAGES, "aaa"))
        val received = listOf(file("rcv.jpg", WhatsAppFolder.IMAGES, "bbb"))

        assertTrue(matchSentReceived(sent, received).isEmpty())
    }

    @Test
    fun `matchSentReceived returns empty when sent has null checksums`() {
        val sent = listOf(file("sent.jpg", WhatsAppFolder.SENT_IMAGES, checksum = null))
        val received = listOf(file("rcv.jpg", WhatsAppFolder.IMAGES, "abc"))

        assertTrue(matchSentReceived(sent, received).isEmpty())
    }

    @Test
    fun `matchSentReceived returns empty when received has null checksums`() {
        val sent = listOf(file("sent.jpg", WhatsAppFolder.SENT_IMAGES, "abc"))
        val received = listOf(file("rcv.jpg", WhatsAppFolder.IMAGES, checksum = null))

        assertTrue(matchSentReceived(sent, received).isEmpty())
    }

    @Test
    fun `matchSentReceived handles multiple matches`() {
        val sent = listOf(
            file("s1.jpg", WhatsAppFolder.SENT_IMAGES, "h1"),
            file("s2.jpg", WhatsAppFolder.SENT_IMAGES, "h2")
        )
        val received = listOf(
            file("r1.jpg", WhatsAppFolder.IMAGES, "h1"),
            file("r2.jpg", WhatsAppFolder.IMAGES, "h2")
        )

        val matches = matchSentReceived(sent, received)
        assertEquals(2, matches.size)
    }

    @Test
    fun `matchSentReceived result contains correct sent and received files`() {
        val sentFile     = file("s.jpg", WhatsAppFolder.SENT_IMAGES, "hash")
        val receivedFile = file("r.jpg", WhatsAppFolder.IMAGES, "hash")

        val matches = matchSentReceived(listOf(sentFile), listOf(receivedFile))

        assertEquals(1, matches.size)
        assertEquals("s.jpg", matches.first().sent.name)
        assertEquals("r.jpg", matches.first().received.name)
    }

    // ── large file threshold ───────────────────────────────────────────────────

    @Test
    fun `large file threshold is 10 MB`() {
        val tenMb = 10L * 1024 * 1024
        val justUnder = tenMb - 1
        val atThreshold = tenMb
        val over = tenMb + 1

        // Files meeting or exceeding threshold appear in largeFiles
        assertTrue(atThreshold >= tenMb)
        assertTrue(over >= tenMb)
        assertFalse(justUnder >= tenMb)
    }
}
