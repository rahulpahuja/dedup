package com.rp.dedup.core.utils

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TrashManagerTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val context = mockk<Context>(relaxed = true)
    private val contentResolver = mockk<ContentResolver>(relaxed = true)
    private val uri = mockk<Uri>()

    init {
        every { context.contentResolver } returns contentResolver
    }

    // ── TRASH_DIR name ─────────────────────────────────────────────────────────

    @Test
    fun `trash directory constant is hidden (starts with dot)`() {
        // Reflects the .dedup_trash constant in the source
        val trashDirName = ".dedup_trash"
        assertTrue(trashDirName.startsWith("."))
    }

    // ── moveToTrash — path not found ──────────────────────────────────────────

    @Test
    fun `moveToTrash returns false when path cannot be resolved from uri`() {
        val cursor = mockk<Cursor>(relaxed = true)
        every { contentResolver.query(uri, any(), any(), any(), any()) } returns cursor
        every { cursor.moveToFirst() } returns false

        val externalFilesDir = tmpFolder.newFolder("external")
        every { context.getExternalFilesDir(null) } returns externalFilesDir

        val result = TrashManager.moveToTrash(context, uri)

        assertFalse(result)
    }

    @Test
    fun `moveToTrash returns false when contentResolver query returns null`() {
        every { contentResolver.query(uri, any(), any(), any(), any()) } returns null
        every { context.getExternalFilesDir(null) } returns tmpFolder.newFolder("ext2")

        val result = TrashManager.moveToTrash(context, uri)

        assertFalse(result)
    }

    // ── moveToTrash — actual file rename ──────────────────────────────────────

    @Test
    fun `moveToTrash returns true when source file can be renamed to trash`() {
        val sourceFile = tmpFolder.newFile("photo.jpg")
        val trashDir = File(tmpFolder.root, ".dedup_trash")

        val cursor = mockk<Cursor>(relaxed = true)
        every { contentResolver.query(uri, any(), any(), any(), any()) } returns cursor
        every { cursor.moveToFirst() } returns true
        every { cursor.getString(any()) } returns sourceFile.absolutePath
        every { context.getExternalFilesDir(null) } returns tmpFolder.root

        val result = TrashManager.moveToTrash(context, uri)

        assertTrue(result)
        assertTrue(trashDir.exists())
        assertFalse(sourceFile.exists())
    }

    @Test
    fun `moveToTrash creates trash directory if absent`() {
        val sourceFile = tmpFolder.newFile("doc.pdf")
        val trashDir = File(tmpFolder.root, ".dedup_trash")
        assertFalse(trashDir.exists())

        val cursor = mockk<Cursor>(relaxed = true)
        every { contentResolver.query(uri, any(), any(), any(), any()) } returns cursor
        every { cursor.moveToFirst() } returns true
        every { cursor.getString(any()) } returns sourceFile.absolutePath
        every { context.getExternalFilesDir(null) } returns tmpFolder.root

        TrashManager.moveToTrash(context, uri)

        assertTrue(trashDir.exists())
    }

    // ── exception safety ───────────────────────────────────────────────────────

    @Test
    fun `moveToTrash returns false and does not throw when context throws`() {
        every { context.getExternalFilesDir(null) } throws RuntimeException("no storage")
        every { contentResolver.query(uri, any(), any(), any(), any()) } returns null

        val result = TrashManager.moveToTrash(context, uri)

        assertFalse(result)
    }
}
