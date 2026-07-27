package com.rp.dedup.core.ai

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = com.rp.dedup.util.TestApp::class)
class GeminiClassifierTest {

    private lateinit var classifier: GeminiClassifier

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        classifier = GeminiClassifier(app)
    }

    // ── isSupported ────────────────────────────────────────────────────────────

    @Test
    fun `isSupported always returns true`() {
        assertTrue(classifier.isSupported())
    }

    // ── isGeminiNanoActive ─────────────────────────────────────────────────────

    @Test
    @Config(sdk = [33])
    fun `isGeminiNanoActive returns false below API 37`() {
        assertFalse(classifier.isGeminiNanoActive())
    }

    // ── classifyFile — heuristic paths ────────────────────────────────────────

    @Test
    fun `classifyFile returns Temporary app cache for cache in name`() {
        assertEquals("Temporary app cache", classifier.classifyFile("image_cache.jpg", "100 KB"))
    }

    @Test
    fun `classifyFile returns Disposable temp file for tmp extension`() {
        assertEquals("Disposable temp file", classifier.classifyFile("data.tmp", "50 KB"))
    }

    @Test
    fun `classifyFile returns Disposable temp file for temp in name`() {
        assertEquals("Disposable temp file", classifier.classifyFile("tempfile.dat", "10 KB"))
    }

    @Test
    fun `classifyFile returns Low-res thumbnail for thumb in name and small size`() {
        assertEquals("Low-res thumbnail", classifier.classifyFile("thumb_img.jpg", "32 KB"))
    }

    @Test
    fun `classifyFile returns Low-res thumbnail for thumbnail in name and small size`() {
        assertEquals("Low-res thumbnail", classifier.classifyFile("thumbnail_001.png", "8 KB"))
    }

    @Test
    fun `classifyFile does not return thumbnail label when size is not small`() {
        // thumbnail in name but size is MB — should fall through to null
        assertNull(classifier.classifyFile("thumbnail_hd.jpg", "5 MB"))
    }

    @Test
    fun `classifyFile returns Screenshot for screenshot in name`() {
        assertEquals("Screenshot", classifier.classifyFile("screenshot_2026.png", "2 MB"))
    }

    @Test
    fun `classifyFile returns Meme or forwarded graphic for meme in name`() {
        assertEquals("Meme or forwarded graphic", classifier.classifyFile("funny_meme.jpg", "200 KB"))
    }

    @Test
    fun `classifyFile returns Low-quality WhatsApp media for whatsapp small file`() {
        assertEquals("Low-quality WhatsApp media", classifier.classifyFile("whatsapp_img.jpg", "45 KB"))
    }

    @Test
    fun `classifyFile does not return whatsapp label when file is not small`() {
        assertNull(classifier.classifyFile("whatsapp_video.mp4", "15 MB"))
    }

    @Test
    fun `classifyFile returns Received media file for received in name`() {
        assertEquals("Received media file", classifier.classifyFile("received_video.mp4", "10 MB"))
    }

    @Test
    fun `classifyFile returns Already-compressed duplicate for compressed in name`() {
        assertEquals("Already-compressed duplicate", classifier.classifyFile("photo_compressed.jpg", "300 KB"))
    }

    @Test
    fun `classifyFile returns Old backup file for backup in name`() {
        assertEquals("Old backup file", classifier.classifyFile("contacts_backup.vcf", "1 MB"))
    }

    @Test
    fun `classifyFile returns System log file for log extension`() {
        assertEquals("System log file", classifier.classifyFile("crash.log", "50 KB"))
    }

    @Test
    fun `classifyFile returns System log file for logcat in name`() {
        assertEquals("System log file", classifier.classifyFile("logcat_dump.txt", "200 KB"))
    }

    @Test
    fun `classifyFile returns Forwarded content for forward in name`() {
        assertEquals("Forwarded content", classifier.classifyFile("forward_clip.mp4", "5 MB"))
    }

    @Test
    fun `classifyFile returns Forwarded content for fwd in name`() {
        assertEquals("Forwarded content", classifier.classifyFile("fwd_image.jpg", "100 KB"))
    }

    @Test
    fun `classifyFile returns null for unrecognised file`() {
        assertNull(classifier.classifyFile("holiday_photo.jpg", "3 MB"))
    }

    // ── case insensitivity ────────────────────────────────────────────────────

    @Test
    fun `classifyFile matching is case-insensitive for Cache`() {
        assertEquals("Temporary app cache", classifier.classifyFile("Cache_File.dat", "10 KB"))
    }

    @Test
    fun `classifyFile matching is case-insensitive for SCREENSHOT`() {
        assertEquals("Screenshot", classifier.classifyFile("SCREENSHOT_001.png", "1 MB"))
    }

    @Test
    fun `classifyFile matching is case-insensitive for BACKUP`() {
        assertEquals("Old backup file", classifier.classifyFile("DB_BACKUP.sql", "500 KB"))
    }

    // ── sizeLabel matching ────────────────────────────────────────────────────

    @Test
    fun `classifyFile KB check is case-insensitive`() {
        assertEquals("Low-res thumbnail", classifier.classifyFile("thumb.jpg", "64 kb"))
    }

    // ── priority order ────────────────────────────────────────────────────────

    @Test
    fun `cache takes priority over other matches`() {
        // name contains both "cache" and "screenshot" — cache rule wins (listed first)
        assertEquals("Temporary app cache", classifier.classifyFile("screenshot_cache.jpg", "100 KB"))
    }
}
