package com.rp.dedup.core.model.state

import android.net.Uri
import com.rp.dedup.core.model.CleanupCategoryStats
import com.rp.dedup.core.model.EmptyFolder
import com.rp.dedup.core.model.FolderNode
import com.rp.dedup.core.model.SentReceivedMatch
import com.rp.dedup.core.model.SocialApp
import com.rp.dedup.core.model.SocialMediaFile
import com.rp.dedup.core.model.SocialMediaType
import com.rp.dedup.core.model.WhatsAppFile
import com.rp.dedup.core.model.WhatsAppFolder
import com.rp.dedup.core.model.WhatsAppScanResult
import com.rp.dedup.core.search.SmartJunkRepository
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class StateModelsTest {

    // ── BigFileMapState ────────────────────────────────────────────────────────

    @Test
    fun `BigFileMapState Idle is correct type`() {
        assertTrue(BigFileMapState.Idle is BigFileMapState)
    }

    @Test
    fun `BigFileMapState Scanning is correct type`() {
        assertTrue(BigFileMapState.Scanning is BigFileMapState)
    }

    @Test
    fun `BigFileMapState Results holds FolderNode`() {
        val root = FolderNode("path", "Storage", 1024L)
        val state = BigFileMapState.Results(root)
        assertEquals(root, state.root)
    }

    @Test
    fun `BigFileMapState Error holds message`() {
        val state = BigFileMapState.Error("scan failed")
        assertEquals("scan failed", state.message)
    }

    // ── CleaningProgress ───────────────────────────────────────────────────────

    @Test
    fun `CleaningProgress Scanning holds filesFound count`() {
        val p = CleaningProgress.Scanning(42)
        assertEquals(42, p.filesFound)
    }

    @Test
    fun `CleaningProgress Cleaning holds progress and bytesCleared`() {
        val p = CleaningProgress.Cleaning(0.75f, 8192L)
        assertEquals(0.75f, p.progress, 0.001f)
        assertEquals(8192L, p.bytesCleared)
    }

    @Test
    fun `CleaningProgress Finished holds totalBytesCleared`() {
        val p = CleaningProgress.Finished(16384L)
        assertEquals(16384L, p.totalBytesCleared)
    }

    @Test
    fun `CleaningProgress Error holds message`() {
        val p = CleaningProgress.Error("disk full")
        assertEquals("disk full", p.message)
    }

    // ── CleanupScreenState ─────────────────────────────────────────────────────

    @Test
    fun `CleanupScreenState defaults are all zero`() {
        val s = CleanupScreenState()
        assertEquals(0L, s.videoStats.totalSize)
        assertEquals(0L, s.archiveStats.totalSize)
        assertEquals(0L, s.appDownloadStats.totalSize)
        assertEquals(0L, s.oldDownloadStats.totalSize)
    }

    @Test
    fun `CleanupScreenState copies correctly`() {
        val stats = CleanupCategoryStats(totalSize = 4096L, count = 3, isLoading = false)
        val s = CleanupScreenState(videoStats = stats)
        assertEquals(4096L, s.videoStats.totalSize)
        assertEquals(3, s.videoStats.count)
    }

    // ── EmptyFolderState ───────────────────────────────────────────────────────

    @Test
    fun `EmptyFolderState Idle is correct type`() {
        assertTrue(EmptyFolderState.Idle is EmptyFolderState)
    }

    @Test
    fun `EmptyFolderState Scanning is correct type`() {
        assertTrue(EmptyFolderState.Scanning is EmptyFolderState)
    }

    @Test
    fun `EmptyFolderState Results holds folder list`() {
        val folders = listOf(
            EmptyFolder("/storage/empty1", "empty1", "/storage"),
            EmptyFolder("/storage/empty2", "empty2", "/storage")
        )
        val state = EmptyFolderState.Results(folders)
        assertEquals(2, state.folders.size)
    }

    @Test
    fun `EmptyFolderState Error holds message`() {
        val state = EmptyFolderState.Error("access denied")
        assertEquals("access denied", state.message)
    }

    // ── SocialMediaCleanerState ────────────────────────────────────────────────

    @Test
    fun `SocialMediaCleanerState Idle is correct type`() {
        assertTrue(SocialMediaCleanerState.Idle is SocialMediaCleanerState)
    }

    @Test
    fun `SocialMediaCleanerState ScanningFiles holds found count`() {
        val state = SocialMediaCleanerState.ScanningFiles(found = 15)
        assertEquals(15, state.found)
    }

    @Test
    fun `SocialMediaCleanerState ComputingChecksums holds progress`() {
        val state = SocialMediaCleanerState.ComputingChecksums(0.33f)
        assertEquals(0.33f, state.progress, 0.001f)
    }

    @Test
    fun `SocialMediaCleanerState Results holds groups and reclaimable bytes`() {
        val uri = mockk<Uri>()
        val file = SocialMediaFile(
            uri = uri, name = "img.jpg", size = 1024L,
            path = "/path", app = SocialApp.WHATSAPP,
            mediaType = SocialMediaType.IMAGE
        )
        val state = SocialMediaCleanerState.Results(
            duplicateGroups  = listOf(listOf(file, file)),
            reclaimableBytes = 1024L
        )
        assertEquals(1, state.duplicateGroups.size)
        assertEquals(1024L, state.reclaimableBytes)
    }

    @Test
    fun `SocialMediaCleanerState Error holds message`() {
        val state = SocialMediaCleanerState.Error("no permission")
        assertEquals("no permission", state.message)
    }

    // ── WhatsAppCleanerState ───────────────────────────────────────────────────

    @Test
    fun `WhatsAppCleanerState Idle is correct type`() {
        assertTrue(WhatsAppCleanerState.Idle is WhatsAppCleanerState)
    }

    @Test
    fun `WhatsAppCleanerState Scanning holds phase string`() {
        val state = WhatsAppCleanerState.Scanning("Indexing files…")
        assertEquals("Indexing files…", state.phase)
    }

    @Test
    fun `WhatsAppCleanerState Results holds scan data`() {
        val result = WhatsAppScanResult(
            duplicateMedia   = emptyList(),
            duplicateStatuses = emptyList(),
            duplicateDocs    = emptyList(),
            largeFiles       = emptyList(),
            sentReceivedMatches = emptyList()
        )
        val state = WhatsAppCleanerState.Results(result)
        assertEquals(result, state.data)
    }

    @Test
    fun `WhatsAppCleanerState Error holds message`() {
        val state = WhatsAppCleanerState.Error("media store unavailable")
        assertEquals("media store unavailable", state.message)
    }

    // ── FolderNode helpers ─────────────────────────────────────────────────────

    @Test
    fun `FolderNode isLeaf is true when no children`() {
        val node = FolderNode("path", "name", 512L)
        assertTrue(node.isLeaf)
    }

    @Test
    fun `FolderNode isLeaf is false when has children`() {
        val child = FolderNode("child", "child", 256L)
        val parent = FolderNode("parent", "parent", 512L, listOf(child))
        assertFalse(parent.isLeaf)
    }

    // ── WhatsAppScanResult ─────────────────────────────────────────────────────

    @Test
    fun `WhatsAppScanResult stores all fields`() {
        val file = WhatsAppFile(mockk(), "file.jpg", 1024L, "/path", WhatsAppFolder.IMAGES)
        val match = SentReceivedMatch(sent = file, received = file)
        val result = WhatsAppScanResult(
            duplicateMedia      = listOf(listOf(file)),
            duplicateStatuses   = emptyList(),
            duplicateDocs       = emptyList(),
            largeFiles          = listOf(file),
            sentReceivedMatches = listOf(match),
            redundantSentMedia  = listOf(file)
        )
        assertEquals(1, result.duplicateMedia.size)
        assertEquals(1, result.largeFiles.size)
        assertEquals(1, result.sentReceivedMatches.size)
        assertEquals(1, result.redundantSentMedia.size)
    }

    // ── CleanupCategoryStats ───────────────────────────────────────────────────

    @Test
    fun `CleanupCategoryStats default values are zero`() {
        val stats = CleanupCategoryStats()
        assertEquals(0L, stats.totalSize)
        assertEquals(0, stats.count)
        assertFalse(stats.isLoading)
        assertTrue(stats.files.isEmpty())
    }
}
